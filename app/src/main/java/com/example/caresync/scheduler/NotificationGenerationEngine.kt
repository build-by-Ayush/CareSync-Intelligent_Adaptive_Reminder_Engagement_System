package com.example.caresync.scheduler

import android.content.Context
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.IntervalUnit
import com.example.caresync.domain.RecurrenceType
import com.example.caresync.domain.ReminderSettings
import java.util.Calendar

/**
 * LAYER 2: Notification Generation Engine
 *
 * Responsibilities:
 * - Compute next trigger times based on recurrence settings
 * - Calculate trigger intervals for scheduling
 * - Support calculations (delegated to TaskConfigurationEngine for actual scheduling)
 *
 * DOES NOT:
 * - Validate settings (TaskConfigurationEngine does this)
 * - Save to database (TaskConfigurationEngine does this)
 * - Route to scheduler (TaskConfigurationEngine does this)
 * - Delete tasks (TaskConfigurationEngine does this)
 *
 * Works with: TaskConfigurationEngine (provides timing calculations)
 */
class NotificationGenerationEngine(private val context: Context) {

    companion object {
        private const val TAG = "NotificationGenEngine"
        private const val CLEANUP_DAYS = 100  // ✅ CONSISTENT with TaskConfigurationEngine
    }

    private val eventDao = AppDatabase.get(context).reminderEventDao()

    /**
     * Compute next trigger time based on recurrence settings
     * Called by: SchedulingCoordinator / Scheduler implementations
     */
    fun computeNextTrigger(reminder: ReminderSettings): Long {
        return when (reminder.recurrenceType) {
            RecurrenceType.NONE -> {
                // One-time: use scheduled time directly
                reminder.scheduledAtMillis ?: System.currentTimeMillis()
            }

            RecurrenceType.DAILY -> {
                computeDailyTrigger(
                    timeOfDayMillis = reminder.timeOfDayMillis ?: 0L,
                    daysOfWeek = reminder.daysOfWeek
                )
            }

            RecurrenceType.WEEKLY -> {
                computeWeeklyTrigger(
                    timeOfDayMillis = reminder.timeOfDayMillis ?: 0L,
                    daysOfWeek = reminder.daysOfWeek
                )
            }

            RecurrenceType.INTERVAL -> {
                computeIntervalTrigger(
                    interval = reminder.repeatInterval ?: 1,
                    unit = reminder.repeatIntervalUnit ?: IntervalUnit.HOUR
                )
            }

            else -> System.currentTimeMillis()
        }
    }

    /**
     * Daily recurrence logic
     *
     * Computes next occurrence considering:
     * - Time of day (hour:minute)
     * - Specific days of week (if set)
     * - Current time
     */
    private fun computeDailyTrigger(timeOfDayMillis: Long, daysOfWeek: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        // ✅ SAFE: Validate input
        val validTimeMillis = if (timeOfDayMillis > 0) timeOfDayMillis else System.currentTimeMillis()

        val hours = (validTimeMillis / (1000 * 60 * 60)) % 24
        val minutes = (validTimeMillis / (1000 * 60)) % 60

        target.set(Calendar.HOUR_OF_DAY, hours.toInt())
        target.set(Calendar.MINUTE, minutes.toInt())
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        // If no specific days selected, trigger every day
        if (daysOfWeek.isEmpty()) {
            // If today's time passed, schedule for tomorrow
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis
        }

        // ✅ OPTIMIZED: Find next matching day of week (max 7 days)
        for (i in 0 until 7) {
            val dayOfWeek = target.get(Calendar.DAY_OF_WEEK) - 1  // Convert to 0-6
            if (dayOfWeek in daysOfWeek && target.timeInMillis > now.timeInMillis) {
                return target.timeInMillis
            }
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Fallback: tomorrow at same time
        return now.timeInMillis + 24 * 60 * 60 * 1000
    }

    /**
     * Weekly recurrence logic
     *
     * Same as daily since weekly is just daily with specific days
     */
    private fun computeWeeklyTrigger(timeOfDayMillis: Long, daysOfWeek: Set<Int>): Long {
        return computeDailyTrigger(timeOfDayMillis, daysOfWeek)
    }

    /**
     * Interval mode (Model mode) logic
     *
     * Computes: current time + (interval * unit)
     * Examples:
     * - 3 hours → now + 3 * 60 * 60 * 1000 ms
     * - 2 days → now + 2 * 24 * 60 * 60 * 1000 ms
     */
    fun computeIntervalTrigger(interval: Int, unit: IntervalUnit): Long {
        // ✅ SAFE: Validate interval
        val validInterval = if (interval > 0) interval else 1

        val now = System.currentTimeMillis()

        val delayMillis = when (unit) {
            IntervalUnit.MINUTE -> validInterval * 60 * 1000L
            IntervalUnit.HOUR -> validInterval * 60 * 60 * 1000L
            IntervalUnit.DAY -> validInterval * 24 * 60 * 60 * 1000L
        }

        return now + delayMillis
    }

    /**
     * ✅ SHARED: Cleanup old event logs
     * Called by: TaskConfigurationEngine during processTaskConfiguration
     * Uses same threshold for consistency
     */
    suspend fun cleanupOldEvents() {
        try {
            val cutoffMillis = System.currentTimeMillis() - (CLEANUP_DAYS * 24 * 60 * 60 * 1000L)
            val deletedCount = eventDao.deleteOldEvents(cutoffMillis)
            Log.d(TAG, "Deleted $deletedCount old event logs (>$CLEANUP_DAYS days)")
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
        }
    }

    /**
     * Get cleanup threshold (for consistency across system)
     */
    fun getCleanupThresholdDays(): Int = CLEANUP_DAYS
}

/**
 * Result of generation operation
 */
sealed class GenerationResult {
    data class Success(
        val taskId: Long,
        val nextTrigger: Long
    ) : GenerationResult()

    data class Failure(val reason: String) : GenerationResult()
}
