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
     * @return Pair of (message, finalTone)
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

            // ✅ NEW: Determine which placeholders have valid data
            val availablePlaceholders = determineAvailablePlaceholders(context)

            // ✅ MODIFIED: Pass filter to getTemplates
            val templates = MessageTemplates.getTemplates(
                tone = finalTone,
                state = context.userState,
                availablePlaceholders = availablePlaceholders
            )

            val message = if (templates.isEmpty()) {
                Log.w(TAG, "No compatible templates found, using generic fallback")
                "Time for ${reminder.title}! 📋"
            } else {
                val template = templates.random()
                fillPlaceholders(template, context)
            }

            Log.d(TAG, "Generated: '$message'")

            Pair(message, finalTone.name)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate message for ${reminder.title}", e)
            Pair("Time for ${reminder.title}! 📋", "AUTO")
        }
    }

    /**
     * ✅ NEW: Determine which placeholders have meaningful data
     * Returns set of placeholder names that should be allowed
     */
    private fun determineAvailablePlaceholders(context: MessageContext): Set<String> {
        val available = mutableSetOf<String>()

        // {task} - Always available
        available.add("task")

        // {streak} - Available if > 0
        if (context.streakDays > 0) {
            available.add("streak")
        }

        // {days} - Available if any days value > 0
        if (context.daysPastDue > 0 ||
            context.daysUntilDue > 0 ||
            context.daysSinceLastDone > 0) {
            available.add("days")
        }

        // {daysUntilDue} - Available if > 0
        if (context.daysUntilDue > 0) {
            available.add("daysUntilDue")
        }

        // {dismissCount} - Available if > 0
        if (context.dismissalCount > 0) {
            available.add("dismissCount")
        }

        Log.d(TAG, "Available placeholders: $available")
        return available
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

        // Priority 0: Night Time Override
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour < 6) {
            Log.d(TAG, "🌙 OVERRIDE: Night time (${currentHour}:00) → SLEEP_ENCOURAGEMENT")
            return MessageTone.SLEEP_ENCOURAGEMENT
        }

        // Priority 1: Severely overdue
        if (daysPastDue >= 3) {
            Log.d(TAG, "OVERRIDE: Severely overdue ($daysPastDue days) → URGENT tone")
            return MessageTone.URGENT
        }

        // Priority 2: User preference
        val userTone = when (userPreference) {
            "AUTO" -> null
            "ENCOURAGING" -> MessageTone.ENCOURAGING
            "PLAYFUL" -> MessageTone.PLAYFUL
            "GUILT_TRIP" -> MessageTone.GUILT_TRIP
            "AGGRESSIVE" -> MessageTone.AGGRESSIVE
            else -> null
        }

        if (userTone != null) {
            Log.d(TAG, "Using user-locked tone: $userTone")
            return userTone
        }

        // Priority 3: AUTO mode
        return selectAutoTone(userState, priority, daysPastDue, avoidanceStats)
    }

    /**
     * Auto-select best tone for current context
     */
    private fun selectAutoTone(
        state: UserState,
        priority: Priority,
        daysPastDue: Int,
        avoidanceStats: Map<String, Int> = emptyMap()
    ): MessageTone {

        val totalAvoidance = avoidanceStats["totalAvoidance"] ?: 0

        return when {
            daysPastDue > 0 || state == UserState.APPROACHING_DEADLINE -> {
                val reason = if (daysPastDue > 0) "Overdue" else "Approaching deadline"
                Log.d(TAG, "Auto: $reason → URGENT")
                MessageTone.URGENT
            }

            state == UserState.ON_STREAK -> {
                Log.d(TAG, "Auto: On streak → CELEBRATORY")
                MessageTone.CELEBRATORY
            }

            priority == Priority.HIGH || priority == Priority.CRITICAL -> {
                Log.d(TAG, "Auto: High priority → MOTIVATING")
                MessageTone.MOTIVATING
            }

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

            state == UserState.FIRST_TIME -> {
                Log.d(TAG, "Auto: First time → PLAYFUL")
                MessageTone.PLAYFUL
            }

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
     */
    private fun fillPlaceholders(template: String, context: MessageContext): String {
        var message = template

        val smartTaskName = getSmartTaskName(context.taskTitle)
        message = message.replace("{task}", smartTaskName)
        message = message.replace("{streak}", context.streakDays.toString())

        val daysValue = when {
            context.daysPastDue > 0 -> context.daysPastDue
            context.daysUntilDue > 0 -> context.daysUntilDue
            context.daysSinceLastDone > 0 -> context.daysSinceLastDone
            else -> 0
        }

        message = message.replace("{days}", daysValue.toString())
        message = message.replace("{daysUntilDue}", context.daysUntilDue.toString())
        message = message.replace("{dismissCount}", context.dismissalCount.toString())

        return message
    }

    /**
     * Get display-friendly task name
     */
    private fun getSmartTaskName(taskTitle: String): String {
        val words = taskTitle.trim().split("\\s+".toRegex())

        return when {
            words.size <= 3 -> taskTitle
            else -> when {
                taskTitle.lowercase().startsWith("study") -> "your study session"
                taskTitle.lowercase().startsWith("exercise") -> "your workout"
                taskTitle.lowercase().startsWith("practice") -> "your practice"
                taskTitle.lowercase().startsWith("call") -> "that call"
                taskTitle.lowercase().startsWith("go to") -> "that task"
                taskTitle.lowercase().startsWith("buy") -> "your shopping"
                taskTitle.lowercase().startsWith("read") -> "your reading"
                taskTitle.lowercase().startsWith("write") -> "your writing"
                else -> "that task"
            }
        }
    }
}
