package com.example.caresync.scheduler

import android.content.Context
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.RecurrenceType
import com.example.caresync.domain.IntervalUnit
import java.util.Calendar
import java.util.concurrent.TimeUnit

interface NotificationScheduler {
    fun scheduleReminder(context: Context, reminder: ReminderSettings)
    fun cancelReminder(context: Context, id: Long)
}

class NotificationSchedulerImpl : NotificationScheduler {

    override fun scheduleReminder(context: Context, reminder: ReminderSettings) {
        if (!reminder.enabled) return

        val nextTrigger = computeNextTrigger(reminder)
        val delay = nextTrigger - System.currentTimeMillis()

        if (delay < 0) {
            // Past time, skip for now (or handle immediately)
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf("reminderId" to reminder.id)
            )
            .addTag("reminder-${reminder.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        println("Scheduler: scheduled reminder ${reminder.id} for ${delay / 1000}s from now")
    }

    override fun cancelReminder(context: Context, id: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$id")
        println("Scheduler: canceled reminder $id")
    }

    private fun computeNextTrigger(reminder: ReminderSettings): Long {
        val now = System.currentTimeMillis()

        return when (reminder.recurrenceType) {
            RecurrenceType.NONE -> {
                // One-time: use scheduledAtMillis
                reminder.scheduledAtMillis ?: now + 60_000 // Default: 1 min from now
            }

            RecurrenceType.DAILY -> {
                // Daily at timeOfDayMillis
                computeDailyTrigger(reminder.timeOfDayMillis ?: 0, reminder.daysOfWeek)
            }

            RecurrenceType.WEEKLY -> {
                // Weekly on selected days
                computeWeeklyTrigger(reminder.timeOfDayMillis ?: 0, reminder.daysOfWeek)
            }

            RecurrenceType.INTERVAL -> {
                // Interval-based (Model mode)
                computeIntervalTrigger(
                    reminder.repeatInterval ?: 1,
                    reminder.repeatIntervalUnit ?: IntervalUnit.HOUR
                )
            }

            else -> now + 60_000 // Default fallback
        }
    }

    private fun computeDailyTrigger(timeOfDayMillis: Long, daysOfWeek: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        // Set time of day
        val hours = (timeOfDayMillis / (1000 * 60 * 60)) % 24
        val minutes = (timeOfDayMillis / (1000 * 60)) % 60

        target.set(Calendar.HOUR_OF_DAY, hours.toInt())
        target.set(Calendar.MINUTE, minutes.toInt())
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // If daysOfWeek is empty, it's daily every day
        if (daysOfWeek.isEmpty()) {
            // If time today has passed, move to tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis
        }

        // If specific days selected (Hours mode), find next matching day
        for (i in 0..7) {
            if (target.timeInMillis > now.timeInMillis &&
                daysOfWeek.contains(target.get(Calendar.DAY_OF_WEEK) - 1)) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return now.timeInMillis + 24 * 60 * 60 * 1000 // Fallback: tomorrow
    }

    private fun computeWeeklyTrigger(timeOfDayMillis: Long, daysOfWeek: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        val hours = (timeOfDayMillis / (1000 * 60 * 60)) % 24
        val minutes = (timeOfDayMillis / (1000 * 60)) % 60

        target.set(Calendar.HOUR_OF_DAY, hours.toInt())
        target.set(Calendar.MINUTE, minutes.toInt())
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // Find next matching weekday (0=Sun, 1=Mon, ..., 6=Sat)
        for (i in 0..7) {
            val dayOfWeek = target.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-6
            if (target.timeInMillis > now.timeInMillis && daysOfWeek.contains(dayOfWeek)) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return now.timeInMillis + 7 * 24 * 60 * 60 * 1000 // Fallback: next week
    }

    private fun computeIntervalTrigger(interval: Int, unit: IntervalUnit): Long {
        val now = System.currentTimeMillis()

        val delayMillis = when (unit) {
            IntervalUnit.MINUTE -> interval * 60 * 1000L
            IntervalUnit.HOUR -> interval * 60 * 60 * 1000L
            IntervalUnit.DAY -> interval * 24 * 60 * 60 * 1000L
        }

        return now + delayMillis
    }
}
