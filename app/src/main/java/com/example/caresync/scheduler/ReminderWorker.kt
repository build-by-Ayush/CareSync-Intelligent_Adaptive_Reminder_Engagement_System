package com.example.caresync.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.R
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.receivers.CompleteTaskReceiver
import com.example.caresync.receivers.DismissTaskReceiver
import com.example.caresync.receivers.NotificationDeletedReceiver
import com.example.caresync.receivers.SnoozeTaskReceiver
import com.example.caresync.scheduler.ReminderWorker.Companion.createOpenAppIntent
import java.util.Calendar

class ReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("reminderId", -1L)
        val isSnooze = inputData.getBoolean("isSnooze", false)  // ✅ NEW

        // ✅ ADD THESE LOGS
        Log.d("REMINDER_WORKER", "=== doWork() START ===")
        Log.d("REMINDER_WORKER", "reminderId: $reminderId")
        Log.d("REMINDER_WORKER", "isSnooze: $isSnooze")  // Check if flag is true

        if (reminderId == -1L) return Result.failure()

        // ✅ NEW: Handle snooze separately (bypass all checks)
        if (isSnooze) {
            return handleSnoozeNotification(reminderId)
        }

        // ✅ NORMAL NOTIFICATION FLOW (existing logic)
        val repo = ReminderRepository(context)
        val reminder = repo.get(reminderId) ?: return Result.failure()

        if (!reminder.enabled) return Result.success()

        // ✅ USE DECISION PIPELINE
        val pipeline = NotificationDecisionPipeline(context)
        val isSnoozed = checkIfSnoozed(reminderId)
        val triggerSource = if (isSnoozed) "SNOOZE" else "SCHEDULED"

        val decision = pipeline.shouldSendNotification(
            reminder,
            bypassCooldown = true,
            triggerSource = triggerSource
        )

        if (!decision.shouldSend) {
            repo.logEvent(
                reminderId,
                "BLOCKED",
                """{"rule": "${decision.blockingRule}", "reason": "${decision.reason}"}"""
            )

            if (isRecurring(reminder.recurrenceType.name)) {
                rescheduleNextOccurrence(reminder)
            }

            return Result.success()
        }

        // ✅ GENERATE MESSAGE AND GET TONE USED
        val (personalizedMessage, actualTone) = try {
            com.example.caresync.messaging.MessageGenerator(context).generateMessage(reminder)
        } catch (e: Exception) {
            Pair(reminder.notes ?: "Time to work!", "AUTO")
        }

        // ✅ CREATE EVENT WITH ACTUAL TONE
        val event = createEventWithContext(reminderId, reminder, actualTone)

        // ✅ INSERT EVENT
        val eventDao = AppDatabase.get(context).reminderEventDao()
        eventDao.insert(event)

        // ✅ SHOW NOTIFICATION
        if (hasNotificationPermission(context)) {
            showNotification(reminder.id, reminder.title, personalizedMessage, reminder)

            // ✅ ADD VOICE SUPPORT
            if (reminder.notifyMethods.contains(com.example.caresync.domain.NotifyMethod.VOICE)) {
                try {
                    val voiceManager = com.example.caresync.voice.VoiceNotificationManager(context)
                    voiceManager.speakNotification(
                        reminder = reminder,
                        textMessage = personalizedMessage,
                        tone = com.example.caresync.messaging.MessageTone.valueOf(actualTone),
                        voiceModel = reminder.voiceModel ?: "Default"
                    )
                    Log.d("REMINDER_WORKER", "🔊 Voice notification spoken")
                } catch (e: Exception) {
                    Log.e("REMINDER_WORKER", "Voice notification failed", e)
                }
            }
        } else {
            repo.logEvent(
                reminderId,
                "BLOCKED",
                """{"rule":"NO_PERMISSION","reason":"POST_NOTIFICATIONS permission not granted"}"""
            )
        }

        // ✅ RESCHEDULE NEXT OCCURRENCE
        if (isRecurring(reminder.recurrenceType.name)) {
            val hasWeekdays = reminder.daysOfWeek?.isNotEmpty() == true

            if (reminder.recurrenceType.name == "DAILY" && !hasWeekdays) {
                val updated = reminder.copy(enabled = false)
                repo.upsert(updated)
                Log.d("REMINDER_WORKER", "⏹️ One-time alarm completed, task auto-disabled: ${reminder.id}")
            } else {
                rescheduleNextOccurrence(reminder)
            }
        }

        return Result.success()
    }

    /**
     * ✅ UPDATED: Handle snooze notification + log event with isSnoozedRetrigger flag
     */
    private suspend fun handleSnoozeNotification(reminderId: Long): Result {
        Log.d("REMINDER_WORKER", "🔔 Processing SNOOZE notification for task $reminderId")

        val repo = ReminderRepository(context)
        val reminder = repo.get(reminderId) ?: return Result.failure()

        // Generate message
        val (personalizedMessage, actualTone) = try {
            com.example.caresync.messaging.MessageGenerator(context).generateMessage(reminder)
        } catch (e: Exception) {
            Pair(reminder.notes ?: "Time to work!", "AUTO")
        }

        // ✅ NEW: Log TRIGGERED event with isSnoozedRetrigger = true
        val event = createEventWithContext(
            reminderId = reminderId,
            reminder = reminder,
            actualTone = actualTone,
            isSnoozedRetrigger = true  // ✅ CRITICAL: Mark as snooze re-trigger
        )
        val eventDao = AppDatabase.get(context).reminderEventDao()
        eventDao.insert(event)
        Log.d("REMINDER_WORKER", "✅ Logged TRIGGERED event (isSnoozedRetrigger=true)")

        // Show notification (no pipeline checks!)
        if (hasNotificationPermission(context)) {
            showNotification(reminder.id, reminder.title, personalizedMessage, reminder)
            Log.d("REMINDER_WORKER", "✅ Snooze notification shown")

            // Voice notification if enabled
            if (reminder.notifyMethods.contains(com.example.caresync.domain.NotifyMethod.VOICE)) {
                try {
                    val voiceManager = com.example.caresync.voice.VoiceNotificationManager(context)
                    voiceManager.speakNotification(
                        reminder = reminder,
                        textMessage = personalizedMessage,
                        tone = com.example.caresync.messaging.MessageTone.valueOf(actualTone),
                        voiceModel = reminder.voiceModel ?: "Default"
                    )
                    Log.d("REMINDER_WORKER", "🔊 Voice notification spoken")
                } catch (e: Exception) {
                    Log.e("REMINDER_WORKER", "Voice notification failed", e)
                }
            }
        }
        return Result.success()
    }

    /**
     * ✅ NEW: Reschedule next occurrence using SchedulingCoordinator
     */
    private suspend fun rescheduleNextOccurrence(reminder: com.example.caresync.domain.ReminderSettings) {
        try {
            val coordinator = SchedulingCoordinator(context)
            val schedulingInfo = coordinator.scheduleTask(reminder)
            Log.d("REMINDER_WORKER", "Rescheduled: $schedulingInfo")
        } catch (e: Exception) {
            Log.e("REMINDER_WORKER", "Failed to reschedule", e)
        }
    }

    /**
     * ✅ UPDATED: Create event log with isSnoozedRetrigger parameter
     */
    private fun createEventWithContext(
        reminderId: Long,
        reminder: com.example.caresync.domain.ReminderSettings,
        actualTone: String,
        isSnoozedRetrigger: Boolean = false  // ✅ NEW PARAMETER (defaults to false)
    ): com.example.caresync.data.ReminderEventEntity {
        val now = Calendar.getInstance()

        return com.example.caresync.data.ReminderEventEntity(
            reminderId = reminderId,
            eventType = EventTypes.TRIGGERED,
            timestamp = System.currentTimeMillis(),

            // Time context
            hourOfDay = now.get(Calendar.HOUR_OF_DAY),
            dayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1,
            isWeekend = now.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY),

            // Notification details (from reminder settings)
            notificationPriority = reminder.priority.name,
            notificationMethod = reminder.notifyMethods.firstOrNull()?.name ?: "PUSH",
            toneUsed = actualTone,
            vibrationUsed = reminder.vibration,

            // Device context
            deviceState = getDeviceState(context),
            activeAppPackage = null,
            activeAppCategory = null,
            screenTimeMinutes = null,
            batteryLevel = getBatteryLevel(context),

            // ML model data
            triggerSource = when (reminder.triggerMode.name) {
                "MODEL_ASSISTED" -> "MODEL"
                "FIXED_TIME" -> "SCHEDULER"
                "MANUAL" -> "USER_MANUAL"
                else -> "SCHEDULER"
            },

            // ✅ NEW: Snooze re-trigger flag
            isSnoozedRetrigger = isSnoozedRetrigger
        )
    }

    /**
     * Check if app has notification permission (Android 13+)
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private suspend fun showNotification(reminderId: Long, title: String, content: String, reminder: com.example.caresync.domain.ReminderSettings) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        // Create channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for scheduled tasks"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // ✅ FIX: Get snooze count from database
        val recentSnoozes = try {
            val eventDao = AppDatabase.get(context).reminderEventDao()
            val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            val events = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())

            val lastTriggeredIndex = events.indexOfLast { it.eventType == EventTypes.TRIGGERED }
            if (lastTriggeredIndex >= 0) {
                events.drop(lastTriggeredIndex + 1).count { it.eventType == EventTypes.SNOOZED }
            } else {
                events.count { it.eventType == EventTypes.SNOOZED }
            }
        } catch (e: Exception) {
            0
        }

        val maxSnoozes = reminder.maxSnoozes

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📋 $title")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(createOpenAppIntent(reminderId))
            .setDeleteIntent(createDeleteIntent(reminderId))
            .addAction(createCompleteAction(reminderId))

        // ✅ ONLY add snooze button if under limit
        if (recentSnoozes < maxSnoozes) {
            notificationBuilder.addAction(createSnoozeAction(reminderId))
            Log.d("NOTIFICATION", "✅ Snooze button added ($recentSnoozes/$maxSnoozes)")
        } else {
            Log.d("NOTIFICATION", "🚫 Snooze button removed (limit reached: $recentSnoozes/$maxSnoozes)")
        }

        notificationBuilder
            .addAction(createDismissAction(reminderId))
            .setVibrate(longArrayOf(0, 500, 200, 500))

        val notification = notificationBuilder.build()
        notificationManager.notify(reminderId.toInt(), notification)
    }

    private fun createOpenAppIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, com.example.caresync.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminderId", reminderId)
        }
        return PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createCompleteAction(reminderId: Long): NotificationCompat.Action {
        val intent = Intent(context, CompleteTaskReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 1).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(0, "✓ Complete", pendingIntent)
    }

    private fun createSnoozeAction(reminderId: Long): NotificationCompat.Action {
        val intent = Intent(context, SnoozeTaskReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 2).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(0, "⏰ Snooze", pendingIntent)
    }

    private fun createDismissAction(reminderId: Long): NotificationCompat.Action {
        val intent = Intent(context, DismissTaskReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 3).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action(0, "✕ Dismiss", pendingIntent)
    }

    private fun createDeleteIntent(reminderId: Long): PendingIntent {
        val intent = Intent(context, NotificationDeletedReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            (reminderId * 10 + 4).toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun isRecurring(recurrenceType: String): Boolean {
        return recurrenceType != "NONE"
    }

    private fun getDeviceState(context: Context): String {
        return "SCREEN_ON"
    }

    private fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
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
        const val CHANNEL_ID = "task_reminders"

        /**
         * Show notification from ML check worker
         * Static function so MLCheckWorker can call it
         */
        fun showNotificationFromML(
            context: Context,
            reminderId: Long,
            title: String,
            content: String,
            reminder: com.example.caresync.domain.ReminderSettings? = null,  // ✅ ADD
            actualTone: String = "AUTO"  // ✅ ADD
        ) {
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            // Create channel (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for scheduled tasks"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Build notification
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("📋 $title")
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setContentIntent(createOpenAppIntent(context, reminderId))
                .setDeleteIntent(createDeleteIntent(context, reminderId))
                .addAction(createCompleteAction(context, reminderId))
                .addAction(createSnoozeAction(context, reminderId))
                .addAction(createDismissAction(context, reminderId))
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            notificationManager.notify(reminderId.toInt(), notification)

            // ✅ ADD VOICE SUPPORT
            if (reminder != null && reminder.notifyMethods.contains(com.example.caresync.domain.NotifyMethod.VOICE)) {
                try {
                    val voiceManager = com.example.caresync.voice.VoiceNotificationManager(context)
                    voiceManager.speakNotification(
                        reminder = reminder,
                        textMessage = content,
                        tone = com.example.caresync.messaging.MessageTone.valueOf(actualTone),
                        voiceModel = reminder.voiceModel ?: "Default"
                    )
                    Log.d("ML_NOTIFICATION", "🔊 Voice notification spoken")
                } catch (e: Exception) {
                    Log.e("ML_NOTIFICATION", "Voice notification failed", e)
                }
            }
        }

        // Also make these helper functions static
        private fun createOpenAppIntent(context: Context, reminderId: Long): PendingIntent {
            val intent = Intent(context, com.example.caresync.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("reminderId", reminderId)
            }
            return PendingIntent.getActivity(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun createCompleteAction(context: Context, reminderId: Long): NotificationCompat.Action {
            val intent = Intent(context, CompleteTaskReceiver::class.java).apply {
                putExtra("reminderId", reminderId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId * 10 + 1).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action(0, "✓ Complete", pendingIntent)
        }

        private fun createSnoozeAction(context: Context, reminderId: Long): NotificationCompat.Action {
            val intent = Intent(context, SnoozeTaskReceiver::class.java).apply {
                putExtra("reminderId", reminderId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId * 10 + 2).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action(0, "⏰ Snooze", pendingIntent)
        }

        private fun createDismissAction(context: Context, reminderId: Long): NotificationCompat.Action {
            val intent = Intent(context, DismissTaskReceiver::class.java).apply {
                putExtra("reminderId", reminderId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (reminderId * 10 + 3).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Action(0, "✕ Dismiss", pendingIntent)
        }

        private fun createDeleteIntent(context: Context, reminderId: Long): PendingIntent {
            val intent = Intent(context, NotificationDeletedReceiver::class.java).apply {
                putExtra("reminderId", reminderId)
            }
            return PendingIntent.getBroadcast(
                context,
                (reminderId * 10 + 4).toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    /**
     * Check if this notification is from a snooze
     * (Check if last event was SNOOZED within reasonable time)
     */
    private suspend fun checkIfSnoozed(reminderId: Long): Boolean {
        return try {
            val eventDao = AppDatabase.get(context).reminderEventDao()
            val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000L
            val recentEvents = eventDao.getEventsBetween(
                reminderId,
                fiveMinutesAgo,
                System.currentTimeMillis()
            )

            // If last event was SNOOZED, this is a snoozed notification
            val lastEvent = recentEvents.maxByOrNull { it.timestamp }
            val isSnoozed = lastEvent?.eventType == EventTypes.SNOOZED

            if (isSnoozed) {
                Log.d("REMINDER_WORKER", "✅ Detected snoozed notification (last event: SNOOZED)")
            }

            isSnoozed
        } catch (e: Exception) {
            Log.e("REMINDER_WORKER", "Failed to check snooze status", e)
            false  // Default to not snoozed if check fails
        }
    }
}