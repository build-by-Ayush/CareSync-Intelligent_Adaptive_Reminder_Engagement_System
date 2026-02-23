package com.example.caresync.scheduler

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.*
import java.util.Calendar

/**
 * Centralized decision pipeline for notification triggering.
 *
 * All notification-blocking rules are checked here in sequence.
 * Easy to add/remove/reorder rules without modifying multiple files.
 *
 * Used by: ReminderWorker before showing notification
 */
class NotificationDecisionPipeline(private val context: Context) {

    companion object {
        private const val TAG = "PIPELINE"
    }
    private val eventDao = AppDatabase.get(context).reminderEventDao()

    /**
     * Main entry point: Should we send this notification?
     *
     * @param reminder The task settings from ReminderSettings
     * @param mlPrediction ML model's yes/no (null if not using ML)
     * @param mlConfidence ML model's confidence score (0.0 to 1.0)
     * @return DecisionResult with shouldSend flag and reason
     */
    suspend fun shouldSendNotification(
        reminder: ReminderSettings,
        mlPrediction: Boolean? = null,
        mlConfidence: Float? = null,
        bypassCooldown: Boolean = false,  // ✅ NEW: For fallback & snooze
        triggerSource: String = "SCHEDULER"  // ✅ NEW: Track source
    ): DecisionResult {

        // ==========================================
        // CHECK 1: Is Reminder Enabled?
        // ==========================================
        if (!reminder.enabled) {
            return DecisionResult(
                shouldSend = false,
                reason = "Reminder is disabled by user",
                blockingRule = "DISABLED"
            )
        }

        // ==========================================
        // CHECK 2: ML Model Prediction (if MODEL_ASSISTED mode)
        // ==========================================
        if (reminder.triggerMode == TriggerMode.MODEL_ASSISTED && mlPrediction == false) {
            return DecisionResult(
                shouldSend = false,
                reason = "ML model predicted 'do not send' (confidence: $mlConfidence)",
                blockingRule = "ML_BLOCKED",
                metadata = mapOf("mlConfidence" to mlConfidence)
            )
        }

        // ==========================================
        // CHECK 3: ML Confidence Threshold
        // ==========================================
        if (mlConfidence != null && mlConfidence < reminder.modelConfidenceThreshold) {
            return DecisionResult(
                shouldSend = false,
                reason = "ML confidence too low: $mlConfidence < ${reminder.modelConfidenceThreshold}",
                blockingRule = "LOW_CONFIDENCE",
                metadata = mapOf("threshold" to reminder.modelConfidenceThreshold, "actual" to mlConfidence)
            )
        }

        // ==========================================
        // ✅ NEW CHECK 4: Night Period Filter (Removed)
        // ==========================================

        // ==========================================
        // CHECK 5: Blacklist Check (ONLY for random-time modes)
        // ==========================================

        //  Only applies to Model Mode ML checks and random-time repetitive modes
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (shouldCheckBlacklist(reminder, triggerSource)) {
            try {
                val blacklistDao = AppDatabase.get(context).blacklistHourDao()
                val blacklist = blacklistDao.getBlacklist(reminder.id, hourNow)
                //              ↑ Now properly error-handled

                if (blacklist != null && blacklist.dismissalCount >= 5) {
                    if (reminder.priority != Priority.HIGH && reminder.priority != Priority.CRITICAL) {
                        return DecisionResult(
                            shouldSend = false,
                            reason = "Hour $hourNow blacklisted (${blacklist.dismissalCount} dismissals)",
                            blockingRule = "BLACKLISTED_HOUR",
                            metadata = mapOf("hour" to hourNow, "dismissalCount" to blacklist.dismissalCount)
                        )
                    } else {
                        Log.d(TAG, "⚠️ Hour $hourNow blacklisted but HIGH priority bypasses")
                    }
                }
            } catch (e: Exception) {
                // ✅ NEW: Handle DB failure gracefully
                Log.w(TAG, "Blacklist check failed, continuing without blacklist", e)
                // Don't block - fail open (allow notification)
            }
        }


        // ==========================================
        // CHECK 6: Cooldown Check (ONLY for Model Mode ML checks)
        // ==========================================
        // ✅ UPDATED: Only applies to ML checks in Model Mode, not fallback/repetitive modes
        if (!bypassCooldown &&
            reminder.triggerMode == com.example.caresync.domain.TriggerMode.MODEL_ASSISTED &&
            triggerSource == "ML_CHECK") {  // Only ML checks, not fallback

            val lastTrigger = getLastTriggerTime(reminder.id)
            val cooldownMillis = 60 * 60 * 1000L // 60 minutes cooldown

            if (lastTrigger != null && (System.currentTimeMillis() - lastTrigger) < cooldownMillis) {
                val minutesAgo = (System.currentTimeMillis() - lastTrigger) / 60000
                return DecisionResult(
                    shouldSend = false,
                    reason = "ML cooldown active (last trigger $minutesAgo min ago, need 60 min gap)",
                    blockingRule = "COOLDOWN",
                    metadata = mapOf("lastTriggerMinutesAgo" to minutesAgo, "cooldownMinutes" to 60)
                )
            }
        }

        // ==========================================
        // ✅ NEW CHECK 7: Priority Quota (Prevent Notification Spam)
        // ==========================================
        // ✅ UPDATED: Skip quota check for snoozed notifications (user-initiated)
        if (triggerSource != "SNOOZE") {  // ✅ ADD THIS CHECK
            val quotaExceeded = checkPriorityQuota(reminder.priority, reminder.id)
            if (quotaExceeded) {
                return DecisionResult(
                    shouldSend = false,
                    reason = "Priority quota exceeded for ${reminder.priority} (too many notifications in last hour)",
                    blockingRule = "QUOTA_EXCEEDED",
                    metadata = mapOf("priority" to reminder.priority.name)
                )
            }
        } else {
            // Log that snooze bypassed quota (for debugging)
            Log.d("PIPELINE", "✅ Snooze notification bypasses priority quota")
        }

        // ✅ NEW CHECK: Time Period Restriction (EXCEPT for snooze and night hours)
        if (reminder.allowedTimePeriods.isNotEmpty() && triggerSource != "SNOOZE") {
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            // ✅ NEW: If it's night time (12 AM - 6 AM), allow it through
            // MessageGenerator will handle with gentle tone
            val isNightTime = currentHour in 0..5

            if (isNightTime) {
                Log.d(TAG, "✅ Night time (hour $currentHour), bypassing time period check for gentle notification")
                // Don't block - let MessageGenerator handle it with gentle tone
            } else {
                // ✅ Normal time period check for daytime hours
                val isWithinAllowedPeriod = reminder.allowedTimePeriods.any {
                    it.isWithinPeriod(currentHour)
                }

                if (!isWithinAllowedPeriod) {
                    Log.d(TAG, "🚫 Blocked by TIME_PERIOD: Hour $currentHour not in ${reminder.allowedTimePeriods}")
                    return DecisionResult(
                        shouldSend = false,
                        reason = "Outside allowed time periods",
                        blockingRule = "TIME_PERIOD_RESTRICTED"
                    )
                }
                Log.d(TAG, "✅ Time period OK (hour $currentHour)")
            }
        } else if (triggerSource == "SNOOZE") {
            Log.d(TAG, "✅ Snooze bypasses time period restrictions")
        }


        // ==========================================
        // CHECK 8: Context-Aware (Enhance During Distraction Apps) [Removed]
        // ==========================================

        // ==========================================
        // CHECK 9: Device State (Low Battery, Driving, etc.)
        // ==========================================
        val batteryLevel = getBatteryLevel(context)
        if (batteryLevel != null && batteryLevel < 10) {
            return DecisionResult(
                shouldSend = false,
                reason = "Battery critically low: $batteryLevel%",
                blockingRule = "LOW_BATTERY",
                metadata = mapOf("batteryLevel" to batteryLevel)
            )
        }

        // ==========================================
        // CHECK 11: Max Snooze Count Check (Removed From Here)
        // ==========================================

        // ==========================================
        // ✅ ALL CHECKS PASSED
        // ==========================================
        return DecisionResult(
            shouldSend = true,
            reason = "All checks passed - notification allowed",
            blockingRule = null
        )
    }

