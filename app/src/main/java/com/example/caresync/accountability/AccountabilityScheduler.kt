package com.example.caresync.accountability

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit
import java.util.Calendar

object AccountabilityScheduler {

    private const val TAG = "AccountScheduler"

    fun scheduleDailyReports(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<DailyAccountabilityWorker>(
            1, TimeUnit.DAYS
        ).setInitialDelay(
            calculateDelayUntil8PM(),
            TimeUnit.MILLISECONDS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_accountability",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "✅ Daily reports scheduled (every day at 8 PM)")
    }

    fun scheduleWeeklyReports(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<WeeklyAccountabilityWorker>(
            7, TimeUnit.DAYS
        ).setInitialDelay(
            calculateDelayUntilSunday8PM(),
            TimeUnit.MILLISECONDS
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "weekly_accountability",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d(TAG, "✅ Weekly reports scheduled (Sundays at 8 PM)")
    }

    private fun calculateDelayUntil8PM(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // If 8 PM today already passed, schedule for tomorrow
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return target.timeInMillis - now.timeInMillis
    }

    private fun calculateDelayUntilSunday8PM(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)

            // Find next Sunday
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                add(Calendar.DAY_OF_YEAR, 1)
            }

            // If this Sunday's 8 PM already passed, go to next Sunday
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 7)
            }
        }

        return target.timeInMillis - now.timeInMillis
    }

    fun cancelAllReports(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("daily_accountability")
        WorkManager.getInstance(context).cancelUniqueWork("weekly_accountability")
        Log.d(TAG, "🛑 All accountability reports canceled")
    }
}
