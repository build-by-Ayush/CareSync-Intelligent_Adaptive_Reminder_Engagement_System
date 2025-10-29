package com.example.caresync.utils

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.caresync.ui.components.BlockScreenActivity

class AppBlockService : Service() {
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 2000

    private val appCheckRunnable = object : Runnable {
        override fun run() {
            if (!running) {
                Log.d("APP_BLOCK_SERVICE", "❌ Not running, stopping checks")
                return
            }

            // ✅ CHECK: If no apps are blocked, stop the service
            if (!AppBlockManager.hasBlockedApps()) {
                Log.d("APP_BLOCK_SERVICE", "✅ No blocked apps, stopping service")
                stopSelf()
                return
            }

            val foregroundApp = getForegroundApp(this@AppBlockService)

            if (foregroundApp != null && AppBlockManager.isBlocked(foregroundApp)) {
                Log.d("APP_BLOCK_SERVICE", "🚫 BLOCKED APP DETECTED: $foregroundApp")
                val intent = Intent(this@AppBlockService, BlockScreenActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("packageName", foregroundApp)
                    putExtra("remaining", AppBlockManager.getRemainingMinutes(foregroundApp))
                }
                startActivity(intent)
            }

            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        handler.post(appCheckRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(appCheckRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getForegroundApp(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val time = System.currentTimeMillis()
                val stats = usm?.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 10000,
                    time
                ) ?: return null
                stats.maxByOrNull { it.lastTimeUsed }?.packageName
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("APP_BLOCK_SERVICE", "Error getting foreground app", e)
            null
        }
    }
}
