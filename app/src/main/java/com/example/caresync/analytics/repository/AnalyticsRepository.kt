package com.example.caresync.analytics.repository

import android.util.Log
import com.example.caresync.analytics.data.AnalyticsDao
import com.example.caresync.analytics.data.UserProgressEntity
import com.example.caresync.analytics.domain.*
import com.example.caresync.analytics.gamification.AchievementEngine
import com.example.caresync.analytics.gamification.PointsCalculator
import com.example.caresync.data.ReminderEventDao
import com.example.caresync.data.ReminderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AnalyticsRepository(
    private val analyticsDao: AnalyticsDao,
    private val reminderEventDao: ReminderEventDao,
    private val reminderDao: ReminderDao,
    private val achievementEngine: AchievementEngine
) {

    /**
     * Get comprehensive user statistics
     */
    suspend fun getUserStatistics(days: Int = 30): UserStatistics {
        val progress = getUserProgressOrDefault()

        // ✅ COUNT TRIGGERED EVENTS (notifications sent)
        // Count notifications (excluding boost and snooze mode)
        val totalTriggered = reminderEventDao.countByTypeExcludingSnoozeAndBoost("TRIGGERED")

        // ✅ COUNT COMPLETED EVENTS
        val totalCompleted = reminderEventDao.countByType("COMPLETED")

        // ✅ CALCULATE REAL COMPLETION RATE
        val completionRate = if (totalTriggered > 0) {
            totalCompleted.toFloat() / totalTriggered
        } else {
            0f  // No notifications sent yet
        }

        // Get best productivity hour
        val hourlyData = analyticsDao.getCompletionsByHour()
        val bestHour = hourlyData.maxByOrNull { it.count }?.hourOfDay

        // Get best tone
        val toneData = analyticsDao.getToneStats()
        val bestTone = toneData
            .filter { it.totalSent > 0 }
            .maxByOrNull { it.completed.toFloat() / it.totalSent }
            ?.toneUsed

        // Calculate average response time
        // ✅ UPDATED: Exclude snooze re-triggers and boost from all events
        val allEvents = reminderEventDao.getAllEvents()
        val events = allEvents.filter { event ->
            // Exclude snooze re-triggers and boost notifications
            !(event.isSnoozedRetrigger || event.triggerSource?.contains("BOOST") == true)
        }
        val completedEvents = events.filter { it.eventType == "COMPLETED" }
        val avgResponseTime = if (completedEvents.isNotEmpty()) {
            5 // Placeholder
        } else {
            0
        }

        // Get tasks completed this week/month
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - TimeUnit.DAYS.toMillis(7)
        val oneMonthAgo = now - TimeUnit.DAYS.toMillis(30)

        val weeklyEvents = events.filter {
            it.eventType == "COMPLETED" && it.timestamp >= oneWeekAgo
        }
        val monthlyEvents = events.filter {
            it.eventType == "COMPLETED" && it.timestamp >= oneMonthAgo
        }

        return UserStatistics(
            totalTasksCompleted = totalCompleted,  // ✅ Use completed count
            completionRate = completionRate,  // ✅ Based on triggered/completed
            currentStreak = progress.currentStreak,
            longestStreak = progress.longestStreak,
            averageResponseTimeMinutes = avgResponseTime,
            totalPoints = progress.totalPoints,
            currentLevel = progress.currentLevel,
            nextLevelPoints = PointsCalculator.getNextLevelPoints(progress.currentLevel),
            bestProductivityHour = bestHour,
            bestTone = bestTone,
            tasksCompletedThisWeek = weeklyEvents.size,
            tasksCompletedThisMonth = monthlyEvents.size,
            totalTasksCreated = totalTriggered  // ✅ Use triggered count (notifications sent)
        )
    }

    /**
     * Get completion rate data for charts
     */
    suspend fun getCompletionRateByDate(days: Int = 30): List<CompletionRateData> {
        val now = System.currentTimeMillis()
        val startDate = now - TimeUnit.DAYS.toMillis(days.toLong())

        // ✅ Get all events in date range
        // ✅ UPDATED: Exclude snooze re-triggers and boost from the start
        val allEvents = reminderEventDao.getAllEvents()
            .filter { event ->
                event.timestamp >= startDate &&
                        // Exclude snooze re-triggers and boost
                        !(event.isSnoozedRetrigger || event.triggerSource?.contains("BOOST") == true)
            }

        // ✅ Group by date string
        val eventsByDate = allEvents.groupBy { event ->
            val cal = Calendar.getInstance().apply { timeInMillis = event.timestamp }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        // ✅ Generate ALL dates in range (fill gaps with zeros)
        val dateMap = mutableMapOf<String, CompletionRateData>()

        // Fill with actual data
        eventsByDate.forEach { (dateString, events) ->
            // ✅ Events already filtered, just count TRIGGERED and COMPLETED
            val triggered = events.count { it.eventType == "TRIGGERED" }
            val completed = events.count { it.eventType == "COMPLETED" }

            val rate = if (triggered > 0) {
                completed.toFloat() / triggered
            } else {
                0f
            }

            dateMap[dateString] = CompletionRateData(
                date = parseDate(dateString),
                completionRate = rate
            )
        }

        // ✅ Generate complete date range (last X days from today)
        val result = mutableListOf<CompletionRateData>()
        val calendar = Calendar.getInstance()

        for (i in (days - 1) downTo 0) {
            calendar.timeInMillis = now - TimeUnit.DAYS.toMillis(i.toLong())
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val dateString = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

            // Use actual data if exists, otherwise use zero
            val data = dateMap[dateString] ?: CompletionRateData(
                date = calendar.timeInMillis,
                completionRate = 0f
            )

            result.add(data)
        }
        return result
    }

    /**
     * Get productivity by hour for bar chart
     */
    suspend fun getProductivityByHour(): List<HourlyProductivity> {
        val hourData = analyticsDao.getCompletionsByHour()

        // ✅ Convert to HourlyProductivity objects
        val allHours = hourData.map {
            HourlyProductivity(
                hour = it.hourOfDay,
                completionCount = it.count
            )
        }

        // ✅ Return top 5 hours (or all if less than 5)
        return if (allHours.isEmpty()) {
            // No data yet - return empty
            emptyList()
        } else {
            // Sort by count descending, take top 5, then sort by hour for display
            allHours
                .sortedByDescending { it.completionCount }
                .take(5)
                .sortedBy { it.hour }
        }
    }

    /**
     * Get weekly completion data for heatmap
     */
    suspend fun getWeeklyCompletions(): List<DailyCompletion> {
        val now = System.currentTimeMillis()
        val twentyEightDaysAgo = now - TimeUnit.DAYS.toMillis(27)  // Today + 27 days back = 28 total

        // Get all completion data
        val dailyData = analyticsDao.getDailyCompletions(twentyEightDaysAgo)

        // Convert to map for easy lookup
        val dataMap = dailyData.associate { day ->
            day.date to day.count
        }

        // ✅ Generate ALL 28 days (4 weeks) from today backwards
        val result = mutableListOf<DailyCompletion>()
        val calendar = Calendar.getInstance()

        // Fill from 27 days ago to today (28 days total)
        for (i in 27 downTo 0) {
            calendar.timeInMillis = now - TimeUnit.DAYS.toMillis(i.toLong())
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val dateString = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

            // Get count from data or 0 if no data
            val count = dataMap[dateString] ?: 0

            result.add(
                DailyCompletion(
                    date = calendar.timeInMillis,
                    completionCount = count
                )
            )
        }
        return result
    }

    /**
     * Get tone effectiveness statistics
     */
    suspend fun getToneEffectiveness(): List<ToneStats> {
        val rawStats = analyticsDao.getToneStats()

        return rawStats.map { raw ->
            ToneStats(
                toneName = raw.toneUsed,
                sentCount = raw.totalSent,
                completedCount = raw.completed,
                completionRate = if (raw.totalSent > 0) {
                    raw.completed.toFloat() / raw.totalSent
                } else {
                    0f
                }
            )
        }.sortedByDescending { it.completionRate }
    }

    /**
     * Get current streak information
     */
    suspend fun getStreakInfo(): Pair<Int, Int> {
        val progress = getUserProgressOrDefault()
        return Pair(progress.currentStreak, progress.longestStreak)
    }

    /**
     * Get or create user progress
     */
    suspend fun getUserProgress(): UserProgressEntity {
        return getUserProgressOrDefault()
    }

    /**
     * Update user progress after task completion
     */
    suspend fun updateUserProgress(
        taskCompleted: Boolean = true,
        pointsEarned: Int = 0
    ) {
        val progress = getUserProgressOrDefault()

        val newPoints = progress.totalPoints + pointsEarned
        val newLevel = PointsCalculator.calculateLevel(newPoints)

        // Update streak if task completed
        val (newStreak, newLongest) = if (taskCompleted) {
            calculateStreakUpdate(progress)
        } else {
            Pair(progress.currentStreak, progress.longestStreak)
        }

        val updatedProgress = progress.copy(
            totalPoints = newPoints,
            currentLevel = newLevel,
            currentStreak = newStreak,
            longestStreak = maxOf(newLongest, progress.longestStreak),
            lastCompletionDate = if (taskCompleted) System.currentTimeMillis() else progress.lastCompletionDate,
            totalTasksCompleted = if (taskCompleted) progress.totalTasksCompleted + 1 else progress.totalTasksCompleted
        )

        analyticsDao.updateUserProgress(updatedProgress)

        // Check for achievement unlocks
        achievementEngine.checkAndUnlockAchievements()
    }

    /**
     * Validate and update streak based on recent activity
     * Called when dashboard loads to check for missed days
     */
    suspend fun validateStreak() {
        val progress = getUserProgressOrDefault()
        val lastCompletion = progress.lastCompletionDate ?: return  // No completions yet

        val now = System.currentTimeMillis()
        val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletion)

        // If more than 1 day has passed, check for missed days
        if (daysSinceLastCompletion > 0) {
            // Get all dates with notifications since last completion
            val datesWithNotifications = reminderEventDao.getDatesWithNotifications(
                startDate = lastCompletion,
                endDate = now
            )

            // Check each date for completions
            var streakBroken = false
            for (dateString in datesWithNotifications) {
                // Skip today (current day) - user might still complete tasks
                val dateTimestamp = parseDate(dateString)
                val daysSinceDate = TimeUnit.MILLISECONDS.toDays(now - dateTimestamp)
                val isToday = daysSinceDate == 0L
                if (isToday) continue

                // ✅ Pass STRING to hasCompletionOnDate
                val hadCompletion = reminderEventDao.hasCompletionOnDate(dateString)
                if (!hadCompletion) {
                    // Found a day with notifications but no completions
                    streakBroken = true
                    break
                }
            }

            if (streakBroken) {
                // Reset streak to 0
                val updatedProgress = progress.copy(
                    currentStreak = 0
                )
                analyticsDao.updateUserProgress(updatedProgress)
                Log.d("STREAK_VALIDATION", "⚠️ Streak broken! Reset to 0")
            }
        }
    }

    /**
     * Get all achievements with unlock status
     */
    suspend fun getAchievements(): List<Achievement> {
        val entities = analyticsDao.getAllAchievements()
        val progress = getUserProgressOrDefault()

        return entities.map { entity ->
            entity.toDomain(
                progress = calculateAchievementProgress(entity.id, progress)
            )
        }
    }

    /**
     * Get only unlocked achievements
     */
    suspend fun getUnlockedAchievements(): List<Achievement> {
        val entities = analyticsDao.getUnlockedAchievements()
        return entities.map { it.toDomain(progress = 1f) }
    }

    /**
     * Manually unlock an achievement (for testing or special events)
     */
    suspend fun unlockAchievement(achievementId: String) {
        analyticsDao.unlockAchievement(achievementId, System.currentTimeMillis())
    }

    /**
     * Generate productivity insights based on user data
     */
    suspend fun generateInsights(): List<ProductivityInsight> {
        val insights = mutableListOf<ProductivityInsight>()
        val stats = getUserStatistics()

        // Best time insight
        stats.bestProductivityHour?.let { hour ->
            insights.add(
                ProductivityInsight(
                    type = InsightType.BEST_TIME,
                    message = "You're most productive at ${formatHour(hour)}",
                    actionable = true,
                    actionText = "Schedule more tasks at this time",
                    icon = "⏰"
                )
            )
        }

        // Best tone insight
        stats.bestTone?.let { tone ->
            val toneStats = getToneEffectiveness().find { it.toneName == tone }
            toneStats?.let {
                val rate = (it.completionRate * 100).toInt()
                insights.add(
                    ProductivityInsight(
                        type = InsightType.BEST_TONE,
                        message = "$tone tone works best for you ($rate% completion)",
                        actionable = true,
                        actionText = "Use this tone more often",
                        icon = "💬"
                    )
                )
            }
        }

        // Streak milestone
        if (stats.currentStreak > 0) {
            val nextMilestone = when {
                stats.currentStreak < 3 -> 3
                stats.currentStreak < 7 -> 7
                stats.currentStreak < 30 -> 30
                else -> null
            }

            nextMilestone?.let { milestone ->
                val daysLeft = milestone - stats.currentStreak
                insights.add(
                    ProductivityInsight(
                        type = InsightType.STREAK_MILESTONE,
                        message = "You're $daysLeft days away from ${milestone}-day streak!",
                        actionable = false,
                        icon = "🏆"
                    )
                )
            }
        }

        // Completion rate insight
        val rate = (stats.completionRate * 100).toInt()
        when {
            rate >= 90 -> {
                insights.add(
                    ProductivityInsight(
                        type = InsightType.COMPLETION_RATE,
                        message = "Excellent! $rate% completion rate",
                        actionable = false,
                        icon = "✅"
                    )
                )
            }
            rate >= 70 -> {
                insights.add(
                    ProductivityInsight(
                        type = InsightType.COMPLETION_RATE,
                        message = "Good job! $rate% completion rate",
                        actionable = false,
                        icon = "✅"
                    )
                )
            }
            rate < 50 -> {
                insights.add(
                    ProductivityInsight(
                        type = InsightType.IMPROVEMENT_SUGGESTION,
                        message = "Completion rate is $rate%. Try setting fewer tasks or adjusting priorities",
                        actionable = true,
                        actionText = "Adjust task settings",
                        icon = "💡"
                    )
                )
            }
        }

        // Streak at risk warning
        if (stats.currentStreak >= 3) {
            val lastCompletion = getUserProgressOrDefault().lastCompletionDate
            if (lastCompletion != null) {
                val hoursSinceLastCompletion = TimeUnit.MILLISECONDS.toHours(
                    System.currentTimeMillis() - lastCompletion
                )
                if (hoursSinceLastCompletion >= 20) {
                    insights.add(
                        ProductivityInsight(
                            type = InsightType.WARNING,
                            message = "Your ${stats.currentStreak}-day streak is at risk! Complete a task today",
                            actionable = true,
                            actionText = "View tasks",
                            icon = "⚠️"
                        )
                    )
                }
            }
        }

        return insights
    }

    // ========== PRIVATE HELPER METHODS ==========

    private suspend fun getUserProgressOrDefault(): UserProgressEntity {
        return analyticsDao.getUserProgress() ?: UserProgressEntity().also {
            analyticsDao.insertUserProgress(it)
        }
    }

    /**
     * ✅ UPDATED: Calculate streak considering only days with notifications
     * Days without notifications don't break the streak
     */
    private suspend fun calculateStreakUpdate(progress: UserProgressEntity): Pair<Int, Int> {
        val lastCompletion = progress.lastCompletionDate
        val now = System.currentTimeMillis()

        if (lastCompletion == null) {
            // First completion ever
            return Pair(1, 1)
        }

        val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletion)

        // Same day completion - streak stays same
        if (daysSinceLastCompletion == 0L) {
            return Pair(progress.currentStreak, progress.longestStreak)
        }

        // ✅ NEW: Get all dates that had notifications between last completion and now
        val datesWithNotifications = reminderEventDao.getDatesWithNotifications(
            startDate = lastCompletion,
            endDate = now
        )

        // ✅ NEW: Check each date with notifications for completions
        var missedDays = 0
        for (dateString in datesWithNotifications) {
            // ✅ Pass STRING to hasCompletionOnDate
            val hadCompletion = reminderEventDao.hasCompletionOnDate(dateString)
            if (!hadCompletion) {
                missedDays++
                break  // One missed day breaks the streak
            }
        }

        return if (missedDays == 0) {
            // No missed days with notifications → streak continues
            val newStreak = progress.currentStreak + 1
            Pair(newStreak, maxOf(newStreak, progress.longestStreak))
        } else {
            // Missed at least one day that had notifications → reset streak
            Pair(1, progress.longestStreak)
        }
    }

    private fun calculateAchievementProgress(achievementId: String, progress: UserProgressEntity): Float {
        return when (achievementId) {
            "first_step" -> minOf(progress.totalTasksCompleted.toFloat() / 1f, 1f)
            "quick_starter" -> minOf(progress.totalTasksCompleted.toFloat() / 5f, 1f)
            "hot_streak" -> minOf(progress.currentStreak.toFloat() / 3f, 1f)
            "week_warrior" -> minOf(progress.currentStreak.toFloat() / 7f, 1f)
            "monthly_master" -> minOf(progress.currentStreak.toFloat() / 30f, 1f)
            "half_century" -> minOf(progress.totalTasksCompleted.toFloat() / 50f, 1f)
            "centurion" -> minOf(progress.totalTasksCompleted.toFloat() / 100f, 1f)
            else -> 0f
        }
    }


    private fun parseDate(dateString: String): Long {
        // Parse "YYYY-MM-DD" format to timestamp
        return try {
            val parts = dateString.split("-")
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)  // Month is 0-indexed
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatHour(hour: Int): String {
        return when (hour) {
            0 -> "midnight"
            in 1..11 -> "$hour AM"
            12 -> "noon"
            else -> "${hour - 12} PM"
        }
    }
}
