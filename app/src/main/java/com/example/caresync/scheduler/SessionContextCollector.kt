package com.example.caresync.scheduler

import android.content.Context
import android.util.Log
import com.example.caresync.utils.AppSessionHelper
import com.example.caresync.utils.CategoryMapper
import com.example.caresync.utils.QualifiedFrequencyCache
import com.example.caresync.utils.UsageSessionLogger
import com.example.caresync.utils.AppSession
import java.util.Calendar

/**
 * SessionContextCollector - Unified logic for collecting device context
 *
 * Purpose:
 * - Determine which path is being used (WorkManager vs Session-End)
 * - Collect appropriate data for each path
 * - Handle category mapping and frequency calculations
 * - Create DeviceContext with all 5 ML model fields
 *
 * Two Paths:
 * -----------
 * 1. WorkManager Path (Every 15 minutes):
 *    - Single active app
 *    - Filtered qualified frequency (same category only)
 *    - Current duration of active app
 *
 * 2. Session-End Path (When session ends):
 *    - All apps from last hour
 *    - Unfiltered qualified frequency (all categories)
 *    - Dominant category for context
 *
 * Usage:
 * ```
 * val context = SessionContextCollector.collectContextFromWorkManager(context)
 * // or
 * val context = SessionContextCollector.collectContextFromSessionEnd(context, "com.app", startTime)
 * // or
 * val context = SessionContextCollector.getDefaultContext()
 * ```
 */
object SessionContextCollector {

    private const val TAG = "SessionContextCollector"

    /**
     * Collect device context from WorkManager trigger (single active app)
     *
     * Logic:
     * - Get currently active app
     * - Get its category
     * - Get its current duration
     * - Get qualified frequency for SAME CATEGORY ONLY (filtered)
     *
     * @param context Application context
     * @return DeviceContext with all 5 fields
     */
    fun collectContextFromWorkManager(context: Context): DeviceContext {
        Log.d(TAG, "🔄 Collecting context: WorkManager path")

        // Step 1: Get currently active app
        val currentApp = AppSessionHelper.getCurrentActiveApp(context)

        if (currentApp == null) {
            Log.d(TAG, "No active app (home screen)")
            return getDefaultContext()
        }

        // Step 2: Get app label and category
        val appLabel = getApplicationLabel(context, currentApp)
        val categoryResult = CategoryMapper.getCategory(appLabel)
        val category = categoryResult.category

        Log.d(TAG, "Active app: $currentApp → Label: $appLabel → Category: $category")

        // Step 3: Get current session duration
        val minsSinceOpen = AppSessionHelper.getSessionDuration(context, currentApp)

        // Step 4: Get qualified frequency for THIS CATEGORY ONLY (filtered)
        val qualifiedFrequency = getQualifiedFrequencyForCategory(context, category)

        Log.d(TAG, """
            ✅ WorkManager Context:
              Category: $category
              Mins Since Open: ${String.format("%.2f", minsSinceOpen)}
              Qualified Frequency: $qualifiedFrequency (FILTERED to $category only)
        """.trimIndent())

        return DeviceContext(
            category = category,
            minsSinceOpen = minsSinceOpen,
            qualifiedFrequency = qualifiedFrequency,
            isNight = if (isNightTime()) "Yes" else "No",
            isWeekend = if (isWeekend()) "Yes" else "No"
        )
    }

