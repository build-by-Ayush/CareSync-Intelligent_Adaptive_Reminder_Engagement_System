package com.example.caresync.scheduler

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.TriggerMode
import com.example.caresync.domain.RecurrenceType
import com.example.caresync.scheduler.schedulers.*

/**
 * LAYER 2: Scheduling Coordinator
 *
 * Responsibilities:
 * - Route tasks to appropriate scheduler based on mode
 * - Coordinate between different scheduling strategies
 * - Handle cancellations
 *
 * Called by: TaskConfigurationEngine
 */
class SchedulingCoordinator(private val context: Context) {

    /**
     * Route task to appropriate scheduler based on mode and recurrence
     */
    suspend fun scheduleTask(reminder: ReminderSettings): String {
        Log.d("COORDINATOR", "Routing task ${reminder.id}: mode=${reminder.triggerMode}, recurrence=${reminder.recurrenceType}")

        return when (reminder.triggerMode) {
            TriggerMode.MODEL_ASSISTED -> {
                // Model mode: ML decides timing
                val scheduler = ModelModeScheduler(context)
                scheduler.schedule(reminder)
                "Model mode: ML will decide timing (checking every hour)"
            }

            TriggerMode.FIXED_TIME -> {
                // Repetitive mode: Fixed timing based on recurrence type
                when (reminder.recurrenceType) {
                    RecurrenceType.DAILY -> {
                        // Hours mode: Fixed time + selected days
                        val scheduler = FixedTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)
                        "Hours mode: Next trigger at ${formatTimestamp(nextTrigger)}"
                    }

                    RecurrenceType.WEEKLY -> {
                        // Days mode: Every N days, random time
                        val scheduler = RandomTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)
                        "Days mode: Next trigger at ${formatTimestamp(nextTrigger)}"
                    }

                    RecurrenceType.INTERVAL -> {
                        // Weekdays mode: Selected days + N notifications/day
                        val scheduler = WeekdayScheduler(context)
                        val triggerCount = scheduler.schedule(reminder)
                        "Weekdays mode: Scheduled $triggerCount notifications"
                    }

                    RecurrenceType.NONE -> {
                        // One-time: Use scheduled time directly
                        val scheduler = FixedTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)
                        "One-time: Trigger at ${formatTimestamp(nextTrigger)}"
                    }

                    RecurrenceType.CUSTOM -> {
                        // Custom recurrence: Use FixedTimeScheduler as fallback
                        val scheduler = FixedTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)
                        "Custom mode: Next trigger at ${formatTimestamp(nextTrigger)}"
                    }
                }
            }
            else -> "Unknown mode"
        }
    }

    /**
     * Cancel all scheduled jobs for a task
     */
    fun cancelTask(reminderId: Long) {
        try {
            // Cancel WorkManager jobs
            WorkManager.getInstance(context).cancelUniqueWork("reminder-$reminderId")
            WorkManager.getInstance(context).cancelUniqueWork("ml-check-$reminderId")

            // ✅ NEW: Cancel fallback alarms
            val scheduler = ModelModeScheduler(context)
            scheduler.cancelFallbackChecks(context, reminderId)

            Log.d("COORDINATOR", "Cancelled all jobs for task: $reminderId")
        } catch (e: Exception) {
            Log.e("COORDINATOR", "Failed to cancel task", e)
        }
    }

    /**
     * Format timestamp for logging
     */
    private fun formatTimestamp(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}
