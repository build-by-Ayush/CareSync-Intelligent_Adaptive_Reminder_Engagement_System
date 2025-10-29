package com.example.caresync.accountability

import com.example.caresync.domain.ReminderSettings
import com.example.caresync.data.ReminderEventEntity
import java.text.SimpleDateFormat
import java.util.*

class AccountabilityReportGenerator {

    fun generateDailyReport(
        task: ReminderSettings,
        todayEvents: List<ReminderEventEntity>
    ): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val today = dateFormat.format(Date())

        val completed = todayEvents.any { it.eventType == "COMPLETED" }
        val missed = todayEvents.any { it.eventType == "MISSED" || it.eventType == "DISMISSED" }

        return buildString {
            appendLine("📊 ${task.title} - Daily Report")
            appendLine(today)
            appendLine()

            if (completed) {
                appendLine("✅ Completed today!")
                todayEvents.find { it.eventType == "COMPLETED" }?.let { event ->
                    val time = SimpleDateFormat("h:mm a", Locale.getDefault())
                        .format(Date(event.timestamp))
                    appendLine("Time: $time")
                }
            } else if (missed) {
                appendLine("⚠️ Task was missed today")
            } else {
                appendLine("⏳ Pending")
            }

            appendLine()
            appendLine("—CareSync")
        }
    }

    fun generateWeeklyReport(
        task: ReminderSettings,
        weekEvents: List<ReminderEventEntity>
    ): String {
        val completedDays = weekEvents.count { it.eventType == "COMPLETED" }
        val totalDays = 7
        val percentage = (completedDays * 100) / totalDays

        return buildString {
            appendLine("📊 ${task.title} - Weekly Report")
            appendLine("${getWeekRange()}")
            appendLine()
            appendLine("✅ Completed: $completedDays/$totalDays days ($percentage%)")
            appendLine()
            appendLine("📅 This week:")

            // Group by day
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -6)

            repeat(7) { index ->
                val dayEvents = weekEvents.filter { event ->
                    val eventCal = Calendar.getInstance().apply { timeInMillis = event.timestamp }
                    eventCal.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
                }

                val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.time)
                val status = when {
                    dayEvents.any { it.eventType == "COMPLETED" } -> "✓"
                    dayEvents.any { it.eventType == "MISSED" } -> "✗"
                    else -> "—"
                }

                appendLine("$dayName $status")
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            appendLine()
            if (percentage >= 80) {
                appendLine("🎉 Excellent work!")
            } else if (percentage >= 50) {
                appendLine("💪 Keep going!")
            } else {
                appendLine("⚠️ Needs improvement")
            }

            appendLine()
            appendLine("—CareSync")
        }
    }

    fun generateStrugglingAlert(
        task: ReminderSettings,
        recentEvents: List<ReminderEventEntity>
    ): String {
        val missedCount = recentEvents.count { it.eventType == "MISSED" || it.eventType == "DISMISSED" }

        return buildString {
            appendLine("⚠️ Alert: ${task.title}")
            appendLine()
            appendLine("${task.title} hasn't been completed for $missedCount days")
            appendLine()
            appendLine("📉 Recent pattern: Missed $missedCount times")
            appendLine()
            appendLine("💬 A quick check-in might help!")
            appendLine()
            appendLine("—CareSync")
        }
    }

    private fun getWeekRange(): String {
        val calendar = Calendar.getInstance()
        val endDate = SimpleDateFormat("MMM dd", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = SimpleDateFormat("MMM dd", Locale.getDefault()).format(calendar.time)
        return "$startDate - $endDate, ${calendar.get(Calendar.YEAR)}"
    }
}
