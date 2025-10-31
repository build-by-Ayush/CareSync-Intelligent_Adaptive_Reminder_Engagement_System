package com.example.caresync.intelligence

import android.content.Context
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.PreferredTime
import com.example.caresync.domain.EventTypes
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Optimal Time Learner - Learns best notification times per task
 *
 * Responsibilities:
 * - Track completion/dismissal patterns by hour
 * - Calculate completion rates and statistical confidence
 * - Update preferred_times table incrementally
 *
 * Called by:
 * - CompleteTaskReceiver (after completion)
 * - DismissTaskReceiver (after dismissal)
 *
 * Design:
 * - Per-task learning (not global)
 * - Incremental updates (not batch)
 * - Statistical confidence scoring
 */
class OptimalTimeLearner(private val context: Context) {

    private val eventDao = AppDatabase.get(context).reminderEventDao()
    private val preferredTimesDao = AppDatabase.get(context).preferredTimesDao()

    companion object {
        private const val TAG = "OptimalTimeLearner"
        private const val MIN_SAMPLES_FOR_CONFIDENCE = 5  // Need 5+ events for reliable data
        private const val LEARNING_WINDOW_DAYS = 90      // Learn from last 90 days
    }

    /**
     * Main entry point: Update preferred times after task interaction
     *
     * @param reminderId Task ID
     * @param hour Hour when notification was sent (0-23)
     * @param completed True if task was completed, False if dismissed
     */
    suspend fun updatePreferredTimes(
        reminderId: Long,
        hour: Int,
        completed: Boolean
    ) {
        try {
            Log.d(TAG, "📚 Learning from task $reminderId at hour $hour (completed: $completed)")

            // Get current preferred time entry (if exists)
            val existing = preferredTimesDao.getPreferredTime(reminderId, hour)

            // Calculate new statistics
            val updated = if (existing != null) {
                // Incremental update
                updateExistingEntry(existing, completed)
            } else {
                // First time seeing this hour for this task
                createNewEntry(reminderId, hour, completed)
            }

            // Save to database
            preferredTimesDao.upsert(updated)

            Log.d(TAG, "✅ Updated preferred time: hour=$hour, rate=${String.format("%.1f", updated.completionRate * 100)}%, confidence=${String.format("%.2f", updated.confidence)}")

            // Optional: Log summary of best hours for this task
            logBestHoursSummary(reminderId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update preferred times for task $reminderId", e)
        }
    }

    /**
     * Update existing preferred time entry (incremental learning)
     */
    private fun updateExistingEntry(
        existing: PreferredTime,
        completed: Boolean
    ): PreferredTime {
        // Increment counters
        val newTotalNotifications = existing.totalNotifications + 1
        val newTotalCompletions = if (completed) existing.totalCompletions + 1 else existing.totalCompletions
        val newTotalDismissals = if (!completed) existing.totalDismissals + 1 else existing.totalDismissals

        // Recalculate completion rate
        val newCompletionRate = newTotalCompletions.toFloat() / newTotalNotifications

        // Recalculate confidence (increases with more samples)
        val newConfidence = calculateConfidence(newTotalNotifications)

        return existing.copy(
            completionRate = newCompletionRate,
            totalNotifications = newTotalNotifications,
            totalCompletions = newTotalCompletions,
            totalDismissals = newTotalDismissals,
            confidence = newConfidence,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Create new preferred time entry (first learning instance)
     */
    private fun createNewEntry(
        reminderId: Long,
        hour: Int,
        completed: Boolean
    ): PreferredTime {
        return PreferredTime(
            reminderId = reminderId,
            hourOfDay = hour,
            completionRate = if (completed) 1.0f else 0.0f,  // 100% or 0% on first try
            totalNotifications = 1,
            totalCompletions = if (completed) 1 else 0,
            totalDismissals = if (!completed) 1 else 0,
            confidence = calculateConfidence(1),  // Low confidence with 1 sample
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Calculate statistical confidence based on sample size
     *
     * Uses diminishing returns formula:
     * confidence = min(1.0, samples / (samples + k))
     *
     * Where k = MIN_SAMPLES_FOR_CONFIDENCE
     *
     * Examples:
     * - 1 sample → 0.17 (low confidence)
     * - 5 samples → 0.50 (medium confidence)
     * - 10 samples → 0.67 (good confidence)
     * - 20 samples → 0.80 (high confidence)
     * - 50+ samples → 0.90+ (very high confidence)
     */
    private fun calculateConfidence(sampleSize: Int): Float {
        val k = MIN_SAMPLES_FOR_CONFIDENCE.toFloat()
        return min(1.0f, sampleSize / (sampleSize + k))
    }

    /**
     * Rebuild preferred times from scratch (batch learning)
     *
     * Use this when:
     * - User enables adaptive layer for the first time
     * - You want to recalculate from historical data
     * - Database migration needs initial population
     */
    suspend fun rebuildPreferredTimes(reminderId: Long) {
        try {
            Log.d(TAG, "🔄 Rebuilding preferred times for task $reminderId from history")

            // Get events from last 90 days
            val learningWindowStart = System.currentTimeMillis() - (LEARNING_WINDOW_DAYS * 24 * 60 * 60 * 1000L)
            val events = eventDao.getEventsBetween(
                reminderId,
                learningWindowStart,
                System.currentTimeMillis()
            )

            // Filter to only TRIGGERED, COMPLETED, DISMISSED events
            val relevantEvents = events.filter {
                it.eventType in setOf(
                    EventTypes.TRIGGERED,
                    EventTypes.COMPLETED,
                    EventTypes.DISMISSED
                )
            }

            if (relevantEvents.isEmpty()) {
                Log.d(TAG, "⏭️ No events found, skipping rebuild")
                return
            }

            // Group by hour and calculate statistics
            val statsByHour = calculateHourlyStats(relevantEvents)

            // Clear existing preferred times for this task
            preferredTimesDao.deleteForTask(reminderId)

            // Insert new preferred times
            var insertedCount = 0
            statsByHour.forEach { (hour, stats) ->
                val preferredTime = PreferredTime(
                    reminderId = reminderId,
                    hourOfDay = hour,
                    completionRate = stats.completionRate,
                    totalNotifications = stats.totalNotifications,
                    totalCompletions = stats.totalCompletions,
                    totalDismissals = stats.totalDismissals,
                    confidence = calculateConfidence(stats.totalNotifications),
                    lastUpdated = System.currentTimeMillis()
                )
                preferredTimesDao.upsert(preferredTime)
                insertedCount++
            }

            Log.d(TAG, "✅ Rebuilt preferred times: $insertedCount hours analyzed")
            logBestHoursSummary(reminderId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to rebuild preferred times for task $reminderId", e)
        }
    }

    /**
     * Calculate hourly statistics from event list
     */
    private fun calculateHourlyStats(
        events: List<com.example.caresync.data.ReminderEventEntity>
    ): Map<Int, HourlyStats> {
        val statsByHour = mutableMapOf<Int, HourlyStats>()

        // Group events by hour
        val eventsByHour: Map<Int, List<com.example.caresync.data.ReminderEventEntity>> =
            events.groupBy { it.hourOfDay }

        // Calculate stats for each hour
        eventsByHour.forEach { entry ->
            val hour = entry.key
            val hourEvents = entry.value

            val triggered = hourEvents.count { it.eventType == EventTypes.TRIGGERED }
            val completed = hourEvents.count { it.eventType == EventTypes.COMPLETED }
            val dismissed = hourEvents.count { it.eventType == EventTypes.DISMISSED }

            // Only count hours with actual notifications (TRIGGERED events)
            if (triggered > 0) {
                val completionRate = completed.toFloat() / triggered

                statsByHour[hour] = HourlyStats(
                    totalNotifications = triggered,
                    totalCompletions = completed,
                    totalDismissals = dismissed,
                    completionRate = completionRate
                )
            }
        }

        return statsByHour
    }

    /**
     * Get best hours for a task (helper for schedulers)
     */
    suspend fun getBestHours(
        reminderId: Long,
        minConfidence: Float = 0.5f,
        minSamples: Int = 3,
        limit: Int = 3
    ): List<PreferredTime> {
        return preferredTimesDao.getBestHours(reminderId, minConfidence, minSamples, limit)
    }

    /**
     * Get best hours within a time quadrant (helper for random schedulers)
     *
     * @param quadrant MORNING (6-11), AFTERNOON (12-17), EVENING (18-23)
     */
    suspend fun getBestHoursInQuadrant(
        reminderId: Long,
        quadrant: TimeQuadrant,
        minConfidence: Float = 0.4f,
        limit: Int = 3
    ): List<Int> {
        val (startHour, endHour) = when (quadrant) {
            TimeQuadrant.MORNING -> 6 to 11
            TimeQuadrant.AFTERNOON -> 12 to 17
            TimeQuadrant.EVENING -> 18 to 23
        }

        val preferredTimes = preferredTimesDao.getBestHoursInQuadrant(
            reminderId,
            startHour,
            endHour,
            minConfidence,
            limit
        )

        return preferredTimes.map { it.hourOfDay }
    }

    /**
     * Check if current hour is a good time for this task
     * (Quick check for decision pipeline)
     */
    suspend fun isGoodTimeNow(reminderId: Long): Boolean {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return preferredTimesDao.isPreferredTime(reminderId, currentHour)
    }

    /**
     * Log summary of best hours for debugging
     */
    private suspend fun logBestHoursSummary(reminderId: Long) {
        try {
            val bestHours = preferredTimesDao.getBestHours(reminderId, minConfidence = 0.3f, minSamples = 1, limit = 5)

            if (bestHours.isEmpty()) {
                Log.d(TAG, "📊 Task $reminderId: No preferred times yet (need more data)")
                return
            }

            Log.d(TAG, "📊 Task $reminderId: Top ${bestHours.size} hours:")
            bestHours.forEachIndexed { index, time ->
                Log.d(TAG, "  ${index + 1}. ${formatHour(time.hourOfDay)}: ${String.format("%.0f", time.completionRate * 100)}% (${time.totalNotifications} samples, confidence: ${String.format("%.2f", time.confidence)})")
            }
        } catch (e: Exception) {
            // Silent fail - logging is optional
        }
    }

    /**
     * Test stale blacklist hours and remove if user habits changed
     *
     * Strategy:
     * - Find blacklist hours older than 30 days
     * - Check if any completions happened at those hours recently
     * - If yes (habits improved) → Remove blacklist
     * - If no (still bad) → Keep blacklist
     */
    suspend fun testAndDecayBlacklists(reminderId: Long) {
        try {
            val blacklistDao = AppDatabase.get(context).blacklistHourDao()
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)

            // Get all blacklisted hours for this task
            val blacklistedHours = blacklistDao.getBlacklistedHours(reminderId, threshold = 5)

            blacklistedHours.forEach { blacklist ->
                // Check if blacklist is stale (older than 30 days)
                if (blacklist.lastDismissalTimestamp < thirtyDaysAgo) {
                    Log.d(TAG, "🧪 Testing stale blacklist: hour ${blacklist.hourOfDay} (${blacklist.dismissalCount} dismissals)")

                    // Check recent performance at this hour (last 14 days)
                    val twoWeeksAgo = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L)
                    val recentEvents = eventDao.getEventsBetween(reminderId, twoWeeksAgo, System.currentTimeMillis())
                        .filter { it.hourOfDay == blacklist.hourOfDay }

                    val triggered = recentEvents.count { it.eventType == EventTypes.TRIGGERED }
                    val completed = recentEvents.count { it.eventType == EventTypes.COMPLETED }

                    if (triggered > 0) {
                        val recentCompletionRate = completed.toFloat() / triggered

                        // If recent completion rate >50% → Habits improved, remove blacklist
                        if (recentCompletionRate > 0.5f) {
                            blacklistDao.deleteById(blacklist.id)
                            Log.d(TAG, "✅ Removed stale blacklist: hour ${blacklist.hourOfDay} (improved to ${(recentCompletionRate * 100).toInt()}%)")
                        } else {
                            Log.d(TAG, "⏭️ Keeping blacklist: hour ${blacklist.hourOfDay} (still ${(recentCompletionRate * 100).toInt()}%)")
                        }
                    } else {
                        Log.d(TAG, "⏭️ No recent data for hour ${blacklist.hourOfDay}, keeping blacklist")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to test blacklists", e)
        }
    }

    /**
     * Format hour for display (e.g., 14 → "2 PM")
     */
    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    }
}

/**
 * Time quadrants for random time selection
 */
enum class TimeQuadrant {
    MORNING,    // 6 AM - 11 AM
    AFTERNOON,  // 12 PM - 5 PM
    EVENING     // 6 PM - 11 PM
}

/**
 * Hourly statistics (internal data class)
 */
private data class HourlyStats(
    val totalNotifications: Int,
    val totalCompletions: Int,
    val totalDismissals: Int,
    val completionRate: Float
)
