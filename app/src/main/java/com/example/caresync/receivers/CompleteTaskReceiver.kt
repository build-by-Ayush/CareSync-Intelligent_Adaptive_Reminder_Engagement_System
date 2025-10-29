package com.example.caresync.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.caresync.analytics.gamification.PointsCalculator
import com.example.caresync.analytics.repository.AnalyticsRepository
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.EventTypes
import com.example.caresync.utils.AppBlockManager  // ✅ ADD
import com.example.caresync.utils.SafeEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import com.example.caresync.utils.AppBlockService

class CompleteTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        if (reminderId == -1L) return

        Log.d("REMINDER_EVENT", "✓ COMPLETED: Task $reminderId")

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.get(context)
            val eventDao = database.reminderEventDao()
            val reminderDao = database.reminderDao()
            val completionTime = System.currentTimeMillis()

            // ✅ GET THE TASK (for priority)
            val task = reminderDao.getById(reminderId)
            if (task == null) {
                Log.e("REMINDER_EVENT", "Task not found for ID $reminderId")
                return@launch
            }

            // ✅ GET NOTIFICATION SENT TIME (for points calculation)
            val sentEvent = eventDao.getLastSentEventBeforeCompletion(
                reminderId = reminderId,
                completionTimestamp = completionTime
            )

            // Calculate response time
            val responseTime = if (sentEvent != null) {
                completionTime - sentEvent.timestamp
            } else {
                // Fallback to old logic if no sent event found
                try {
                    val oneHourAgo = completionTime - 60 * 60 * 1000L
                    val events = eventDao.getEventsBetween(reminderId, oneHourAgo, completionTime)
                    val lastTriggered = events.lastOrNull { it.eventType == EventTypes.TRIGGERED }
                    if (lastTriggered != null) {
                        completionTime - lastTriggered.timestamp
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            // Get snooze count (existing logic)
            val snoozeCount = try {
                val oneHourAgo = completionTime - 60 * 60 * 1000L
                val events = eventDao.getEventsBetween(reminderId, oneHourAgo, completionTime)
                val lastTriggeredIndex = events.indexOfLast { it.eventType == EventTypes.TRIGGERED }
                if (lastTriggeredIndex >= 0) {
                    events.drop(lastTriggeredIndex + 1).count { it.eventType == EventTypes.SNOOZED }
                } else 0
            } catch (e: Exception) {
                0
            }

            // ✅ GET TONE FROM TRIGGERED EVENT (not from task settings)
            val toneUsed = sentEvent?.toneUsed ?: "AUTO"

            // ✅ LOG COMPLETION EVENT WITH SAME TONE
            val success = SafeEventLogger.logEvent(
                context = context,
                reminderId = reminderId,
                eventType = EventTypes.COMPLETED,
                toneUsed = toneUsed, // ← FIXED: Use tone from TRIGGERED event
                responseTimeMillis = responseTime,
                snoozeCount = snoozeCount
            )

            if (success) {
                Log.d("REMINDER_EVENT", "✓ Logged COMPLETED with responseTime=$responseTime ms")
            }

            // ✅ CALCULATE POINTS & UPDATE DASHBOARD
            try {
                // ✅ CONVERT STRING PRIORITY TO ENUM
                val priorityEnum = try {
                    when (task.priority.uppercase()) {
                        "CRITICAL" -> com.example.caresync.domain.Priority.CRITICAL
                        "HIGH" -> com.example.caresync.domain.Priority.HIGH
                        "NORMAL" -> com.example.caresync.domain.Priority.NORMAL
                        "LOW" -> com.example.caresync.domain.Priority.LOW
                        else -> com.example.caresync.domain.Priority.NORMAL // Default
                    }
                } catch (e: Exception) {
                    com.example.caresync.domain.Priority.NORMAL // Safe fallback
                }

                val points = PointsCalculator.calculateTaskPoints(
                    priority = priorityEnum,
                    completedAt = completionTime,
                    notificationSentAt = sentEvent?.timestamp
                )

                Log.d("GAMIFICATION", "Earned $points points for task $reminderId (priority: $priorityEnum)")

                // ✅ UPDATE USER PROGRESS
                val analyticsDao = database.analyticsDao()
                val achievementEngine = com.example.caresync.analytics.gamification.AchievementEngine(
                    analyticsDao = analyticsDao,
                    reminderEventDao = eventDao
                )

                val repository = AnalyticsRepository(
                    analyticsDao = analyticsDao,
                    reminderEventDao = eventDao,
                    reminderDao = reminderDao,
                    achievementEngine = achievementEngine
                )

                repository.updateUserProgress(
                    taskCompleted = true,
                    pointsEarned = points
                )

                Log.d("GAMIFICATION", "✓ Updated user progress with $points points")

                // Check for achievement unlocks
                val newUnlocks = achievementEngine.checkAndUnlockAchievements()
                if (newUnlocks.isNotEmpty()) {
                    Log.d("GAMIFICATION", "🏆 Unlocked ${newUnlocks.size} achievements!")
                    newUnlocks.forEach {
                        Log.d("GAMIFICATION", " - ${it.name} ${it.icon}")
                    }
                }

            } catch (e: Exception) {
                Log.e("GAMIFICATION", "Error updating progress: ${e.message}", e)
            }

            // ✅ NEW: App Blocking Logic
            task.targetAppPackage?.let { packageName ->
                if (packageName.isNotBlank()) {
                    AppBlockManager.blockApp(packageName)

                    val appName = getAppName(context, packageName)

                    // Show toast and start service
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "🚫 $appName blocked for 30 minutes",
                            Toast.LENGTH_LONG
                        ).show()

                        // ✅ FIXED: Start the blocking service
                        val serviceIntent = Intent(context, AppBlockService::class.java)
                        context.startService(serviceIntent)
                        Log.d("APP_BLOCK", "🚀 Started AppBlockService")
                    }

                    Log.d("APP_BLOCK", "🚫 Blocked $appName ($packageName) for 30 minutes")
                }
            }
        }

        // Cancel notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId.toInt())
    }

    /**
     * Calculate time between last TRIGGERED event and now
     */
    private suspend fun calculateResponseTime(
        eventDao: com.example.caresync.data.ReminderEventDao,
        reminderId: Long
    ): Long? {
        return try {
            val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            val events = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())
            val lastTriggered = events.lastOrNull { it.eventType == EventTypes.TRIGGERED }

            if (lastTriggered != null) {
                System.currentTimeMillis() - lastTriggered.timestamp
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Count snoozes since last TRIGGERED
     */
    private suspend fun getSnoozeCountForThisNotification(
        eventDao: com.example.caresync.data.ReminderEventDao,
        reminderId: Long
    ): Int {
        return try {
            val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            val events = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())

            // Find last TRIGGERED, count SNOOZEDs after it
            val lastTriggeredIndex = events.indexOfLast { it.eventType == EventTypes.TRIGGERED }
            if (lastTriggeredIndex >= 0) {
                events.drop(lastTriggeredIndex + 1).count { it.eventType == EventTypes.SNOOZED }
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get current battery level
     */
    private fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ✅ NEW: Helper to get app name
    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}