    /**
     * Collect device context from Session-End trigger (all apps, unfiltered)
     *
     * Logic:
     * - Get ALL qualified sessions from last hour
     * - Group by category
     * - Find dominant category (most sessions)
     * - Count ALL sessions (unfiltered)
     * - Get current active app duration (if any)
     *
     * @param context Application context
     * @param closedPackageName Package that just closed (optional)
     * @param closedStartTime When that session started (optional)
     * @return DeviceContext with all 5 fields
     */
    fun collectContextFromSessionEnd(
        context: Context,
        closedPackageName: String? = null,
        closedStartTime: Long? = null
    ): DeviceContext {
        Log.d(TAG, "📱 Collecting context: Session-End path")

        // Step 1: Get ALL qualified sessions (no filtering)
        val allSessions = mutableListOf<AppSession>()
        UsageSessionLogger.scanQualifiedFrequency(context) { session ->
            allSessions.add(session)
        }

        if (allSessions.isEmpty()) {
            Log.d(TAG, "No qualified sessions in last hour")
            return getDefaultContext()
        }

        // Step 2: Group sessions by category
        val sessionsByCategory = allSessions.groupBy { session ->
            val appLabel = getApplicationLabel(context, session.packageName)
            CategoryMapper.getCategory(appLabel).category
        }

        // Step 3: Find dominant category (tie-break: most recent)
        val dominantCategory = getDominantCategory(sessionsByCategory)

        // Step 4: Count ALL sessions (unfiltered!)
        val qualifiedFrequency = allSessions.size.toFloat()

        // Step 5: Get current active app duration (if any)
        val currentApp = AppSessionHelper.getCurrentActiveApp(context)
        val minsSinceOpen = if (currentApp != null) {
            AppSessionHelper.getSessionDuration(context, currentApp)
        } else {
            // No active app - use closed app's final duration if recent
            val justEnded = if (closedPackageName != null && closedStartTime != null) {
                allSessions.find {
                    it.packageName == closedPackageName &&
                            kotlin.math.abs(it.startTime - closedStartTime) < 5000  // ±5 second tolerance
                }
            } else null

            justEnded?.durationMinutes ?: 0.0f
        }

        Log.d(TAG, """
            ✅ Session-End Context:
              Total sessions: ${allSessions.size}
              Category breakdown:
              ${sessionsByCategory.entries.joinToString("\n  ") {
            "  - ${it.key}: ${it.value.size} sessions"
        }}
              Dominant: $dominantCategory
              Mins Since Open: ${String.format("%.2f", minsSinceOpen)}
              Qualified Frequency: $qualifiedFrequency (UNFILTERED, all categories)
        """.trimIndent())

        return DeviceContext(
            category = dominantCategory,
            minsSinceOpen = minsSinceOpen,
            qualifiedFrequency = qualifiedFrequency,
            isNight = if (isNightTime()) "Yes" else "No",
            isWeekend = if (isWeekend()) "Yes" else "No"
        )
    }

    /**
     * Get qualified frequency for specific category (with caching)
     *
     * @param context Application context
     * @param category Category to filter
     * @return Count of qualified sessions in that category
     */
    private fun getQualifiedFrequencyForCategory(context: Context, category: String): Float {
        // Try cache first
        val cached = QualifiedFrequencyCache.getQualifiedFrequency(category)
        if (cached != null) {
            return cached
        }

        // Calculate fresh
        var count = 0
        UsageSessionLogger.scanQualifiedFrequency(context) { session ->
            val sessionCategory = CategoryMapper.getCategory(
                getApplicationLabel(context, session.packageName)
            ).category

            if (sessionCategory == category) {
                count++
            }
        }

        val frequency = count.toFloat()

        // Cache result
        QualifiedFrequencyCache.cacheQualifiedFrequency(category, frequency)

        return frequency
    }

    /**
     * Determine dominant category from session map
     *
     * Tie-breaking: Use most recent session's category
     *
     * @param sessionsByCategory Map of category → list of sessions
     * @return Dominant category name
     */
    fun getDominantCategory(sessionsByCategory: Map<String, List<AppSession>>): String {
        if (sessionsByCategory.isEmpty()) {
            return "Unknown"
        }

        // Find max count
        val maxCount = sessionsByCategory.values.maxOfOrNull { it.size } ?: 0

        // Get all categories with max count (handle ties)
        val tiedCategories = sessionsByCategory.filter { it.value.size == maxCount }

        if (tiedCategories.size == 1) {
            // Clear winner
            return tiedCategories.keys.first()
        }

        // Tie-break: Use category with most recent session
        val dominant = tiedCategories.maxByOrNull { (_, sessions) ->
            sessions.maxOfOrNull { it.endTime } ?: 0L
        }?.key ?: "Unknown"

        Log.d(TAG, "Tie-break: Multiple categories with $maxCount sessions, using $dominant (most recent)")
        return dominant
    }

