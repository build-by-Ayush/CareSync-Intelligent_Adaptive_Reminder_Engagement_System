package com.example.caresync.utils

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderEventEntity
import com.example.caresync.domain.EventTypes
import java.util.Calendar

/**
 * Safe event logging that handles deleted tasks gracefully
 * Prevents crashes from foreign key constraints
 */
object SafeEventLogger {
    private const val TAG = "SAFE_LOGGER"

    /**
     * Log event with full error handling
     * Returns true if successful, false if task was deleted
     */
    suspend fun logEvent(
        context: Context,
        reminderId: Long,
        eventType: String,
        responseTimeMillis: Long? = null,
        snoozeDurationMinutes: Int? = null,
        snoozeCount: Int = 0,
        metadataJson: String? = null
    ): Boolean {
        return try {
            // First check if task still exists
            val db = AppDatabase.get(context)
            val taskExists = db.reminderDao().getById(reminderId) != null

            if (!taskExists) {
                Log.w(TAG, "⚠️ Task $reminderId no longer exists, skipping $eventType event")
                return false
            }

            // Task exists, safe to log
            val now = Calendar.getInstance()
            val event = ReminderEventEntity(
                reminderId = reminderId,
                eventType = eventType,
                timestamp = System.currentTimeMillis(),

                // Time context
                hourOfDay = now.get(Calendar.HOUR_OF_DAY),
                dayOfWeek = now.get(Calendar.DAY_OF_WEEK) - 1,
                isWeekend = now.get(Calendar.DAY_OF_WEEK) in listOf(
                    Calendar.SATURDAY,
                    Calendar.SUNDAY
                ),

                // User behavior
                responseTimeMillis = responseTimeMillis,
                snoozeDurationMinutes = snoozeDurationMinutes,
                snoozeCount = snoozeCount,

                // Device context
                batteryLevel = getBatteryLevel(context),

                // Metadata
                metadataJson = metadataJson,
                triggerSource = "USER_ACTION"
            )

            db.reminderEventDao().insert(event)
            Log.d(TAG, "✅ Logged $eventType for task $reminderId")
            true

        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "⚠️ Foreign key error for task $reminderId: Task was just deleted")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log $eventType for task $reminderId", e)
            false
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
}
