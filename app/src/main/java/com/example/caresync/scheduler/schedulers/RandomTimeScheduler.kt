package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * LAYER 3C: Random Time Scheduler
 *
 * Responsibilities:
 * - Handle Days mode (every N days, random time)
 * - Generate random times within allowed day periods
 * - Avoid night period (12 AM - 6 AM)
 *
 * Logic:
 * - User sets: "Every 2 days"
 * - Generates random time in morning/afternoon/evening
 * - Schedules for day 1, 3, 5, 7, etc.
 */
class RandomTimeScheduler(private val context: Context) {

    /**
     * Schedule reminder at random time every N days
     * @return Next trigger timestamp
     */
    fun schedule(reminder: ReminderSettings): Long {
        val intervalDays = reminder.repeatInterval ?: 1

        val nextTrigger = computeRandomTrigger(intervalDays)
        scheduleWorkManagerJob(reminder, nextTrigger)

        Log.d("RANDOM_SCHEDULER", "Scheduled task ${reminder.id} for ${formatTime(nextTrigger)} (every $intervalDays days)")
        return nextTrigger
    }

    /**
     * Calculate next random trigger time
     */
    private fun computeRandomTrigger(intervalDays: Int): Long {
        val target = Calendar.getInstance()

        // Add interval days
        target.add(Calendar.DAY_OF_MONTH, intervalDays)

        // Pick random time from allowed periods (avoiding night)
        val randomTime = pickRandomDayPeriodTime()

        target.set(Calendar.HOUR_OF_DAY, randomTime.first)
        target.set(Calendar.MINUTE, randomTime.second)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        return target.timeInMillis
    }

    /**
     * Pick random time from morning/afternoon/evening periods
     * Avoids night period (12 AM - 6 AM)
     */
    private fun pickRandomDayPeriodTime(): Pair<Int, Int> {
        val periods = listOf(
            6..11,   // Morning: 6 AM - 11 AM
            12..17,  // Afternoon: 12 PM - 5 PM
            18..23   // Evening: 6 PM - 11 PM
        )

        // Pick random period
        val period = periods.random()

        // Pick random hour and minute in that period
        val hour = period.random()
        val minute = (0..59).random()

        return Pair(hour, minute)
    }

    /**
     * Schedule WorkManager job
     */
    private fun scheduleWorkManagerJob(reminder: ReminderSettings, triggerTime: Long) {
        val delay = triggerTime - System.currentTimeMillis()

        if (delay < 0) {
            Log.w("RANDOM_SCHEDULER", "Trigger time is in the past, skipping")
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
