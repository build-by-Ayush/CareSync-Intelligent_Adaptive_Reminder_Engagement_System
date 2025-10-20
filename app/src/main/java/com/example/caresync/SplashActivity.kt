package com.example.caresync

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.example.caresync.data.ProfileDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

    private var hasNavigated = false
    private var isRequestingPermission = false
    private var lastPermissionRequest = ""

    companion object {
        private const val TAG = "SPLASH"
        private const val REQUEST_NOTIFICATION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "🚀 Splash screen created")

        val profileDataStore = ProfileDataStore(this)

        // ✅ SAME APPROACH AS WELCOMEPAGE - Check BEFORE setContent
        lifecycleScope.launch {
            val permissionsGranted = profileDataStore.arePermissionsGranted.first()

            if (permissionsGranted) {
                // ✅ Navigate immediately WITHOUT showing UI
                Log.d(TAG, "✅ Permissions already granted, navigating directly")
                navigateToMain()
            } else {
                // ✅ First time - show UI and request permissions
                Log.d(TAG, "📋 First time, showing splash and requesting permissions")

                setContent {
                    SplashScreen()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestNextPermission()
                }, 500)
            }
        }
    }

    @Composable
    fun SplashScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CareSync",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF560154)
                )

                Spacer(modifier = Modifier.height(24.dp))

                CircularProgressIndicator(
                    color = Color(0xFF560154),
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Setting up permissions...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }

    private fun checkAndRequestNextPermission() {
        if (hasNavigated || isRequestingPermission) {
            Log.d(TAG, "⚠️ Already navigated or requesting, ignoring")
            return
        }

        val notificationGranted = isNotificationPermissionGranted()
        val alarmGranted = isExactAlarmPermissionGranted()
        val usageStatsGranted = isUsageStatsPermissionGranted()

        Log.d(TAG, "📋 Checking permissions:")
        Log.d(TAG, "   Notification: $notificationGranted")
        Log.d(TAG, "   Exact Alarm: $alarmGranted")
        Log.d(TAG, "   Usage Stats: $usageStatsGranted")

        when {
            !notificationGranted && lastPermissionRequest != "NOTIFICATION" -> {
                lastPermissionRequest = "NOTIFICATION"
                requestNotificationPermission()
            }
            !alarmGranted && lastPermissionRequest != "ALARM" -> {
                lastPermissionRequest = "ALARM"
                requestExactAlarmPermission()
            }
            !usageStatsGranted && lastPermissionRequest != "USAGE_STATS" -> {
                lastPermissionRequest = "USAGE_STATS"
                requestUsageStatsPermission()
            }
            else -> {
                savePermissionsAndNavigate()
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "🔔 Requesting notification permission...")
        isRequestingPermission = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        } else {
            isRequestingPermission = false
            lastPermissionRequest = ""
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 300)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "📋 Permission result received")
        isRequestingPermission = false
        lastPermissionRequest = ""

        Handler(Looper.getMainLooper()).postDelayed({
            checkAndRequestNextPermission()
        }, 500)
    }

    private fun isExactAlarmPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking alarm permission", e)
                true
            }
        } else {
            true
        }
    }

    private fun requestExactAlarmPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "⏰ Requesting exact alarm permission...")
        isRequestingPermission = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open alarm settings", e)
                isRequestingPermission = false
                lastPermissionRequest = ""
                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestNextPermission()
                }, 300)
            }
        } else {
            isRequestingPermission = false
            lastPermissionRequest = ""
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 300)
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                Log.e(TAG, "Error checking usage stats", e)
                false
            }
        } else {
            true
        }
    }

    private fun requestUsageStatsPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "📊 Requesting usage stats permission...")
        isRequestingPermission = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open usage stats settings", e)
                isRequestingPermission = false
                savePermissionsAndNavigate()
            }
        } else {
            isRequestingPermission = false
            savePermissionsAndNavigate()
        }
    }

    override fun onResume() {
        super.onResume()

        Log.d(TAG, "📱 Activity resumed")

        if (isRequestingPermission && !hasNavigated) {
            isRequestingPermission = false
            lastPermissionRequest = ""

            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 1000)
        }
    }

    private fun savePermissionsAndNavigate() {
        if (hasNavigated) return

        Log.d(TAG, "✅ All permissions granted! Saving...")
        hasNavigated = true

        lifecycleScope.launch {
            val profileDataStore = ProfileDataStore(this@SplashActivity)
            profileDataStore.setPermissionsGranted()

            Handler(Looper.getMainLooper()).postDelayed({
                navigateToMain()
            }, 500)
        }
    }

    private fun navigateToMain() {
        if (!hasNavigated) {
            hasNavigated = true
        }

        Log.d(TAG, "🎯 Navigating to MainActivity...")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }
}
