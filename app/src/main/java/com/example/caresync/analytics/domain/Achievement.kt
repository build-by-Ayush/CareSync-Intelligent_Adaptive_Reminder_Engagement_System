package com.example.caresync.analytics.domain

import com.example.caresync.analytics.data.AchievementEntity

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,  // Emoji like "🏆", "🔥", "⭐"
    val pointsRequired: Int,
    val isUnlocked: Boolean,
    val unlockedAt: Long?,
    val progress: Float = 0f  // 0.0 to 1.0 for showing progress on locked badges
)

// ✅ Helper extension function to convert Entity → Domain
fun AchievementEntity.toDomain(progress: Float = 0f): Achievement {
    return Achievement(
        id = id,
        name = name,
        description = description,
        icon = icon,
        pointsRequired = pointsRequired,
        isUnlocked = isUnlocked,
        unlockedAt = unlockedAt,
        progress = progress
    )
}

// ✅ Helper extension function to convert Domain → Entity
fun Achievement.toEntity(): AchievementEntity {
    return AchievementEntity(
        id = id,
        name = name,
        description = description,
        icon = icon,
        pointsRequired = pointsRequired,
        isUnlocked = isUnlocked,
        unlockedAt = unlockedAt
    )
}
