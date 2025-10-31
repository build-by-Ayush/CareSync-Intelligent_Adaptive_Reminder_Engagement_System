package com.example.caresync.intelligence.riskdetection

import android.util.Log
import com.example.caresync.domain.Priority
import java.util.Locale  // ✅ ADD THIS

/**
 * Analyzes at-risk tasks and generates actionable suggestions
 */
object RiskAnalyzer {

    /**
     * Generate suggestions for an at-risk task
     */
    fun generateSuggestions(task: TaskAtRiskData): RiskSuggestion {
        val riskLevel = calculateRiskLevel(task.completionRate)
        val suggestions = mutableListOf<String>()
        var mainAction = ""

        // Analysis 1: Time pattern (worst hour)
        if (task.worstHour > 0) {
            val hour = task.worstHour
            when {
                hour in 20..23 || hour in 0..5 -> {
                    suggestions.add("📍 Worst time: ${formatHour(hour)} (only ${(task.worstHourCompletion * 100).toInt()}% completion)")
                    suggestions.add("Try scheduling in morning (6-11 AM) when you're more productive")
                    mainAction = "⏰ Shift to morning (6 AM)"
                }
                hour in 13..17 -> {
                    suggestions.add("📍 Afternoon dip detected: ${formatHour(hour)}")
                    suggestions.add("Try early morning (6-9 AM) or evening (6-8 PM) instead")
                    mainAction = "⏰ Try different time slot"
                }
            }
        }

        // Analysis 2: Best hours suggestion
        if (task.bestHours.isNotEmpty()) {
            val bestHoursStr = task.bestHours  // ✅ SIMPLIFIED chain
                .take(2)
                .joinToString(", ") { "${formatHour(it.hourOfDay)} (${(it.completionRate * 100).toInt()}%)" }
            suggestions.add("✅ Best times: $bestHoursStr")
        }

        // Analysis 3: Priority check
        if (task.completionRate < 0.2f && task.currentPriority == Priority.LOW) {
            suggestions.add("⬆️ Very low completion (${(task.completionRate * 100).toInt()}%) + LOW priority")
            suggestions.add("Try increasing to HIGH priority for better visibility")
            if (mainAction.isEmpty()) mainAction = "⬆️ Increase to HIGH priority"
        }

        // Analysis 4: Frequency check
        if (task.frequencyMultiplier > 1.5f && task.completionRate < 0.5f && riskLevel != RiskLevel.CRITICAL) {
            suggestions.add("🔔 Frequency: ${String.format(Locale.US, "%.1f", task.frequencyMultiplier)}x normal")  // ✅ FIXED
            suggestions.add("Too many notifications may be overwhelming - try reducing")
            if (mainAction.isEmpty()) mainAction = "📉 Reduce notification frequency"
        } else if (task.frequencyMultiplier < 0.8f && riskLevel == RiskLevel.CRITICAL) {
            suggestions.add("🔔 Frequency: ${String.format(Locale.US, "%.1f", task.frequencyMultiplier)}x normal")  // ✅ FIXED
            suggestions.add("Very few reminders - try increasing frequency for more cues")
            if (mainAction.isEmpty()) mainAction = "📈 Increase frequency"
        }

        // Analysis 5: Risk level warning
        suggestions.add(
            when (riskLevel) {
                RiskLevel.CRITICAL -> "🚨 CRITICAL: ${(task.completionRate * 100).toInt()}% completion - immediate action needed"
                RiskLevel.HIGH -> "⚠️ HIGH RISK: ${(task.completionRate * 100).toInt()}% completion"
                RiskLevel.MEDIUM -> "ℹ️ Tasks under 40% completion"
            }
        )

        return RiskSuggestion(
            reminderId = task.reminderId,
            taskTitle = task.taskTitle,
            completionRate = task.completionRate,
            completionPercentage = task.completionPercentage,
            confidence = task.confidence,
            riskLevel = riskLevel,
            suggestions = suggestions,
            mainAction = mainAction.takeIf { it.isNotEmpty() } ?: "Review settings",
            dataPoints = task.dataPoints
        )
    }

    /**
     * Calculate risk level based on completion rate
     */
    private fun calculateRiskLevel(completionRate: Float): RiskLevel {
        return when {
            completionRate < 0.2f -> RiskLevel.CRITICAL
            completionRate < 0.35f -> RiskLevel.HIGH
            else -> RiskLevel.MEDIUM
        }
    }

    /**
     * Format hour to readable time string
     */
    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }
    }
}

// ========== DATA CLASSES ==========

enum class RiskLevel {
    CRITICAL,  // < 20%
    HIGH,      // 20-35%
    MEDIUM     // 35-40%
}

data class RiskSuggestion(
    val reminderId: Long,
    val taskTitle: String,
    val completionRate: Float,
    val completionPercentage: Int,
    val confidence: Float,
    val riskLevel: RiskLevel,
    val suggestions: List<String>,
    val mainAction: String,
    val dataPoints: Int
)
