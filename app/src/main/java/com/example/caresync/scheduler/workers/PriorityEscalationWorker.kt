package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.toDomain
import com.example.caresync.data.toEntity
import com.example.caresync.domain.Priority
import com.example.caresync.domain.ReminderSettings

class PriorityEscalationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PriorityEscalation" // ✅ Now 19, safe!
    }

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.get(context)
            val reminderDao = db.reminderDao()
            var escalatedCount = 0

            val reminders = reminderDao.getAllReminders()
            val now = System.currentTimeMillis()

            reminders.forEach { r ->
                val task = r.toDomain()
                // Only handle adaptive reminders with due date
                if (!task.autoOptimizeEnabled || task.dueDate == null || task.dueDate == 0L) return@forEach

                // Get current original priority
                val originalPriority = task.originalPriority?.let {
                    try { Priority.valueOf(it) } catch (e: Exception) { task.priority }
                } ?: task.priority

                // Days until due/overdue
                val millisLeft = task.dueDate - now
                val daysLeft = (millisLeft / (1000 * 60 * 60 * 24)).toInt()

                val newPriority = when {
                    millisLeft < 0              -> Priority.CRITICAL  // Overdue
                    daysLeft <= 2               -> Priority.HIGH
                    daysLeft in 3..6            -> if (originalPriority < Priority.NORMAL) Priority.NORMAL else originalPriority
                    else                        -> originalPriority
                }

                // Only escalate if changed or currently auto-adjusted
                val needsEscalation = newPriority != task.priority || !task.priorityAutoAdjusted

                if (needsEscalation) {
                    val updatedTask = task.copy(
                        priority = newPriority,
                        originalPriority = originalPriority.name,
                        priorityAutoAdjusted = true
                    )
                    reminderDao.upsert(updatedTask.toEntity())
                    Log.d(TAG, "🚨 Priority auto-escalated: '${task.title}' → $newPriority (was $originalPriority, due in $daysLeft days)")
                    escalatedCount++
                }
            }
            Log.d(TAG, "✅ Priority escalation done: $escalatedCount tasks auto-escalated")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Priority escalation worker failed", e)
            Result.retry()
        }
    }
}
