package com.example.caresync.accountability

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class WeeklyAccountabilityWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 Weekly accountability check started")

            val db = AppDatabase.get(applicationContext)
            val reminderDao = db.reminderDao()
            val eventDao = db.reminderEventDao()

            // Get all tasks with weekly report enabled
            val allTasks = reminderDao.getAllReminders()
            val tasksWithWeekly = allTasks.filter {
                it.shareProgressEnabled && it.sendWeeklyReport &&
                        !it.shareProgressContactPhone.isNullOrEmpty()
            }

            Log.d(TAG, "Found ${tasksWithWeekly.size} tasks with weekly reports")

            // Group by contact phone
            val byContact = tasksWithWeekly.groupBy { it.shareProgressContactPhone }

            byContact.forEach { (phone, tasks) ->
                if (tasks.size < 5) {
                    // Send individual reports
                    tasks.forEach { task ->
                        sendWeeklyReport(task.id, phone!!, reminderDao, eventDao)
                    }
                } else {
                    // Send combined summary
                    sendCombinedWeeklyReport(tasks.map { it.id }, phone!!, reminderDao, eventDao)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in weekly accountability worker", e)
            Result.retry()
        }
    }

    private suspend fun sendWeeklyReport(
        taskId: Long,
        phone: String,
        reminderDao: com.example.caresync.data.ReminderDao,
        eventDao: com.example.caresync.data.ReminderEventDao
    ) {
        // ✅ FIXED: Use getById
        val task = reminderDao.getById(taskId)?.toDomain() ?: return

        val weekStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        // ✅ FIXED: Use getEventsBetween
        val weekEvents = eventDao.getEventsBetween(
            taskId,
            weekStart,
            System.currentTimeMillis()
        )

        val report = AccountabilityReportGenerator().generateWeeklyReport(task, weekEvents)
        WhatsAppSender.sendReport(applicationContext, phone, report)
    }

    private suspend fun sendCombinedWeeklyReport(
        taskIds: List<Long>,
        phone: String,
        reminderDao: com.example.caresync.data.ReminderDao,
        eventDao: com.example.caresync.data.ReminderEventDao
    ) {
        val weekStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val taskReports = mutableListOf<Pair<String, Int>>()

        taskIds.forEach { taskId ->
            val task = reminderDao.getById(taskId)?.toDomain()
            if (task != null) {
                val events = eventDao.getEventsBetween(taskId, weekStart, System.currentTimeMillis())
                val completed = events.count { it.eventType == "COMPLETED" }
                val percentage = (completed * 100) / 7
                taskReports.add(task.title to percentage)
            }
        }

        val report = buildString {
            appendLine("📊 Weekly Summary")
            appendLine(getWeekRange())
            appendLine()

            val excellent = taskReports.filter { it.second >= 90 }
            val good = taskReports.filter { it.second in 60..89 }
            val needsWork = taskReports.filter { it.second < 60 }

            if (excellent.isNotEmpty()) {
                appendLine("🏆 Excellent (90%+):")
                excellent.forEach { (title, pct) -> appendLine("• $title - $pct%") }
                appendLine()
            }

            if (good.isNotEmpty()) {
                appendLine("📊 Good (60-89%):")
                good.forEach { (title, pct) -> appendLine("• $title - $pct%") }
                appendLine()
            }

            if (needsWork.isNotEmpty()) {
                appendLine("⚠️ Needs Attention (<60%):")
                needsWork.forEach { (title, pct) -> appendLine("• $title - $pct%") }
                appendLine()
            }

            val avgCompletion = if (taskReports.isNotEmpty()) {
                taskReports.map { it.second }.average().toInt()
            } else 0

            appendLine("Overall: $avgCompletion% completion")
            appendLine()
            appendLine("—CareSync")
        }

        WhatsAppSender.sendReport(applicationContext, phone, report)
    }

    private fun getWeekRange(): String {
        val calendar = Calendar.getInstance()
        val endDate = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(calendar.time)
        return "$startDate - $endDate, ${calendar.get(Calendar.YEAR)}"
    }

    companion object {
        private const val TAG = "WeeklyAccountWorker"
    }
}
