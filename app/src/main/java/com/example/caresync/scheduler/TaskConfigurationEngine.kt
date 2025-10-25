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
            // Cancel all scheduled jobs
            coordinator.cancelTask(reminderId)

            // Delete from database
            repo.delete(reminderId)

            // Delete event logs
            eventDao.deleteEventsForReminder(reminderId)

            Log.d("CONFIG_ENGINE", "Cancelled task: $reminderId")
        } catch (e: Exception) {
            Log.e("CONFIG_ENGINE", "Failed to cancel task", e)
        }
    }

    /**
     * Validate task settings before saving
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

        // Check min occurrence for Model mode
        if (reminder.triggerMode == com.example.caresync.domain.TriggerMode.MODEL_ASSISTED) {
            if (reminder.repeatInterval == null || reminder.repeatInterval <= 0) {
                return ValidationResult(false, "Please set minimum occurrences for Model mode")
            }
        }

        return ValidationResult(true)
    }

    /**
     * Cleanup event logs older than 45 days
     */
    private suspend fun cleanupOldEvents() {
        try {
            val fortyFiveDaysAgo = System.currentTimeMillis() - 100 * 24 * 60 * 60 * 1000L
            val deletedCount = eventDao.deleteOldEvents(fortyFiveDaysAgo)
            Log.d("CLEANUP", "Deleted $deletedCount old event logs (>45 days)")
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
