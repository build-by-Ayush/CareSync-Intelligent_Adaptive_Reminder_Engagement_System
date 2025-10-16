package com.example.caresync.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.EventTypes
import com.example.caresync.scheduler.NotificationSchedulerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SnoozeTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        if (reminderId == -1L) return

        // Default snooze: 10 minutes
        val snoozeDuration = 10

        Log.d("REMINDER_EVENT", "⏰ SNOOZED: Task $reminderId for $snoozeDuration min")

        CoroutineScope(Dispatchers.IO).launch {
            val repo = ReminderRepository(context)
            val eventDao = AppDatabase.get(context).reminderEventDao()

            // ✅ GET CURRENT SNOOZE COUNT
            val currentSnoozeCount = getSnoozeCountForThisNotification(eventDao, reminderId)

            // ✅ CREATE EVENT WITH CONTEXT
            val event = createEventWithContext(
                reminderId = reminderId,
                eventType = EventTypes.SNOOZED,
                context = context,
                snoozeDurationMinutes = snoozeDuration,
                snoozeCount = currentSnoozeCount + 1  // Increment for this snooze
            )

            eventDao.insert(event)
            Log.d("REMINDER_EVENT", "⏰ Logged SNOOZED (count=${currentSnoozeCount + 1})")

            // Reschedule for snooze time
            val reminder = repo.get(reminderId)
            if (reminder != null) {
                val snoozedReminder = reminder.copy(
                    scheduledAtMillis = System.currentTimeMillis() + snoozeDuration * 60 * 1000
                )
                NotificationSchedulerImpl().scheduleReminder(context, snoozedReminder)
            }
        }

        // Cancel current notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(reminderId.toInt())
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
