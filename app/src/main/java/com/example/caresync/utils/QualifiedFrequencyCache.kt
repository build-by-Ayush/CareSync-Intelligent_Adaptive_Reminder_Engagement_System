package com.example.caresync.utils

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * QualifiedFrequencyCache - Performance optimization for frequency calculations
 *
 * Purpose:
 * - Cache qualified frequency results (category-based)
 * - Auto-expire after 30 seconds (data meaningful only for short time)
 * - Thread-safe access
 * - Clear on significant app state changes
 *
 * Why needed:
 * - scanQualifiedFrequency() scans 1 hour of UsageStatsManager events (expensive)
 * - Called from multiple places (WorkManager, Session-End, ML checks)
 * - Data only meaningful for ~30 second window
 *
 * Usage:
 * ```
 * // Store
 * QualifiedFrequencyCache.cacheQualifiedFrequency("SOCIAL_MEDIA", 3.0f)
 *
 * // Retrieve
 * val freq = QualifiedFrequencyCache.getQualifiedFrequency("SOCIAL_MEDIA")
 * if (freq != null) {
 *     // Use cached value
 * } else {
 *     // Calculate fresh value
 * }
 * ```
 */
object QualifiedFrequencyCache {

    private const val TAG = "QualifiedFrequencyCache"
    private const val CACHE_EXPIRY_SECONDS = 30  // Cache valid for 30 seconds

    // Thread-safe storage
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private var lastClearTime = System.currentTimeMillis()

    /**
     * Cache entry with timestamp
     */
    private data class CacheEntry(
        val frequency: Float,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isValid(maxAgeSeconds: Int = CACHE_EXPIRY_SECONDS): Boolean {
            val ageSeconds = (System.currentTimeMillis() - timestamp) / 1000
            return ageSeconds < maxAgeSeconds
        }

        fun ageSeconds(): Long {
            return (System.currentTimeMillis() - timestamp) / 1000
        }
    }

    /**
     * Store qualified frequency for a category
     *
     * @param category Category name (e.g., "SOCIAL_MEDIA", "EDUCATION")
     * @param frequency Qualified frequency count
     */
    fun cacheQualifiedFrequency(category: String, frequency: Float) {
        cache[category] = CacheEntry(frequency)
        Log.d(TAG, "✅ Cached: $category → $frequency")
    }

    /**
     * Retrieve cached qualified frequency for a category
     *
     * @param category Category name
     * @param maxAgeSeconds Maximum age in seconds (default: 30)
     * @return Cached frequency, or null if not found/expired
     */
    fun getQualifiedFrequency(category: String, maxAgeSeconds: Int = CACHE_EXPIRY_SECONDS): Float? {
        val entry = cache[category]

        if (entry == null) {
            Log.v(TAG, "❌ Cache MISS: $category (not found)")
            return null
        }

        if (!entry.isValid(maxAgeSeconds)) {
            Log.v(TAG, "⏰ Cache EXPIRED: $category (age: ${entry.ageSeconds()}s)")
            cache.remove(category)
            return null
        }

        Log.d(TAG, "✅ Cache HIT: $category → ${entry.frequency} (age: ${entry.ageSeconds()}s)")
        return entry.frequency
    }

    /**
     * Check if cache is valid and fresh
     *
     * @param maxAgeSeconds Maximum age threshold
     * @return True if cache has fresh entries
     */
    fun isCacheValid(maxAgeSeconds: Int = CACHE_EXPIRY_SECONDS): Boolean {
        return cache.values.any { it.isValid(maxAgeSeconds) }
    }

    /**
     * Clear all cached entries
     */
    fun clearCache() {
        val size = cache.size
        cache.clear()
        lastClearTime = System.currentTimeMillis()
        Log.d(TAG, "🗑️ Cache cleared ($size entries removed)")
    }

    /**
     * Clear only expired entries (cleanup)
     */
    fun clearExpired() {
        val before = cache.size

        // ✅ FIXED: Use iterator instead of removeIf() for API 21 compatibility
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.value.isValid()) {
                iterator.remove()
            }
        }

        val after = cache.size
        val removed = before - after
        if (removed > 0) {
            Log.d(TAG, "🗑️ Expired entries cleared: $removed removed, $after remaining")
        }
    }

    /**
     * Get cache statistics (for debugging)
     */
    fun getStats(): CacheStats {
        val now = System.currentTimeMillis()
        val validEntries = cache.values.count { it.isValid() }
        val expiredEntries = cache.size - validEntries
        val oldestEntry = cache.values.minByOrNull { it.timestamp }
        val newestEntry = cache.values.maxByOrNull { it.timestamp }

        return CacheStats(
            totalEntries = cache.size,
            validEntries = validEntries,
            expiredEntries = expiredEntries,
            oldestAgeSeconds = oldestEntry?.let { (now - it.timestamp) / 1000 } ?: 0,
            newestAgeSeconds = newestEntry?.let { (now - it.timestamp) / 1000 } ?: 0,
            cacheAgeSeconds = (now - lastClearTime) / 1000
        )
    }

    /**
     * Log cache contents (for debugging)
     */
    fun logCacheContents() {
        if (cache.isEmpty()) {
            Log.d(TAG, "📊 Cache is empty")
            return
        }

        Log.d(TAG, "📊 Cache contents (${cache.size} entries):")
        cache.entries.sortedBy { it.value.timestamp }.forEach { (category, entry) ->
            val status = if (entry.isValid()) "VALID" else "EXPIRED"
            Log.d(TAG, "  - $category: ${entry.frequency} ($status, age: ${entry.ageSeconds()}s)")
        }
    }

    /**
     * Invalidate cache for specific category
     *
     * @param category Category to invalidate
     */
    fun invalidateCategory(category: String) {
        val removed = cache.remove(category)
        if (removed != null) {
            Log.d(TAG, "🗑️ Invalidated: $category")
        }
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val totalEntries: Int,
    val validEntries: Int,
    val expiredEntries: Int,
    val oldestAgeSeconds: Long,
    val newestAgeSeconds: Long,
    val cacheAgeSeconds: Long
) {
    override fun toString(): String {
        return """
            CacheStats:
              Total entries: $totalEntries
              Valid: $validEntries
              Expired: $expiredEntries
              Age range: ${newestAgeSeconds}s - ${oldestAgeSeconds}s
              Cache age: ${cacheAgeSeconds}s
        """.trimIndent()
    }
}
