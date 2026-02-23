package com.example.caresync

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.runtime.Composable
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

/**
 * Splash Activity - Permission Flow
 *
 * Responsibilities:
 * - Block navigation until ALL permissions are granted
 * - Request permissions in sequence
 * - Save permission state for next launch
 * - Navigate to MainActivity only when done
 *
 * Thread-safe: Uses @Volatile flags and synchronized blocks
 */
class SplashActivity : ComponentActivity() {

    // ✅ THREAD-SAFE: Volatile flags prevent race conditions
    @Volatile
    private var hasNavigated = false

    @Volatile
    private var isRequestingPermission = false

    private var lastPermissionRequest = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "🚀 Splash screen created")

        // Check if permissions were previously granted
        val profileDataStore = ProfileDataStore(this)
        lifecycleScope.launch {
            val permissionsGranted = profileDataStore.arePermissionsGranted.first()

            if (permissionsGranted) {
                Log.d(TAG, "✅ Permissions already granted, navigating directly")
                navigateToMain()
            } else {
                Log.d(TAG, "⏳ First launch - requesting permissions")
                setContent {
                    SplashScreen()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestNextPermission()
                }, 500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "📱 Activity resumed")

        // ✅ FIXED: Check if we were waiting for a permission dialog to close
        if (isRequestingPermission && !hasNavigated) {
            isRequestingPermission = false
            lastPermissionRequest = ""

            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 1000)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d(TAG, "📋 onRequestPermissionsResult: requestCode=$requestCode")

        when (requestCode) {
            REQUEST_NOTIFICATION, REQUEST_CONTACTS -> {
                isRequestingPermission = false
                lastPermissionRequest = ""

                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestNextPermission()
                }, 500)
            }
        }
    }

    // ========================================
    // PERMISSION CHECKING METHODS
    // ========================================

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true  // Older APIs don't need this permission
        }
    }

    private fun isContactsPermissionGranted(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isExactAlarmPermissionGranted(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true  // Older APIs don't need this permission
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking exact alarm permission", e)
            true  // Fail open for older APIs
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } else {
                false  // Older APIs don't have this
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats permission", e)
            false
        }
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true  // Older APIs don't need this permission
        }
    }

    // ========================================
    // PERMISSION REQUEST METHODS
    // ========================================

    private fun requestNotificationPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "📱 Requesting notification permission...")
        isRequestingPermission = true
        lastPermissionRequest = "NOTIFICATION"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        } else {
            // Older APIs - skip this permission
            isRequestingPermission = false
            lastPermissionRequest = ""
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 300)
        }
    }

    private fun requestContactsPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "📱 Requesting contacts permission...")
        isRequestingPermission = true
        lastPermissionRequest = "CONTACTS"

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_CONTACTS),
            REQUEST_CONTACTS
        )
    }

    private fun requestExactAlarmPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "⏰ Requesting exact alarm permission...")
        isRequestingPermission = true
        lastPermissionRequest = "ALARM"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                // onResume will continue permission flow
            } else {
                // Older APIs - skip this permission
                isRequestingPermission = false
                lastPermissionRequest = ""
                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestNextPermission()
                }, 300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting alarm permission", e)
            isRequestingPermission = false
            lastPermissionRequest = ""
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 300)
        }
    }

    private fun requestUsageStatsPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "📊 Requesting usage stats permission...")
        isRequestingPermission = true
        lastPermissionRequest = "USAGE_STATS"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
                // ✅ FIXED: DON'T call savePermissionsAndNavigate here!
                // Just return - onResume will continue permission flow
            } else {
                // ✅ FIXED: Older APIs - skip this permission, don't bypass
                Log.d(TAG, "⏭️ API < LOLLIPOP, usage stats not available")
                isRequestingPermission = false
                lastPermissionRequest = ""
                Handler(Looper.getMainLooper()).postDelayed({
                    checkAndRequestNextPermission()
                }, 300)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting usage stats permission", e)
            // ✅ FIXED: Don't bypass, just retry
            isRequestingPermission = false
            lastPermissionRequest = ""
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 300)
        }
    }

    private fun requestOverlayPermission() {
        if (isRequestingPermission) return

        Log.d(TAG, "🖼️ Requesting overlay permission...")
        isRequestingPermission = true
        lastPermissionRequest = "OVERLAY"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            // onResume will continue permission flow
        } else {
            // Older APIs - skip this permission
            isRequestingPermission = false
            lastPermissionRequest = ""
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestNextPermission()
            }, 300)
        }
    }

    // ========================================
    // FLOW CONTROL METHODS
    // ========================================

    private fun checkAndRequestNextPermission() {
        // ✅ THREAD-SAFE: Use synchronized block
        synchronized(this) {
            if (hasNavigated || isRequestingPermission) {
                return
            }
        }

        val notificationGranted = isNotificationPermissionGranted()
        val contactsGranted = isContactsPermissionGranted()
        val alarmGranted = isExactAlarmPermissionGranted()
        val usageGranted = isUsageStatsPermissionGranted()
        val overlayGranted = isOverlayPermissionGranted()

        Log.d(TAG, "🔍 Permission status:")
        Log.d(TAG, "   Notification: $notificationGranted")
        Log.d(TAG, "   Contacts: $contactsGranted")
        Log.d(TAG, "   Exact Alarm: $alarmGranted")
        Log.d(TAG, "   Usage Stats: $usageGranted")
        Log.d(TAG, "   Overlay: $overlayGranted")

        when {
            !notificationGranted && lastPermissionRequest != "NOTIFICATION" -> {
                requestNotificationPermission()
            }
            !contactsGranted && lastPermissionRequest != "CONTACTS" -> {
                requestContactsPermission()
            }
            !alarmGranted && lastPermissionRequest != "ALARM" -> {
                requestExactAlarmPermission()
            }
            !usageGranted && lastPermissionRequest != "USAGE_STATS" -> {
                requestUsageStatsPermission()
            }
            !overlayGranted && lastPermissionRequest != "OVERLAY" -> {
                requestOverlayPermission()
            }
            else -> {
                Log.d(TAG, "✅ All permissions granted, navigating...")
                savePermissionsAndNavigate()
            }
        }
    }

    private fun savePermissionsAndNavigate() {
        // ✅ THREAD-SAFE: Synchronized block prevents double navigation
        synchronized(this) {
            if (hasNavigated) {
                Log.d(TAG, "⏭️ Already navigated, skipping")
                return
            }
            hasNavigated = true
        }

        Log.d(TAG, "💾 Saving permissions and navigating...")

        val profileDataStore = ProfileDataStore(this)

        lifecycleScope.launch {
            profileDataStore.setPermissionsGranted()
            Log.d(TAG, "💾 Permissions state saved")

            // Navigate on main thread
            Handler(Looper.getMainLooper()).post {
                navigateToMain()
            }
        }
    }

    private fun navigateToMain() {
        Log.d(TAG, "🎯 Navigating to MainActivity...")

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    // ========================================
    // UI COMPOSABLE
    // ========================================

    @Composable
    private fun SplashScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "CareSync",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF560154)
                )

                CircularProgressIndicator(
                    color = Color(0xFF560154),
                    strokeWidth = 3.dp
                )

                Text(
                    text = "Setting up permissions...",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }

    companion object {
        private const val TAG = "SPLASH"

        // ✅ ALL request codes defined
        private const val REQUEST_NOTIFICATION = 100
        private const val REQUEST_CONTACTS = 101
        private const val REQUEST_ALARM = 102
        private const val REQUEST_USAGE_STATS = 103
        private const val REQUEST_OVERLAY = 104
    }
}
