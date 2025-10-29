package com.example.caresync.accountability

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class DailyAccountabilityWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 Daily accountability check started")

            val db = AppDatabase.get(applicationContext)
            val reminderDao = db.reminderDao()
            val eventDao = db.reminderEventDao()

            // Get all tasks with daily report enabled
            val allTasks = reminderDao.getAllReminders()
            val tasksWithDaily = allTasks.filter {
                it.shareProgressEnabled && it.sendDailyReport &&
                        !it.shareProgressContactPhone.isNullOrEmpty()
            }

            Log.d(TAG, "Found ${tasksWithDaily.size} tasks with daily reports")

            // Group by contact phone
            val byContact = tasksWithDaily.groupBy { it.shareProgressContactPhone }

            byContact.forEach { (phone, tasks) ->
                if (tasks.size < 5) {
                    // Send individual reports
                    tasks.forEach { task ->
                        sendDailyReport(task.id, phone!!, reminderDao, eventDao)
                    }
                } else {
                    // Send combined summary
                    sendCombinedDailyReport(tasks.map { it.id }, phone!!, reminderDao, eventDao)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in daily accountability worker", e)
            Result.retry()
        }
    }

    private suspend fun sendDailyReport(
        taskId: Long,
        phone: String,
        reminderDao: com.example.caresync.data.ReminderDao,
        eventDao: com.example.caresync.data.ReminderEventDao
    ) {
        // ✅ FIXED: Use getById instead of getReminderById
        val task = reminderDao.getById(taskId)?.toDomain() ?: return

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        // ✅ FIXED: Use getEventsBetween instead of getEventsForReminderSince
        val todayEvents = eventDao.getEventsBetween(
            taskId,
            todayStart,
            System.currentTimeMillis()
        )

        val report = AccountabilityReportGenerator().generateDailyReport(task, todayEvents)
        WhatsAppSender.sendReport(applicationContext, phone, report)
    }

    private suspend fun sendCombinedDailyReport(
        taskIds: List<Long>,
        phone: String,
        reminderDao: com.example.caresync.data.ReminderDao,
        eventDao: com.example.caresync.data.ReminderEventDao
    ) {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val completedTasks = mutableListOf<String>()
        val missedTasks = mutableListOf<String>()

        taskIds.forEach { taskId ->
            val task = reminderDao.getById(taskId)?.toDomain()
            if (task != null) {
                val events = eventDao.getEventsBetween(taskId, todayStart, System.currentTimeMillis())
                val completed = events.any { it.eventType == "COMPLETED" }

                if (completed) {
                    completedTasks.add(task.title)
                } else {
                    missedTasks.add(task.title)
                }
            }
        }

        val report = buildString {
            appendLine("📊 Daily Summary")
            appendLine(java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date()))
            appendLine()

            if (completedTasks.isNotEmpty()) {
                appendLine("✅ ${completedTasks.size} completed:")
                completedTasks.forEach { appendLine("• $it") }
                appendLine()
            }

            if (missedTasks.isNotEmpty()) {
                appendLine("⚠️ ${missedTasks.size} pending:")
                missedTasks.forEach { appendLine("• $it") }
                appendLine()
            }

            val percentage = (completedTasks.size * 100) / (completedTasks.size + missedTasks.size)
            appendLine("Today: $percentage% completion")
            appendLine()
            appendLine("—CareSync")
        }

        WhatsAppSender.sendReport(applicationContext, phone, report)
    }

    companion object {
        private const val TAG = "DailyAccountWorker"
    }
}
