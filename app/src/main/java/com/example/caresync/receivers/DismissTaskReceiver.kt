package com.example.caresync.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.domain.EventTypes
import com.example.caresync.utils.SafeEventLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DismissTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        if (reminderId == -1L) return

        Log.d("REMINDER_EVENT", "✕ DISMISSED: Task $reminderId")

        CoroutineScope(Dispatchers.IO).launch {
            val eventDao = AppDatabase.get(context).reminderEventDao()
            val blacklistDao = AppDatabase.get(context).blacklistHourDao()

            // Get snooze count
            val snoozeCount = try {
                val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
                val events = eventDao.getEventsBetween(reminderId, oneHourAgo, System.currentTimeMillis())
                val lastTriggeredIndex = events.indexOfLast { it.eventType == EventTypes.TRIGGERED }
                if (lastTriggeredIndex >= 0) {
                    events.drop(lastTriggeredIndex + 1).count { it.eventType == EventTypes.SNOOZED }
                } else 0
            } catch (e: Exception) {
                0
            }

            // ✅ SAFE LOGGING
            val success = SafeEventLogger.logEvent(
                context = context,
                reminderId = reminderId,
                eventType = EventTypes.DISMISSED,
                snoozeCount = snoozeCount
            )

            if (success) {
                // Only update blacklist if event was logged (task still exists)
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                updateBlacklist(blacklistDao, reminderId, currentHour, System.currentTimeMillis())
            }
        }

        // Cancel notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId.toInt())
    }

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
    /**
     * Update blacklist tracking for this hour
     */
    private suspend fun updateBlacklist(
        blacklistDao: com.example.caresync.data.BlacklistHourDao,
        reminderId: Long,
        hourOfDay: Int,
        timestamp: Long
    ) {
        try {
            val existing = blacklistDao.getBlacklist(reminderId, hourOfDay)

            if (existing == null) {
                // First dismissal at this hour
                blacklistDao.insert(
                    com.example.caresync.data.BlacklistHour(
                        reminderId = reminderId,
                        hourOfDay = hourOfDay,
                        dismissalCount = 1,
                        lastDismissalTimestamp = timestamp
                    )
                )
                Log.d("BLACKLIST", "📝 Started tracking hour $hourOfDay for task $reminderId (count: 1)")
            } else {
                // Increment existing
                val updatedCount = blacklistDao.incrementDismissal(reminderId, hourOfDay, timestamp)
                val newCount = existing.dismissalCount + 1

                if (newCount >= 5) {
                    Log.w("BLACKLIST", "⚠️ Hour $hourOfDay BLACKLISTED for task $reminderId ($newCount dismissals)")
                } else {
                    Log.d("BLACKLIST", "📝 Updated hour $hourOfDay for task $reminderId (count: $newCount)")
                }
            }
        } catch (e: Exception) {
            Log.e("BLACKLIST", "Failed to update blacklist", e)
        }
    }

}
