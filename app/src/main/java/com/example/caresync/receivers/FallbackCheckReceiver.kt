package com.example.caresync.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.caresync.scheduler.workers.FallbackCheckWorker

/**
 * Fallback Check Receiver - Triggered by AlarmManager
 *
 * Responsibilities:
 * - Receives alarm from AlarmManager at end of each time slot
 * - Starts FallbackCheckWorker to check database and fire if needed
 * - Schedules next fallback check alarm
 *
 * Why BroadcastReceiver:
 * - AlarmManager requires BroadcastReceiver (can't use Worker directly)
 * - Receiver then starts Worker to do actual work
 */
class FallbackCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        val slotStart = intent.getLongExtra("slotStart", -1L)
        val slotEnd = intent.getLongExtra("slotEnd", -1L)
        val slotDuration = intent.getLongExtra("slotDuration", -1L)

        if (reminderId == -1L) {
            Log.e(TAG, "No reminderId in intent")
            return
        }

        Log.d(TAG, "⏰ Fallback alarm triggered for task $reminderId")

        // Start FallbackCheckWorker
        val workRequest = OneTimeWorkRequestBuilder<FallbackCheckWorker>()
            .setInputData(
                workDataOf(
                    "reminderId" to reminderId,
                    "slotStart" to slotStart,
                    "slotEnd" to slotEnd
                )
            )
            .addTag("fallback-check-$reminderId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)

        // Schedule next fallback check alarm
        if (slotDuration > 0) {
            scheduleNextFallbackAlarm(context, reminderId, slotEnd, slotDuration)
        }
    }

    /**
     * Schedule next fallback check alarm
     */
    /**
     * Schedule next fallback check alarm
     */
    private fun scheduleNextFallbackAlarm(
        context: Context,
        reminderId: Long,
        lastSlotEnd: Long,
        slotDuration: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if we can schedule exact alarms (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "⚠️ Cannot schedule exact alarms - permission not granted")
                // Fallback to inexact alarm
                scheduleInexactAlarm(context, alarmManager, reminderId, lastSlotEnd, slotDuration)
                return
            }
        }

        val nextSlotStart = lastSlotEnd
        val nextSlotEnd = lastSlotEnd + slotDuration

        val intent = Intent(context, FallbackCheckReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
            putExtra("slotStart", nextSlotStart)
            putExtra("slotEnd", nextSlotEnd)
            putExtra("slotDuration", slotDuration)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            // Use API-appropriate method
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0+ (API 23+): Use setExactAndAllowWhileIdle
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextSlotEnd,
                    pendingIntent
                )
            } else {
                // Android 5.0-5.1 (API 21-22): Use setExact
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextSlotEnd,
                    pendingIntent
                )
            }

            Log.d(TAG, "📅 Next fallback alarm scheduled for ${formatTime(nextSlotEnd)}")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException when scheduling alarm", e)
            // Fallback to inexact alarm
            scheduleInexactAlarm(context, alarmManager, reminderId, lastSlotEnd, slotDuration)
        }
    }

    /**
     * Fallback to inexact alarm if exact alarms not allowed
     */
    private fun scheduleInexactAlarm(
        context: Context,
        alarmManager: AlarmManager,
        reminderId: Long,
        lastSlotEnd: Long,
        slotDuration: Long
    ) {
        val nextSlotStart = lastSlotEnd
        val nextSlotEnd = lastSlotEnd + slotDuration

        val intent = Intent(context, FallbackCheckReceiver::class.java).apply {
            putExtra("reminderId", reminderId)
            putExtra("slotStart", nextSlotStart)
            putExtra("slotEnd", nextSlotEnd)
            putExtra("slotDuration", slotDuration)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Use setWindow for inexact alarm (allows system to batch)
        val windowStart = nextSlotEnd
        val windowEnd = nextSlotEnd + (60 * 1000L)  // 1-minute window

        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            windowStart,
            windowEnd - windowStart,
            pendingIntent
        )

        Log.d(TAG, "📅 Inexact alarm scheduled (window: ${formatTime(windowStart)} - ${formatTime(windowEnd)})")
    }

    private fun formatTime(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    companion object {
        private const val TAG = "FallbackCheckReceiver"
    }
}
