package com.example.caresync.utils

import android.content.Context
import android.util.Log
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.json.JSONObject

/**
 * Category Mapper - Maps app labels to ML model categories
 *
 * Features:
 * - Fuzzy matching (handles typos, variations, case differences)
 * - 15,600+ app coverage
 * - <1ms exact match, <5ms fuzzy match
 * - Fallback to "Unknown" for unmapped apps
 */
object CategoryMapper {

    private const val TAG = "CategoryMapper"
    private const val FUZZY_THRESHOLD = 85 // Minimum similarity score (0-100)

    // Cached mapping data
    private var mapping: Map<String, String>? = null
    private var appNamesList: List<String>? = null // For fuzzy search
    private var isInitialized = false

    /**
     * Initialize mapper (call once at app startup)
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        try {
            val startTime = System.currentTimeMillis()

            // Load JSON from assets
            val jsonString = context.assets.open("app_mapping.json")
                .bufferedReader()
                .use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()

            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }

            mapping = map
            appNamesList = map.keys.toList()
            isInitialized = true

            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Initialized with ${map.size} apps in ${loadTime}ms")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize mapping", e)
            mapping = emptyMap()
            appNamesList = emptyList()
            isInitialized = true
        }
    }

    /**
     * Get category for app label
     *
     * @param appLabel App name from PackageManager.getApplicationLabel()
     * @return CategoryResult with category, confidence, and match type
     */
    fun getCategory(appLabel: String): CategoryResult {
        // Ensure initialized
        if (!isInitialized || mapping == null) {
            Log.w(TAG, "Not initialized, returning Unknown")
            return CategoryResult("Unknown", 0, MatchType.NO_MATCH)
        }

        // Normalize input
        val normalized = normalizeAppName(appLabel)

        // Try exact match (case-insensitive)
        val exactMatch = mapping?.entries?.find {
            it.key.equals(normalized, ignoreCase = true)
        }

        if (exactMatch != null) {
            return CategoryResult(
                category = exactMatch.value,
                confidence = 100,
                matchType = MatchType.EXACT
            )
        }

        // Try fuzzy match
        val fuzzyResult = findBestFuzzyMatch(normalized)

        if (fuzzyResult != null && fuzzyResult.confidence >= FUZZY_THRESHOLD) {
            return fuzzyResult
        }

        // No match found
        Log.d(TAG, "⚠️ Unknown app: $appLabel (normalized: $normalized)")
        return CategoryResult("Unknown", 0, MatchType.NO_MATCH)
    }

    /**
     * Normalize app name for matching
     */
    private fun normalizeAppName(appLabel: String): String {
        return appLabel
            .trim()
            .replace(Regex("[^a-zA-Z0-9\\s+]"), "") // Remove special chars except space and +
            .replace(Regex("\\s+"), " ") // Normalize whitespace
    }

    /**
     * Find best fuzzy match
     */
    private fun findBestFuzzyMatch(normalized: String): CategoryResult? {
        val appNames = appNamesList ?: return null

        // Find best match using fuzzy search
        val extractResult = FuzzySearch.extractOne(normalized, appNames)

        if (extractResult.score >= FUZZY_THRESHOLD) {
            val matchedApp = extractResult.string
            val category = mapping?.get(matchedApp) ?: "Unknown"

            Log.d(TAG, "🔍 Fuzzy match: '$normalized' → '$matchedApp' (${extractResult.score}%)")

            return CategoryResult(
                category = category,
                confidence = extractResult.score,
                matchType = MatchType.FUZZY
            )
        }

        return null
    }

    /**
     * Get coverage statistics (for debugging)
     */
    fun getCoverageStats(context: Context): CoverageStats {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)

        var exactMatches = 0
        var fuzzyMatches = 0
        var unknownApps = 0

        installedApps.forEach { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString()
            val result = getCategory(label)

            when (result.matchType) {
                MatchType.EXACT -> exactMatches++
                MatchType.FUZZY -> fuzzyMatches++
                MatchType.NO_MATCH -> unknownApps++
            }
        }

        val total = installedApps.size
        val coverage = ((exactMatches + fuzzyMatches) * 100.0 / total).toInt()

        return CoverageStats(
            totalApps = total,
            exactMatches = exactMatches,
            fuzzyMatches = fuzzyMatches,
            unknownApps = unknownApps,
            coveragePercent = coverage
        )
    }
}

/**
 * Result of category lookup
 */
data class CategoryResult(
    val category: String,
    val confidence: Int, // 0-100
    val matchType: MatchType
)

/**
 * Type of match found
 */
enum class MatchType {
    EXACT,      // Exact case-insensitive match
    FUZZY,      // Fuzzy match above threshold
    NO_MATCH    // No match, returned "Unknown"
}

/**
 * Coverage statistics
 */
data class CoverageStats(
    val totalApps: Int,
    val exactMatches: Int,
    val fuzzyMatches: Int,
    val unknownApps: Int,
    val coveragePercent: Int
)
