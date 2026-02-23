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
 * - Uses consistent threshold (5+ dismissals) from RandomTimeScheduler
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
        private const val BLACKLIST_THRESHOLD = 5  // ✅ CONSISTENT: Same as RandomTimeScheduler
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🧹 Starting weekly blacklist decay check...")

            val database = AppDatabase.get(context)
            val reminderDao = database.reminderDao()
            val learner = OptimalTimeLearner(context)

            // Get all enabled tasks
            val allTasks = reminderDao.getAllReminders()
            // ✅ CONSISTENT: Check all enabled tasks
            val enabledTasks = allTasks.filter { it.enabled }

            if (enabledTasks.isEmpty()) {
                Log.d(TAG, "⏭️ No enabled tasks found, skipping")
                return Result.success()
            }

            Log.d(TAG, "📊 Checking ${enabledTasks.size} tasks for stale blacklists...")

            var totalChecked = 0
            var totalRemoved = 0

            // Check each task
            enabledTasks.forEach { task ->
                try {
                    // ✅ CONSISTENT: Use same threshold as RandomTimeScheduler
                    val blacklistsBefore = database.blacklistHourDao()
                        .getBlacklistedHours(task.id, threshold = BLACKLIST_THRESHOLD)
                        .size

                    // Test and decay stale blacklists
                    learner.testAndDecayBlacklists(task.id)

                    // ✅ CONSISTENT: Check again with same threshold
                    val blacklistsAfter = database.blacklistHourDao()
                        .getBlacklistedHours(task.id, threshold = BLACKLIST_THRESHOLD)
                        .size

                    val removed = blacklistsBefore - blacklistsAfter
                    totalRemoved += removed

                    if (removed > 0) {
                        Log.d(TAG, "✅ Task ${task.id} (${task.title}): Removed $removed stale blacklists (threshold=$BLACKLIST_THRESHOLD)")
                    }

                    totalChecked++
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to check task ${task.id}", e)
                }
            }

            Log.d(TAG, "✅ Decay check complete: $totalChecked tasks checked, $totalRemoved blacklists removed (threshold=$BLACKLIST_THRESHOLD)")

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Blacklist decay worker failed", e)
            Result.retry()
        }
    }
}