    // ==========================================
    // HELPER FUNCTIONS (Query Event Logs)
    // ==========================================

    /**
     * Get timestamp of last triggered notification (for cooldown check)
     */
    private suspend fun getLastTriggerTime(reminderId: Long): Long? {
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        return try {
            val events = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())
            events.firstOrNull { it.eventType == EventTypes.TRIGGERED }?.timestamp
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Count snoozes in the last hour (for max snooze check)
     */
    private suspend fun getRecentSnoozeCount(reminderId: Long): Int {
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        return try {
            val events = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())
            events.count { it.eventType == EventTypes.SNOOZED }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * ✅ UPDATED: Check if priority quota exceeded (prevent notification spam)
     */
    // ✅ CORRECT:
    private suspend fun checkPriorityQuota(priority: Priority, reminderId: Long): Boolean {
        val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        return try {
            // ✅ Get events for THIS reminder only
            val recentNotifications = eventDao.getEventsBetween(reminderId, oneHourAgo, now)
                .filter {
                    it.eventType == EventTypes.TRIGGERED &&
                            it.notificationPriority == priority.name
                }.size

            // ✅ UPDATED: New quota limits per priority
            val quotaLimit = when (priority) {
                Priority.HIGH, Priority.CRITICAL -> Int.MAX_VALUE  // ✅ Unlimited (was 5)
                Priority.NORMAL -> 5  // ✅ Changed from 3 to 5
                Priority.LOW -> 3     // ✅ Changed from 1 to 3
            }

            // ✅ HIGH priority never exceeds quota (unlimited)
            if (priority == Priority.HIGH || priority == Priority.CRITICAL) {
                return false  // Never block HIGH priority
            }

            recentNotifications >= quotaLimit
        } catch (e: Exception) {
            false  // Fail open (allow notification if quota check fails)
        }
    }

    /**
     * Get current battery level percentage
     */
    private fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Determine if blacklist check should apply
     *
     * Apply blacklist to:
     * - Model Mode (ML checks only, not fallback)
     * - Days Mode (random times)
     * - Weekdays Mode (random times)
     *
     * Do NOT apply to:
     * - Hours Mode (fixed times - user chose these times intentionally)
     * - Fallback notifications (guaranteed delivery)
     */
    private fun shouldCheckBlacklist(
        reminder: com.example.caresync.domain.ReminderSettings,
        triggerSource: String
    ): Boolean {
        // Never block fallback notifications
        if (triggerSource == "FALLBACK") return false

        return when (reminder.triggerMode) {
            com.example.caresync.domain.TriggerMode.MODEL_ASSISTED -> {
                // Only apply to ML checks, not fallback
                triggerSource == "ML_CHECK"
            }
            com.example.caresync.domain.TriggerMode.FIXED_TIME -> {
                // Check recurrence type to determine if times are random
                when (reminder.recurrenceType) {
                    com.example.caresync.domain.RecurrenceType.WEEKLY -> true   // Days mode (random times)
                    com.example.caresync.domain.RecurrenceType.INTERVAL -> true // Weekdays mode (random times)
                    com.example.caresync.domain.RecurrenceType.DAILY -> false   // Hours mode (fixed times)
                    else -> false
                }
            }
            else -> false
        }
    }

}

// ==========================================
// RESULT DATA CLASS
// ==========================================

/**
 * Result of the decision pipeline
 *
 * @param shouldSend True if notification should be shown, false if blocked
 * @param reason Human-readable explanation of the decision
 * @param blockingRule Which rule blocked it (null if allowed), used for analytics
 * @param metadata Extra data about the decision (for logging/debugging)
 */
data class DecisionResult(
    val shouldSend: Boolean,
    val reason: String,
    val blockingRule: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)
