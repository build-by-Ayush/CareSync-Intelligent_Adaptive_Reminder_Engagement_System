package com.example.caresync.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.scheduler.NotificationSchedulerImpl
import com.example.caresync.utils.SafeEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SnoozeTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        if (reminderId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val repo = ReminderRepository(context)
            val reminder = repo.get(reminderId)

            if (reminder == null) {
                Log.w("SNOOZE", "⚠️ Task $reminderId no longer exists, ignoring snooze")
                return@launch
            }

            val eventDao = AppDatabase.get(context).reminderEventDao()

            // ✅ CHECK: Count recent snoozes
            val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            val recentEvents = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())

            val currentSnoozeCount = try {
                val lastTriggeredIndex = recentEvents.indexOfLast { it.eventType == EventTypes.TRIGGERED }
                if (lastTriggeredIndex >= 0) {
                    recentEvents.drop(lastTriggeredIndex + 1).count { it.eventType == EventTypes.SNOOZED }
                } else {
                    recentEvents.count { it.eventType == EventTypes.SNOOZED }
                }
            } catch (e: Exception) {
                0
            }

            // ✅ BLOCK if limit reached (defensive check)
            if (currentSnoozeCount >= reminder.maxSnoozes) {
                Log.w("SNOOZE", "🚫 Max snooze limit reached ($currentSnoozeCount >= ${reminder.maxSnoozes})")

                // Show toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "⚠️ Max snooze limit reached. Please complete or dismiss the task.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // Cancel notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(reminderId.toInt())

                return@launch
            }

            val snoozeDuration = reminder.snoozeDurationMinutes
            Log.d("REMINDER_EVENT", "⏰ SNOOZED: Task $reminderId for $snoozeDuration min (count: ${currentSnoozeCount + 1}/${reminder.maxSnoozes})")

            // ✅ SAFE LOGGING
            val success = SafeEventLogger.logEvent(
                context = context,
                reminderId = reminderId,
                eventType = EventTypes.SNOOZED,
                snoozeDurationMinutes = snoozeDuration,
                snoozeCount = currentSnoozeCount + 1
            )

            if (success) {
                // ✅ FIXED: Use AlarmManager for reliable snooze timing
                val snoozeDelayMillis = snoozeDuration * 60 * 1000L
                val snoozeTime = System.currentTimeMillis() + snoozeDelayMillis

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

                // Create PendingIntent for ReminderWorker
                val snoozeIntent = Intent(context, SnoozeAlarmReceiver::class.java).apply {
                    putExtra("reminderId", reminderId)
                }

                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    (reminderId * 1000 + 999).toInt(),  // Unique ID for snooze
                    snoozeIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )

                // ✅ Check permission and schedule exact alarm
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        // Android 12+ requires permission check
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                snoozeTime,
                                pendingIntent
                            )
                            Log.d("SNOOZE", "✅ Scheduled exact snooze alarm for ${java.text.SimpleDateFormat("HH:mm:ss").format(snoozeTime)}")
                        } else {
                            // Fallback to inexact alarm if permission not granted
                            alarmManager.setAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                snoozeTime,
                                pendingIntent
                            )
                            Log.w("SNOOZE", "⚠️ Scheduled inexact snooze (no exact alarm permission)")
                        }
                    } else {
                        // Android 11 and below - no permission needed
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(
                                android.app.AlarmManager.RTC_WAKEUP,
                                snoozeTime,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setExact(
                                android.app.AlarmManager.RTC_WAKEUP,
                                snoozeTime,
                                pendingIntent
                            )
                        }
                        Log.d("SNOOZE", "✅ Scheduled exact snooze alarm for ${java.text.SimpleDateFormat("HH:mm:ss").format(snoozeTime)}")
                    }
                } catch (e: SecurityException) {
                    Log.e("SNOOZE", "Failed to schedule snooze alarm", e)
                    // Fallback to WorkManager if AlarmManager fails
                    val snoozeWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.caresync.scheduler.ReminderWorker>()
                        .setInitialDelay(snoozeDelayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .setInputData(
                            androidx.work.workDataOf(
                                "reminderId" to reminderId,
                                "isSnooze" to true
                            )
                        )
                        .addTag("snooze-$reminderId")
                        .build()

                    androidx.work.WorkManager.getInstance(context).enqueue(snoozeWorkRequest)
                    Log.d("SNOOZE", "⚠️ Fallback: Scheduled via WorkManager")
                }
            }

            // Cancel notification
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(reminderId.toInt())
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

            val lastTriggeredIndex = events.indexOfLast { it.eventType == EventTypes.TRIGGERED }
            if (lastTriggeredIndex >= 0) {
                events.drop(lastTriggeredIndex + 1).count { it.eventType == EventTypes.SNOOZED }
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Create event with all context fields populated
     */
    private fun createEventWithContext(
        reminderId: Long,
        eventType: String,
        context: Context,
        responseTimeMillis: Long? = null,
        snoozeDurationMinutes: Int? = null,
        snoozeCount: Int = 0
    ): com.example.caresync.data.ReminderEventEntity {
        val now = java.util.Calendar.getInstance()

        return com.example.caresync.data.ReminderEventEntity(
            reminderId = reminderId,
            eventType = eventType,
            timestamp = System.currentTimeMillis(),

            // Time context
            hourOfDay = now.get(java.util.Calendar.HOUR_OF_DAY),
            dayOfWeek = now.get(java.util.Calendar.DAY_OF_WEEK) - 1,
            isWeekend = now.get(java.util.Calendar.DAY_OF_WEEK) in listOf(
                java.util.Calendar.SATURDAY,
                java.util.Calendar.SUNDAY
            ),

            // User behavior
            responseTimeMillis = responseTimeMillis,
            snoozeDurationMinutes = snoozeDurationMinutes,
            snoozeCount = snoozeCount,

            // Device context (basic)
            batteryLevel = getBatteryLevel(context),

            // Trigger source
            triggerSource = "USER_ACTION"
        )
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
}
