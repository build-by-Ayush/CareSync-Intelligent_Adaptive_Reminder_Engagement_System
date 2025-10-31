package com.example.caresync

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.caresync.accountability.AccountabilityScheduler
import com.example.caresync.scheduler.workers.BlacklistDecayWorker
import com.example.caresync.scheduler.workers.FrequencyOptimizationWorker
import com.example.caresync.scheduler.workers.PriorityEscalationWorker
import com.example.caresync.utils.CategoryMapper
import com.example.caresync.utils.StateDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Application class - Runs once when app starts.
 *
 * Responsibilities:
 * - Initialize CategoryMapper
 * - Start StateDetector
 * - Schedule background maintenance workers
 */
class CareSyncApplication : Application() {

    private val TAG = "CareSyncApp"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var stateDetector: StateDetector
        private set

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Application starting...")

        // Initialize CategoryMapper (background thread)
        applicationScope.launch(Dispatchers.IO) {
            CategoryMapper.initialize(applicationContext)
        }

        // Start StateDetector (monitors IDLE/OFF)
        stateDetector = StateDetector(this)
        stateDetector.start()
        Log.d(TAG, "✅ State detector started")

        // Schedule accountability report jobs (unchanged)
        AccountabilityScheduler.scheduleDailyReports(this)
        AccountabilityScheduler.scheduleWeeklyReports(this)
        Log.d(TAG, "✅ Accountability reports scheduled")

        // ✅ Schedule adaptive layer maintenance jobs
        scheduleBlacklistDecay()
        scheduleFrequencyOptimization()
        schedulePriorityEscalationWorker()
        Log.d(TAG, "✅ Maintenance workers scheduled")

        Log.d(TAG, "✅ Application initialized")
    }

    override fun onTerminate() {
        super.onTerminate()
        stateDetector.stop()
        Log.d(TAG, "⏹️ Application terminated")
    }

    /**
     * Schedules the weekly blacklist decay worker.
     * Runs every Sunday at 3 AM to clean up stale blacklist hours.
     */
    private fun scheduleBlacklistDecay() {
        try {
            // Find next Sunday 3 AM
            val currentTime = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(currentTime)) add(Calendar.DAY_OF_MONTH, 7)
            }
            val initialDelayMillis = targetTime.timeInMillis - currentTime.timeInMillis

            val decayWorkRequest = PeriodicWorkRequestBuilder<BlacklistDecayWorker>(
                7, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .addTag("blacklist_decay")
                .build()

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "blacklist_decay_weekly",
                    ExistingPeriodicWorkPolicy.KEEP,
                    decayWorkRequest
                )
            Log.d(TAG, "✅ Blacklist decay scheduled (next run: Sunday 3 AM)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule blacklist decay", e)
        }
    }

    /**
     * Schedules the weekly frequency optimization worker.
     * Runs every Sunday at 3 AM to adjust notification frequency per user success.
     */
    private fun scheduleFrequencyOptimization() {
        try {
            // Find next Sunday 3 AM
            val currentTime = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(currentTime)) add(Calendar.DAY_OF_MONTH, 7)
            }
            val initialDelayMillis = targetTime.timeInMillis - currentTime.timeInMillis

            val freqWorkRequest = PeriodicWorkRequestBuilder<FrequencyOptimizationWorker>(
                7, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .addTag("dynamic_frequency_opt")
                .build()

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "dynamic_frequency_opt",
                    ExistingPeriodicWorkPolicy.KEEP,
                    freqWorkRequest
                )
            Log.d(TAG, "✅ Scheduled FrequencyOptimizationWorker (weekly, Sunday 3AM)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule dynamic frequency optimization", e)
        }
    }

    /**
     * Schedules the daily priority escalation worker.
     * Runs at 3 AM to bump up task priority as deadlines approach or are missed.
     */
    private fun schedulePriorityEscalationWorker() {
        try {
            // Next 3 AM (today or tomorrow)
            val currentTime = Calendar.getInstance()
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(currentTime)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val initialDelayMillis = targetTime.timeInMillis - currentTime.timeInMillis

            val escalationWorkRequest = PeriodicWorkRequestBuilder<PriorityEscalationWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiresBatteryNotLow(true).build()
                )
                .addTag("priority_escalation")
                .build()

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "priority_escalation_daily",
                    ExistingPeriodicWorkPolicy.KEEP,
                    escalationWorkRequest
                )

            Log.d(TAG, "✅ Priority escalation scheduled (daily, 3AM)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to schedule priority escalation worker", e)
        }
    }
}
