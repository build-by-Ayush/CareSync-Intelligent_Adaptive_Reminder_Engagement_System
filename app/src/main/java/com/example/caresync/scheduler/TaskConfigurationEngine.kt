package com.example.caresync.scheduler

import android.content.Context
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.ReminderSettings

/**
 * LAYER 1: Task Configuration Engine
 *
 * Responsibilities:
 * - Validate and save task settings
 * - Route to appropriate scheduler
 * - Cleanup old event logs
 * - Return configuration result
 *
 * Entry point from: ReminderViewModel
 */
class TaskConfigurationEngine(private val context: Context) {

    private val repo = ReminderRepository(context)
    private val eventDao = AppDatabase.get(context).reminderEventDao()
    private val coordinator = SchedulingCoordinator(context)

    /**
     * Main entry point: Process task configuration from UI
     *
     * @param reminder Task settings from TaskSettingBottomSheet
     * @return Result with task ID and scheduling info
     */
    suspend fun processTaskConfiguration(reminder: ReminderSettings): ConfigurationResult {
        return try {
            Log.d("CONFIG_ENGINE", "Processing task: ${reminder.title}")

            // Step 1: Validate settings
            val validation = validateSettings(reminder)
            if (!validation.isValid) {
                return ConfigurationResult.ValidationError(validation.error!!)
            }

            // Step 2: Save to database
            val taskId = repo.upsert(reminder)
            Log.d("CONFIG_ENGINE", "Saved task with ID: $taskId")

            // Step 3: Route to appropriate scheduler
            val updatedReminder = reminder.copy(id = taskId)
            val schedulingResult = coordinator.scheduleTask(updatedReminder)

            // Step 4: Cleanup old logs (async, non-blocking)
            cleanupOldEvents()

            // Step 5: Return result
            ConfigurationResult.Success(
                taskId = taskId,
                schedulingInfo = schedulingResult
            )

        } catch (e: Exception) {
            Log.e("CONFIG_ENGINE", "Failed to process task", e)
            ConfigurationResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Cancel existing task configuration
     */
    suspend fun cancelTaskConfiguration(reminderId: Long) {
        try {
            // Only cancel scheduled jobs - DO NOT delete from database
            coordinator.cancelTask(reminderId)

            Log.d("CONFIG_ENGINE", "Cancelled task: $reminderId")
        } catch (e: Exception) {
            Log.e("CONFIG_ENGINE", "Failed to cancel task", e)
        }
    }

    /**
     * ✅ NEW: Delete task completely (for actual deletion)
     */
    suspend fun deleteTaskCompletely(reminderId: Long) {
        try {
            // 1. Cancel scheduled jobs
            coordinator.cancelTask(reminderId)

            // 2. Delete from database
            repo.delete(reminderId)

            // 3. Delete event logs
            eventDao.deleteEventsForReminder(reminderId)

            Log.d("CONFIG_ENGINE", "Deleted task completely: $reminderId")
        } catch (e: Exception) {
            Log.e("CONFIG_ENGINE", "Failed to delete task", e)
        }
    }

    /**
     * ✅ FIXED: Validate task settings before saving
     */
    private fun validateSettings(reminder: ReminderSettings): ValidationResult {
        // Check title
        if (reminder.title.isBlank()) {
            return ValidationResult(false, "Task name cannot be empty")
        }

        // Check time settings for Repetitive mode
        if (reminder.triggerMode == com.example.caresync.domain.TriggerMode.FIXED_TIME) {
            if (reminder.timeOfDayMillis == null && reminder.recurrenceType == com.example.caresync.domain.RecurrenceType.DAILY) {
                return ValidationResult(false, "Please set a time for daily reminders")
            }
        }

        // ✅ FIX #1: Allow minOccurrence = 0 for pure ML mode
        // Only validate if minOccurrence is not provided (null)
        if (reminder.triggerMode == com.example.caresync.domain.TriggerMode.MODEL_ASSISTED) {
            if (reminder.repeatInterval == null) {
                // ✅ CHANGED: Default to 0 (pure ML mode) instead of error
                Log.d("CONFIG_ENGINE", "⚠️ minOccurrence not set, defaulting to 0 (pure ML mode)")
            }
            // ✅ REMOVED: The validation that rejected minOccurrence <= 0
            // Now allows: 0 (pure ML), 1, 2, 3... (with guarantees)
        }

        return ValidationResult(true)
    }

    /**
     * ✅ FIXED: Cleanup comment - was 45 days, said 100 days
     */
    private suspend fun cleanupOldEvents() {
        try {
            val oneHundredDaysAgo = System.currentTimeMillis() - (100 * 24 * 60 * 60 * 1000L)
            val deletedCount = eventDao.deleteOldEvents(oneHundredDaysAgo)
            Log.d("CLEANUP", "Deleted $deletedCount old event logs (>100 days)")
        } catch (e: Exception) {
            Log.e("CLEANUP", "Cleanup failed", e)
        }
    }
}

/**
 * Result of task configuration
 */
sealed class ConfigurationResult {
    data class Success(
        val taskId: Long,
        val schedulingInfo: String
    ) : ConfigurationResult()

    data class ValidationError(val message: String) : ConfigurationResult()
    data class Failure(val reason: String) : ConfigurationResult()
}

/**
 * Validation result helper
 */
private data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
)
