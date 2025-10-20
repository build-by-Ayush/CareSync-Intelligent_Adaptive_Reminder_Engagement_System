package com.example.caresync.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * State Detector - Detects IDLE and OFF special states
 *
 * States:
 * - OFF: Screen is turned off
 * - IDLE: Screen on but no app activity for 2+ minutes
 * - ACTIVE: Normal app usage
 */
class StateDetector(private val context: Context) {

    private val TAG = "StateDetector"
    private val IDLE_THRESHOLD_MS = 2 * 60 * 1000L // 2 minutes

    private var isScreenOn = true
    private var lastActivityTime = System.currentTimeMillis()
    private var currentState = DeviceState.ACTIVE

    private val handler = Handler(Looper.getMainLooper())
    private var idleCheckRunnable: Runnable? = null

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

        Log.d(TAG, "✅ State detector started")
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
     * Update activity timestamp (call when app activity detected)
     */
    fun updateActivity() {
        lastActivityTime = System.currentTimeMillis()
        if (currentState == DeviceState.IDLE) {
            currentState = DeviceState.ACTIVE
            Log.d(TAG, "🔄 State changed: IDLE → ACTIVE")
        }
    }

    /**
     * Start periodic idle checks
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
     * Check if device is idle
     */
    private fun checkIdleState() {
        if (!isScreenOn) return

        val timeSinceActivity = System.currentTimeMillis() - lastActivityTime

        if (timeSinceActivity >= IDLE_THRESHOLD_MS && currentState != DeviceState.IDLE) {
            currentState = DeviceState.IDLE
            Log.d(TAG, "😴 State changed: ACTIVE → IDLE (${timeSinceActivity / 1000}s idle)")
        }
    }
}

/**
 * Device state enum
 */
enum class DeviceState {
    OFF,      // Screen off
    IDLE,     // Screen on, no activity for 2+ minutes
    ACTIVE    // Normal usage
}
