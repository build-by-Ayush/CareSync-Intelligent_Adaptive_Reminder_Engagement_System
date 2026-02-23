package com.example.caresync.scheduler.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * SessionPollingWorker.kt - CORRECTED VERSION
 *
 * Purpose:
 * --------
 * Periodic WorkManager worker that scans for ongoing app usage sessions and triggers
 * mini-alarms when app sessions approach behavioral health thresholds.
 *
 * Core Logic (CORRECTED):
 * -----------------------
 * - Runs every 15 minutes (minimum interval allowed by WorkManager)
 * - For every CURRENTLY ACTIVE session (app in foreground NOW):
 *   - Get current duration (NOW - session start)
 *   - Calculate NEXT 5-minute milestone
 *   - Calculate minutes UNTIL that milestone
 *   - If < 15 minutes away: Schedule mini-alarm
 * - Mini-alarm checks at exact 5-min boundaries (15, 20, 25, etc.)
 * - No alarms scheduled beyond 15-min window (next WorkManager handles it)
 *
 * Critical Fixes Applied:
 * =======================
 * ✅ Query window: 2 hours → 30 minutes (avoid stale events)
 * ✅ Rounding on: Current duration (not session start time)
 * ✅ Wait time: (milestone - current) not (milestone - start)
 * ✅ Stop before: 15-min WorkManager window boundary
 * ✅ Cancel existing: Prevent duplicate alarms for same session
 *
 * Why this works:
 * ===============
 * - Maximizes notification responsiveness with minimal battery cost
 * - No redundant alarms: At most one rolling mini-alarm per session
 * - Aligned to user behavior: Alarms fire at exact engagement milestones
 * - Respects WorkManager windows: Stops scheduling before next 15-min check
 */

class SessionPollingWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SessionPollingWorker"
        private const val SESSION_MIN_THRESHOLD_MINUTES = 10       // Minimum for rolling alarm
        private const val SESSION_MIN_MULTIPLE = 5                 // Multiples of 5: 15, 20, 25, ...
        private const val MAX_SCHEDULE_WINDOW_MINUTES = 15         // ✅ NEW: Don't schedule > 15 min away
        private const val QUERY_WINDOW_MINUTES = 180           // ✅ FIXED: Changed to 3 hours for continuous detection!
    }

    override suspend fun doWork(): Result {
        try {
            Log.d(TAG, """
            ══════════════════════════════════════════════════════
            🚀 SessionPollingWorker ACTUALLY EXECUTING!!!
            Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
            ══════════════════════════════════════════════════════
        """.trimIndent())

            Log.d(TAG, "🔄 SessionPollingWorker starting...")

            scanOngoingSessionsAndScheduleAlarms()

            Log.d(TAG, "✅ SessionPollingWorker completed successfully")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in polling worker", e)
            return Result.retry()
        }
    }

    private fun scanOngoingSessionsAndScheduleAlarms() {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()

        // ✅ FIXED: Changed from 2 hours to 30 minutes
        val earliestTime = now - (QUERY_WINDOW_MINUTES * 60 * 1000L)

        try {
            val events = usm.queryEvents(earliestTime, now)
            val ongoingSessions = mutableMapOf<String, Long>()  // packageName → startTime

            var event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        ongoingSessions[event.packageName] = event.timeStamp
                        Log.v(TAG, "FOREGROUND: ${event.packageName}")
                    }
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        ongoingSessions.remove(event.packageName)
                        Log.v(TAG, "BACKGROUND: ${event.packageName}")
                    }
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            Log.d(TAG, "📊 Found ${ongoingSessions.size} ongoing sessions")

            for ((packageName, startTime) in ongoingSessions) {
                // ✅ FIXED: Calculate duration from NOW, not from session start time
                val now_ms = System.currentTimeMillis()
                val sessionDurationMinutes = (now_ms - startTime) / 60000f

                Log.d(TAG, "📱 Checking $packageName: ${String.format("%.2f", sessionDurationMinutes)} min active")

                if (sessionDurationMinutes >= SESSION_MIN_THRESHOLD_MINUTES) {
                    // ✅ FIXED: Find next multiple of 5 based on CURRENT duration
                    val nextMultipleOfFive = ((sessionDurationMinutes / SESSION_MIN_MULTIPLE).toInt() + 1) * SESSION_MIN_MULTIPLE

                    // ✅ FIXED: Calculate minutes UNTIL next milestone
                    val minutesUntilNext = nextMultipleOfFive - sessionDurationMinutes

                    Log.d(TAG, """
                        ⏰ Milestone calculation:
                           App: $packageName
                           Current duration: ${String.format("%.2f", sessionDurationMinutes)} min
                           Next milestone: $nextMultipleOfFive min
                           Wait time: ${String.format("%.2f", minutesUntilNext)} min
                    """.trimIndent())

                    // ✅ NEW: Only schedule if within WorkManager window
                    if (minutesUntilNext >= 1.0f && minutesUntilNext <= MAX_SCHEDULE_WINDOW_MINUTES) {
                        val alarmTime = now_ms + (minutesUntilNext.toLong() * 60 * 1000L)

                        // ✅ NEW: Cancel existing alarm first (avoid duplicates)
                        cancelExistingAlarm(packageName, startTime, alarmManager)

                        scheduleSessionAlarm(packageName, startTime, alarmTime, alarmManager)
                        Log.d(TAG, "✅ Scheduled mini-alarm for $packageName in ${String.format("%.1f", minutesUntilNext)} min (will check at $nextMultipleOfFive min)")

                    } else if (minutesUntilNext > MAX_SCHEDULE_WINDOW_MINUTES) {
                        Log.d(TAG, "⏭️ Too far away (${String.format("%.1f", minutesUntilNext)} min) - next WorkManager will handle")
                    } else {
                        Log.d(TAG, "⏭️ Already past milestone (wait time < 1 min)")
                    }
                } else {
                    Log.v(TAG, "⏭️ $packageName: Only ${String.format("%.2f", sessionDurationMinutes)} min (threshold is $SESSION_MIN_THRESHOLD_MINUTES min)")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scanning sessions", e)
        }
    }

    /**
     * ✅ NEW: Cancel existing alarm before scheduling new one
     * Prevents duplicate mini-alarms for same session
     */
    private fun cancelExistingAlarm(
        packageName: String,
        sessionStart: Long,
        alarmManager: AlarmManager
    ) {
        val requestCode = (packageName.hashCode() xor sessionStart.hashCode()).toInt()
        val intent = Intent(context, com.example.caresync.receivers.SessionAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            try {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "🗑️ Cancelled existing alarm for $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel alarm", e)
            }
        }
    }

    private fun scheduleSessionAlarm(
        packageName: String,
        sessionStart: Long,
        alarmTime: Long,
        alarmManager: AlarmManager
    ) {
        val intent = Intent(context, com.example.caresync.receivers.SessionAlarmReceiver::class.java)
            .setAction("com.example.caresync.SESSION_ALARM")  // ✅ ADD THIS LINE!
            .apply {
                putExtra("packageName", packageName)
                putExtra("sessionStart", sessionStart)
                putExtra("alarmTime", alarmTime)
            }

        val requestCode = (packageName.hashCode() xor sessionStart.hashCode()).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "⚠️ Exact alarm permission not granted (SCHEDULE_EXACT_ALARM)!")
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent
                )
            }

            Log.d(TAG, "⏰ Scheduled alarm for $packageName at ${formatTime(alarmTime)}")

        } catch (se: SecurityException) {
            Log.e(TAG, "❌ SecurityException when scheduling alarm", se)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling alarm", e)
        }
    }

    private fun formatTime(ts: Long): String {
        return try {
            val date = java.util.Date(ts)
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "N/A"
        }
    }
}
