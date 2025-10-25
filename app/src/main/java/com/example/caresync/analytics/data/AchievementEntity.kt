package com.example.caresync.analytics.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val icon: String,  // Emoji like "🏆", "🔥", "⭐"
    val pointsRequired: Int,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)
