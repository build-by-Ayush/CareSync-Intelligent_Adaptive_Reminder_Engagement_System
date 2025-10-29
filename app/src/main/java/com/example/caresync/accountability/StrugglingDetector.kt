package com.example.caresync.accountability

import android.content.Context
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderEventEntity
import com.example.caresync.data.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object StrugglingDetector {

    private const val TAG = "StrugglingDetector"
    private const val MISSED_THRESHOLD = 3 // Alert after 3 consecutive misses

    /**
     * Check if task is struggling and send alert if needed
     * Call this after each MISSED/DISMISSED event
     */
    suspend fun checkAndAlert(context: Context, taskId: Long) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.get(context)
            val reminderDao = db.reminderDao()
            val eventDao = db.reminderEventDao()

            val task = reminderDao.getById(taskId)?.toDomain() ?: return@withContext

            // Check if struggling alerts enabled
            if (!task.shareProgressEnabled || !task.sendStrugglingAlerts ||
                task.shareProgressContactPhone.isNullOrEmpty()) {
                return@withContext
            }

            // Get recent events (last 7 days)
            val weekAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
            }.timeInMillis

            val recentEvents = eventDao.getEventsBetween(
                taskId,
                weekAgo,
                System.currentTimeMillis()
            )

            // Count consecutive misses
            val consecutiveMisses = countConsecutiveMisses(recentEvents)

            if (consecutiveMisses >= MISSED_THRESHOLD) {
                Log.d(TAG, "⚠️ Task $taskId struggling: $consecutiveMisses consecutive misses")
                sendStrugglingAlert(context, task, recentEvents, task.shareProgressContactPhone!!)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error checking struggling status", e)
        }
    }

    private fun countConsecutiveMisses(events: List<ReminderEventEntity>): Int {
        // Sort by timestamp descending (newest first)
        val sorted = events.sortedByDescending { it.timestamp }

        var count = 0
        for (event in sorted) {
            when (event.eventType) {
                "MISSED", "DISMISSED", "IGNORED" -> count++
                "COMPLETED" -> break // Reset on completion
                else -> continue
            }
        }

        return count
    }

    private fun sendStrugglingAlert(
        context: Context,
        task: com.example.caresync.domain.ReminderSettings,
        recentEvents: List<ReminderEventEntity>,
        phone: String
    ) {
        val missedCount = recentEvents.count {
            it.eventType in listOf("MISSED", "DISMISSED", "IGNORED")
        }

        val report = AccountabilityReportGenerator().generateStrugglingAlert(task, recentEvents)
        WhatsAppSender.sendReport(context, phone, report)

        Log.d(TAG, "📤 Struggling alert sent for ${task.title}")
    }
}
