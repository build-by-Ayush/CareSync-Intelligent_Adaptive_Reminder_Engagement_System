package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.toDomain
import com.example.caresync.intelligence.FrequencyOptimizer

class FrequencyOptimizationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FrequencyOptWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.get(context)
            val reminderDao = database.reminderDao()
            val optimizer = FrequencyOptimizer(context)

            // Get all reminders (filter for adaptive layer)
            val allTasks = reminderDao.getAllReminders()
            val adaptiveTasks = allTasks.filter { it.autoOptimizeEnabled }

            var changed = 0

            adaptiveTasks.forEach { task ->
                try {
                    optimizer.optimizeTaskFrequency(task.toDomain())
                    changed++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to optimize task ${task.id} (${task.title})", e)
                }
            }

            Log.d(TAG, "✅ Frequency optimization complete: ${adaptiveTasks.size} tasks checked, $changed optimized")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Frequency optimization worker failed", e)
            Result.retry()
        }
    }
}
