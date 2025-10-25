package com.example.caresync.analytics.domain

// For completion rate line chart
data class CompletionRateData(
    val date: Long,  // Timestamp
    val completionRate: Float  // 0.0 to 1.0
)

// For productivity hours bar chart
data class HourlyProductivity(
    val hour: Int,  // 0-23
    val completionCount: Int
)

// For weekly heatmap
data class DailyCompletion(
    val date: Long,  // Timestamp
    val completionCount: Int
)

// For tone performance table
data class ToneStats(
    val toneName: String,  // "ENCOURAGING", "PLAYFUL", etc.
    val sentCount: Int,
    val completedCount: Int,
    val completionRate: Float  // 0.0 to 1.0
)

// For level progress
data class LevelInfo(
    val currentLevel: Int,
    val currentPoints: Int,
    val nextLevelPoints: Int,
    val progress: Float  // 0.0 to 1.0
) {
    val levelName: String
        get() = when (currentLevel) {
            1 -> "Beginner"
            2 -> "Consistent"
            3 -> "Productive"
            4 -> "Champion"
            5 -> "Master"
            else -> "Unknown"
        }
}
