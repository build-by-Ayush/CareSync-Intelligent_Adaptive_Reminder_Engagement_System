package com.example.caresync.utils

import android.util.Log

/**
 * Manages app blocking state
 * When user completes a task, the selected app is blocked for 30 minutes
 */
object AppBlockManager {
    private const val TAG = "AppBlockManager"
    private val blockedApps = mutableMapOf<String, Long>()  // packageName -> unblock timestamp

    /**
     * Block an app for 30 minutes
     */
    fun blockApp(packageName: String) {
        val unblockTime = System.currentTimeMillis() + (30 * 60 * 1000)  // 30 min
        blockedApps[packageName] = unblockTime
        Log.d(TAG, "🚫 Blocked $packageName until ${formatTime(unblockTime)}")
    }

    /**
     * Check if app is currently blocked
     */
    fun isBlocked(packageName: String): Boolean {
        val unblockTime = blockedApps[packageName] ?: return false

        if (System.currentTimeMillis() < unblockTime) {
            return true  // Still blocked
        } else {
            blockedApps.remove(packageName)  // Expired, remove
            return false
        }
    }

    /**
     * Get remaining block time in minutes
     */
    fun getRemainingMinutes(packageName: String): Int {
        val unblockTime = blockedApps[packageName] ?: return 0
        val remaining = unblockTime - System.currentTimeMillis()
        return (remaining / 60000).toInt().coerceAtLeast(0)
    }

    /**
     * Unblock an app manually (for testing or unlock anyway button)
     */
    fun unblockApp(packageName: String) {
        blockedApps.remove(packageName)
        Log.d(TAG, "✅ Unblocked $packageName")
    }

    /**
     * Check if any apps are currently blocked
     * ✅ ADDED: This function was missing!
     */
    fun hasBlockedApps(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Remove expired blocks
        val iterator = blockedApps.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime >= entry.value) {
                iterator.remove()
            }
        }

        return blockedApps.isNotEmpty()
    }

    private fun formatTime(millis: Long): String {
        val date = java.util.Date(millis)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }
}
