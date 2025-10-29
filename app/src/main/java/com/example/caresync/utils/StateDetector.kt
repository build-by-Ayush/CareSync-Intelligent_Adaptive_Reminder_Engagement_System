package com.example.caresync.utils

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * State Detector - Detects IDLE and OFF special states
 *
 * States:
 * - OFF: Screen is turned off
 * - IDLE: Screen on but no app activity for 2+ minutes (system-wide)
 * - ACTIVE: Normal app usage
 *
 * ✅ Enhanced: Now tracks system-wide app usage, not just CareSync
 */
class StateDetector(private val context: Context) {

    private val TAG = "StateDetector"
    private val IDLE_THRESHOLD_MS = 2 * 60 * 1000L // 2 minutes

    private var isScreenOn = true
    private var lastActivityTime = System.currentTimeMillis()
    private var currentState = DeviceState.ACTIVE

    private val handler = Handler(Looper.getMainLooper())
    private var idleCheckRunnable: Runnable? = null

    // Known launcher package names (home screen apps)
    private val launcherPackages = setOf(
        "com.google.android.apps.nexuslauncher", // Pixel Launcher
        "com.android.launcher3",                  // Stock Android
        "com.samsung.android.app.launcher",       // Samsung
        "com.miui.home",                          // Xiaomi
        "com.huawei.android.launcher",            // Huawei
        "com.oppo.launcher",                      // Oppo
        "com.vivo.launcher"                       // Vivo
    )

    // Screen on/off receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    currentState = DeviceState.OFF
                    Log.d(TAG, "📴 Screen OFF")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    currentState = DeviceState.ACTIVE
                    lastActivityTime = System.currentTimeMillis()
                    startIdleChecks()
                    Log.d(TAG, "📱 Screen ON")
                }
            }
        }
    }

    /**
     * Start monitoring
     */
    fun start() {
        // Register screen on/off receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenReceiver, filter)

        // Check initial screen state
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = powerManager.isInteractive

        // Start idle checks
        if (isScreenOn) {
            startIdleChecks()
        }

        Log.d(TAG, "✅ State detector started (system-wide tracking)")
    }

    /**
     * Stop monitoring
     */
    fun stop() {
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered")
        }

        idleCheckRunnable?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "⏹️ State detector stopped")
    }

    /**
     * Get current device state
     */
    fun getCurrentState(): DeviceState {
        return currentState
    }

    /**
     * ✅ KEPT: Update activity timestamp (manual override for CareSync UI interactions)
     * This allows immediate state updates when user interacts with CareSync
     */
    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
        if (currentState == DeviceState.IDLE) {
            currentState = DeviceState.ACTIVE
            Log.d(TAG, "🔄 State changed: IDLE → ACTIVE (manual update)")
        }
    }

    /**
     * Start periodic idle checks
     * ✅ ENHANCED: Now checks system-wide app usage
     */
    private fun startIdleChecks() {
        idleCheckRunnable?.let { handler.removeCallbacks(it) }

        idleCheckRunnable = object : Runnable {
            override fun run() {
                checkIdleState()
                handler.postDelayed(this, 10_000) // Check every 10 seconds
            }
        }

        handler.post(idleCheckRunnable!!)
    }

    /**
     * ✅ ENHANCED: Check if device is idle (system-wide)
     * Now tracks all app usage, not just CareSync
     */
    private fun checkIdleState() {
        if (!isScreenOn) return

        // ✅ NEW: Check foreground app
        val foregroundApp = getForegroundApp()

        // If any app is open (not launcher/null), user is active
        if (foregroundApp != null && !isLauncher(foregroundApp)) {
            lastActivityTime = System.currentTimeMillis()
            if (currentState == DeviceState.IDLE) {
                currentState = DeviceState.ACTIVE
                Log.d(TAG, "🔄 State changed: IDLE → ACTIVE (app: ${getAppName(foregroundApp)})")
            }
        }

        // Check if enough time has passed for idle state
        val timeSinceActivity = System.currentTimeMillis() - lastActivityTime

        if (timeSinceActivity >= IDLE_THRESHOLD_MS && currentState != DeviceState.IDLE) {
            currentState = DeviceState.IDLE
            Log.d(TAG, "😴 State changed: ACTIVE → IDLE (${timeSinceActivity / 1000}s idle)")
        }
    }

    /**
     * ✅ NEW: Get current foreground app package name
     */
    private fun getForegroundApp(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val time = System.currentTimeMillis()
                val stats = usm?.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    time - 10000, // Last 10 seconds
                    time
                ) ?: return null

                stats.maxByOrNull { it.lastTimeUsed }?.packageName
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app", e)
            null
        }
    }

    /**
     * ✅ NEW: Check if package is a launcher (home screen)
     */
    private fun isLauncher(packageName: String): Boolean {
        return launcherPackages.contains(packageName)
    }

    /**
     * ✅ NEW: Get app name for logging (helper)
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName // Fallback to package name
        }
    }
}

/**
 * Device state enum
 */
enum class DeviceState {
    OFF,    // Screen off
    IDLE,   // Screen on, no app activity for 2+ minutes
    ACTIVE  // Normal usage
}
