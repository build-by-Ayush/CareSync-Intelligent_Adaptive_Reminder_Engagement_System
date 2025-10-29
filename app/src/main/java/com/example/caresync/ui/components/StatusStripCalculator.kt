package com.example.caresync.ui.components

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Calculates status strip color based on multi-factor analysis
 *
 * Color Logic:
 * - RED: Completion rate <30% in last 7 days
 * - ORANGE: Missed last 2-3 notifications
 * - YELLOW: Due within next 24 hours
 * - GREEN: Completion rate >70%
 * - BLUE: Created within last 3 days
 * - GRAY: Task disabled
 */
object StatusStripCalculator {

    // Color definitions (matching your requirements)
    val StatusRed = Color(0xFFF44336)      // Completion <30%
    val StatusOrange = Color(0xFFFF9800)   // Missed 2-3 last
    val StatusYellow = Color(0xFFFFC107)   // Due within 24h
    val StatusGreen = Color(0xFF4CAF50)    // Completion >70%
    val StatusBlue = Color(0xFF2196F3)     // New (< 3 days)
    val StatusGray = Color(0xFF616161)     // Disabled

    /**
     * Main function to calculate strip color
     */
    suspend fun calculateStripColor(
        reminder: ReminderSettings,
        context: Context
    ): Color = withContext(Dispatchers.IO) {

        // 1. GRAY - Disabled
        if (!reminder.enabled) return@withContext StatusGray

        // Get database instance (use 'get' not 'getDatabase')
        val db = AppDatabase.get(context)
        val eventDao = db.reminderEventDao()

        // Get events from last 7 days
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val now = System.currentTimeMillis()
        val events = eventDao.getEventsBetween(reminder.id, sevenDaysAgo, now)

        // 2. BLUE - New task (< 3 days old)
        val threeDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3)
        if (reminder.createdAt > threeDaysAgo) {
            return@withContext StatusBlue
        }

        // If no events yet, treat as new
        if (events.isEmpty()) return@withContext StatusBlue

        // Calculate completion rate
        val triggeredEvents = events.filter { event -> event.eventType == "TRIGGERED" }
        val completedEvents = events.filter { event -> event.eventType == "COMPLETED" }

        val completionRate = if (triggeredEvents.isNotEmpty()) {
            completedEvents.size.toFloat() / triggeredEvents.size.toFloat()
        } else 0f

        // 3. RED - Very poor completion rate (<30%)
        if (completionRate < 0.3f && triggeredEvents.size >= 5) {
            return@withContext StatusRed
        }

        // 4. ORANGE - Missed last 2-3 notifications
        val recentEvents = events
            .filter { event ->
                event.eventType == "COMPLETED" ||
                        event.eventType == "DISMISSED" ||
                        event.eventType == "IGNORED"
            }
            .sortedByDescending { event -> event.timestamp }
            .take(3)

        val missedCount = recentEvents.count { event ->
            event.eventType == "DISMISSED" || event.eventType == "IGNORED"
        }

        if (missedCount >= 2 && recentEvents.size >= 2) {
            return@withContext StatusOrange
        }

        // 5. YELLOW - Due within 24 hours
        reminder.dueDate?.let { dueDate ->
            val timeUntilDue = dueDate - now
            val twentyFourHours = TimeUnit.HOURS.toMillis(24)

            if (timeUntilDue in 1..twentyFourHours) {
                return@withContext StatusYellow
            }
        }

        // 6. GREEN - Good completion rate (>70%)
        if (completionRate >= 0.7f && triggeredEvents.size >= 5) {
            return@withContext StatusGreen
        }

        // Default: Moderate performance or not enough data
        return@withContext StatusYellow
    }
}
