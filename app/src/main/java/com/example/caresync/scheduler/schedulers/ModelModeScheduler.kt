package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.IntervalUnit
import com.example.caresync.scheduler.workers.MLCheckWorker  // ✅ ADD THIS IMPORT
import java.util.concurrent.TimeUnit

/**
 * LAYER 3A: Model Mode Scheduler
 *
 * Responsibilities:
 * - Set up periodic ML checks (every hour)
 * - ML model decides when to fire notifications
 * - Enforce minimum occurrence quotas
 *
 * Logic:
 * - Schedules MLCheckWorker to run every hour
 * - Worker calls ML model with device context
 * - Worker checks if min occurrence quota met
 * - If (ML says YES) OR (quota not met): fire notification
 */
class ModelModeScheduler(private val context: Context) {

    fun schedule(reminder: ReminderSettings) {
        Log.d("MODEL_SCHEDULER", "Setting up ML checks for task: ${reminder.id}")

        // Determine check interval (default: every hour)
        val checkInterval = getCheckInterval(reminder)

        // Create periodic ML check worker
        val checkRequest = PeriodicWorkRequestBuilder<MLCheckWorker>(
            checkInterval.first,
            checkInterval.second
        )
            .setInputData(
                workDataOf(
                    "reminderId" to reminder.id,
                    "minOccurrence" to (reminder.repeatInterval ?: 0),
                    "intervalUnit" to (reminder.repeatIntervalUnit?.name ?: "HOUR")
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag("ml-check-${reminder.id}")
            .build()

        // Enqueue ML check worker
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ml-check-${reminder.id}",
            ExistingPeriodicWorkPolicy.KEEP,
            checkRequest
        )

        Log.d("MODEL_SCHEDULER", "✅ ML check worker scheduled: checks every ${checkInterval.first} ${checkInterval.second}")

        // ✅ NEW: Only schedule fallback if min occurrence > 0
        val minOccurrence = reminder.repeatInterval ?: 0
        if (minOccurrence > 0) {
            scheduleFallbackChecks(context, reminder)
            Log.d("MODEL_SCHEDULER", "✅ Fallback safety net enabled: $minOccurrence notifications per ${reminder.repeatIntervalUnit ?: "DAY"}")
            Log.d("MODEL_SCHEDULER", "   (Fallback fires if ML doesn't meet minimum)")
        } else {
            Log.d("MODEL_SCHEDULER", "✅ Pure ML mode: No fallback (min occurrence = 0)")
            Log.d("MODEL_SCHEDULER", "   (Notifications ONLY when ML model predicts)")
        }

    }


    /**
     * Determine how often to run ML checks based on min occurrence settings
     */
    private fun getCheckInterval(reminder: ReminderSettings): Pair<Long, TimeUnit> {
        val minOccurrence = reminder.repeatInterval ?: 1
        val unit = reminder.repeatIntervalUnit ?: IntervalUnit.HOUR

        // ✅ FIXED: Removed WEEK, added MINUTE
        return when (unit) {
            IntervalUnit.MINUTE -> {
                // Check every X minutes (minimum 15 minutes for WorkManager)
                val minutes = maxOf(15, minOccurrence)  // WorkManager minimum is 15 min
                Pair(minutes.toLong(), TimeUnit.MINUTES)
            }
            IntervalUnit.HOUR -> {
                // Check every hour for hourly quotas
                Pair(1, TimeUnit.HOURS)
            }
            IntervalUnit.DAY -> {
                // For daily quotas, check every 2-4 hours
                val checkEvery = maxOf(1, 24 / (minOccurrence * 2))
                Pair(checkEvery.toLong(), TimeUnit.HOURS)
            }
        }
    }

    /**
     * Schedule fallback checks via AlarmManager
     * These ensure minimum quota is met even if ML doesn't fire
     */
    fun scheduleFallbackChecks(context: Context, reminder: ReminderSettings) {
        val minOccurrence = reminder.repeatInterval ?: 0
        if (minOccurrence <= 0) {
            Log.d("MODEL_SCHEDULER", "No min occurrence set, skipping fallback checks")
            return
        }

        val unit = reminder.repeatIntervalUnit ?: com.example.caresync.domain.IntervalUnit.HOUR

        // Calculate slot duration
        val slotDuration = when (unit) {
            com.example.caresync.domain.IntervalUnit.MINUTE -> {
                val totalMinutes = reminder.repeatInterval ?: 60
                (totalMinutes * 60 * 1000L) / minOccurrence
            }
            com.example.caresync.domain.IntervalUnit.HOUR -> {
                (60 * 60 * 1000L) / minOccurrence
            }
            com.example.caresync.domain.IntervalUnit.DAY -> {
                (24 * 60 * 60 * 1000L) / minOccurrence
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        // ✅ NEW: Check permission before scheduling (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("MODEL_SCHEDULER", "⚠️ Exact alarm permission not granted, using inexact alarms")
            }
        }

        val now = System.currentTimeMillis()
        val firstSlotEnd = now + slotDuration

        val intent = android.content.Intent(context, com.example.caresync.receivers.FallbackCheckReceiver::class.java).apply {
            putExtra("reminderId", reminder.id)
            putExtra("slotStart", now)
            putExtra("slotEnd", firstSlotEnd)
            putExtra("slotDuration", slotDuration)
        }

        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            // ✅ UPDATED: Use API-appropriate method
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0+ (API 23+)
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    firstSlotEnd,
                    pendingIntent
                )
            } else {
                // Android 5.0-5.1 (API 21-22)
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    firstSlotEnd,
                    pendingIntent
                )
            }

            Log.d("MODEL_SCHEDULER", "📅 Fallback checks scheduled: every ${slotDuration / 1000 / 60} minutes")
        } catch (e: SecurityException) {
            Log.e("MODEL_SCHEDULER", "❌ Failed to schedule exact alarm", e)
            // You could fallback to inexact alarm here if needed
        }
    }

    /**
     * Cancel fallback alarms for a task
     */
    fun cancelFallbackChecks(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        val intent = android.content.Intent(context, com.example.caresync.receivers.FallbackCheckReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_NO_CREATE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d("MODEL_SCHEDULER", "📅 Fallback alarms cancelled for task $reminderId")
        }
    }
}
