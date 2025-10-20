package com.example.caresync

import android.app.Application
import android.util.Log
import com.example.caresync.utils.CategoryMapper
import com.example.caresync.utils.StateDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class - Runs once when app starts
 *
 * Responsibilities:
 * - Initialize CategoryMapper (load 15,600+ app mapping)
 * - Start StateDetector (monitor IDLE/OFF states)
 * - Provide global access to these utilities
 */
class CareSyncApplication : Application() {

    private val TAG = "CareSyncApp"

    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // StateDetector instance (accessible from anywhere in app)
    lateinit var stateDetector: StateDetector
        private set

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "🚀 Application starting...")

        // Initialize CategoryMapper (background thread)
        applicationScope.launch(Dispatchers.IO) {
            CategoryMapper.initialize(applicationContext)
        }

        // Start StateDetector (monitors IDLE/OFF)
        stateDetector = StateDetector(this)
        stateDetector.start()

        Log.d(TAG, "✅ Application initialized")
    }

    override fun onTerminate() {
        super.onTerminate()

        // Stop state detector
        stateDetector.stop()

        Log.d(TAG, "⏹️ Application terminated")
    }
}
