package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.TimePeriod
import com.example.caresync.intelligence.OptimalTimeLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RandomTimeScheduler(private val context: Context) {

    private val learner = OptimalTimeLearner(context)

    fun schedule(reminder: ReminderSettings): Long {
        val intervalDays = reminder.repeatInterval ?: 1

        val nextTrigger = computeSmartRandomTrigger(reminder, intervalDays)
        scheduleWorkManagerJob(reminder, nextTrigger)

        Log.d("RANDOM_SCHEDULER", "Scheduled task ${reminder.id} for ${formatTime(nextTrigger)} (every $intervalDays days)")
        return nextTrigger
    }

    private fun computeSmartRandomTrigger(reminder: ReminderSettings, intervalDays: Int): Long {
        val target = Calendar.getInstance()
        target.add(Calendar.DAY_OF_MONTH, intervalDays)

        val randomTime = pickSmartDayPeriodTime(reminder)

        target.set(Calendar.HOUR_OF_DAY, randomTime.first)
        target.set(Calendar.MINUTE, randomTime.second)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        return target.timeInMillis
    }

    /**
     * ✅ CORRECTED: Weighted smart selection with user preferences + blacklist
     */
    private fun pickSmartDayPeriodTime(reminder: ReminderSettings): Pair<Int, Int> {
        // ✅ Get best learned hours - returns List<PreferredTime>
        val bestHours = runBlocking {
            try {
                learner.getBestHours(
                    reminderId = reminder.id,
                    minConfidence = 0.4f,
                    minSamples = 3,
                    limit = 5
                )
            } catch (e: Exception) {
                Log.w("RANDOM_SCHEDULER", "Failed to get learned hours", e)
                emptyList()
            }
        }

        // ✅ CORRECTED: Use getBlacklistedHours() which already exists
        val blacklistedHours = runBlocking {
            try {
                val blacklistDao = AppDatabase.get(context).blacklistHourDao()
                val blacklistedList = blacklistDao.getBlacklistedHours(
                    reminderId = reminder.id,
                    threshold = 5  // Get hours with 5+ dismissals
                )

                blacklistedList.map { it.hourOfDay }.toSet()
            } catch (e: Exception) {
                Log.w("RANDOM_SCHEDULER", "Failed to get blacklist", e)
                emptySet()
            }
        }

        // ✅ Convert PreferredTime to just hourOfDay, then filter
        val validBestHours = bestHours
            .map { it.hourOfDay }
            .filter { it !in blacklistedHours }

        // ✅ DECISION LOGIC
        when {
            validBestHours.size >= 3 -> {
                val selectedHour = validBestHours.random()
                Log.d("RANDOM_SCHEDULER", "✨ Smart selection: hour $selectedHour from ${validBestHours.size} good hours")
                return Pair(selectedHour, (0..59).random())
            }
            validBestHours.isNotEmpty() -> {
                val useLearnedTime = (Math.random() < 0.7)

                if (useLearnedTime) {
                    val selectedHour = validBestHours.random()
                    Log.d("RANDOM_SCHEDULER", "✨ Smart selection: hour $selectedHour (70% probability)")
                    return Pair(selectedHour, (0..59).random())
                } else {
                    Log.d("RANDOM_SCHEDULER", "🎲 Exploration mode: trying random time (30% probability)")
                    return pickRandomDayPeriodTime(reminder, blacklistedHours)
                }
            }
            else -> {
                Log.d("RANDOM_SCHEDULER", "No learned data yet, using random within user preferences")
                return pickRandomDayPeriodTime(reminder, blacklistedHours)
            }
        }
    }

    /**
     * ✅ Pick random time respecting user restrictions and blacklist
     */
    private fun pickRandomDayPeriodTime(
        reminder: ReminderSettings,
        blacklistedHours: Set<Int> = emptySet()
    ): Pair<Int, Int> {

        // ✅ Map TimePeriod to hour ranges
        val timePeriodRanges = mapOf(
            TimePeriod.MORNING to (6..11),
            TimePeriod.AFTERNOON to (12..17),
            TimePeriod.EVENING to (18..23)
        )

        // ✅ Filter to only allowed periods
        val allowedPeriods = reminder.allowedTimePeriods.ifEmpty {
            listOf(TimePeriod.MORNING, TimePeriod.AFTERNOON, TimePeriod.EVENING)
        }

        val availablePeriods = allowedPeriods.mapNotNull { timePeriodRanges[it] }

        if (availablePeriods.isEmpty()) {
            Log.w("RANDOM_SCHEDULER", "No allowed periods found, using all periods")
            val allPeriods = listOf(6..11, 12..17, 18..23)
            return pickFromPeriods(allPeriods, blacklistedHours)
        }

        return pickFromPeriods(availablePeriods, blacklistedHours)
    }

    /**
     * ✅ Pick hour from allowed periods, avoiding blacklisted hours
     */
    private fun pickFromPeriods(
        periods: List<IntRange>,
        blacklistedHours: Set<Int>
    ): Pair<Int, Int> {

        val period = periods.random()
        var hour = period.random()

        // ✅ Retry up to 10 times if hour is blacklisted
        var attempts = 0
        while (hour in blacklistedHours && attempts < 10) {
            hour = period.random()
            attempts++
        }

        if (hour in blacklistedHours) {
            Log.w("RANDOM_SCHEDULER", "⚠️ All hours in period are blacklisted, using anyway: $hour")
        }

        val minute = (0..59).random()

        Log.d("RANDOM_SCHEDULER", "🎲 Random selection: hour $hour (blacklisted: ${hour in blacklistedHours})")
        return Pair(hour, minute)
    }

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
