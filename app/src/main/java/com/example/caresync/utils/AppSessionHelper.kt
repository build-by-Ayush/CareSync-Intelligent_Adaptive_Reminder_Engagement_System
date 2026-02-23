package com.example.caresync.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

/**
 * AppSessionHelper - Utility for retrieving current app session information
 *
 * Responsibilities:
 * - Get currently active app (foreground app NOW)
 * - Get duration of active sessions
 * - Get list of all ongoing sessions
 * - Handle edge cases (no app active, system apps, etc.)
 *
 * Usage:
 * ```
 * val activeApp = AppSessionHelper.getCurrentActiveApp(context)
 * val duration = AppSessionHelper.getSessionDuration(context, activeApp)
 * ```
 */
object AppSessionHelper {

    private const val TAG = "AppSessionHelper"
    private const val QUERY_WINDOW_MINUTES = 180  // Look back 30 minutes max

    /**
     * Get the currently active (foreground) app package name
     *
     * @param context Application context
     * @return Package name of active app, or null if none (home screen, etc.)
     */
    fun getCurrentActiveApp(context: Context): String? {
        val ongoingSessions = getOngoingSessionsNOW(context)

        if (ongoingSessions.isEmpty()) {
            Log.d(TAG, "No active app (home screen or no recent activity)")
            return null
        }

        // Return the most recently started app (last entry in map)
        val currentApp = ongoingSessions.keys.lastOrNull()
        Log.d(TAG, "Current active app: $currentApp")
        return currentApp
    }

    /**
     * Get all currently ongoing sessions
     *
     * @param context Application context
     * @return Map of packageName → sessionStartTime (millis)
     */
    fun getOngoingSessionsNOW(context: Context): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Log.e(TAG, "UsageStatsManager unavailable")
            return emptyMap()
        }

        val now = System.currentTimeMillis()
        val queryStart = now - (QUERY_WINDOW_MINUTES * 60 * 1000L)

        try {
            val events = usm.queryEvents(queryStart, now)
            val sessionTracker = mutableMapOf<String, Long>()

            var event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        // App came to foreground
                        sessionTracker[event.packageName] = event.timeStamp
                        Log.v(TAG, "FOREGROUND: ${event.packageName} at ${formatTime(event.timeStamp)}")
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        // App went to background
                        sessionTracker.remove(event.packageName)
                        Log.v(TAG, "BACKGROUND: ${event.packageName} at ${formatTime(event.timeStamp)}")
                    }
                }
            }

            // Filter out system apps and our own app
            val filteredSessions = sessionTracker.filterKeys { pkg ->
                !isSystemApp(pkg) && pkg != context.packageName
            }

            Log.d(TAG, "Ongoing sessions: ${filteredSessions.size} active")
            filteredSessions.forEach { (pkg, startTime) ->
                val duration = (now - startTime) / 60000f
                Log.d(TAG, "  - $pkg: ${String.format("%.1f", duration)} minutes")
            }

            return filteredSessions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting ongoing sessions", e)
            return emptyMap()
        }
    }

    /**
     * Get the duration (in minutes) of a currently active session
     *
     * @param context Application context
     * @param packageName Package name of app to check
     * @return Duration in minutes, or 0.0f if not active
     */
    fun getSessionDuration(context: Context, packageName: String?): Float {
        if (packageName == null) return 0.0f

        val ongoingSessions = getOngoingSessionsNOW(context)
        val sessionStart = ongoingSessions[packageName]

        if (sessionStart == null) {
            Log.d(TAG, "No ongoing session for $packageName")
            return 0.0f
        }

        val now = System.currentTimeMillis()
        val durationMinutes = (now - sessionStart) / 60000f

        Log.d(TAG, "Session duration for $packageName: ${String.format("%.2f", durationMinutes)} min")
        return durationMinutes
    }

    /**
     * Get the last active app (if no app is currently active)
     *
     * @param context Application context
     * @return Package name of last active app, or null
     */
    fun getLastActiveApp(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usm == null) {
            Log.e(TAG, "UsageStatsManager unavailable")
            return null
        }

        val now = System.currentTimeMillis()
        val queryStart = now - (QUERY_WINDOW_MINUTES * 60 * 1000L)

        try {
            val events = usm.queryEvents(queryStart, now)
            var lastPackage: String? = null
            var lastTime = 0L

            var event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > lastTime && !isSystemApp(event.packageName)) {
                        lastPackage = event.packageName
                        lastTime = event.timeStamp
                    }
                }
            }

            Log.d(TAG, "Last active app: $lastPackage at ${formatTime(lastTime)}")
            return lastPackage

        } catch (e: Exception) {
            Log.e(TAG, "Error getting last active app", e)
            return null
        }
    }

    /**
     * Check if a package is a system app (filter out Android system)
     *
     * @param packageName Package to check
     * @return True if system app
     */
    private fun isSystemApp(packageName: String): Boolean {
        return packageName.startsWith("com.android.") ||
                packageName.startsWith("android") ||
                packageName == "com.google.android.apps.nexuslauncher" ||
                packageName == "com.google.android.inputmethod.latin"
    }

    /**
     * Format timestamp for logging
     *
     * @param timestamp Milliseconds since epoch
     * @return Formatted time string (HH:mm:ss)
     */
    private fun formatTime(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Check if there is ANY app currently active
     *
     * @param context Application context
     * @return True if any app is in foreground
     */
    fun hasActiveApp(context: Context): Boolean {
        return getOngoingSessionsNOW(context).isNotEmpty()
    }

    /**
     * Get start time of a currently active session
     *
     * @param context Application context
     * @param packageName Package name to check
     * @return Start time in millis, or 0L if not active
     */
    fun getSessionStartTime(context: Context, packageName: String?): Long {
        if (packageName == null) return 0L
        return getOngoingSessionsNOW(context)[packageName] ?: 0L
    }
}
