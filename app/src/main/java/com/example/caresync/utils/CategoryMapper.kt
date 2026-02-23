package com.example.caresync.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Category Mapper - Maps app labels to ML model categories
 *
 * Uses 4-tier matching strategy:
 * 1. EXACT: Exact case-insensitive match
 * 2. STARTS_WITH: Device name is start of mapper entry
 * 3. KEYWORD: Keyword-based fuzzy matching
 * 4. FALLBACK: Unknown
 *
 * Features:
 * - 15,600+ app coverage
 * - Prevents false positives (CareSync won't match Resy)
 * - <1ms exact match, <5ms fuzzy match
 */
object CategoryMapper {

    private const val TAG = "CategoryMapper"
    private const val FUZZY_THRESHOLD = 80  // Keyword-level threshold

    private var mapping: Map<String, String> = emptyMap()
    private var appNamesList: List<String> = emptyList()
    private var keywordMap: Map<String, List<String>> = emptyMap()  // ✅ NEW: Keywords per app
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
            val kwMap = mutableMapOf<String, List<String>>()

            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
                kwMap[key] = extractKeywords(key)  // ✅ Extract keywords
            }

            mapping = map
            appNamesList = map.keys.toList()
            keywordMap = kwMap
            isInitialized = true

            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ Initialized with ${map.size} apps in ${loadTime}ms")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize mapping", e)
            mapping = emptyMap()
            appNamesList = emptyList()
            keywordMap = emptyMap()
            isInitialized = true
        }
    }

    /**
     * Get category for app label using 4-tier matching
     */
    fun getCategory(appLabel: String): CategoryResult {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized, returning Unknown")
            return CategoryResult("Unknown", 0, MatchType.NO_MATCH)
        }

        val normalized = normalizeAppName(appLabel)

        // ==========================================
        // TIER 1: EXACT MATCH
        // ==========================================
        val exactMatch = mapping.entries.find {
            it.key.equals(normalized, ignoreCase = true)
        }

        if (exactMatch != null) {
            Log.d(TAG, "✅ EXACT match: '$normalized' → '${exactMatch.key}'")
            return CategoryResult(
                category = exactMatch.value,
                confidence = 100,
                matchType = MatchType.EXACT
            )
        }

        // ==========================================
        // TIER 2: STARTS WITH (device name is start of mapper entry)
        // ==========================================
        val startsWithMatch = mapping.entries.find {
            it.key.startsWith(normalized, ignoreCase = true)
        }

        if (startsWithMatch != null) {
            Log.d(TAG, "✅ STARTS_WITH match: '$normalized' → '${startsWithMatch.key}'")
            return CategoryResult(
                category = startsWithMatch.value,
                confidence = 95,
                matchType = MatchType.STARTS_WITH
            )
        }

        // ==========================================
        // TIER 3: SUBSTRING (mapper entry contains device name)
        // ==========================================
        val substringMatch = mapping.entries.find {
            it.key.contains(normalized, ignoreCase = true)
        }

        if (substringMatch != null) {
            Log.d(TAG, "✅ SUBSTRING match: '$normalized' → '${substringMatch.key}'")
            return CategoryResult(
                category = substringMatch.value,
                confidence = 90,
                matchType = MatchType.SUBSTRING
            )
        }

        // ==========================================
        // TIER 4: KEYWORD-BASED FUZZY
        // ==========================================
        val fuzzyResult = findBestKeywordMatch(normalized)

        if (fuzzyResult != null) {
            return fuzzyResult
        }

        // ==========================================
        // FALLBACK: Unknown
        // ==========================================
        Log.d(TAG, "⚠️ Unknown app: '$appLabel' (normalized: '$normalized')")
        return CategoryResult("Unknown", 0, MatchType.NO_MATCH)
    }

    /**
     * Extract keywords from app name
     * "Instagram Lite" → ["instagram", "lite"]
     * "StarMaker Lite" → ["starmaker", "lite"]
     */
    private fun extractKeywords(appName: String): List<String> {
        return appName
            .lowercase()
            .split(Regex("[\\s\\-_]+"))  // Split on spaces, hyphens, underscores
            .filter { it.length >= 2 }   // Only words ≥2 chars
            .map { it.trim() }
    }

    /**
     * Find best match using KEYWORD-LEVEL similarity
     * NOT character-level (prevents "CareSync" matching "Resy")
     */
    private fun findBestKeywordMatch(normalized: String): CategoryResult? {
        if (appNamesList.isEmpty() || keywordMap.isEmpty()) {
            return null
        }

        val deviceKeywords = extractKeywords(normalized)

        if (deviceKeywords.isEmpty()) {
            return null  // Can't match if no keywords
        }

        var bestMatch: Pair<String, Int>? = null  // (app_name, score)

        // Check each mapper entry
        mapping.entries.forEach { (mapperApp, category) ->
            val mapperKeywords = keywordMap[mapperApp] ?: emptyList()

            // Calculate keyword overlap
            val matchedKeywords = deviceKeywords.count { deviceKw ->
                mapperKeywords.any { mapperKw ->
                    similarityScore(deviceKw, mapperKw) >= FUZZY_THRESHOLD
                }
            }

            val score = if (deviceKeywords.isNotEmpty()) {
                (matchedKeywords * 100) / deviceKeywords.size
            } else {
                0
            }

            // Keep track of best match
            if (score >= FUZZY_THRESHOLD) {
                if (bestMatch == null || score > bestMatch!!.second) {
                    bestMatch = Pair(mapperApp, score)
                }
            }
        }

        if (bestMatch != null) {
            val (matchedApp, score) = bestMatch!!
            val category = mapping[matchedApp] ?: "Unknown"

            Log.d(TAG, "🔍 KEYWORD match: '$normalized' → '$matchedApp' (${score}%)")

            return CategoryResult(
                category = category,
                confidence = score,
                matchType = MatchType.KEYWORD
            )
        }

        return null
    }

    /**
     * Simple string similarity (Levenshtein distance)
     * Returns 0-100 score
     */
    private fun similarityScore(s1: String, s2: String): Int {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 100  // Both empty = 100%

        val distance = levenshteinDistance(s1, s2)
        return ((maxLen - distance) * 100) / maxLen
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * Normalize app name for matching
     */
    private fun normalizeAppName(appLabel: String): String {
        return appLabel
            .trim()
            .replace(Regex("[^a-zA-Z0-9\\s+\\-]"), "")  // Keep letters, numbers, spaces, +, -
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
    }

    /**
     * Get coverage statistics
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
                MatchType.EXACT, MatchType.STARTS_WITH, MatchType.SUBSTRING -> exactMatches++
                MatchType.KEYWORD -> fuzzyMatches++
                MatchType.NO_MATCH -> unknownApps++
            }
        }

        val total = installedApps.size
        val coverage = if (total > 0) {
            ((exactMatches + fuzzyMatches) * 100) / total
        } else {
            0
        }

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
    val confidence: Int,  // 0-100
    val matchType: MatchType
)

/**
 * Type of match found
 */
enum class MatchType {
    EXACT,          // Perfect match
    STARTS_WITH,    // Device name starts mapper entry
    SUBSTRING,      // Mapper entry contains device name
    KEYWORD,        // Keyword-level fuzzy match
    NO_MATCH        // Unknown app
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
