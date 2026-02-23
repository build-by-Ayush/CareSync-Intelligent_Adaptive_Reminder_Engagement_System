package com.example.caresync.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.caresync.data.ReminderRepository
import com.example.caresync.utils.UsageSessionLogger
import com.example.caresync.utils.AppSessionHelper
import com.example.caresync.scheduler.workers.MLCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * SessionAlarmReceiver - FULLY CORRECTED VERSION (Multi-Reminder Aware with Coroutines)
 *
 * Purpose:
 * --------
 * Handles mini-alarms fired by SessionPollingWorker. When a mini-alarm fires:
 * 1. Check if original app is STILL ACTIVE now
 *    → YES: Trigger ML check for ALL Model Mode reminders
 *    → NO: Continue to step 2
 * 2. Check if app JUST ENDED (recently, within 2 minutes)
 *    → YES: Trigger ML check for ALL Model Mode reminders
 *    → NO: Ignore (let future windows handle it)
 * 3. For EACH Model Mode reminder: Pass REAL reminder object to MLCheckWorker
 *    → NO dummy reminders!
 *    → Priority system handles conflicts
 *
 * Critical Architecture:
 * ======================
 * ✅ Query: ALL reminders with Model Mode enabled
 * ✅ For each: Create MLCheckWorker with REAL reminder object
 * ✅ No dummy reminders (was hacky, now removed)
 * ✅ Pipeline gets proper context per reminder
 * ✅ Priority system decides which notification sends
 * ✅ Uses coroutines (goAsync + scope.launch) for database operations
 *
 * Data Tracking Strategy:
 * ======================
 * Session-End Detection:
 *   ├─ If app still active NOW: Use CURRENT duration
 *   ├─ If app just ended: Use final duration
 *   └─ If old exit: Ignore (qualifiedFrequency will catch if needed)
 *
 * Multi-Reminder Handling:
 *   ├─ Query: SELECT * FROM reminders WHERE triggerMode = 'MODEL_ASSISTED'
 *   ├─ For each reminder found:
 *   │  ├─ Create MLCheckWorker with real reminder object
 *   │  └─ Pass correct context to pipeline
 *   └─ Result: All reminders checked independently
 *
 * Why This Works:
 * ===============
 * - Catches continuously engaged sessions (if still active)
 * - Catches just-ended sessions with qualifying engagement
 * - Checks ALL Model Mode reminders at once
 * - Each reminder gets real ReminderSettings object
 * - Pipeline has full context (no null, no dummy)
 * - Priority system handles conflicts
 */
class SessionAlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SessionAlarmReceiver"
        private const val JUST_ENDED_TIMEOUT_SECONDS = 120  // 2 minutes tolerance
    }

    // ✅ Instance coroutine scope for database operations
    private val scope = CoroutineScope(
        Dispatchers.Default + Job()
    )

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("packageName") ?: return
        val sessionStart = intent.getLongExtra("sessionStart", -1L)

        if (sessionStart == -1L) {
            Log.w(TAG, "Invalid sessionStart time")
            return
        }

        Log.d(TAG, """
            🔔 Session alarm fired
               Package: $packageName
               Started: ${formatTime(sessionStart)}
        """.trimIndent())

        try {
            // ===== CHECK 1: Is app STILL ACTIVE? =====
            val currentSessions = AppSessionHelper.getOngoingSessionsNOW(context)

            if (packageName in currentSessions) {
                // ✅ App still active - use current data
                val currentDuration = AppSessionHelper.getSessionDuration(context, packageName)

                Log.d(TAG, """
                    ✅ App STILL ACTIVE
                       Package: $packageName
                       Duration: ${String.format("%.2f", currentDuration)} min
                       Querying Model Mode reminders...
                """.trimIndent())

                // ✅ NEW: Get ALL Model Mode reminders and check each
                triggerMLCheckForAllModelModeReminders(
                    context = context,
                    packageName = packageName,
                    minsSinceOpen = currentDuration,
                    triggerSource = "SESSION_STILL_ACTIVE"
                )
                return
            }

            // ===== CHECK 2: Did it JUST END as qualified session? =====
            val justEnded = UsageSessionLogger.findJustEndedSession(
                context,
                packageName = packageName,
                sessionStartTime = sessionStart,
                withinSeconds = JUST_ENDED_TIMEOUT_SECONDS
            )

            if (justEnded != null) {
                // ✅ Session just ended with good engagement - use final data
                Log.d(TAG, """
                    ✅ App JUST ENDED
                       Package: $packageName
                       Duration: ${String.format("%.2f", justEnded.durationMinutes)} min
                       Ended: ${(System.currentTimeMillis() - justEnded.endTime) / 1000}s ago
                       Querying Model Mode reminders...
                """.trimIndent())

                // ✅ NEW: Get ALL Model Mode reminders and check each
                triggerMLCheckForAllModelModeReminders(
                    context = context,
                    packageName = packageName,
                    minsSinceOpen = justEnded.durationMinutes,
                    triggerSource = "SESSION_JUST_ENDED"
                )
                return
            }

            // ===== OTHERWISE: Ignore =====
            Log.d(TAG, """
                ⏭️ Ignoring:
                   App not currently active
                   Not recently ended
                   (Future WorkManager windows will handle if needed)
            """.trimIndent())

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in SessionAlarmReceiver", e)
        }
    }

    /**
     * ✅ CORRECTED: Query ALL Model Mode reminders and trigger MLCheckWorker for each
     *
     * Uses goAsync() to allow async database query in BroadcastReceiver
     * Launches coroutine within the scope to call suspend functions
     */
    private fun triggerMLCheckForAllModelModeReminders(
        context: Context,
        packageName: String,
        minsSinceOpen: Float,
        triggerSource: String
    ) {
        // ✅ Use goAsync() to allow async operations in BroadcastReceiver
        val pendingResult = goAsync()

        // ✅ Launch coroutine to query database
        scope.launch {
            try {
                // ✅ Now we can call suspend function
                val repository = ReminderRepository(context)
                val modelModeReminders = repository.getAllWithModelMode()

                if (modelModeReminders.isEmpty()) {
                    Log.d(TAG, "No reminders with Model Mode enabled")
                    pendingResult.finish()
                    return@launch
                }

                Log.d(TAG, """
                    📊 Found ${modelModeReminders.size} reminders with Model Mode enabled
                       Triggering ML check for each...
                """.trimIndent())

                // ✅ For EACH reminder: Create MLCheckWorker with REAL reminder object
                for (reminder in modelModeReminders) {
                    Log.d(TAG, """
                        ✅ Triggering MLCheckWorker:
                           Reminder: ${reminder.title} (ID: ${reminder.id})
                           Trigger: $triggerSource
                           Package: $packageName
                           Duration: ${String.format("%.2f", minsSinceOpen)} min
                    """.trimIndent())

                    // ✅ CRITICAL: Pass REAL reminder object, NOT null or dummy!
                    MLCheckWorker.triggerForReminder(
                        context = context,
                        reminder = reminder,  // ✅ REAL ReminderSettings object!
                        packageName = packageName,
                        minsSinceOpen = minsSinceOpen,
                        triggerSource = triggerSource
                    )
                }

                Log.d(TAG, """
                    ✅ All Model Mode reminders queued for ML check
                       Count: ${modelModeReminders.size}
                       Pipeline will handle conflicts via priority system
                """.trimIndent())

                // ✅ Mark async operation as complete
                pendingResult.finish()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error querying Model Mode reminders", e)
                // ✅ Always finish pending result
                pendingResult.finish()
            }
        }
    }

    /**
     * Format timestamp for logging
     */
    private fun formatTime(timestamp: Long): String {
        return try {
            val date = Date(timestamp)
            val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "N/A"
        }
    }
}
