package com.example.caresync.messaging

import android.content.Context
import android.util.Log
import com.example.caresync.domain.Priority
import com.example.caresync.domain.ReminderSettings
import java.util.Calendar

/**
 * Main message generation engine
 * Coordinates between analyzer, templates, and tone selection
 */
class MessageGenerator(private val appContext: Context) {

    private val analyzer = UserStateAnalyzer(appContext)

    companion object {
        private const val TAG = "MessageGenerator"
    }

    /**
     * Generate personalized message for notification
     *
     * @param reminder Task settings (includes user's tone preference)
     * @return Pair of (message, finalTone) - ✅ CHANGED: Now returns tone too
     */
    suspend fun generateMessage(reminder: ReminderSettings): Pair<String, String> {
        return try {
            val context = analyzer.analyzeState(reminder)

            Log.d(TAG, "Analyzed state for '${reminder.title}': ${context.userState}")

            val finalTone = determineFinalTone(
                userPreference = reminder.toneUri,
                userState = context.userState,
                priority = reminder.priority,
                daysPastDue = context.daysPastDue,
                avoidanceStats = context.avoidanceStats
            )

            Log.d(TAG, "Selected tone: $finalTone (user pref: ${reminder.toneUri})")

            val templates = MessageTemplates.getTemplates(finalTone, context.userState)

            val message = if (templates.isEmpty()) {
                Log.w(TAG, "No templates found for $finalTone + ${context.userState}, using fallback")
                "Time for ${reminder.title}! 📋"
            } else {
                val template = templates.random()
                fillPlaceholders(template, context)
            }

            Log.d(TAG, "Generated: '$message'")

            // ✅ RETURN BOTH MESSAGE AND TONE
            Pair(message, finalTone.name)  // ← CHANGED

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate message for ${reminder.title}", e)
            Pair("Time for ${reminder.title}! 📋", "AUTO")  // ← CHANGED
        }
    }

    /**
     * Determine final message tone
     */
    private fun determineFinalTone(
        userPreference: String?,
        userState: UserState,
        priority: Priority,
        daysPastDue: Int,
        avoidanceStats: Map<String, Int> = emptyMap()
    ): MessageTone {

        // ✅ NEW: Priority 0 - Night Time Override (HIGHEST PRIORITY)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour < 6) {  // 12 AM - 6 AM (0:00 - 5:59)
            Log.d(TAG, "🌙 OVERRIDE: Night time (${currentHour}:00) → SLEEP_ENCOURAGEMENT")
            return MessageTone.SLEEP_ENCOURAGEMENT
        }

        // Priority 1: CRITICAL OVERRIDE: Severely overdue (3+ days)
        if (daysPastDue >= 3) {
            Log.d(TAG, "OVERRIDE: Severely overdue ($daysPastDue days) → URGENT tone")
            return MessageTone.URGENT
        }

        // Priority 2: Parse user preference
        val userTone = when (userPreference) {
            "AUTO" -> null
            "ENCOURAGING" -> MessageTone.ENCOURAGING
            "PLAYFUL" -> MessageTone.PLAYFUL
            "GUILT_TRIP" -> MessageTone.GUILT_TRIP
            "AGGRESSIVE" -> MessageTone.AGGRESSIVE
            else -> null
        }

        // If user locked a tone, use it
        if (userTone != null) {
            Log.d(TAG, "Using user-locked tone: $userTone")
            return userTone
        }

