package com.example.caresync.messaging

import android.content.Context
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.EventTypes
import com.example.caresync.domain.ReminderSettings

/**
 * Analyzes user behavior to determine current state
 * Now tracks: completions, dismissals, ignores, snoozes
 */
class UserStateAnalyzer(private val context: Context) {

    private val eventDao = AppDatabase.get(context).reminderEventDao()

    /**
     * Analyze user's behavior for this task
     * Returns MessageContext with state + behavior stats
     */
    suspend fun analyzeState(reminder: ReminderSettings): MessageContext {
        val now = System.currentTimeMillis()

        // Get last 10 events for this task
        val recentEvents = try {
            val threeMonthAgo = now - 90L * 24 * 60 * 60 * 1000
            eventDao.getEventsBetween(reminder.id, threeMonthAgo, now)
                .sortedByDescending { it.timestamp }
                .take(10)
        } catch (e: Exception) {
            emptyList()
        }

        // ✅ FIXED: Determine user state and get stats
        val (userState, avoidanceStats) = determineUserState(recentEvents, reminder.dueDate)

        // Calculate other stats
        val streakDays = calculateStreak(recentEvents)
        val daysSinceLastDone = calculateDaysSinceLastDone(recentEvents, now)

        // ✅ NEW: Get counts from stats map
        val dismissalCount = avoidanceStats["dismissals"] ?: 0
        val ignoreCount = avoidanceStats["ignores"] ?: 0
        val snoozeCount = avoidanceStats["snoozes"] ?: 0

        // Calculate due date stats
        val (daysUntilDue, daysPastDue) = calculateDueDateStats(reminder.dueDate, now)

        return MessageContext(
            taskTitle = reminder.title,
            userState = userState,
            messageTone = MessageTone.AUTO, // Will be overridden by generator
            streakDays = streakDays,
            daysSinceLastDone = daysSinceLastDone,
            daysUntilDue = daysUntilDue,
            daysPastDue = daysPastDue,
            dismissalCount = dismissalCount,
            priority = reminder.priority.name,

            // ✅ NEW: Add avoidance stats for tone escalation
            avoidanceStats = avoidanceStats
        )
    }

    /**
     * Determine user's current behavior state (ENHANCED)
     * Now returns state + detailed stats map
     */
    private fun determineUserState(
        events: List<com.example.caresync.data.ReminderEventEntity>,
        dueDate: Long?
    ): Pair<UserState, Map<String, Int>> {

        val now = System.currentTimeMillis()

        // Priority 1: Check overdue first
        if (dueDate != null && dueDate < now) {
            return Pair(UserState.OVERDUE, emptyMap())
        }

        // No events yet
        if (events.isEmpty()) {
            return Pair(UserState.FIRST_TIME, emptyMap())
        }

        // Count different types of behavior
        val consecutiveDismissals = countConsecutive(events, EventTypes.DISMISSED)
        val consecutiveIgnores = countConsecutive(events, EventTypes.IGNORED)
        val consecutiveSnoozes = countConsecutive(events, EventTypes.SNOOZED)
        val consecutiveCompletions = countConsecutive(events, EventTypes.COMPLETED)

        // Total avoidance (dismissals + ignores)
        val totalAvoidance = consecutiveDismissals + consecutiveIgnores

        // Stats map for tone escalation
        val stats = mapOf(
            "dismissals" to consecutiveDismissals,
            "ignores" to consecutiveIgnores,
            "snoozes" to consecutiveSnoozes,
            "completions" to consecutiveCompletions,
            "totalAvoidance" to totalAvoidance
        )

        // ✅ Priority 2: NEW - Check approaching deadline with avoidance
        if (dueDate != null) {
            val daysUntilDue = ((dueDate - now) / (24 * 60 * 60 * 1000)).toInt()

            // Deadline approaching (1-2 days) + avoiding (3+ dismissals/ignores)
            if (daysUntilDue in 1..2 && totalAvoidance >= 3) {
                return Pair(UserState.APPROACHING_DEADLINE, stats)
            }
        }

        // Priority 3: Determine state based on behavior
        val state = when {
            consecutiveCompletions >= 3 -> UserState.ON_STREAK
            totalAvoidance >= 5 -> UserState.LAGGING
            consecutiveSnoozes >= 3 -> UserState.LAGGING
            else -> UserState.NEUTRAL
        }

        return Pair(state, stats)
    }

    /**
     * Count consecutive occurrences of event type from newest to oldest
     * Stops when different action encountered (except TRIGGERED)
     */
    private fun countConsecutive(
        events: List<com.example.caresync.data.ReminderEventEntity>,
        eventType: String
    ): Int {
        var count = 0
        for (event in events) {
            if (event.eventType == eventType) {
                count++
            } else if (event.eventType != EventTypes.TRIGGERED) {
                // Stop counting when user takes different action
                // (TRIGGERED doesn't break streak - it's just a notification)
                break
            }
        }
        return count
    }

    /**
     * Calculate consecutive completion streak
     * Similar to countConsecutive but only for completions
     */
    private fun calculateStreak(
        events: List<com.example.caresync.data.ReminderEventEntity>
    ): Int {
        var streak = 0
        for (event in events) {
            if (event.eventType == EventTypes.COMPLETED) {
                streak++
            } else if (event.eventType == EventTypes.DISMISSED ||
                event.eventType == EventTypes.IGNORED) {
                // Dismissals/ignores break streak
                break
            }
            // TRIGGERED and SNOOZED don't break streak
        }
        return streak
    }

    /**
     * Calculate days since last completion
     */
    private fun calculateDaysSinceLastDone(
        events: List<com.example.caresync.data.ReminderEventEntity>,
        now: Long
    ): Int {
        val lastCompleted = events.firstOrNull {
            it.eventType == EventTypes.COMPLETED
        }

        return if (lastCompleted != null) {
            val daysDiff = (now - lastCompleted.timestamp) / (24 * 60 * 60 * 1000)
            daysDiff.toInt()
        } else {
            0
        }
    }

    /**
     * Calculate days until/past due date
     */
    private fun calculateDueDateStats(dueDate: Long?, now: Long): Pair<Int, Int> {
        if (dueDate == null) {
            return Pair(0, 0)
        }

        val diff = (dueDate - now) / (24 * 60 * 60 * 1000)

        return if (diff >= 0) {
            Pair(diff.toInt(), 0) // Days until due
        } else {
            Pair(0, Math.abs(diff).toInt()) // Days past due
        }
    }
}
