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
        private const val BOOST_END_REQUEST_CODE_OFFSET = 100000

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

            // ✅ FIX #2: Validate frequency before scheduling
            if (frequencyPerHour <= 0) {
                Log.e(TAG, "❌ Invalid frequency: $frequencyPerHour (must be > 0)")
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // ✅ Check permission for exact alarms (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "❌ Cannot schedule exact alarms - permission denied")
                    throw SecurityException("SCHEDULE_EXACT_ALARM permission not granted")
                }
            }

            // ✅ FIX #2: Calculate expected alarm count and cap at 100
            val expectedAlarmCount = durationHours * frequencyPerHour
            if (expectedAlarmCount > 100) {
                Log.w(TAG, "⚠️ Too many boost alarms: $expectedAlarmCount (capping at 100)")
            }
            val maxAlarms = minOf(expectedAlarmCount, 100)

            val intervalMillis = (60 * 60 * 1000L) / frequencyPerHour
            val endTime = System.currentTimeMillis() + (durationHours * 60 * 60 * 1000L)

            var nextAlarmTime = System.currentTimeMillis() + intervalMillis
            var alarmCount = 0

            // ✅ FIX #2: Use calculated maxAlarms instead of hard-coded 1000
            while (nextAlarmTime <= endTime && alarmCount < maxAlarms) {
                val intent = Intent(context, FallbackCheckReceiver::class.java).apply {
                    putExtra("reminderId", reminder.id)
                    putExtra("slotStart", nextAlarmTime - 10000L)
                    putExtra("slotEnd", nextAlarmTime)
                    putExtra("isBoostMode", true)
                    putExtra("boostAlarmId", alarmCount)
                }

                // ✅ FIX #1: Fixed requestCode collision - spread reminder.id ranges
                val requestCode = BOOST_REQUEST_CODE_OFFSET + (reminder.id * 10000).toInt() + alarmCount

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

            // ✅ Store scheduled count for later cleanup
            saveScheduledBoostCount(context, reminder.id, alarmCount)

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

            // ✅ FIX #3: Only loop through scheduled alarms, not all 1001
            val scheduledCount = getScheduledBoostCount(context, reminderId)
            Log.d(TAG, "Cleaning up $scheduledCount boost alarms...")

            for (i in 0 until scheduledCount) {
                val intent = Intent(context, FallbackCheckReceiver::class.java)
                // ✅ Use same calculation as startBoostMode
                val requestCode = BOOST_REQUEST_CODE_OFFSET + (reminderId * 10000).toInt() + i

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )

                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                    Log.d(TAG, "   Cancelled alarm $i")
                }
            }

            // Cancel auto-disable alarm
            val endIntent = Intent(context, com.example.caresync.receivers.BoostModeEndReceiver::class.java)
            // ✅ FIX #1: Use separate offset for end receiver
            val endRequestCode = BOOST_END_REQUEST_CODE_OFFSET + reminderId.toInt()
            val endPendingIntent = PendingIntent.getBroadcast(
                context,
                endRequestCode,
                endIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            endPendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "   Cancelled auto-disable alarm")
            }

            // Clean up stored count
            deleteScheduledBoostCount(context, reminderId)

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

            // ✅ FIX #1: Use separate offset for end receiver to avoid collision
            val requestCode = BOOST_END_REQUEST_CODE_OFFSET + reminderId.toInt()

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

        /**
         * ✅ FIX #3: Store scheduled boost count in SharedPreferences
         */
        private fun saveScheduledBoostCount(context: Context, reminderId: Long, count: Int) {
            val prefs = context.getSharedPreferences("boost_mode", Context.MODE_PRIVATE)
            prefs.edit().putInt("boost_count_$reminderId", count).apply()
        }

        /**
         * ✅ FIX #3: Retrieve scheduled boost count
         */
        private fun getScheduledBoostCount(context: Context, reminderId: Long): Int {
            val prefs = context.getSharedPreferences("boost_mode", Context.MODE_PRIVATE)
            return prefs.getInt("boost_count_$reminderId", 0)
        }

        /**
         * ✅ FIX #3: Clean up stored count
         */
        private fun deleteScheduledBoostCount(context: Context, reminderId: Long) {
            val prefs = context.getSharedPreferences("boost_mode", Context.MODE_PRIVATE)
            prefs.edit().remove("boost_count_$reminderId").apply()
        }
    }
}
