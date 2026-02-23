package com.example.caresync.scheduler

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.domain.TriggerMode
import com.example.caresync.domain.RecurrenceType
import com.example.caresync.scheduler.schedulers.*
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * LAYER 2: Scheduling Coordinator
 *
 * Responsibilities:
 * - Route tasks to appropriate scheduler based on mode
 * - Coordinate between different scheduling strategies
 * - Handle cancellations and lifecycle management
 * - Call ModelModeScheduler for enable/disable control
 *
 * Called by: TaskConfigurationEngine
 */
class SchedulingCoordinator(private val context: Context) {

    /**
     * Route task to appropriate scheduler based on mode and recurrence
     *
     * @param reminder The ReminderSettings to schedule
     * @return Status message describing what was scheduled
     */
    suspend fun scheduleTask(reminder: ReminderSettings): String {
        Log.d(TAG, """
            📋 Routing task ${reminder.id}
               Title: ${reminder.title}
               Mode: ${reminder.triggerMode}
               Recurrence: ${reminder.recurrenceType}
        """.trimIndent())

        return when (reminder.triggerMode) {
            TriggerMode.MODEL_ASSISTED -> {
                // ✅ Model Mode: Enable SessionPollingWorker lifecycle
                Log.d(TAG, "🚀 Model Mode task - calling ModelModeScheduler.enableForReminder()")

                ModelModeScheduler(context).enableForReminder(reminder)

                Log.d(TAG, """
                    ✅ Model Mode scheduled
                       SessionPollingWorker: Now active (if not already)
                       Detection: 15-min windows + Session-End events
                       Status: Monitoring app engagement
                """.trimIndent())

                "Model mode: SessionPollingWorker active (15-min windows + Session-End detection)"
            }

            TriggerMode.FIXED_TIME -> {
                // Repetitive mode: Fixed timing based on recurrence type
                when (reminder.recurrenceType) {
                    RecurrenceType.DAILY -> {
                        // Hours mode: Fixed time + selected days
                        Log.d(TAG, "📅 Hours mode task - routing to FixedTimeScheduler")

                        val scheduler = FixedTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)

                        "Hours mode: Next trigger at ${formatTimestamp(nextTrigger)}"
                    }

                    RecurrenceType.WEEKLY -> {
                        // Days mode: Every N days, random time
                        Log.d(TAG, "📅 Days mode task - routing to RandomTimeScheduler")

                        val scheduler = RandomTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)

                        "Days mode: Next trigger at ${formatTimestamp(nextTrigger)}"
                    }

                    RecurrenceType.INTERVAL -> {
                        // Weekdays mode: Selected days + N notifications/day
                        Log.d(TAG, "📅 Weekdays mode task - routing to WeekdayScheduler")

                        val scheduler = WeekdayScheduler(context)
                        val triggerCount = scheduler.schedule(reminder)

                        "Weekdays mode: Scheduled $triggerCount notifications"
                    }

                    RecurrenceType.NONE -> {
                        // One-time: Use scheduled time directly
                        Log.d(TAG, "⏰ One-time task - routing to FixedTimeScheduler")

                        val scheduler = FixedTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)

                        "One-time: Trigger at ${formatTimestamp(nextTrigger)}"
                    }

                    RecurrenceType.CUSTOM -> {
                        // Custom recurrence: Use FixedTimeScheduler as fallback
                        Log.d(TAG, "📅 Custom mode task - routing to FixedTimeScheduler (fallback)")

                        val scheduler = FixedTimeScheduler(context)
                        val nextTrigger = scheduler.schedule(reminder)

                        "Custom mode: Next trigger at ${formatTimestamp(nextTrigger)}"
                    }
                }
            }

            else -> {
                Log.w(TAG, "⚠️ Unknown trigger mode: ${reminder.triggerMode}")
                "Unknown mode"
            }
        }
    }

    /**
     * Cancel all scheduled jobs for a task
     *
     * Handles both Model Mode (SessionPollingWorker) and Fixed Mode schedulers
     *
     * @param reminderId The ID of reminder to cancel
     * @param triggerMode The trigger mode (to determine cleanup strategy)
     */
    suspend fun cancelTask(reminderId: Long, triggerMode: TriggerMode? = null) {
        try {
            Log.d(TAG, """
                🗑️ Cancelling task
                   Reminder ID: $reminderId
                   Mode: $triggerMode
            """.trimIndent())

            // Cancel WorkManager jobs
            WorkManager.getInstance(context).cancelUniqueWork("reminder-$reminderId")
            WorkManager.getInstance(context).cancelUniqueWork("ml-check-$reminderId")

            // ✅ If Model Mode: Call ModelModeScheduler to disable & check if other Model Mode reminders exist
            if (triggerMode == TriggerMode.MODEL_ASSISTED) {
                Log.d(TAG, "📌 Model Mode task detected - calling ModelModeScheduler.disableForReminder()")

                ModelModeScheduler(context).disableForReminder(reminderId)

                // ModelModeScheduler will automatically stop SessionPollingWorker if no other Model Mode reminders exist
            }

            Log.d(TAG, """
                ✅ Task cancelled
                   Reminder ID: $reminderId
                   All jobs stopped
            """.trimIndent())

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to cancel task $reminderId", e)
        }
    }

    /**
     * Alternative cancel method when triggerMode is not available
     * Looks up reminder from database to determine mode
     *
     * @param reminderId The ID of reminder to cancel
     */
    suspend fun cancelTaskWithDatabaseLookup(reminderId: Long) {  // ✅ Add suspend
        try {
            Log.d(TAG, "🔍 Looking up reminder from database: $reminderId")

            val repository = com.example.caresync.data.ReminderRepository(context)
            val reminder = repository.get(reminderId)  // ✅ Now valid (suspend context)

            if (reminder != null) {
                Log.d(TAG, "✅ Found reminder: ${reminder.title}")
                cancelTask(reminderId, reminder.triggerMode)
            } else {
                Log.w(TAG, "⚠️ Reminder not found in database: $reminderId")
                WorkManager.getInstance(context).cancelUniqueWork("reminder-$reminderId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during database lookup cancel", e)
        }
    }


    /**
     * Format timestamp to readable format for logging
     *
     * @param millis Milliseconds timestamp
     * @return Formatted string "MMM dd, HH:mm"
     */
    private fun formatTimestamp(millis: Long): String {
        return try {
            val date = Date(millis)
            val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "N/A"
        }
    }

    companion object {
        private const val TAG = "SchedulingCoordinator"
    }
}
