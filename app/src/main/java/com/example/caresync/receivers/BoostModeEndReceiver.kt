package com.example.caresync.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.caresync.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BoostModeEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminderId", -1L)
        if (reminderId == -1L) return

        Log.d("BOOST_MODE", "⏱️ Boost Mode ended for task $reminderId")

        // ✅ CRITICAL FIX: Use goAsync() to extend receiver lifetime
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ReminderRepository(context)
                val reminder = repo.get(reminderId)

                if (reminder != null) {
                    val updated = reminder.copy(
                        boostModeActive = false,
                        boostModeEndTime = null
                    )
                    repo.upsert(updated)

                    Log.d("BOOST_MODE", "✅ Boost Mode disabled")
                }
            } finally {
                // ✅ CRITICAL: Signal completion to system
                pendingResult.finish()
            }
        }
    }
}
