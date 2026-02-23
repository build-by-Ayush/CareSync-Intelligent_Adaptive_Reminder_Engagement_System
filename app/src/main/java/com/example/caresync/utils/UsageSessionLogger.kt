package com.example.caresync.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlin.math.abs

data class AppSession(
    val packageName: String,
    val startTime: Long,
    val endTime: Long
) {
    val durationMinutes: Float
        get() = (endTime - startTime) / 60000f
}

object UsageSessionLogger {
    private const val TAG = "UsageSessionLogger"
    private const val QUALIFIED_THRESHOLD_MINUTES = 5
    private const val TIMESTAMP_TOLERANCE_MS = 5000  // ✅ NEW: ±5 second tolerance for matching

    /**
     * Scans for qualified sessions in the past hour, logs them, and provides them to the callback.
     * - Calls `onQualifiedSession` for every qualifying session found (per app, no filter/reminder tie).
     *
     * ✅ UPDATED: Now with caching support
     */
    fun scanQualifiedFrequency(
        context: Context,
        onQualifiedSession: (AppSession) -> Unit
    ) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 60 * 60 * 1000L

        try {
            val events = usm.queryEvents(oneHourAgo, now)
            val sessionList = mutableListOf<AppSession>()
            val lastStartMap = mutableMapOf<String, Long>() // Tracks start time per app

            var event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastStartMap[event.packageName] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = lastStartMap[event.packageName]
                        if (start != null) {
                            val duration = (event.timeStamp - start) / 60000f
                            if (duration >= QUALIFIED_THRESHOLD_MINUTES) {
                                val session = AppSession(event.packageName, start, event.timeStamp)
                                sessionList.add(session)
                            }
                            lastStartMap.remove(event.packageName)
                        }
                    }
                }
            }

            // Log and expose all qualified sessions (callback per session, per app)
            for (session in sessionList) {
                Log.d(
                    TAG,
                    "Qualified session: ${session.packageName}, " +
                            "start=${formatTimestamp(session.startTime)}, " +
                            "end=${formatTimestamp(session.endTime)}, " +
                            "duration=${String.format("%.2f", session.durationMinutes)} min"
                )
                onQualifiedSession(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning qualified frequency", e)
        }
    }

    /**
     * Returns the count of qualified (5+ min) sessions for the given app in last hour.
     * Used for ML model/pipeline input or quick analytics.
     *
     * ✅ UPDATED: Now uses caching for performance
     */
    fun getQualifiedFrequencyForApp(
        context: Context,
        appPackage: String?,
        useCache: Boolean = true
    ): Float {
        if (appPackage == null) return 0f

        // ✅ NEW: Try cache first
        if (useCache) {
            val cached = QualifiedFrequencyCache.getQualifiedFrequency(appPackage)
            if (cached != null) {
                Log.d(TAG, "✅ Cache HIT for app $appPackage: $cached")
                return cached
            }
        }

        var count = 0
        scanQualifiedFrequency(context) { session ->
            if (session.packageName == appPackage) count++
        }

        val frequency = count.toFloat()

        // ✅ NEW: Cache the result
        if (useCache) {
            QualifiedFrequencyCache.cacheQualifiedFrequency(appPackage, frequency)
            Log.d(TAG, "✅ Cached frequency for $appPackage: $frequency")
        }

        return frequency
    }

    /**
     * ✅ NEW METHOD: Find a session that just ended recently
     *
     * Purpose: Used by SessionAlarmReceiver to verify if a session is still active
     *          or just ended as a qualified session
     *
     * @param context Application context
     * @param packageName Package name to search for
     * @param sessionStartTime Expected start time (±5 sec tolerance)
     * @param withinSeconds Maximum age (default: 120 seconds = 2 minutes)
     * @return AppSession if found and recent, null otherwise
     */
    fun findJustEndedSession(
        context: Context,
        packageName: String,
        sessionStartTime: Long,
        withinSeconds: Int = 120
    ): AppSession? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 60 * 60 * 1000L
        val withinMs = withinSeconds * 1000L

        try {
            val events = usm.queryEvents(oneHourAgo, now)
            val lastStartMap = mutableMapOf<String, Long>()
            val recentSessions = mutableListOf<AppSession>()

            var event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        lastStartMap[event.packageName] = event.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = lastStartMap[event.packageName]
                        if (start != null) {
                            val duration = (event.timeStamp - start) / 60000f
                            if (duration >= QUALIFIED_THRESHOLD_MINUTES) {
                                recentSessions.add(
                                    AppSession(event.packageName, start, event.timeStamp)
                                )
                            }
                            lastStartMap.remove(event.packageName)
                        }
                    }
                }
            }

            // ✅ NEW: Find matching session with tolerance
            val matching = recentSessions.find { session ->
                session.packageName == packageName &&
                        abs(session.startTime - sessionStartTime) < TIMESTAMP_TOLERANCE_MS &&
                        (now - session.endTime) < withinMs
            }

            if (matching != null) {
                Log.d(TAG, """
                    ✅ Found recently ended session:
                       Package: $packageName
                       Duration: ${String.format("%.2f", matching.durationMinutes)} min
                       Ended: ${(now - matching.endTime) / 1000}s ago
                """.trimIndent())
                return matching
            } else {
                Log.d(TAG, "⏭️ No recently ended session found for $packageName")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error finding just ended session", e)
            return null
        }
    }

    /**
     * ✅ NEW METHOD: Get qualified frequency filtered by category
     *
     * Purpose: Used by SessionContextCollector for WorkManager path
     *          Only counts sessions from ONE specific category
     *
     * @param context Application context
     * @param category Category to filter by (e.g., "SOCIAL_MEDIA")
     * @param useCache Whether to use caching
     * @return Count of qualified sessions in that category
     */
    fun getQualifiedFrequencyForCategory(
        context: Context,
        category: String,
        useCache: Boolean = true
    ): Float {
        // ✅ NEW: Try cache first
        if (useCache) {
            val cached = QualifiedFrequencyCache.getQualifiedFrequency(category)
            if (cached != null) {
                Log.d(TAG, "✅ Cache HIT for category $category: $cached")
                return cached
            }
        }

        var count = 0
        scanQualifiedFrequency(context) { session ->
            try {
                val pm = context.packageManager
                val appLabel = pm.getApplicationLabel(
                    pm.getApplicationInfo(session.packageName, 0)
                ).toString()
                val sessionCategory = CategoryMapper.getCategory(appLabel).category

                if (sessionCategory == category) {
                    count++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to categorize ${session.packageName}", e)
            }
        }

        val frequency = count.toFloat()

        // ✅ NEW: Cache the result
        if (useCache) {
            QualifiedFrequencyCache.cacheQualifiedFrequency(category, frequency)
            Log.d(TAG, "✅ Cached frequency for category $category: $frequency")
        }

        Log.d(TAG, "📊 Qualified frequency for category $category: $frequency")
        return frequency
    }

    /**
     * Get total count of ALL qualified sessions in the past hour
     * Used for fragmentation detection
     *
     * ✅ NEW: Helper method
     */
    fun getTotalQualifiedFrequency(context: Context): Float {
        var count = 0
        scanQualifiedFrequency(context) { _ -> count++ }
        return count.toFloat()
    }

    /**
     * Clear cache when needed
     *
     * ✅ NEW: Helper method
     */
    fun clearFrequencyCache() {
        QualifiedFrequencyCache.clearCache()
        Log.d(TAG, "🗑️ Frequency cache cleared")
    }

    private fun formatTimestamp(ts: Long): String {
        return try {
            val date = java.util.Date(ts)
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            "N/A"
        }
    }
}
