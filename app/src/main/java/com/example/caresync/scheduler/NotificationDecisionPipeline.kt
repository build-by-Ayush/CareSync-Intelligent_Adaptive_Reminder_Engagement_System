package com.example.caresync.scheduler

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
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
        mlConfidence: Float? = null
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
        // CHECK 4: Blacklist Check (User Repeatedly Dismissed at This Hour)
        // ==========================================
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val blacklistedHours = getBlacklistedHours(reminder.id)
        if (hourNow in blacklistedHours) {
            return DecisionResult(
                shouldSend = false,
                reason = "Hour $hourNow is blacklisted (user dismissed/ignored ≥5 times in last 7 days)",
                blockingRule = "BLACKLISTED_HOUR",
                metadata = mapOf("hour" to hourNow, "blacklistedHours" to blacklistedHours.joinToString())
            )
        }

        // ==========================================
        // CHECK 5: Cooldown Check (Don't Spam Notifications)
        // ==========================================
        val lastTrigger = getLastTriggerTime(reminder.id)
        val cooldownMillis = 60 * 60 * 1000L // 60 minutes cooldown
        if (lastTrigger != null && (System.currentTimeMillis() - lastTrigger) < cooldownMillis) {
            val minutesAgo = (System.currentTimeMillis() - lastTrigger) / 60000
            return DecisionResult(
                shouldSend = false,
                reason = "Cooldown active (last trigger $minutesAgo min ago, need 60 min gap)",
                blockingRule = "COOLDOWN",
                metadata = mapOf("lastTriggerMinutesAgo" to minutesAgo, "cooldownMinutes" to 60)
            )
        }

        // ==========================================
        // CHECK 6: Context-Aware (Avoid During Distraction Apps)
        // ==========================================
        val activeCategory = getActiveAppCategory(context)
        val distractionCategories = listOf("Social Media", "Games", "Entertainment", "Shopping")
        if (activeCategory in distractionCategories) {
            return DecisionResult(
                shouldSend = false,
                reason = "User is in distraction app category: $activeCategory",
                blockingRule = "DISTRACTION_APP",
                metadata = mapOf("category" to activeCategory)
            )
        }

        // ==========================================
        // CHECK 7: Device State (Low Battery, Driving, etc.)
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
        // CHECK 8: Time Window Check (Allowed Hours)
        // ==========================================
        if (reminder.allowedWindowStart != null && reminder.allowedWindowEnd != null) {
            val windowStart = reminder.allowedWindowStart
            val windowEnd = reminder.allowedWindowEnd

            if (hourNow !in windowStart..windowEnd) {
                return DecisionResult(
                    shouldSend = false,
                    reason = "Current hour $hourNow is outside allowed window ($windowStart-$windowEnd)",
                    blockingRule = "OUTSIDE_WINDOW",
                    metadata = mapOf("windowStart" to windowStart, "windowEnd" to windowEnd, "currentHour" to hourNow)
                )
            }
        }

        // ==========================================
        // CHECK 9: Max Snooze Count Check
        // ==========================================
        val snoozeCount = getRecentSnoozeCount(reminder.id)
        if (snoozeCount >= reminder.maxSnoozes) {
            return DecisionResult(
                shouldSend = false,
                reason = "Max snooze count reached ($snoozeCount >= ${reminder.maxSnoozes})",
                blockingRule = "MAX_SNOOZES",
                metadata = mapOf("snoozeCount" to snoozeCount, "maxAllowed" to reminder.maxSnoozes)
            )
        }

        // ==========================================
        // CHECK 10: Priority-Based Filtering
        // ==========================================
        // High/Critical priority tasks bypass some checks (optional future enhancement)

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
     * Get hours that should be blacklisted (user dismissed/ignored ≥5 times)
     */
    private suspend fun getBlacklistedHours(reminderId: Long): Set<Int> {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        return try {
            val badHours = eventDao.getBlacklistedHours(reminderId, sevenDaysAgo, threshold = 5)
            badHours.map { it.hourOfDay }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

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
     * Get currently active app category (from UsageStatsManager)
     * TODO: Implement using UsageStatsManager.queryUsageStats
     */
    private fun getActiveAppCategory(context: Context): String? {
        // Placeholder - implement in Phase 5.5 when integrating ML model
        return null
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
