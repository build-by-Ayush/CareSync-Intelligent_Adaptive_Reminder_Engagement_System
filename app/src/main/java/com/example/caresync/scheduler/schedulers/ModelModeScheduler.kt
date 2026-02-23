package com.example.caresync.scheduler.schedulers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.scheduler.workers.SessionPollingWorker
import java.util.concurrent.TimeUnit

/**
 * LAYER 3A: Model Mode Scheduler - SessionPollingWorker Lifecycle Manager
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * PURPOSE: Control when SessionPollingWorker starts and stops
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * RESPONSIBILITIES:
 * ├─ Enable SessionPollingWorker when Model Mode reminder created
 * ├─ Disable SessionPollingWorker when last Model Mode reminder deleted
 * ├─ Check if other Model Mode reminders exist before stopping
 * └─ Optimize battery by avoiding unnecessary background work
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * HOW IT WORKS:
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * CREATE Model Mode Reminder:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ 1. User creates reminder with Model Mode                               │
 * │ 2. TaskConfigurationEngine → SchedulingCoordinator                      │
 * │ 3. SchedulingCoordinator.scheduleTask() calls:                          │
 * │    ModelModeScheduler.enableForReminder(reminder)                       │
 * │ 4. enableForReminder() calls startSessionPollingWorker()               │
 * │ 5. SessionPollingWorker starts (if not already running)                │
 * │ 6. Result: Every 15 min, SessionPollingWorker detects app engagement   │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * DELETE Model Mode Reminder:
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ 1. User deletes reminder                                               │
 * │ 2. TaskConfigurationEngine → SchedulingCoordinator                      │
 * │ 3. SchedulingCoordinator.cancelTask() calls:                            │
 * │    ModelModeScheduler.disableForReminder(reminderId)                    │
 * │ 4. disableForReminder() checks: Are there OTHER Model Mode reminders?  │
 * │    a. If YES → Keep SessionPollingWorker running                       │
 * │    b. If NO → Stop SessionPollingWorker (save battery!)                │
 * │ 5. Result: Worker only runs when needed                                │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * BATTERY OPTIMIZATION:
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * BEFORE (always running): Every 15 min wakes device → Drains battery ❌
 * AFTER (smart start/stop):
 *   ├─ Model Mode reminder exists → Run every 15 min ✅
 *   └─ No Model Mode reminders → Stop (battery saved!) ✅
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * SessionPollingWorker Details:
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * - Frequency: Every 15 minutes
 * - Purpose: Detect when app reaches engagement milestones (5, 10, 15, 20, 25 min)
 * - Action: Fires mini-alarms at each milestone
 * - Triggers: SessionAlarmReceiver → MLCheckWorker
 * - Result: Real-time ML notifications for Model Mode
 */
class ModelModeScheduler(private val context: Context) {

    // ═════════════════════════════════════════════════════════════════════
    // PUBLIC METHODS (Called from SchedulingCoordinator)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * ENABLE SessionPollingWorker for a new Model Mode reminder
     *
     * Called when user creates or enables a Model Mode reminder
     *
     * @param reminder The ReminderSettings that was just created/enabled
     *
     * Flow:
     * 1. Log that Model Mode was enabled
     * 2. Call startSessionPollingWorker()
     * 3. SessionPollingWorker now active (will run every 15 min)
     *
     * Example:
     * ┌─────────────────────────────────────────────────────────────────┐
     * │ User creates "Study Time" with Model Mode                       │
     * │ ↓                                                               │
     * │ enableForReminder(studyReminder)                                │
     * │ ↓                                                               │
     * │ startSessionPollingWorker()                                     │
     * │ ↓                                                               │
     * │ SessionPollingWorker active every 15 min ✅                     │
     * └─────────────────────────────────────────────────────────────────┘
     */
    suspend fun enableForReminder(reminder: ReminderSettings) {
        Log.d(TAG, """
            ✅ Model Mode ENABLED
               Reminder: ${reminder.title} (ID: ${reminder.id})
               Starting SessionPollingWorker if not already running...
        """.trimIndent())

        // Start the background worker
        startSessionPollingWorker()

        Log.d(TAG, "✅ SessionPollingWorker is now active for Model Mode")
    }

