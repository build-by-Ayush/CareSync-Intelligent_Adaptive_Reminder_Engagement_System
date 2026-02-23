package com.example.caresync.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Status Strip Color Calculator - Simple, One Color One Reason
 *
 * 🔴 RED:    Completion rate < 30% in last 7 days AND ≥5 triggered notifications  // ✅ CHANGED
 * 🟠 ORANGE: Missed 2-3 consecutive notifications
 * 🟡 YELLOW: Due within 24 hours
 * 🟢 GREEN:  Completion rate >= 70% (requires ≥5 triggered notifications)
 * 🔵 BLUE:   Created < 3 days ago OR less than 5 notifications
 * ⚫ GRAY:    Task disabled
 */
object StatusStripCalculator {

    val StatusRed = Color(0xFFF44336)      // Completion <30% AND ≥5 notifications  // ✅ CHANGED
    val StatusOrange = Color(0xFFFF9800)   // Missed 2-3 notifications
    val StatusYellow = Color(0xFFFFC107)   // Due within 24h
    val StatusGreen = Color(0xFF4CAF50)    // Completion ≥70% (≥5 events)
    val StatusBlue = Color(0xFF2196F3)     // New (<3 days) OR insufficient data (<5 events)
    val StatusGray = Color(0xFF616161)     // Disabled

    /**
     * Main function - returns ONE color based on ONE reason
     */
    suspend fun calculateStripColor(
        reminder: ReminderSettings,
        context: Context
    ): Color = withContext(Dispatchers.IO) {

        // GRAY: Task is disabled
        if (!reminder.enabled) {
            return@withContext StatusGray
        }

        val db = AppDatabase.get(context)
        val eventDao = db.reminderEventDao()
        val now = System.currentTimeMillis()

        // Get events from last 7 days
        val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)
        val events = eventDao.getEventsBetween(reminder.id, sevenDaysAgo, now)

        // ==========================================
        // RED: Completion < 30% in last 7 days AND ≥5 triggered notifications
        // ==========================================
        if (events.isNotEmpty()) {
            val triggeredCount = events.count { it.eventType == "TRIGGERED" }
            val completedCount = events.count { it.eventType == "COMPLETED" }

            // ✅ CHANGED: Only show RED if BOTH conditions are true:
            // 1. At least 5 notifications have been triggered
            // 2. Completion rate is less than 30%
            if (triggeredCount >= 5 && triggeredCount > 0) {  // ✅ ADDED: >= 5 check
                val completionRate = completedCount.toFloat() / triggeredCount.toFloat()
                if (completionRate < 0.3f) {
                    return@withContext StatusRed
                }
            }
        }

        // ==========================================
        // ORANGE: Missed 2-3 consecutive notifications
        // ==========================================
        if (events.isNotEmpty()) {
            val recentEvents = events
                .sortedByDescending { it.timestamp }
                .take(3)

            val missedCount = recentEvents.count {
                it.eventType == "DISMISSED" || it.eventType == "IGNORED"
            }

            if (missedCount >= 2) {
                return@withContext StatusOrange
            }
        }

        // ==========================================
        // YELLOW: Due within 24 hours
        // ==========================================
        reminder.dueDate?.let { dueDate ->
            val timeUntilDue = dueDate - now
            val twentyFourHours = TimeUnit.HOURS.toMillis(24)

            if (timeUntilDue in 1..twentyFourHours) {
                return@withContext StatusYellow
            }
        }

        // ==========================================
        // GREEN: Completion >= 70% (requires ≥5 triggered events)
        // ==========================================
        if (events.isNotEmpty()) {
            val triggeredCount = events.count { it.eventType == "TRIGGERED" }
            val completedCount = events.count { it.eventType == "COMPLETED" }

            if (triggeredCount >= 5 && completedCount > 0) {
                val completionRate = completedCount.toFloat() / triggeredCount.toFloat()
                if (completionRate >= 0.7f) {
                    return@withContext StatusGreen
                }
            }
        }

        // ==========================================
        // BLUE: Created < 3 days ago OR < 5 notifications
        // ==========================================
        val threeDaysAgo = now - TimeUnit.DAYS.toMillis(3)
        if (reminder.createdAt > threeDaysAgo) {
            return@withContext StatusBlue
        }

        if (events.size < 5) {
            return@withContext StatusBlue
        }

        // ==========================================
        // DEFAULT: YELLOW (shouldn't reach here)
        // ==========================================
        return@withContext StatusYellow
    }
}
