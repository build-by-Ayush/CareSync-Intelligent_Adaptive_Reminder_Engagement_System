package com.example.caresync.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.caresync.scheduler.ReminderWorker

class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        if (reminderId == -1L) return

        Log.d("SNOOZE_ALARM", "⏰ Snooze alarm triggered for task $reminderId")

        // Trigger ReminderWorker immediately with isSnooze flag
        val snoozeWork = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(
                workDataOf(
                    "reminderId" to reminderId,
                    "isSnooze" to true
                )
            )
            .addTag("snooze-immediate-$reminderId")
            .build()

        WorkManager.getInstance(context).enqueue(snoozeWork)

        Log.d("SNOOZE_ALARM", "✅ Triggered immediate snooze notification work")
    }
}
