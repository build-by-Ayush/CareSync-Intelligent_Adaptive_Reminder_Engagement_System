package com.example.caresync.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.receivers.FallbackCheckReceiver

class BoostModeScheduler {

    companion object {
        private const val TAG = "BOOST_SCHEDULER"
        private const val BOOST_REQUEST_CODE_OFFSET = 50000

        /**
         * Start Boost Mode - ADDS extra notifications on top of existing schedule
         */
        fun startBoostMode(
            context: Context,
            reminder: ReminderSettings,
            durationHours: Int,
            frequencyPerHour: Int
        ) {
            Log.d(TAG, "🚀 Starting Boost Mode: ${reminder.title}")
            Log.d(TAG, "   Duration: $durationHours hours")
            Log.d(TAG, "   Frequency: $frequencyPerHour per hour")
            Log.d(TAG, "   ✅ Normal reminders CONTINUE running (boost is additional)")

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // ✅ Check permission for exact alarms (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "❌ Cannot schedule exact alarms - permission denied")
                    throw SecurityException("SCHEDULE_EXACT_ALARM permission not granted")
                }
            }

            val intervalMillis = (60 * 60 * 1000L) / frequencyPerHour
            val endTime = System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)

            var nextAlarmTime = System.currentTimeMillis() + intervalMillis
            var alarmCount = 0

            while (nextAlarmTime <= endTime && alarmCount < 1000) {
                val intent = Intent(context, FallbackCheckReceiver::class.java).apply {
                    putExtra("reminderId", reminder.id)
                    putExtra("slotStart", nextAlarmTime - 10000L)
                    putExtra("slotEnd", nextAlarmTime)
                    putExtra("isBoostMode", true)
                    putExtra("boostAlarmId", alarmCount)
                }

                val requestCode = BOOST_REQUEST_CODE_OFFSET + (reminder.id * 1000).toInt() + alarmCount

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    // ✅ API level check for setExactAndAllowWhileIdle
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextAlarmTime,
                            pendingIntent
                        )
                    } else {
                        // Fallback for API 21-22
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            nextAlarmTime,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "❌ SecurityException scheduling alarm", e)
                    break
                }

                alarmCount++
                nextAlarmTime += intervalMillis
            }

            scheduleBoostModeEnd(context, reminder.id, endTime, alarmManager)

            Log.d(TAG, "✅ Scheduled $alarmCount ADDITIONAL boost notifications")
            Log.d(TAG, "   Total = Normal schedule + $alarmCount boost notifications")
        }

        /**
         * Stop Boost Mode - cancel ONLY boost alarms
         */
        fun stopBoostMode(context: Context, reminderId: Long) {
            Log.d(TAG, "🛑 Stopping Boost Mode for task $reminderId")
            Log.d(TAG, "   ✅ Normal reminders will CONTINUE working")

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            for (i in 0..1000) {
                val intent = Intent(context, FallbackCheckReceiver::class.java)
                val requestCode = BOOST_REQUEST_CODE_OFFSET + (reminderId * 1000).toInt() + i

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )

                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }

            // Cancel auto-disable alarm
            val endIntent = Intent(context, com.example.caresync.receivers.BoostModeEndReceiver::class.java)
            val endRequestCode = BOOST_REQUEST_CODE_OFFSET + reminderId.toInt()
            val endPendingIntent = PendingIntent.getBroadcast(
                context,
                endRequestCode,
                endIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            endPendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }

            Log.d(TAG, "✅ Boost Mode stopped (normal reminders unaffected)")
        }

        /**
         * Schedule automatic disable at end time
         */
        private fun scheduleBoostModeEnd(
            context: Context,
            reminderId: Long,
            endTime: Long,
            alarmManager: AlarmManager
        ) {
            val intent = Intent(context, com.example.caresync.receivers.BoostModeEndReceiver::class.java).apply {
                putExtra("reminderId", reminderId)
            }

            val requestCode = BOOST_REQUEST_CODE_OFFSET + reminderId.toInt()

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                // ✅ API level check
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        endTime,
                        pendingIntent
                    )
                }

                val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                Log.d(TAG, "⏰ Scheduled boost auto-disable at ${formatter.format(endTime)}")

            } catch (e: SecurityException) {
                Log.e(TAG, "❌ Failed to schedule auto-disable", e)
            }
        }
    }
}