        // Priority 3: AUTO mode: System chooses based on context
        return selectAutoTone(userState, priority, daysPastDue, avoidanceStats)
    }

    /**
     * Auto-select best tone for current context
     * ✅ UPDATED: Now handles APPROACHING_DEADLINE state
     */
    private fun selectAutoTone(
        state: UserState,
        priority: Priority,
        daysPastDue: Int,
        avoidanceStats: Map<String, Int> = emptyMap()
    ): MessageTone {

        // Get avoidance count
        val totalAvoidance = avoidanceStats["totalAvoidance"] ?: 0

        return when {
            // ✅ UPDATED: Overdue OR Approaching deadline → Urgent
            daysPastDue > 0 || state == UserState.APPROACHING_DEADLINE -> {
                val reason = if (daysPastDue > 0) "Overdue" else "Approaching deadline"
                Log.d(TAG, "Auto: $reason → URGENT")
                MessageTone.URGENT
            }

            // On streak → Celebrate
            state == UserState.ON_STREAK -> {
                Log.d(TAG, "Auto: On streak → CELEBRATORY")
                MessageTone.CELEBRATORY
            }

            // High priority → Motivating
            priority == Priority.HIGH || priority == Priority.CRITICAL -> {
                Log.d(TAG, "Auto: High priority → MOTIVATING")
                MessageTone.MOTIVATING
            }

            // Escalating harshness for repeated avoidance
            state == UserState.LAGGING -> {
                when {
                    totalAvoidance >= 8 -> {
                        Log.d(TAG, "Auto: Lagging (8+ avoidances) → AGGRESSIVE")
                        MessageTone.AGGRESSIVE
                    }
                    totalAvoidance >= 5 -> {
                        Log.d(TAG, "Auto: Lagging (5-7 avoidances) → GUILT_TRIP")
                        MessageTone.GUILT_TRIP
                    }
                    else -> {
                        Log.d(TAG, "Auto: Lagging (3-4 avoidances) → ENCOURAGING")
                        MessageTone.ENCOURAGING
                    }
                }
            }

            // First time → Playful
            state == UserState.FIRST_TIME -> {
                Log.d(TAG, "Auto: First time → PLAYFUL")
                MessageTone.PLAYFUL
            }

            // Neutral → Random variety
            else -> {
                val neutralSafeTones = listOf(
                    MessageTone.PLAYFUL,
                    MessageTone.ENCOURAGING,
                    MessageTone.MOTIVATING
                )
                val selectedTone = neutralSafeTones.random()
                Log.d(TAG, "Auto: Neutral → Random: $selectedTone")
                selectedTone
            }
        }
    }

    /**
     * Fill template placeholders with actual data
     * ✅ UPDATED: Now supports {daysUntilDue} placeholder
     *
     * Available placeholders:
     * {task} - Task title
     * {streak} - Consecutive completion days
     * {days} - Days since last done / until due / past due (context-dependent)
     * {daysUntilDue} - Days until due date (for approaching deadline messages)
     * {dismissCount} - Recent dismissal count
     */
    private fun fillPlaceholders(template: String, context: MessageContext): String {
        var message = template

        // Smart task name replacement
        val smartTaskName = getSmartTaskName(context.taskTitle)
        message = message.replace("{task}", smartTaskName)

        // {streak} - Streak count
        message = message.replace("{streak}", context.streakDays.toString())

        // {days} - Context-dependent days count
        val daysValue = when {
            context.daysPastDue > 0 -> context.daysPastDue
            context.daysUntilDue > 0 -> context.daysUntilDue
            context.daysSinceLastDone > 0 -> context.daysSinceLastDone
            else -> 0
        }
        message = message.replace("{days}", daysValue.toString())

        // ✅ NEW: {daysUntilDue} - Explicit days until due (for APPROACHING_DEADLINE)
        message = message.replace("{daysUntilDue}", context.daysUntilDue.toString())

        // {dismissCount} - Recent dismissals
        message = message.replace("{dismissCount}", context.dismissalCount.toString())

        return message
    }

    /**
     * Get display-friendly task name
     * - Short tasks (≤3 words): Use full name
     * - Long tasks (>3 words): Use generic reference
     */
    private fun getSmartTaskName(taskTitle: String): String {
        val words = taskTitle.trim().split("\\s+".toRegex())

        return when {
            // Short task (1-3 words) - use as-is
            words.size <= 3 -> taskTitle

            // Long task (4+ words) - use generic reference
            else -> when {
                // Check if it starts with action verbs
                taskTitle.lowercase().startsWith("study") -> "your study session"
                taskTitle.lowercase().startsWith("exercise") -> "your workout"
                taskTitle.lowercase().startsWith("practice") -> "your practice"
                taskTitle.lowercase().startsWith("call") -> "that call"
                taskTitle.lowercase().startsWith("go to") -> "that task"
                taskTitle.lowercase().startsWith("buy") -> "your shopping"
                taskTitle.lowercase().startsWith("read") -> "your reading"
                taskTitle.lowercase().startsWith("write") -> "your writing"

                // Generic fallback
                else -> "that task"
            }
        }
    }
}
