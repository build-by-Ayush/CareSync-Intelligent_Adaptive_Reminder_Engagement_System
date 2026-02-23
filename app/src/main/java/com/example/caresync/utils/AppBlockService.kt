package com.example.caresync.utils

import android.app.ActivityManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.example.caresync.ui.components.BlockScreenActivity

class AppBlockService : Service() {
    private var running = false
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 1000
    private var lastBlockedApp: String? = null
    private var lastBlockTime = 0L // ⏱ prevent rapid re-launch spam

    private val appCheckRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            if (!AppBlockManager.hasBlockedApps()) {
                Log.d("APP_BLOCK_SERVICE", "✅ No blocked apps, stopping service")
                stopSelf()
                return
            }

            val foregroundApp = getForegroundApp()

            if (foregroundApp == null) {
                if (lastBlockedApp != null) {
                    Log.d("APP_BLOCK_SERVICE", "🏠 Foreground null, resetting from $lastBlockedApp")
                }
                lastBlockedApp = null
                handler.postDelayed(this, checkInterval)
                return
            }

            Log.d("APP_BLOCK_SERVICE", "📱 Foreground: $foregroundApp")

            val isBlocked = AppBlockManager.isBlocked(foregroundApp)
            val isLauncher = isLauncherPackage(foregroundApp)

            if (isBlocked) {
                if (foregroundApp != lastBlockedApp) {
                    Log.d("APP_BLOCK_SERVICE", "🚫 BLOCKED APP DETECTED: $foregroundApp")
                    lastBlockedApp = foregroundApp
                    launchBlockScreen(foregroundApp)
                }
            } else {
                if (isLauncher || foregroundApp != lastBlockedApp) {
                    if (lastBlockedApp != null) {
                        Log.d("APP_BLOCK_SERVICE", "🏠 Switched away from ${lastBlockedApp}, reset")
                    }
                    lastBlockedApp = null
                }
            }

            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        Log.d("APP_BLOCK_SERVICE", "🚀 AppBlockService started")
        handler.post(appCheckRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        lastBlockedApp = null
        handler.removeCallbacks(appCheckRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * ✅ Robust launcher that works for YouTube/Chrome and handles old APIs
     */
    private fun launchBlockScreen(packageName: String) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTime < 2500) { // 2.5 sec cooldown
            Log.d("APP_BLOCK_SERVICE", "⏳ Skipping duplicate launch (cooldown active)")
            return
        }
        lastBlockTime = now

        val intent = Intent(this, BlockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra("packageName", packageName)
            putExtra("remaining", AppBlockManager.getRemainingMinutes(packageName))
        }

        try {
            if (canDrawOverlaysCompat(this)) {
                startActivity(intent)
                Log.d("APP_BLOCK_SERVICE", "✅ BlockScreenActivity launched successfully (overlay allowed)")
            } else {
                Log.w("APP_BLOCK_SERVICE", "⚠️ Overlay permission missing — requesting it.")
                requestOverlayPermission()
            }
        } catch (e: Exception) {
            Log.e("APP_BLOCK_SERVICE", "❌ Failed to launch BlockScreenActivity: ${e.message}", e)
        }
    }

    /**
     * ✅ Overlay permission checker for API 21–34+
     */
    private fun canDrawOverlaysCompat(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true // API < 23 always allowed
        } else {
            Settings.canDrawOverlays(context)
        }
    }

    /**
     * ✅ Requests overlay permission if missing
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("APP_BLOCK_SERVICE", "❌ Failed to request overlay permission: ${e.message}")
            }
        }
    }

    private fun getForegroundApp(): String? {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = activityManager?.runningAppProcesses

            if (!processes.isNullOrEmpty()) {
                val foregroundProcess = processes.find {
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
                if (foregroundProcess != null) {
                    val packageName = foregroundProcess.processName.split(":")[0]

                    // ✅ NEW: Skip logging your own app
                    if (packageName != "com.example.caresync") {
                        Log.d("APP_BLOCK_SERVICE", "📱 Foreground: $packageName")
                    }

                    return packageName
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val time = System.currentTimeMillis()
                val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000, time)
                val packageName = stats?.maxByOrNull { it.lastTimeUsed }?.packageName

                // ✅ NEW: Skip logging your own app
                if (packageName != null && packageName != "com.example.caresync") {
                    Log.d("APP_BLOCK_SERVICE", "📱 Foreground: $packageName")
                }

                return packageName
            }
            null
        } catch (e: Exception) {
            Log.e("APP_BLOCK_SERVICE", "Error: ${e.message}")
            null
        }
    }


    // ✅ Detects common launchers
    private fun isLauncherPackage(pkg: String): Boolean {
        return pkg.contains("launcher", ignoreCase = true) ||
                pkg.contains("home", ignoreCase = true) ||
                pkg == "com.google.android.apps.nexuslauncher" ||
                pkg == "com.miui.home" ||
                pkg == "com.sec.android.app.launcher" ||
                pkg == "com.oppo.launcher" ||
                pkg == "com.oneplus.launcher"
    }
}
