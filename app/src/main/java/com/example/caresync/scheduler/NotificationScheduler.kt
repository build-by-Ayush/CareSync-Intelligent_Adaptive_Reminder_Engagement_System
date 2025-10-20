package com.example.caresync.scheduler

import android.content.Context
import androidx.work.*

/**
 * SIMPLIFIED: NotificationScheduler Interface
 *
 * Note: This is now a thin wrapper. Time calculation logic has been
 * moved to specialized schedulers (FixedTimeScheduler, RandomTimeScheduler, etc.)
 *
 * Used by: Specialized schedulers for WorkManager integration
 */
interface NotificationScheduler {
    fun scheduleReminder(context: Context, reminder: com.example.caresync.domain.ReminderSettings)
    fun cancelReminder(context: Context, id: Long)
}

/**
 * SIMPLIFIED: NotificationScheduler Implementation
 *
 * No longer calculates trigger times - just schedules WorkManager jobs
 */
class NotificationSchedulerImpl : NotificationScheduler {

    /**
     * Schedule a WorkManager job (trigger time must be pre-calculated)
     */
    override fun scheduleReminder(context: Context, reminder: com.example.caresync.domain.ReminderSettings) {
        if (!reminder.enabled) return

        val nextTrigger = reminder.scheduledAtMillis ?: System.currentTimeMillis()
        val delay = nextTrigger - System.currentTimeMillis()

        if (delay < 0) {
            android.util.Log.w("SCHEDULER", "Trigger time is in the past, skipping")
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("reminderId" to reminder.id))
            .addTag("reminder-${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        android.util.Log.d("SCHEDULER", "Scheduled reminder ${reminder.id} for ${delay / 1000}s from now")
    }

    /**
     * Cancel a scheduled reminder
     */
    override fun cancelReminder(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$id")
        android.util.Log.d("SCHEDULER", "Cancelled reminder $id")
    }
}
