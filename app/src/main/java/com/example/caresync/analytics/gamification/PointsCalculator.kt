package com.example.caresync.analytics.gamification

import com.example.caresync.domain.Priority

object PointsCalculator {

    // Point values for different scenarios
    private const val BASE_POINTS_CRITICAL = 20
    private const val BASE_POINTS_HIGH = 15
    private const val BASE_POINTS_NORMAL = 10
    private const val BASE_POINTS_LOW = 5

    // ✅ NEW: Response time bonuses (not due date bonuses)
    private const val QUICK_RESPONSE_BONUS = 5   // Within 30 min
    private const val FAST_RESPONSE_BONUS = 2    // Within 1 hour
    private const val DELAYED_PENALTY = 2        // Next day or later

    // Streak bonuses
    private const val THREE_DAY_STREAK_BONUS = 20
    private const val SEVEN_DAY_STREAK_BONUS = 50
    private const val THIRTY_DAY_STREAK_BONUS = 200

    /**
     * ✅ UPDATED: Calculate points for a completed task
     * No longer uses due date - uses notification response time instead
     *
     * @param priority Task priority (CRITICAL, HIGH, NORMAL, LOW)
     * @param completedAt Timestamp when task was completed
     * @param notificationSentAt Timestamp when notification was sent (null if no notification)
     * @return Points earned for this completion
     */
    fun calculateTaskPoints(
        priority: Priority,
        completedAt: Long,
        notificationSentAt: Long? = null  // ← Changed from dueAt
    ): Int {
        // Base points by priority
        val basePoints = when (priority) {
            Priority.CRITICAL -> BASE_POINTS_CRITICAL
            Priority.HIGH -> BASE_POINTS_HIGH
            Priority.NORMAL -> BASE_POINTS_NORMAL
            Priority.LOW -> BASE_POINTS_LOW
        }

        // ✅ NEW: If no notification time tracked, just return base points
        if (notificationSentAt == null) {
            return basePoints
        }

        // ✅ NEW: Calculate response time bonus based on how quickly user responded
        val responseTimeBonus = calculateResponseTimeBonus(completedAt, notificationSentAt)

        // Total can't be negative
        return maxOf(basePoints + responseTimeBonus, 1)
    }

    /**
     * ✅ NEW: Calculate bonus/penalty based on notification response time
     * Rewards quick responses, penalizes delayed responses
     */
    private fun calculateResponseTimeBonus(completedAt: Long, notificationSentAt: Long): Int {
        val responseTime = completedAt - notificationSentAt

        return when {
            // Completed within 30 minutes of notification
            responseTime <= 1800_000 -> QUICK_RESPONSE_BONUS  // +5 points

            // Completed within 1 hour of notification
            responseTime <= 3600_000 -> FAST_RESPONSE_BONUS  // +2 points

            // Completed same day (within 24 hours)
            responseTime <= 86400_000 -> 0  // No bonus, no penalty

            // Completed next day or later
            else -> -DELAYED_PENALTY  // -2 points
        }
    }

    /**
     * Calculate streak bonus points
     *
     * @param streakDays Current streak length in days
     * @return Bonus points for reaching streak milestone
     */
    fun calculateStreakBonus(streakDays: Int): Int {
        return when {
            streakDays >= 30 -> THIRTY_DAY_STREAK_BONUS
            streakDays >= 7 -> SEVEN_DAY_STREAK_BONUS
            streakDays >= 3 -> THREE_DAY_STREAK_BONUS
            else -> 0
        }
    }

    /**
     * Calculate user level from total points
     *
     * Level 1 (Beginner): 0-100 points
     * Level 2 (Consistent): 100-500 points
     * Level 3 (Productive): 500-1500 points
     * Level 4 (Champion): 1500-5000 points
     * Level 5 (Master): 5000+ points
     */
    fun calculateLevel(totalPoints: Int): Int {
        return when {
            totalPoints < 100 -> 1
            totalPoints < 500 -> 2
            totalPoints < 1500 -> 3
            totalPoints < 5000 -> 4
            else -> 5
        }
    }

    /**
     * Get points required for next level
     */
    fun getNextLevelPoints(currentLevel: Int): Int {
        return when (currentLevel) {
            1 -> 100
            2 -> 500
            3 -> 1500
            4 -> 5000
            5 -> Int.MAX_VALUE  // Max level reached
            else -> 100
        }
    }

    /**
     * Calculate progress towards next level (0.0 to 1.0)
     */
    fun calculateLevelProgress(totalPoints: Int, currentLevel: Int): Float {
        val currentLevelStart = when (currentLevel) {
            1 -> 0
            2 -> 100
            3 -> 500
            4 -> 1500
            5 -> 5000
            else -> 0
        }

        val nextLevelStart = getNextLevelPoints(currentLevel)

        if (nextLevelStart == Int.MAX_VALUE) return 1f  // Max level

        val pointsInLevel = totalPoints - currentLevelStart
        val pointsNeeded = nextLevelStart - currentLevelStart

        return (pointsInLevel.toFloat() / pointsNeeded).coerceIn(0f, 1f)
    }
}
