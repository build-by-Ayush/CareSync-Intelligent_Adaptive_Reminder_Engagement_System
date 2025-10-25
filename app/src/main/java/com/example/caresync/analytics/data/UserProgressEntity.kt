package com.example.caresync.analytics.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 1,  // Always 1 (single user app)
    val totalPoints: Int = 0,
    val currentLevel: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletionDate: Long? = null,
    val totalTasksCompleted: Int = 0
)
