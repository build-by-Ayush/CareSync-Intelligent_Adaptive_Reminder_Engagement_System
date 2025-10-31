package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.intelligence.OptimalTimeLearner
import kotlinx.coroutines.runBlocking
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
     * ✅ UPDATED: Weighted smart selection
     *
     * Strategy:
     * - ≥3 good hours → Always pick from them (enough variety)
     * - 1-2 good hours → Pick 70% of time, explore 30%
     * - 0 good hours → Fully random
     */
    private fun pickSmartDayPeriodTime(reminder: ReminderSettings): Pair<Int, Int> {
        // ✅ CHANGED: Always use smart learning for random scheduling, no toggle check
        // Smart time learning is independent of the optimization toggle

        val bestHours = runBlocking {
            try {
                learner.getBestHours(
                    reminderId = reminder.id,
                    minConfidence = 0.4f,
                    minSamples = 3,
                    limit = 5  // Get up to 5 best hours
                )
            } catch (e: Exception) {
                Log.w("RANDOM_SCHEDULER", "Failed to get learned hours", e)
                emptyList()
            }
        }

        // ✅ DECISION LOGIC (same as before)
        when {
            bestHours.size >= 3 -> {
                val selectedHour = bestHours.random().hourOfDay
                Log.d("RANDOM_SCHEDULER", "✨ Smart selection: hour $selectedHour from ${bestHours.size} good hours")
                return Pair(selectedHour, (0..59).random())
            }
            bestHours.isNotEmpty() -> {
                val useLearnedTime = (Math.random() < 0.7)

                if (useLearnedTime) {
                    val selectedHour = bestHours.random().hourOfDay
                    Log.d("RANDOM_SCHEDULER", "✨ Smart selection: hour $selectedHour (70% probability)")
                    return Pair(selectedHour, (0..59).random())
                } else {
                    Log.d("RANDOM_SCHEDULER", "🎲 Exploration mode: trying random time (30% probability)")
                    return pickRandomDayPeriodTime()
                }
            }
            else -> {
                Log.d("RANDOM_SCHEDULER", "No learned data yet, using random")
                return pickRandomDayPeriodTime()
            }
        }
    }

    private fun pickRandomDayPeriodTime(): Pair<Int, Int> {
        val periods = listOf(
            6..11,   // Morning
            12..17,  // Afternoon
            18..23   // Evening
        )

        val period = periods.random()
        val hour = period.random()
        val minute = (0..59).random()

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