    /**
     * Get application label from package name
     *
     * @param context Application context
     * @param packageName Package to lookup
     * @return App label or package name if lookup fails
     */
    private fun getApplicationLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get label for $packageName", e)
            packageName
        }
    }

    /**
     * Check if current time is night (12 AM - 6 AM)
     */
    private fun isNightTime(): Boolean {
        val hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hourNow in 0..5
    }

    /**
     * Check if current day is weekend (Saturday or Sunday)
     */
    private fun isWeekend(): Boolean {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    /**
     * ✅ GET DEFAULT CONTEXT - Called from MLCheckWorker and others
     *
     * Single definition (not duplicated!)
     * Public so MLCheckWorker.kt can call it
     */
    fun getDefaultContext(): DeviceContext {
        return DeviceContext(
            category = "Unknown",
            minsSinceOpen = 0.0f,
            qualifiedFrequency = 0.0f,
            isNight = if (isNightTime()) "Yes" else "No",
            isWeekend = if (isWeekend()) "Yes" else "No"
        )
    }

    /**
     * ✅ NEW: Collect context from SessionAlarmReceiver trigger
     *
     * When SessionAlarmReceiver fires (mini-alarm):
     * - Receives triggerSource: "SESSION_STILL_ACTIVE" or "SESSION_JUST_ENDED"
     * - Receives minsSinceOpen: Actual duration passed from alarm receiver
     *
     * This allows using REAL engagement data instead of querying old history
     *
     * @param context Application context
     * @param packageName App that triggered the alarm
     * @param minsSinceOpen Duration of the session
     * @param triggerSource "SESSION_STILL_ACTIVE" or "SESSION_JUST_ENDED"
     * @return DeviceContext with corrected qualifiedFrequency
     */
    fun collectContextFromSessionAlarm(
        context: Context,
        packageName: String,
        minsSinceOpen: Float,
        triggerSource: String
    ): DeviceContext {
        Log.d(TAG, "🔔 Collecting context: Session-Alarm path (trigger: $triggerSource)")

        // Step 1: Get app label and category
        val appLabel = getApplicationLabel(context, packageName)
        val categoryResult = CategoryMapper.getCategory(appLabel)
        val category = categoryResult.category

        Log.d(TAG, "App: $packageName → Label: $appLabel → Category: $category")

        // ✅ STEP 2: Fix qualifiedFrequency based on triggerSource
        val qualifiedFrequency = when (triggerSource) {
            "SESSION_STILL_ACTIVE" -> {
                // App is actively engaged NOW
                // Count it as one qualified session if >= 10 min
                if (minsSinceOpen >= 10f) {
                    1f  // One active session = frequency of 1
                } else {
                    0f
                }
            }
            "SESSION_JUST_ENDED" -> {
                // App just ended
                // Count it as one qualified session if >= 5 min
                if (minsSinceOpen >= 5f) {
                    1f  // One completed session = frequency of 1
                } else {
                    0f
                }
            }
            else -> {
                // Fallback: query from history
                UsageSessionLogger.getQualifiedFrequencyForApp(context, packageName)
            }
        }

        Log.d(TAG, """
        ✅ Session-Alarm Context:
          Category: $category
          Mins Since Open: ${String.format("%.2f", minsSinceOpen)}
          Qualified Frequency: $qualifiedFrequency (from $triggerSource)
          Trigger: $triggerSource
    """.trimIndent())

        return DeviceContext(
            category = category,
            minsSinceOpen = minsSinceOpen,
            qualifiedFrequency = qualifiedFrequency,
            isNight = if (isNightTime()) "Yes" else "No",
            isWeekend = if (isWeekend()) "Yes" else "No"
        )
    }
}

/**
 * Device context data class for ML model input
 */
data class DeviceContext(
    val category: String,           // App category (SOCIAL_MEDIA, EDUCATION, etc.)
    val minsSinceOpen: Float,       // Duration of current session
    val qualifiedFrequency: Float,  // Count of 5+ minute sessions
    val isNight: String,            // "Yes" or "No"
    val isWeekend: String           // "Yes" or "No"
)
