package com.example.caresync.utils

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.ReminderEventEntity
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import java.util.Calendar

/**
 * Safe event logging that handles deleted tasks gracefully
 * Prevents crashes from foreign key constraints
 */
object SafeEventLogger {
    private const val TAG = "SAFE_LOGGER"
    private const val BATTERY_CACHE_DURATION_MS = 60 * 1000L

    private var cachedBatteryLevel: Int? = null
    private var lastBatteryCheckTime: Long = 0

    /**
     * Log event with full error handling
     * Returns true if successful, false if task was deleted
     */
    suspend fun logEvent(
        context: Context,
        reminderId: Long,
        eventType: String,
        toneUsed: String? = null,
        responseTimeMillis: Long? = null,
        snoozeDurationMinutes: Int? = null,
        snoozeCount: Int = 0,
        metadataJson: String? = null
    ): Boolean {
        return try {
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

                // ✅ Tone tracking
                toneUsed = toneUsed,

                // Metadata
                metadataJson = metadataJson,
                triggerSource = "USER_ACTION"
            )

            // ✅ FIXED: Just try to insert and let database handle constraints
            val db = AppDatabase.get(context)
            db.reminderEventDao().insert(event)

            Log.d(TAG, "✅ Logged $eventType for task $reminderId (tone: $toneUsed)")
            true

        } catch (e: SQLiteConstraintException) {
            // Task was deleted - foreign key constraint violated
            Log.w(TAG, "⚠️ Foreign key error for task $reminderId: Task was just deleted")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to log $eventType for task $reminderId", e)
            false
        }
    }

    /**
     * ✅ OPTIMIZED: Get current battery level with caching
     */
    private fun getBatteryLevel(context: Context): Int? {
        val now = System.currentTimeMillis()

        // Return cached value if still fresh
        if (now - lastBatteryCheckTime < BATTERY_CACHE_DURATION_MS && cachedBatteryLevel != null) {
            return cachedBatteryLevel
        }

        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            val result = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else null

            // Cache the result
            cachedBatteryLevel = result
            lastBatteryCheckTime = now

            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get battery level", e)
            null
        }
    }
}