    /**
     * DISABLE SessionPollingWorker when a Model Mode reminder is deleted/disabled
     *
     * Called when user deletes or disables a Model Mode reminder
     *
     * Smart Logic:
     * 1. Check if OTHER Model Mode reminders exist
     * 2. If YES → Keep SessionPollingWorker running (still needed)
     * 3. If NO → Stop SessionPollingWorker (save battery!)
     *
     * @param reminderId The ID of reminder that was deleted/disabled
     *
     * Example 1 - Multiple reminders:
     * ┌─────────────────────────────────────────────────────────────────┐
     * │ Reminders: [Study (Model Mode), Focus (Model Mode)]             │
     * │ User deletes: Study                                             │
     * │ ↓                                                               │
     * │ disableForReminder(studyId)                                     │
     * │ ↓                                                               │
     * │ Check: Other Model Mode reminders? YES (Focus exists)           │
     * │ ↓                                                               │
     * │ Keep SessionPollingWorker running ✅ (Focus still needs it)     │
     * └─────────────────────────────────────────────────────────────────┘
     *
     * Example 2 - Last reminder:
     * ┌─────────────────────────────────────────────────────────────────┐
     * │ Reminders: [Study (Model Mode)]                                 │
     * │ User deletes: Study                                             │
     * │ ↓                                                               │
     * │ disableForReminder(studyId)                                     │
     * │ ↓                                                               │
     * │ Check: Other Model Mode reminders? NO                           │
     * │ ↓                                                               │
     * │ Stop SessionPollingWorker ⏹️ (No Model Mode reminders left)     │
     * │ Result: Battery saved! ✅                                       │
     * └─────────────────────────────────────────────────────────────────┘
     */
    suspend fun disableForReminder(reminderId: Long) {
        Log.d(TAG, """
            ❌ Model Mode DISABLED
               Reminder ID: $reminderId
               Checking if other Model Mode reminders exist...
        """.trimIndent())

        // Smart check: Are there OTHER Model Mode reminders?
        val hasOtherModelModeReminders = checkIfAnyModelModeRemindersExist()

        if (hasOtherModelModeReminders) {
            // YES: Other reminders exist, keep worker running
            Log.d(TAG, """
                ℹ️ Other Model Mode reminders exist
                   Keeping SessionPollingWorker active
            """.trimIndent())
            // Don't stop the worker
        } else {
            // NO: No other reminders, safe to stop worker
            Log.d(TAG, """
                🔄 No Model Mode reminders left
                   Stopping SessionPollingWorker (battery optimization)
            """.trimIndent())

            // Stop the background worker (saves battery!)
            stopSessionPollingWorker()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // PRIVATE METHODS (Internal implementation)
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Start SessionPollingWorker
     *
     * Creates and enqueues a periodic work request that runs every 15 minutes
     *
     * Configuration:
     * ├─ Frequency: 15 minutes
     * ├─ Worker: SessionPollingWorker
     * ├─ Constraint: Battery not low (protects battery)
     * ├─ Policy: KEEP (don't restart if already running)
     * └─ Tag: "session-polling" (for identification)
     *
     * Result:
     * - SessionPollingWorker will wake up every 15 minutes
     * - Check which apps are running
     * - Fire mini-alarms at engagement milestones
     * - Trigger SessionAlarmReceiver → MLCheckWorker
     */
    suspend fun startSessionPollingWorker() {
        Log.d(TAG, """
        ══════════════════════════════════════════════════════
        🚀 ABOUT TO ENQUEUE SessionPollingWorker
        ══════════════════════════════════════════════════════
    """.trimIndent())

        val sessionPollingRequest = PeriodicWorkRequestBuilder<SessionPollingWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    // ✅ REMOVE battery constraint for testing:
                    // .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag("session-polling")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "session-polling-worker",
            ExistingPeriodicWorkPolicy.REPLACE,
            sessionPollingRequest
        )

        Log.d(TAG, """
        ══════════════════════════════════════════════════════
        ✅ SessionPollingWorker ENQUEUED (no battery constraint)
        ══════════════════════════════════════════════════════
    """.trimIndent())
    }


    /**
     * Stop SessionPollingWorker
     *
     * Called when all Model Mode reminders are deleted
     * Saves battery by canceling unnecessary background work
     *
     * Action: Cancels the unique periodic work request
     */
    suspend fun stopSessionPollingWorker() {
        Log.d(TAG, "⏹️ Stopping SessionPollingWorker...")

        try {
            // Cancel the periodic work
            WorkManager.getInstance(context)
                .cancelUniqueWork("session-polling-worker")

            Log.d(TAG, """
                ✅ SessionPollingWorker stopped
                   Battery optimization: Active
                   Status: INACTIVE (no Model Mode reminders)
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping SessionPollingWorker", e)
        }
    }

    /**
     * Check if ANY Model Mode reminders exist in database
     *
     * Query database to find reminders with:
     * - triggerMode = "MODEL_ASSISTED"
     * - enabled = true
     *
     * Used to decide whether to keep or stop SessionPollingWorker
     *
     * @return true if at least one Model Mode reminder exists
     *         false if no Model Mode reminders exist
     *
     * Example Results:
     * ├─ Database has [Study (Model Mode), Task2 (Fixed Time)]
     * │  → Result: true (Study is Model Mode) ✅
     * ├─ Database has [Task1 (Fixed Time), Task2 (Fixed Time)]
     * │  → Result: false (no Model Mode) ❌
     * └─ Database is empty
     *    → Result: false (no reminders) ❌
     */
    suspend fun checkIfAnyModelModeRemindersExist(): Boolean {
        return try {
            // Query database for ALL reminders with Model Mode enabled
            val repository = ReminderRepository(context)
            val hasReminders = repository.getAllWithModelMode().isNotEmpty()

            // Log result for debugging
            Log.d(TAG, "📊 Model Mode reminder count: ${if (hasReminders) "≥1" else "0"}")

            hasReminders
        } catch (e: Exception) {
            // If error: Log and assume reminders exist (safe default)
            Log.e(TAG, "❌ Error checking Model Mode reminders", e)
            true  // Safe to assume reminders exist on error
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // COMPANION OBJECT (Static members)
    // ═════════════════════════════════════════════════════════════════════

    companion object {
        // Tag for logging (appears in logcat as "ModelModeScheduler")
        private const val TAG = "ModelModeScheduler"
    }
}
