package com.example.caresync

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.caresync.accountability.AccountabilityScheduler
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.toDomain
import com.example.caresync.domain.TriggerMode
import com.example.caresync.scheduler.schedulers.ModelModeScheduler
import com.example.caresync.scheduler.workers.BlacklistDecayWorker
import com.example.caresync.scheduler.workers.FrequencyOptimizationWorker
import com.example.caresync.scheduler.workers.PriorityEscalationWorker
import com.example.caresync.utils.CategoryMapper
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
 * - Schedule background maintenance workers
 * - Schedule polling worker and fallback alarms for robust hybrid reminders
 */
class CareSyncApplication : Application() {

    companion object {
        private const val TAG = "CareSyncApp"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Application starting...")

        // Initialize CategoryMapper with error handling
        applicationScope.launch(Dispatchers.IO) {
            try {
                CategoryMapper.initialize(applicationContext)
                Log.d(TAG, "✅ CategoryMapper initialized")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize CategoryMapper", e)
            }
        }

        // Schedule daily/weekly accountability report jobs
        AccountabilityScheduler.scheduleDailyReports(this)
        AccountabilityScheduler.scheduleWeeklyReports(this)
        Log.d(TAG, "✅ Accountability reports scheduled")

        // Schedule analytics/maintenance jobs
        scheduleBlacklistDecay()
        scheduleFrequencyOptimization()
        schedulePriorityEscalationWorker()
        Log.d(TAG, "✅ Maintenance workers scheduled")

        // Hybrid logic – Core Phase 6 additions:
        scheduleSessionPollingWorker()
        rescheduleFallbackAlarmsForAllReminders()

        Log.d(TAG, "✅ Application initialized")
    }

    /**
     * Schedules the event session polling worker (hybrid requirement)
     */
    private fun scheduleSessionPollingWorker() {
        val pollingRequest = PeriodicWorkRequestBuilder<com.example.caresync.scheduler.workers.SessionPollingWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "session-polling-worker",
                ExistingPeriodicWorkPolicy.KEEP,
                pollingRequest
            )
        Log.d(TAG, "✅ SessionPollingWorker scheduled (15 min interval, unique)")
    }

    /**
     * Reschedules fallback alarms for all reminders on startup
     */
    private fun rescheduleFallbackAlarmsForAllReminders() {
        applicationScope.launch(Dispatchers.IO) {  // ✅ Launch coroutine
            val reminders = com.example.caresync.data.AppDatabase
                .get(this@CareSyncApplication)
                .reminderDao()
                .getAllReminders()

            var hasModelMode = false
            reminders.forEach { reminderEntity ->
                val mode = try {
                    com.example.caresync.domain.TriggerMode.valueOf(reminderEntity.triggerMode)
                } catch (e: Exception) {
                    null
                }

                if (mode == com.example.caresync.domain.TriggerMode.MODEL_ASSISTED) {
                    hasModelMode = true
                    Log.d(TAG, "✅ Model Mode reminder found: ${reminderEntity.title}")
                }
            }

            // ✅ Now can call suspend methods
            if (hasModelMode) {
                Log.d(TAG, "🚀 Enabling SessionPollingWorker (Model Mode reminders exist)")
                ModelModeScheduler(this@CareSyncApplication)
                    .startSessionPollingWorker()  // ✅ Now public
            } else {
                Log.d(TAG, "⏹️ Disabling SessionPollingWorker (no Model Mode reminders)")
                ModelModeScheduler(this@CareSyncApplication)
                    .stopSessionPollingWorker()  // ✅ Now public
            }
        }
    }


    /**
     * Helper: Calculate next occurrence of specific day/time (for weekly workers)
     */
    private fun getNextOccurrenceMillis(targetDayOfWeek: Int, targetHour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var daysUntilTarget = (targetDayOfWeek - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
        if (daysUntilTarget == 0 && now.timeInMillis >= target.timeInMillis) {
            daysUntilTarget = 7
        }
        target.add(Calendar.DAY_OF_YEAR, daysUntilTarget)
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Schedules the weekly blacklist decay worker (Sunday 3 AM)
     */
    private fun scheduleBlacklistDecay() {
        try {
            val initialDelayMillis = getNextOccurrenceMillis(Calendar.SUNDAY, 3)
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
     * Schedules the weekly frequency optimization worker (Sunday 3 AM)
     */
    private fun scheduleFrequencyOptimization() {
        try {
            val initialDelayMillis = getNextOccurrenceMillis(Calendar.SUNDAY, 3)
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
     * Schedules the daily priority escalation worker (3 AM daily)
     */
    private fun schedulePriorityEscalationWorker() {
        try {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var initialDelayMillis = target.timeInMillis - now.timeInMillis
            if (initialDelayMillis <= 0) {
                initialDelayMillis += 24 * 60 * 60 * 1000L // Add 24 hours
            }

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
