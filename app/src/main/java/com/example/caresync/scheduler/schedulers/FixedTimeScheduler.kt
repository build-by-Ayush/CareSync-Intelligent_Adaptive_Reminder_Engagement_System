package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.RecurrenceType
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * LAYER 3B: Fixed Time Scheduler
 *
 * Responsibilities:
 * - Handle Hours mode (alarm-style reminders)
 * - Handle one-time reminders
 * - Calculate exact trigger times
 *
 * Logic:
 * - User sets: Time (e.g., 9 AM) + Days (e.g., Mon, Wed, Fri)
 * - Calculates next matching day and time
 * - Schedules with WorkManager
 */
class FixedTimeScheduler(private val context: Context) {

    /**
     * Schedule reminder at fixed time
     * @return Next trigger timestamp
     */
    fun schedule(reminder: ReminderSettings): Long {
        val nextTrigger = when (reminder.recurrenceType) {
            RecurrenceType.NONE -> {
                // One-time: use scheduledAtMillis directly
                reminder.scheduledAtMillis ?: System.currentTimeMillis()
            }
            RecurrenceType.DAILY -> {
                // Hours mode: fixed time + selected days
                computeNextFixedTime(reminder)
            }
            else -> System.currentTimeMillis()
        }

        scheduleWorkManagerJob(reminder, nextTrigger)

        Log.d("FIXED_SCHEDULER", "Scheduled task ${reminder.id} for ${formatTime(nextTrigger)}")
        return nextTrigger
    }

    /**
     * Calculate next trigger time for Hours mode
     */
    private fun computeNextFixedTime(reminder: ReminderSettings): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        // Extract hour and minute from timeOfDayMillis
        val timeOfDay = reminder.timeOfDayMillis ?: 0L
        val hours = (timeOfDay / (1000 * 60 * 60)) % 24
        val minutes = (timeOfDay / (1000 * 60)) % 60

        target.set(Calendar.HOUR_OF_DAY, hours.toInt())
        target.set(Calendar.MINUTE, minutes.toInt())
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // If no specific days selected, trigger every day
        if (reminder.daysOfWeek.isEmpty()) {
            // If today's time already passed, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis
        }

        // Find next matching day of week
        for (i in 0..7) {
            if (target.timeInMillis > now.timeInMillis) {
                val dayOfWeek = target.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday, 6=Saturday
                if (dayOfWeek in reminder.daysOfWeek) {
                    return target.timeInMillis
                }
            }
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Fallback: tomorrow at same time
        return now.timeInMillis + 24 * 60 * 60 * 1000
    }

    /**
     * Schedule WorkManager job
     */
    private fun scheduleWorkManagerJob(reminder: ReminderSettings, triggerTime: Long) {
        val delay = triggerTime - System.currentTimeMillis()

        if (delay < 0) {
            Log.w("FIXED_SCHEDULER", "Trigger time is in the past, skipping")
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<com.example.caresync.scheduler.ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("reminderId" to reminder.id))
            .addTag("reminder-${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun formatTime(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}
