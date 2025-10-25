package com.example.caresync.analytics.domain

enum class InsightType {
    BEST_TIME,              // "You're most productive at 2 PM"
    BEST_TONE,              // "PLAYFUL tone works best (87%)"
    BEST_DAY,               // "You're most productive on Tuesdays"
    PRODUCTIVITY_PATTERN,   // "You complete Exercise tasks faster at 7 AM"
    IMPROVEMENT_SUGGESTION, // "You've dismissed Study 8 times. Try increasing priority?"
    STREAK_MILESTONE,       // "You're 3 days away from Week Warrior badge!"
    COMPLETION_RATE,        // "Great job! 85% completion rate this week"
    WARNING                 // "Your streak is at risk! Complete a task today"
}

data class ProductivityInsight(
    val type: InsightType,
    val message: String,
    val actionable: Boolean = false,  // Can user take action on this?
    val actionText: String? = null,   // Text for action button (e.g., "Adjust Schedule")
    val icon: String = "💡"           // Emoji icon for the insight
)

// ✅ Helper function to determine icon based on type
fun ProductivityInsight.getIconForType(): String {
    return when (type) {
        InsightType.BEST_TIME -> "⏰"
        InsightType.BEST_TONE -> "💬"
        InsightType.BEST_DAY -> "📅"
        InsightType.PRODUCTIVITY_PATTERN -> "📊"
        InsightType.IMPROVEMENT_SUGGESTION -> "💡"
        InsightType.STREAK_MILESTONE -> "🏆"
        InsightType.COMPLETION_RATE -> "✅"
        InsightType.WARNING -> "⚠️"
    }
}

// ✅ Helper function to determine color based on type
fun ProductivityInsight.getColorForType(): Long {
    return when (type) {
        InsightType.BEST_TIME,
        InsightType.BEST_TONE,
        InsightType.BEST_DAY,
        InsightType.COMPLETION_RATE -> 0xFF4CAF50  // Success Green

        InsightType.PRODUCTIVITY_PATTERN -> 0xFF2196F3  // Info Blue

        InsightType.IMPROVEMENT_SUGGESTION -> 0xFFFF9800  // Warning Orange

        InsightType.STREAK_MILESTONE -> 0xFF9C27B0  // Light Purple

        InsightType.WARNING -> 0xFFF44336  // Error Red
    }
}
