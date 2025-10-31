package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.intelligence.OptimalTimeLearner
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WeekdayScheduler(private val context: Context) {

    private val learner = OptimalTimeLearner(context)

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

        // Generate smart times using learned data
        val triggers = generateSmartRandomTimesForDay(reminder, nextDay, notificationsPerDay)

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
     * ✅ UPDATED: Generate N smart times using weighted selection
     *
     * Strategy:
     * - ≥count good hours → Use all learned (enough variety)
     * - 1 to (count-1) good hours → Mix learned + random
     * - 0 good hours → Fully random
     */
    private fun generateSmartRandomTimesForDay(
        reminder: ReminderSettings,
        day: Calendar,
        count: Int
    ): List<Long> {
        // ✅ CHANGED: Always use smart learning for scheduling, no toggle check
        // Smart time learning is independent of the optimization toggle

        // Try to get learned best hours
        val bestHours = runBlocking {
            try {
                learner.getBestHours(
                    reminderId = reminder.id,
                    minConfidence = 0.4f,
                    minSamples = 2,
                    limit = count + 2  // Get a few extra for variety
                )
            } catch (e: Exception) {
                Log.w("WEEKDAY_SCHEDULER", "Failed to get learned hours, using random", e)
                emptyList()
            }
        }

        // ✅ DECISION LOGIC
        when {
            bestHours.size >= count -> {
                // Enough learned hours to satisfy count - use all learned
                val times = mutableListOf<Long>()
                val selectedHours = bestHours.shuffled().take(count)

                selectedHours.forEach { preferredTime ->
                    val target = day.clone() as Calendar
                    target.set(Calendar.HOUR_OF_DAY, preferredTime.hourOfDay)
                    target.set(Calendar.MINUTE, (0..59).random())
                    target.set(Calendar.SECOND, 0)
                    target.set(Calendar.MILLISECOND, 0)
                    times.add(target.timeInMillis)
                }

                Log.d("WEEKDAY_SCHEDULER", "✨ All smart: ${selectedHours.size} learned times (from ${bestHours.size} available)")
                return times.sorted()
            }

            bestHours.isNotEmpty() -> {
                // Have some learned hours but not enough - mix learned + random
                val times = mutableListOf<Long>()

                // Use all learned hours
                bestHours.forEach { preferredTime ->
                    val target = day.clone() as Calendar
                    target.set(Calendar.HOUR_OF_DAY, preferredTime.hourOfDay)
                    target.set(Calendar.MINUTE, (0..59).random())
                    target.set(Calendar.SECOND, 0)
                    target.set(Calendar.MILLISECOND, 0)
                    times.add(target.timeInMillis)
                }

                // Fill remaining with random
                val remaining = count - bestHours.size
                if (remaining > 0) {
                    val randomTimes = generateRandomTimesForDay(day, remaining)
                    times.addAll(randomTimes)
                }

                Log.d("WEEKDAY_SCHEDULER", "✨ Mixed: ${bestHours.size} learned + $remaining random")
                return times.sorted()
            }

            else -> {
                // No learned data yet - fully random
                Log.d("WEEKDAY_SCHEDULER", "No learned data yet, using random distribution")
                return generateRandomTimesForDay(day, count)
            }
        }
    }

    /**
     * ORIGINAL: Generate N random times for a specific day
     * Distributes across morning/afternoon/evening periods
     * Used as fallback when no learned data available
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

        return times.sorted()
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
