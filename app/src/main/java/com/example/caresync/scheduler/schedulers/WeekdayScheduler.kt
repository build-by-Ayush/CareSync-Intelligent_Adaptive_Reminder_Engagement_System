package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * LAYER 3D: Weekday Scheduler
 *
 * Responsibilities:
 * - Handle Weekdays mode (selected days + N notifications per day)
 * - Generate multiple random times per day
 * - Distribute across morning/afternoon/evening periods
 *
 * Logic:
 * - User sets: "Saturday, Sunday" + "3 notifications per day"
 * - Generates 3 random times for each selected day
 * - Schedules multiple WorkManager jobs
 */
class WeekdayScheduler(private val context: Context) {

    /**
     * Schedule multiple notifications for selected weekdays
     * @return Number of notifications scheduled
     */
    fun schedule(reminder: ReminderSettings): Int {
        val daysOfWeek = reminder.daysOfWeek
        val notificationsPerDay = reminder.repeatInterval ?: 1

        if (daysOfWeek.isEmpty()) {
            Log.w("WEEKDAY_SCHEDULER", "No days selected, skipping")
            return 0
        }

        // Find next matching day
        val nextDay = findNextMatchingDay(daysOfWeek)

        // Generate N random times for that day
        val triggers = generateRandomTimesForDay(nextDay, notificationsPerDay)

        // Schedule each trigger
        triggers.forEachIndexed { index, triggerTime ->
            scheduleWorkManagerJob(reminder, triggerTime, index)
        }

        Log.d("WEEKDAY_SCHEDULER", "Scheduled ${triggers.size} notifications for ${formatDate(nextDay)}")
        return triggers.size
    }

    /**
     * Find next day that matches selected weekdays
     */
    private fun findNextMatchingDay(daysOfWeek: Set<Int>): Calendar {
        val target = Calendar.getInstance()

        for (i in 0..7) {
            val dayOfWeek = target.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday, 6=Saturday
            if (dayOfWeek in daysOfWeek && target.timeInMillis > System.currentTimeMillis()) {
                return target
            }
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        return target
    }

    /**
     * Generate N random times for a specific day
     * Distributes across morning/afternoon/evening periods
     */
    private fun generateRandomTimesForDay(day: Calendar, count: Int): List<Long> {
        val periods = listOf(
            6..11,   // Morning: 6 AM - 11 AM
            12..17,  // Afternoon: 12 PM - 5 PM
            18..23   // Evening: 6 PM - 11 PM
        )

        val times = mutableListOf<Long>()

        // Pick `count` random times from different periods
        val selectedPeriods = if (count <= periods.size) {
            periods.shuffled().take(count)
        } else {
            // If count > 3, repeat periods
            periods + periods.shuffled().take(count - periods.size)
        }

        for (period in selectedPeriods) {
            val hour = period.random()
            val minute = (0..59).random()

            val target = day.clone() as Calendar
            target.set(Calendar.HOUR_OF_DAY, hour)
            target.set(Calendar.MINUTE, minute)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)

            times.add(target.timeInMillis)
        }

        return times.sorted()  // Sort chronologically
    }

    /**
     * Schedule individual WorkManager job
     */
    private fun scheduleWorkManagerJob(reminder: ReminderSettings, triggerTime: Long, index: Int) {
        val delay = triggerTime - System.currentTimeMillis()

        if (delay < 0) {
            Log.w("WEEKDAY_SCHEDULER", "Trigger time is in the past, skipping")
            return
        }

        val workRequest = OneTimeWorkRequestBuilder<com.example.caresync.scheduler.ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("reminderId" to reminder.id))
            .addTag("reminder-${reminder.id}-$index")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-${reminder.id}-$index",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d("WEEKDAY_SCHEDULER", "Scheduled trigger #$index at ${formatTime(triggerTime)}")
    }

    private fun formatDate(calendar: Calendar): String {
        val format = java.text.SimpleDateFormat("EEEE, MMM dd", java.util.Locale.getDefault())
        return format.format(calendar.time)
    }

    private fun formatTime(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}
