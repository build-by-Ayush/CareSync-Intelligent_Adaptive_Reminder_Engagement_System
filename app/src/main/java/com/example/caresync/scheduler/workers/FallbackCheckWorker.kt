package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.intelligence.OptimalTimeLearner  // ✅ ADD
import com.example.caresync.scheduler.NotificationDecisionPipeline
import java.util.Calendar

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
 * - ✅ NEW: Uses learned preferred times for smart fallback scheduling
 *
 * Triggered by: FallbackCheckReceiver (AlarmManager alarm)
 */
class FallbackCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repo = ReminderRepository(context)
    private val eventDao = AppDatabase.get(context).reminderEventDao()
    private val learner = OptimalTimeLearner(context)  // ✅ ADD

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

            // ✅ NEW: Check if current time is a good time based on learned data
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isGoodTime = checkIfGoodTime(reminder, currentHour)

            // ✅ STEP 3: Run through decision pipeline (still check blocking rules)
            val pipeline = NotificationDecisionPipeline(context)
            val decision = pipeline.shouldSendNotification(
                reminder = reminder,
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
                    slotEnd = slotEnd,
                    isSmartFallback = isGoodTime
                )

                eventDao.insert(event)

                // ✅ Fire fallback notification with all parameters
                com.example.caresync.scheduler.ReminderWorker.showNotificationFromML(
                    context = context,
                    reminderId = reminderId,
                    title = reminder.title,
                    content = personalizedMessage,
                    reminder = reminder,
                    actualTone = actualTone
                )

                val fallbackType = if (isGoodTime) "smart (learned time)" else "random"
                Log.d(TAG, "🔔 Fallback notification fired ($fallbackType) at hour $currentHour")
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
     * ✅ NEW: Check if current hour is a good time based on learned data
     *
     * Returns true if:
     * - Adaptive layer enabled AND
     * - Current hour is in learned preferred times
     */
    private suspend fun checkIfGoodTime(
        reminder: com.example.caresync.domain.ReminderSettings,
        currentHour: Int
    ): Boolean {
        // ✅ CHANGED: Always use smart learning, no toggle check
        // Smart time learning is independent of the optimization toggle

        return try {
            // Check if current hour is a preferred time
            learner.isGoodTimeNow(reminder.id)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check if good time, assuming false", e)
            false
        }
    }

    /**
     * ✅ NEW: Get suggested fallback hour from learned data
     *
     * Used when we want to schedule a future fallback at a better time
     * (Currently logged for analytics, not actively used)
     */
    private suspend fun getSuggestedFallbackHour(
        reminder: com.example.caresync.domain.ReminderSettings
    ): Int? {
        // ✅ CHANGED: Always use smart learning, no toggle check
        // Smart time learning is independent of the optimization toggle

        return try {
            val bestHours = learner.getBestHours(
                reminderId = reminder.id,
                minConfidence = 0.3f,
                minSamples = 2,
                limit = 1
            )
            bestHours.firstOrNull()?.hourOfDay
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get suggested fallback hour", e)
            null
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
     * ✅ UPDATED: Add isSmartFallback flag to metadata
     */
    private fun createEventWithContext(
        reminderId: Long,
        reminder: com.example.caresync.domain.ReminderSettings,
        actualTone: String,
        slotStart: Long,
        slotEnd: Long,
        isSmartFallback: Boolean  // ✅ ADD
    ): com.example.caresync.data.ReminderEventEntity {
        val now = Calendar.getInstance()

        return com.example.caresync.data.ReminderEventEntity(
            reminderId = reminderId,
            eventType = EventTypes.TRIGGERED,
            timestamp = System.currentTimeMillis(),

            // Time context
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1,
            isWeekend = now.get(Calendar.DAY_OF_WEEK) in listOf(
                Calendar.SATURDAY,
                Calendar.SUNDAY
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
            triggerSource = if (isSmartFallback) "FALLBACK_SMART" else "FALLBACK",  // ✅ TRACK TYPE

            // ✅ UPDATED: Metadata JSON with smart flag
            metadataJson = """{"source":"FALLBACK","slotStart":$slotStart,"slotEnd":$slotEnd,"smartFallback":$isSmartFallback}"""
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
