package com.example.caresync.analytics.gamification

import com.example.caresync.analytics.data.AchievementEntity
import com.example.caresync.analytics.data.AnalyticsDao
import com.example.caresync.analytics.domain.Achievement
import com.example.caresync.analytics.domain.toDomain
import com.example.caresync.data.ReminderEventDao
import java.util.Calendar

class AchievementEngine(
    private val analyticsDao: AnalyticsDao,
    private val reminderEventDao: ReminderEventDao
) {

    /**
     * All possible achievements in the app
     */
    fun getAllAchievementDefinitions(): List<AchievementEntity> {
        return listOf(
            // Starter achievements
            AchievementEntity(
                id = "first_step",
                name = "First Step",
                description = "Complete your first task",
                icon = "🏆",
                pointsRequired = 0,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "quick_starter",
                name = "Quick Starter",
                description = "Complete 5 tasks",
                icon = "⚡",
                pointsRequired = 50,
                isUnlocked = false,
                unlockedAt = null
            ),

            // Streak achievements
            AchievementEntity(
                id = "hot_streak",
                name = "Hot Streak",
                description = "Maintain a 3-day streak",
                icon = "🔥",
                pointsRequired = 30,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "week_warrior",
                name = "Week Warrior",
                description = "Maintain a 7-day streak",
                icon = "⭐",
                pointsRequired = 70,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "monthly_master",
                name = "Monthly Master",
                description = "Maintain a 30-day streak",
                icon = "💎",
                pointsRequired = 300,
                isUnlocked = false,
                unlockedAt = null
            ),

            // Milestone achievements
            AchievementEntity(
                id = "half_century",
                name = "Half Century",
                description = "Complete 50 tasks",
                icon = "🎯",
                pointsRequired = 500,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "centurion",
                name = "Centurion",
                description = "Complete 100 tasks",
                icon = "💯",
                pointsRequired = 1000,
                isUnlocked = false,
                unlockedAt = null
            ),

            // Time-based achievements
            AchievementEntity(
                id = "early_bird",
                name = "Early Bird",
                description = "Complete 10 tasks before 9 AM",
                icon = "🌅",
                pointsRequired = 150,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "night_owl",
                name = "Night Owl",
                description = "Complete 10 tasks after 9 PM",
                icon = "🦉",
                pointsRequired = 150,
                isUnlocked = false,
                unlockedAt = null
            ),

            // Performance achievements
            AchievementEntity(
                id = "perfect_week",
                name = "Perfect Week",
                description = "100% completion rate for 7 days",
                icon = "✨",
                pointsRequired = 200,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "comeback_kid",
                name = "Comeback Kid",
                description = "Complete an overdue task",
                icon = "💪",
                pointsRequired = 20,
                isUnlocked = false,
                unlockedAt = null
            ),
            AchievementEntity(
                id = "productivity_master",
                name = "Productivity Master",
                description = "Achieve 90%+ completion rate",
                icon = "🚀",
                pointsRequired = 500,
                isUnlocked = false,
                unlockedAt = null
            )
        )
    }

    /**
     * Seed achievements into database on first launch
     */
    suspend fun seedAchievements() {
        val existing = analyticsDao.getAllAchievements()
        if (existing.isEmpty()) {
            analyticsDao.insertAchievements(getAllAchievementDefinitions())
        }
    }

    /**
     * Check and unlock any newly earned achievements
     * @return List of newly unlocked achievements
     */
    suspend fun checkAndUnlockAchievements(): List<Achievement> {
        val newlyUnlocked = mutableListOf<Achievement>()

        // Get all achievements
        val allAchievements = analyticsDao.getAllAchievements()

        // Check each locked achievement
        for (achievement in allAchievements.filter { !it.isUnlocked }) {
            if (checkAchievementCondition(achievement.id)) {
                // Unlock it!
                analyticsDao.unlockAchievement(achievement.id, System.currentTimeMillis())
                newlyUnlocked.add(achievement.copy(
                    isUnlocked = true,
                    unlockedAt = System.currentTimeMillis()
                ).toDomain())
            }
        }

        return newlyUnlocked
    }

    /**
     * Check if specific achievement condition is met
     */
    private suspend fun checkAchievementCondition(achievementId: String): Boolean {
        return when (achievementId) {
            "first_step" -> checkFirstStep()
            "quick_starter" -> checkQuickStarter()
            "hot_streak" -> checkHotStreak()
            "week_warrior" -> checkWeekWarrior()
            "monthly_master" -> checkMonthlyMaster()
            "half_century" -> checkHalfCentury()
            "centurion" -> checkCenturion()
            "early_bird" -> checkEarlyBird()
            "night_owl" -> checkNightOwl()
            "perfect_week" -> checkPerfectWeek()
            "comeback_kid" -> checkComebackKid()
            "productivity_master" -> checkProductivityMaster()
            else -> false
        }
    }

    // Achievement check methods

    private suspend fun checkFirstStep(): Boolean {
        val count = analyticsDao.getTotalCompletionsCount()
        return count >= 1
    }

    private suspend fun checkQuickStarter(): Boolean {
        val count = analyticsDao.getTotalCompletionsCount()
        return count >= 5
    }

    private suspend fun checkHotStreak(): Boolean {
        val progress = analyticsDao.getUserProgress()
        return progress?.currentStreak ?: 0 >= 3
    }

    private suspend fun checkWeekWarrior(): Boolean {
        val progress = analyticsDao.getUserProgress()
        return progress?.currentStreak ?: 0 >= 7
    }

    private suspend fun checkMonthlyMaster(): Boolean {
        val progress = analyticsDao.getUserProgress()
        return progress?.currentStreak ?: 0 >= 30
    }

    private suspend fun checkHalfCentury(): Boolean {
        val count = analyticsDao.getTotalCompletionsCount()
        return count >= 50
    }

    private suspend fun checkCenturion(): Boolean {
        val count = analyticsDao.getTotalCompletionsCount()
        return count >= 100
    }

    private suspend fun checkEarlyBird(): Boolean {
        // Count completions between 0:00 and 9:00 (hours 0-8)
        val events = reminderEventDao.getEventsByTypeAndTimeRange(
            eventType = "COMPLETED",
            startHour = 0,
            endHour = 8
        )
        return events.size >= 10
    }

    private suspend fun checkNightOwl(): Boolean {
        // Count completions between 21:00 and 23:59 (hours 21-23)
        val events = reminderEventDao.getEventsByTypeAndTimeRange(
            eventType = "COMPLETED",
            startHour = 21,
            endHour = 23
        )
        return events.size >= 10
    }

    private suspend fun checkPerfectWeek(): Boolean {
        // Check if completion rate is 100% for last 7 days
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val completions = analyticsDao.getDailyCompletions(sevenDaysAgo)

        // Need 7 consecutive days with completions
        if (completions.size < 7) return false

        // Check if all days have completions (perfect attendance)
        // This is simplified - you might want more complex logic
        return completions.all { it.count > 0 }
    }

    private suspend fun checkComebackKid(): Boolean {
        // Simplified: Check if there's a COMPLETED event after DISMISSED events
        // This shows user came back after dismissing
        val events = reminderEventDao.getAllEvents()

        // Group events by reminder ID
        val eventsByReminder = events.groupBy { it.reminderId }

        // Check if any reminder had DISMISSED followed by COMPLETED
        return eventsByReminder.any { (_, reminderEvents) ->
            val sorted = reminderEvents.sortedBy { it.timestamp }
            var hadDismissal = false
            for (event in sorted) {
                if (event.eventType == "DISMISSED") {
                    hadDismissal = true
                }
                if (event.eventType == "COMPLETED" && hadDismissal) {
                    return@any true  // Found comeback pattern!
                }
            }
            false
        }
    }

    private suspend fun checkProductivityMaster(): Boolean {
        // ✅ Get total notifications sent (excluding boost)
        val totalTriggered = reminderEventDao.countByTypeExcludingBoost("TRIGGERED")

        // ✅ Get total completions
        val totalCompleted = reminderEventDao.countByType("COMPLETED")

        // ✅ Require minimum 10 notifications
        if (totalTriggered < 10) return false

        // ✅ Calculate completion rate
        val completionRate = totalCompleted.toFloat() / totalTriggered

        // ✅ Require 90%+ completion rate
        return completionRate >= 0.90f
    }

}
