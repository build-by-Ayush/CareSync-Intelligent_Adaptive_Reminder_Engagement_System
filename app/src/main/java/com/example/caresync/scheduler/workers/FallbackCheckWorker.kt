package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.scheduler.NotificationDecisionPipeline

/**
 * Fallback Check Worker - Ensures minimum notification quota is met
 *
 * Responsibilities:
 * - Checks if ML already fired notification in this time slot
 * - If NOT: Fires fallback notification to meet minimum quota
 * - If YES: Skips (ML already handled this slot)
 *
 * Logic:
 * - Does NOT call ML model (just checks database)
 * - Runs at end of each time slot (e.g., every 10 min for "6 per hour")
 * - Scheduled via AlarmManager (can be <15 min)
 *
 * Triggered by: FallbackCheckReceiver (AlarmManager alarm)
 */
class FallbackCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo = ReminderRepository(context)
    private val eventDao = AppDatabase.get(context).reminderEventDao()

    override suspend fun doWork(): Result {
        // Get parameters from input data
        val reminderId = inputData.getLong("reminderId", -1L)
        val slotStart = inputData.getLong("slotStart", -1L)
        val slotEnd = inputData.getLong("slotEnd", -1L)

        if (reminderId == -1L || slotStart == -1L || slotEnd == -1L) {
            Log.e(TAG, "Invalid input data")
            return Result.failure()
        }

        Log.d(TAG, "📊 Fallback check for task $reminderId (slot: ${formatTime(slotStart)} - ${formatTime(slotEnd)})")

        try {
            // Load reminder
            val reminder = repo.get(reminderId) ?: run {
                Log.e(TAG, "Task $reminderId not found")
                return Result.failure()
            }

            // Check if task still enabled
            if (!reminder.enabled) {
                Log.d(TAG, "Task $reminderId disabled, skipping fallback")
                return Result.success()
            }

            // STEP 1: Check if ML already fired in this slot
            val events = eventDao.getEventsBetween(reminderId, slotStart, slotEnd)
            val mlAlreadyFired = events.any { it.eventType == EventTypes.TRIGGERED }

            if (mlAlreadyFired) {
                // ML handled this slot, skip fallback
                Log.d(TAG, "✅ ML already fired in this slot, skipping fallback")
                logFallbackEvent(reminderId, "FALLBACK_SKIPPED", slotStart, slotEnd)
                return Result.success()
            }

            // STEP 2: Slot is empty, fire fallback notification
            Log.d(TAG, "⚠️ Slot empty, firing fallback notification")

            // STEP 3: Run through decision pipeline (still check blocking rules)
            val pipeline = NotificationDecisionPipeline(context)
            val decision = pipeline.shouldSendNotification(
                reminder,
                mlPrediction = null,  // No ML prediction for fallback
                mlConfidence = null,
                bypassCooldown = true,
                triggerSource = "FALLBACK"
            )

            if (decision.shouldSend) {
                // ✅ GENERATE MESSAGE AND GET TONE
                val (personalizedMessage, actualTone) = try {
                    com.example.caresync.messaging.MessageGenerator(context).generateMessage(reminder)
                } catch (e: Exception) {
                    Pair(reminder.notes ?: "Time to work!", "AUTO")
                }

                // ✅ CREATE EVENT WITH ACTUAL TONE
                val event = createEventWithContext(
                    reminderId = reminderId,
                    reminder = reminder,
                    actualTone = actualTone,
                    slotStart = slotStart,
                    slotEnd = slotEnd
                )

                eventDao.insert(event)

                // Fire fallback notification
                com.example.caresync.scheduler.ReminderWorker.showNotificationFromML(
                    context,
                    reminderId,
                    reminder.title,
                    personalizedMessage
                )

                Log.d(TAG, "🔔 Fallback notification fired with personalized message")
            } else {
                // Blocked by pipeline
                Log.d(TAG, "🚫 Fallback blocked by ${decision.blockingRule}: ${decision.reason}")
                logFallbackEvent(reminderId, "FALLBACK_BLOCKED", slotStart, slotEnd, decision.blockingRule)
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Fallback check failed for task $reminderId", e)
            return Result.retry()
        }
    }

    /**
     * Log fallback event (for debugging and analytics)
     */
    private suspend fun logFallbackEvent(
        reminderId: Long,
        eventType: String,
        slotStart: Long,
        slotEnd: Long,
        reason: String? = null
    ) {
        val metadata = """{"slotStart":$slotStart,"slotEnd":$slotEnd,"reason":"${reason ?: "N/A"}"}"""
        repo.logEvent(reminderId, eventType, metadata)
    }

    /**
     * Format milliseconds to HH:mm:ss
     */
    private fun formatTime(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Create event with fallback metadata
     */
    private fun createEventWithContext(
        reminderId: Long,
        reminder: com.example.caresync.domain.ReminderSettings,
        actualTone: String,
        slotStart: Long,
        slotEnd: Long
    ): com.example.caresync.data.ReminderEventEntity {
        val now = java.util.Calendar.getInstance()

        return com.example.caresync.data.ReminderEventEntity(
            reminderId = reminderId,
            eventType = com.example.caresync.domain.EventTypes.TRIGGERED,
            timestamp = System.currentTimeMillis(),

            // Time context
            hourOfDay = now.get(java.util.Calendar.HOUR_OF_DAY),
            dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK) - 1,
            isWeekend = now.get(java.util.Calendar.DAY_OF_WEEK) in listOf(
                java.util.Calendar.SATURDAY,
                java.util.Calendar.SUNDAY
            ),

            // Device context (basic - fallback doesn't collect full context)
            batteryLevel = getBatteryLevel(context),

            // Notification details
            notificationPriority = reminder.priority.name,
            notificationMethod = reminder.notifyMethods.firstOrNull()?.name ?: "PUSH",

            // ✅ USE ACTUAL TONE FROM MESSAGE GENERATOR
            toneUsed = actualTone,

            vibrationUsed = reminder.vibration,

            // ML model data
            modelConfidence = null,  // No ML model for fallback
            triggerSource = "FALLBACK",

            // Metadata JSON
            metadataJson = """{"source":"FALLBACK","slotStart":$slotStart,"slotEnd":$slotEnd}"""
        )
    }

    /**
     * Get battery level
     */
    private fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "FallbackCheckWorker"
    }
}
