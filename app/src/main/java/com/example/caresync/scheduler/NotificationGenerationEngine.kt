package com.example.caresync.scheduler

import android.content.Context
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.RecurrenceType
import java.util.Calendar

/**
 * Centralized layer for notification generation logic.
 *
 * Handles:
 * - Parsing complex task settings
 * - Computing next trigger times
 * - Managing database operations
 * - Coordinating with scheduler
 * - Cleanup old events
 */
class NotificationGenerationEngine(private val context: Context) {

    private val repo = ReminderRepository(context)
    private val scheduler = NotificationSchedulerImpl()
    private val eventDao = AppDatabase.get(context).reminderEventDao()

    /**
     * Main entry point: Generate and schedule notification plan
     *
     * @param reminder Task settings from UI
     * @return Success/failure result
     */
    suspend fun generateNotificationPlan(reminder: ReminderSettings): GenerationResult {
        return try {
            // Step 1: Save to database
            val taskId = repo.upsert(reminder)

            // Step 2: Calculate next trigger time
            val nextTrigger = computeNextTrigger(reminder)

            // Step 3: Schedule with WorkManager
            val updatedReminder = reminder.copy(
                id = taskId,
                scheduledAtMillis = nextTrigger
            )
            scheduler.scheduleReminder(context, updatedReminder)

            // Step 4: Run cleanup (optional, async)
            cleanupOldEvents()

            GenerationResult.Success(taskId, nextTrigger)

        } catch (e: Exception) {
            GenerationResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Compute next trigger time based on recurrence settings
     */
    private fun computeNextTrigger(reminder: ReminderSettings): Long {
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
                    unit = reminder.repeatIntervalUnit ?: com.example.caresync.domain.IntervalUnit.HOUR
                )
            }

            else -> System.currentTimeMillis()
        }
    }

    /**
     * Daily recurrence logic
     */
    private fun computeDailyTrigger(timeOfDayMillis: Long, daysOfWeek: Set<Int>): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance()

        // Extract hour and minute from timeOfDayMillis
        val hours = (timeOfDayMillis / (1000 * 60 * 60)) % 24
        val minutes = (timeOfDayMillis / (1000 * 60)) % 60

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

        // Find next matching day of week
        for (i in 0..7) {
            if (target.timeInMillis > now.timeInMillis) {
                val dayOfWeek = target.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-6
                if (dayOfWeek in daysOfWeek) {
                    return target.timeInMillis
                }
            }
            target.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Fallback: tomorrow at same time
        return now.timeInMillis + 24 * 60 * 60 * 1000
    }

    /**
     * Weekly recurrence logic
     */
    private fun computeWeeklyTrigger(timeOfDayMillis: Long, daysOfWeek: Set<Int>): Long {
        // Same logic as daily, but explicitly weekly context
        return computeDailyTrigger(timeOfDayMillis, daysOfWeek)
    }

    /**
     * Interval mode (Model mode) logic
     */
    private fun computeIntervalTrigger(interval: Int, unit: com.example.caresync.domain.IntervalUnit): Long {
        val now = System.currentTimeMillis()

        val delayMillis = when (unit) {
            com.example.caresync.domain.IntervalUnit.MINUTE -> interval * 60 * 1000L
            com.example.caresync.domain.IntervalUnit.HOUR -> interval * 60 * 60 * 1000L
            com.example.caresync.domain.IntervalUnit.DAY -> interval * 24 * 60 * 60 * 1000L
        }

        return now + delayMillis
    }

    /**
     * Cleanup old event logs (>30 days)
     */
    private suspend fun cleanupOldEvents() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
            val deletedCount = eventDao.deleteOldEvents(thirtyDaysAgo)
            android.util.Log.d("CLEANUP", "Deleted $deletedCount old events")
        } catch (e: Exception) {
            android.util.Log.e("CLEANUP", "Cleanup failed", e)
        }
    }

    /**
     * Cancel existing notification plan
     */
    suspend fun cancelNotificationPlan(reminderId: Long) {
        scheduler.cancelReminder(context, reminderId)
        repo.delete(reminderId)
        eventDao.deleteEventsForReminder(reminderId)
    }
}

/**
 * Result of generation operation
 */
sealed class GenerationResult {
    data class Success(val taskId: Long, val nextTrigger: Long) : GenerationResult()
    data class Failure(val reason: String) : GenerationResult()
}
