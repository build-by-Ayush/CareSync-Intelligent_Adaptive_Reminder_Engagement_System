package com.example.caresync.scheduler.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.caresync.data.AppDatabase
import com.example.caresync.intelligence.OptimalTimeLearner

/**
 * Blacklist Decay Worker - Weekly maintenance job
 *
 * Responsibilities:
 * - Runs every Sunday at 3 AM
 * - Tests stale blacklists (>30 days old)
 * - Removes blacklists if user habits improved
 *
 * Why Weekly:
 * - Not time-sensitive (doesn't need daily)
 * - Reduces overhead
 * - Enough time to collect new data between runs
 */
class BlacklistDecayWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BlacklistDecayWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🧹 Starting weekly blacklist decay check...")

            val database = AppDatabase.get(context)
            val reminderDao = database.reminderDao()
            val learner = OptimalTimeLearner(context)

            // Get all enabled tasks
            val allTasks = reminderDao.getAllReminders()
            // Blacklist decay runs for ALL enabled tasks (smart time always on)
            val enabledTasks = allTasks.filter { it.enabled }

            if (enabledTasks.isEmpty()) {
                Log.d(TAG, "⏭️ No tasks with adaptive layer enabled, skipping")
                return Result.success()
            }

            Log.d(TAG, "📊 Checking ${enabledTasks.size} tasks for stale blacklists...")

            var totalChecked = 0
            var totalRemoved = 0

            // Check each task
            enabledTasks.forEach { task ->
                try {
                    val blacklistsBefore = database.blacklistHourDao()
                        .getBlacklistedHours(task.id, threshold = 5).size

                    // Test and decay stale blacklists
                    learner.testAndDecayBlacklists(task.id)

                    val blacklistsAfter = database.blacklistHourDao()
                        .getBlacklistedHours(task.id, threshold = 5).size

                    val removed = blacklistsBefore - blacklistsAfter
                    totalRemoved += removed

                    if (removed > 0) {
                        Log.d(TAG, "✅ Task ${task.id} (${task.title}): Removed $removed stale blacklists")
                    }

                    totalChecked++
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to check task ${task.id}", e)
                }
            }

            Log.d(TAG, "✅ Decay check complete: $totalChecked tasks checked, $totalRemoved blacklists removed")

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Blacklist decay worker failed", e)
            Result.retry()
        }
    }
}
