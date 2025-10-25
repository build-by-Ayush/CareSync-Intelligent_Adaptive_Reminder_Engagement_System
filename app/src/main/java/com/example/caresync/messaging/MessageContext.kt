package com.example.caresync.messaging

/**
 * User behavior states for message personalization
 */
enum class UserState {
    ON_STREAK,    // 3+ consecutive completions
    LAGGING,      // 3+ consecutive dismissals/ignores
    OVERDUE,      // Past due date
    FIRST_TIME,   // No history yet
    NEUTRAL,       // Mixed behavior
    APPROACHING_DEADLINE // ✅ NEW STATE
}

/**
 * Message tone/personality options
 */
enum class MessageTone {
    AUTO,          // System decides
    ENCOURAGING,   // Always supportive
    PLAYFUL,       // Always fun
    GUILT_TRIP,    // Pressure-based
    AGGRESSIVE,    // Tough love
    CELEBRATORY,   // System-only: celebrations
    URGENT,        // System-only: deadlines
    MOTIVATING,     // System-only: high priority
    SLEEP_ENCOURAGEMENT  // ✅ NEW: System-only: night-time sleep messages
}

/**
 * Context data for message generation
 */
data class MessageContext(
    val taskTitle: String,
    val userState: UserState,
    val messageTone: MessageTone,

    // Stats for placeholders
    val streakDays: Int = 0,
    val daysSinceLastDone: Int = 0,
    val daysUntilDue: Int = 0,
    val daysPastDue: Int = 0,
    val dismissalCount: Int = 0,
    val priority: String = "NORMAL",

    // ✅ NEW: Behavior stats for tone escalation
    val avoidanceStats: Map<String, Int> = emptyMap()
)
