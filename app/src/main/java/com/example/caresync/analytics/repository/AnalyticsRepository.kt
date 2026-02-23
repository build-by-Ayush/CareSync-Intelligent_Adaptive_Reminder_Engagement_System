package com.example.caresync.analytics.repository

import android.util.Log
import com.example.caresync.analytics.data.AnalyticsDao
import com.example.caresync.analytics.data.UserProgressEntity
import com.example.caresync.analytics.domain.*
import com.example.caresync.analytics.gamification.AchievementEngine
import com.example.caresync.analytics.gamification.PointsCalculator
import com.example.caresync.data.ReminderEventDao
import com.example.caresync.data.ReminderDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AnalyticsRepository(
    private val analyticsDao: AnalyticsDao,
    private val reminderEventDao: ReminderEventDao,
    private val reminderDao: ReminderDao,
    private val achievementEngine: AchievementEngine
) {

    suspend fun getUserStatistics(days: Int = 30): UserStatistics {
        val progress = getUserProgressOrDefault()
        val totalTriggered = reminderEventDao.countByTypeExcludingSnoozeAndBoost("TRIGGERED")
        val totalCompleted = reminderEventDao.countByType("COMPLETED")

        val completionRate = if (totalTriggered > 0) {
            totalCompleted.toFloat() / totalTriggered
        } else {
            0f
        }

        val hourlyData = analyticsDao.getCompletionsByHour()
        val bestHour = hourlyData.maxByOrNull { it.count }?.hourOfDay

        val toneData = analyticsDao.getToneStats()
        val bestTone = toneData
            .filter { it.totalSent > 0 }
            .maxByOrNull { it.completed.toFloat() / it.totalSent }
            ?.toneUsed

        val allEvents = reminderEventDao.getAllEvents().filter { event ->
            !(event.isSnoozedRetrigger || event.triggerSource?.contains("BOOST") == true)
        }

        val now = System.currentTimeMillis()
        val oneWeekAgo = now - TimeUnit.DAYS.toMillis(7)
        val oneMonthAgo = now - TimeUnit.DAYS.toMillis(30)

        val weeklyCompletions = allEvents.count {
            it.eventType == "COMPLETED" && it.timestamp >= oneWeekAgo
        }
        val monthlyCompletions = allEvents.count {
            it.eventType == "COMPLETED" && it.timestamp >= oneMonthAgo
        }

        return UserStatistics(
            totalTasksCompleted = totalCompleted,
            completionRate = completionRate,
            currentStreak = progress.currentStreak,
            longestStreak = progress.longestStreak,
            averageResponseTimeMinutes = 5,
            totalPoints = progress.totalPoints,
            currentLevel = progress.currentLevel,
            nextLevelPoints = PointsCalculator.getNextLevelPoints(progress.currentLevel),
            bestProductivityHour = bestHour,
            bestTone = bestTone,
            tasksCompletedThisWeek = weeklyCompletions,
            tasksCompletedThisMonth = monthlyCompletions,
            totalTasksCreated = totalTriggered
        )
    }

    suspend fun getCompletionRateByDate(days: Int = 7): List<CompletionRateData> {
        return try {
            val now = System.currentTimeMillis()
            val startDate = now - (days.toLong() * 24 * 60 * 60 * 1000)

            val dailyStats = analyticsDao.getDailyCompletionStats(startDate, now)
            val dataMap = dailyStats.associate { it.date to it.completionRate }

            val result = mutableListOf<CompletionRateData>()
            val calendar = Calendar.getInstance()

            for (i in (days - 1) downTo 0) {
                calendar.timeInMillis = now - (i.toLong() * 24 * 60 * 60 * 1000)
                val dateString = calendar.formatDateString()
                val rate = dataMap[dateString] ?: 0f

                result.add(
                    CompletionRateData(
                        date = calendar.timeInMillis,
                        completionRate = rate
                    )
                )
            }

            result
        } catch (e: Exception) {
            Log.e("AnalyticsRepo", "Error fetching completion rate", e)
            emptyList()
        }
    }

    suspend fun getProductivityByHour(): List<HourlyProductivity> {
        return analyticsDao.getCompletionsByHour()
            .map { HourlyProductivity(it.hourOfDay, it.count) }
            .sortedByDescending { it.completionCount }
            .take(5)
            .sortedBy { it.hour }
    }

    suspend fun getWeeklyCompletions(): List<DailyCompletion> {
        val now = System.currentTimeMillis()
        val twentyEightDaysAgo = now - TimeUnit.DAYS.toMillis(27)
        val dailyData = analyticsDao.getDailyCompletions(twentyEightDaysAgo)
        val dataMap = dailyData.associate { it.date to it.count }

        val result = mutableListOf<DailyCompletion>()
        val calendar = Calendar.getInstance()

        for (i in 27 downTo 0) {
            calendar.timeInMillis = now - TimeUnit.DAYS.toMillis(i.toLong())
            val dateString = calendar.formatDateString()
            val count = dataMap[dateString] ?: 0

            result.add(DailyCompletion(calendar.timeInMillis, count))
        }
        return result
    }

    suspend fun getToneEffectiveness(): List<ToneStats> {
        return analyticsDao.getToneStats()
            .map { raw ->
                ToneStats(
                    toneName = raw.toneUsed,
                    sentCount = raw.totalSent,
                    completedCount = raw.completed,
                    completionRate = if (raw.totalSent > 0) raw.completed.toFloat() / raw.totalSent else 0f
                )
            }
            .sortedByDescending { it.completionRate }
    }

    suspend fun getStreakInfo(): Pair<Int, Int> {
        val progress = getUserProgressOrDefault()
        return Pair(progress.currentStreak, progress.longestStreak)
    }

    suspend fun getUserProgress(): UserProgressEntity = getUserProgressOrDefault()

    suspend fun updateUserProgress(taskCompleted: Boolean = true, pointsEarned: Int = 0) {
        val progress = getUserProgressOrDefault()
        val newPoints = progress.totalPoints + pointsEarned
        val newLevel = PointsCalculator.calculateLevel(newPoints)
        val (newStreak, newLongest) = if (taskCompleted) calculateStreakUpdate(progress)
        else Pair(progress.currentStreak, progress.longestStreak)

        val updated = progress.copy(
            totalPoints = newPoints,
            currentLevel = newLevel,
            currentStreak = newStreak,
            longestStreak = maxOf(newLongest, progress.longestStreak),
            lastCompletionDate = if (taskCompleted) System.currentTimeMillis() else progress.lastCompletionDate,
            totalTasksCompleted = if (taskCompleted) progress.totalTasksCompleted + 1 else progress.totalTasksCompleted
        )

        analyticsDao.updateUserProgress(updated)
        achievementEngine.checkAndUnlockAchievements()
    }

    suspend fun validateStreak() {
        val progress = getUserProgressOrDefault()
        val lastCompletion = progress.lastCompletionDate ?: return
        val now = System.currentTimeMillis()
        val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletion)

        if (daysSinceLastCompletion > 0) {
            val datesWithNotifications = reminderEventDao.getDatesWithNotifications(lastCompletion, now)
            var streakBroken = false

            for (dateString in datesWithNotifications) {
                val dateTimestamp = parseDate(dateString)
                val daysSinceDate = TimeUnit.MILLISECONDS.toDays(now - dateTimestamp)
                if (daysSinceDate == 0L) continue

                if (!reminderEventDao.hasCompletionOnDate(dateString)) {
                    streakBroken = true
                    break
                }
            }

            if (streakBroken) {
                analyticsDao.updateUserProgress(progress.copy(currentStreak = 0))
                Log.d("STREAK_VALIDATION", "Streak broken! Reset to 0")
            }
        }
    }

    suspend fun getAchievements(): List<Achievement> {
        val entities = analyticsDao.getAllAchievements()
        val progress = getUserProgressOrDefault()
        return entities.map { it.toDomain(calculateAchievementProgress(it.id, progress)) }
    }

    suspend fun getUnlockedAchievements(): List<Achievement> {
        return analyticsDao.getUnlockedAchievements().map { it.toDomain(1f) }
    }

    suspend fun unlockAchievement(achievementId: String) {
        analyticsDao.unlockAchievement(achievementId, System.currentTimeMillis())
    }

    suspend fun generateInsights(): List<ProductivityInsight> {
        val insights = mutableListOf<ProductivityInsight>()
        val stats = getUserStatistics()

        stats.bestProductivityHour?.let { hour ->
            insights.add(ProductivityInsight(
                type = InsightType.BEST_TIME,
                message = "You're most productive at ${formatHour(hour)}",
                actionable = true,
                actionText = "Schedule more tasks at this time",
                icon = "⏰"
            ))
        }

        stats.bestTone?.let { tone ->
            getToneEffectiveness().find { it.toneName == tone }?.let {
                insights.add(ProductivityInsight(
                    type = InsightType.BEST_TONE,
                    message = "$tone tone works best for you (${(it.completionRate * 100).toInt()}% completion)",
                    actionable = true,
                    actionText = "Use this tone more often",
                    icon = "💬"
                ))
            }
        }

        if (stats.currentStreak > 0) {
            val nextMilestone = when {
                stats.currentStreak < 3 -> 3
                stats.currentStreak < 7 -> 7
                stats.currentStreak < 30 -> 30
                else -> null
            }
            nextMilestone?.let {
                insights.add(ProductivityInsight(
                    type = InsightType.STREAK_MILESTONE,
                    message = "You're ${it - stats.currentStreak} days away from $it-day streak!",
                    actionable = false,
                    icon = "🏆"
                ))
            }
        }

        val rate = (stats.completionRate * 100).toInt()
        when {
            rate >= 90 -> insights.add(ProductivityInsight(
                type = InsightType.COMPLETION_RATE,
                message = "Excellent! $rate% completion rate",
                actionable = false,
                icon = "✅"
            ))
            rate >= 70 -> insights.add(ProductivityInsight(
                type = InsightType.COMPLETION_RATE,
                message = "Good job! $rate% completion rate",
                actionable = false,
                icon = "✅"
            ))
            rate < 50 -> insights.add(ProductivityInsight(
                type = InsightType.IMPROVEMENT_SUGGESTION,
                message = "Completion rate is $rate%. Try setting fewer tasks or adjusting priorities",
                actionable = true,
                actionText = "Adjust task settings",
                icon = "💡"
            ))
        }

        if (stats.currentStreak >= 3) {
            getUserProgressOrDefault().lastCompletionDate?.let { lastCompletion ->
                val hoursSinceLastCompletion = TimeUnit.MILLISECONDS.toHours(
                    System.currentTimeMillis() - lastCompletion
                )
                if (hoursSinceLastCompletion >= 20) {
                    insights.add(ProductivityInsight(
                        type = InsightType.WARNING,
                        message = "Your ${stats.currentStreak}-day streak is at risk! Complete a task today",
                        actionable = true,
                        actionText = "View tasks",
                        icon = "⚠️"
                    ))
                }
            }
        }

        return insights
    }

    // ========== PRIVATE HELPERS ==========

    private suspend fun getUserProgressOrDefault(): UserProgressEntity {
        return analyticsDao.getUserProgress() ?: UserProgressEntity().also {
            analyticsDao.insertUserProgress(it)
        }
    }

    private suspend fun calculateStreakUpdate(progress: UserProgressEntity): Pair<Int, Int> {
        val lastCompletion = progress.lastCompletionDate ?: return Pair(1, 1)
        val now = System.currentTimeMillis()
        val daysSinceLastCompletion = TimeUnit.MILLISECONDS.toDays(now - lastCompletion)

        if (daysSinceLastCompletion == 0L) {
            return Pair(progress.currentStreak, progress.longestStreak)
        }

        val datesWithNotifications = reminderEventDao.getDatesWithNotifications(lastCompletion, now)
        var missedDays = 0

        for (dateString in datesWithNotifications) {
            if (!reminderEventDao.hasCompletionOnDate(dateString)) {
                missedDays++
                break
            }
        }

        return if (missedDays == 0) {
            val newStreak = progress.currentStreak + 1
            Pair(newStreak, maxOf(newStreak, progress.longestStreak))
        } else {
            Pair(1, progress.longestStreak)
        }
    }

    private fun calculateAchievementProgress(achievementId: String, progress: UserProgressEntity): Float {
        return when (achievementId) {
            "first_step" -> minOf(progress.totalTasksCompleted / 1f, 1f)
            "quick_starter" -> minOf(progress.totalTasksCompleted / 5f, 1f)
            "hot_streak" -> minOf(progress.currentStreak / 3f, 1f)
            "week_warrior" -> minOf(progress.currentStreak / 7f, 1f)
            "monthly_master" -> minOf(progress.currentStreak / 30f, 1f)
            "half_century" -> minOf(progress.totalTasksCompleted / 50f, 1f)
            "centurion" -> minOf(progress.totalTasksCompleted / 100f, 1f)
            else -> 0f
        }
    }

    private fun Calendar.formatDateString(): String {
        val year = get(Calendar.YEAR)
        val month = (get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        return "$year-$month-$day"
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val parts = dateString.split("-")
            Calendar.getInstance().apply {
                set(Calendar.YEAR, parts[0].toInt())
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun formatHour(hour: Int): String = when (hour) {
        0 -> "midnight"
        in 1..11 -> "$hour AM"
        12 -> "noon"
        else -> "${hour - 12} PM"
    }
}
