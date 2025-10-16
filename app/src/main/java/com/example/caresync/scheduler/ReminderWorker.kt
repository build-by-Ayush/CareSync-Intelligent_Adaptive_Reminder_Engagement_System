package com.example.caresync.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.util.Calendar

class ReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("reminderId", -1L)
        if (reminderId == -1L) return Result.failure()

        val repo = ReminderRepository(context)
        val reminder = repo.get(reminderId) ?: return Result.failure()

        if (!reminder.enabled) return Result.success()

        // ✅ USE DECISION PIPELINE
        val pipeline = NotificationDecisionPipeline(context)
        val decision = pipeline.shouldSendNotification(reminder)

        if (!decision.shouldSend) {
            // Log blocked event with reason
            repo.logEvent(
                reminderId,
                "BLOCKED",
                """{"rule": "${decision.blockingRule}", "reason": "${decision.reason}"}"""
            )

            // Reschedule if recurring
            if (isRecurring(reminder.recurrenceType.name)) {
                NotificationSchedulerImpl().scheduleReminder(context, reminder)
            }

            return Result.success()
        }

        // ✅ CREATE EVENT WITH RICH CONTEXT
        val event = createEventWithContext(reminderId, reminder)

        // ✅ INSERT EVENT DIRECTLY VIA DAO (NOT repo.logEvent)
        val eventDao = AppDatabase.get(context).reminderEventDao()
        eventDao.insert(event)

        // ✅ SHOW NOTIFICATION (with permission check)
        if (hasNotificationPermission(context)) {
            showNotification(reminder.id, reminder.title, reminder.notes ?: "Time to work!")
        } else {
            // Log permission denied
            repo.logEvent(
                reminderId,
                "BLOCKED",
                """{"rule": "NO_PERMISSION", "reason": "POST_NOTIFICATIONS permission not granted"}"""
            )
        }

        // Reschedule if recurring
        if (isRecurring(reminder.recurrenceType.name)) {
            NotificationSchedulerImpl().scheduleReminder(context, reminder)
        }

        return Result.success()
    }

    /**
     * Create event log with all context fields populated
     */
    private fun createEventWithContext(
        reminderId: Long,
        reminder: com.example.caresync.domain.ReminderSettings
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
            toneUsed = reminder.toneUri,
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
            }
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

    private fun showNotification(reminderId: Long, title: String, content: String) {
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
            .setContentIntent(createOpenAppIntent(reminderId))
            .setDeleteIntent(createDeleteIntent(reminderId))
            .addAction(createCompleteAction(reminderId))
            .addAction(createSnoozeAction(reminderId))
            .addAction(createDismissAction(reminderId))
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

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
    }
}