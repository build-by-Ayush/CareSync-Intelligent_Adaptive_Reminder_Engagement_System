package com.example.caresync.analytics.domain

data class UserStatistics(
    val totalTasksCompleted: Int,
    val completionRate: Float,  // 0.0 to 1.0 (will display as %)
    val currentStreak: Int,
    val longestStreak: Int,
    val averageResponseTimeMinutes: Int,
    val totalPoints: Int,
    val currentLevel: Int,
    val nextLevelPoints: Int,  // Points needed to reach next level
    val bestProductivityHour: Int?,  // 0-23, null if no data yet
    val bestTone: String?,  // Most effective tone, null if no data
    val tasksCompletedThisWeek: Int,
    val tasksCompletedThisMonth: Int,
    val totalTasksCreated: Int
)
