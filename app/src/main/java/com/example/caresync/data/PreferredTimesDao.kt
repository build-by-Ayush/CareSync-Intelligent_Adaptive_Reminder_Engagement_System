package com.example.caresync.data

import androidx.room.*

/**
 * DAO for preferred_times table (opposite of blacklist_hours)
 *
 * Tracks which hours are BEST for each task based on completion patterns.
 */
@Dao
interface PreferredTimesDao {

    /**
     * Get preferred time for specific hour
     */
    @Query("SELECT * FROM preferred_times WHERE reminderId = :reminderId AND hourOfDay = :hour")
    suspend fun getPreferredTime(reminderId: Long, hour: Int): PreferredTime?

    /**
     * Insert or update preferred time
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preferredTime: PreferredTime)

    /**
     * Get all preferred times for a task (sorted by completion rate)
     */
    @Query("""
        SELECT * FROM preferred_times 
        WHERE reminderId = :reminderId 
        ORDER BY completionRate DESC, confidence DESC
    """)
    suspend fun getAllForTask(reminderId: Long): List<PreferredTime>

    /**
     * Get best hours for a task (top N hours with high completion rate)
     *
     * @param minConfidence Minimum confidence threshold (default 0.5)
     * @param minSamples Minimum sample size (default 3)
     * @param limit How many hours to return (default 3)
     */
    @Query("""
        SELECT * FROM preferred_times 
        WHERE reminderId = :reminderId 
          AND confidence >= :minConfidence
          AND totalNotifications >= :minSamples
        ORDER BY completionRate DESC, confidence DESC
        LIMIT :limit
    """)
    suspend fun getBestHours(
        reminderId: Long,
        minConfidence: Float = 0.5f,
        minSamples: Int = 3,
        limit: Int = 3
    ): List<PreferredTime>

    /**
     * Get best hours within a specific time quadrant
     *
     * @param startHour Start of quadrant (e.g., 6 for morning)
     * @param endHour End of quadrant (e.g., 11 for morning)
     */
    @Query("""
        SELECT * FROM preferred_times 
        WHERE reminderId = :reminderId 
          AND hourOfDay >= :startHour 
          AND hourOfDay <= :endHour
          AND confidence >= :minConfidence
        ORDER BY completionRate DESC
        LIMIT :limit
    """)
    suspend fun getBestHoursInQuadrant(
        reminderId: Long,
        startHour: Int,
        endHour: Int,
        minConfidence: Float = 0.5f,
        limit: Int = 3
    ): List<PreferredTime>

    /**
     * Check if specific hour is a preferred time
     * (Useful for quick checks in decision pipeline)
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM preferred_times 
            WHERE reminderId = :reminderId 
              AND hourOfDay = :hour
              AND completionRate >= 0.5
              AND confidence >= 0.5
        )
    """)
    suspend fun isPreferredTime(reminderId: Long, hour: Int): Boolean

    /**
     * Delete all preferred times for a task
     */
    @Query("DELETE FROM preferred_times WHERE reminderId = :reminderId")
    suspend fun deleteForTask(reminderId: Long): Int

    /**
     * Delete stale preferred times (older than cutoff)
     */
    @Query("DELETE FROM preferred_times WHERE lastUpdated < :cutoffMillis")
    suspend fun deleteOldPreferredTimes(cutoffMillis: Long): Int

    /**
     * Get global best hours across all tasks
     * (Used for new tasks with no history)
     */
    @Query("""
        SELECT hourOfDay, AVG(completionRate) as avgRate
        FROM preferred_times 
        WHERE confidence >= :minConfidence
        GROUP BY hourOfDay
        HAVING COUNT(*) >= :minTasks
        ORDER BY avgRate DESC
        LIMIT :limit
    """)
    suspend fun getGlobalBestHours(
        minConfidence: Float = 0.6f,
        minTasks: Int = 3,
        limit: Int = 3
    ): List<GlobalHourStat>

    // Add these methods to your existing PreferredTimesDao class

    /**
     * Get average completion rate across all hours for a task
     * Used to determine if task is at-risk
     */
    @Query("""
    SELECT 
        AVG(completionRate) as avgCompletionRate,
        AVG(confidence) as avgConfidence,
        SUM(totalNotifications) as totalNotifications,
        SUM(totalCompletions) as totalCompletions,
        COUNT(*) as dataPoints
    FROM preferred_times 
    WHERE reminderId = :reminderId
""")
    suspend fun getAverageStats(reminderId: Long): TaskAverageStatsEntity?

    /**
     * Get the worst-performing hour for a task
     * (Lowest completion rate)
     */
    @Query("""
    SELECT * FROM preferred_times 
    WHERE reminderId = :reminderId 
    ORDER BY completionRate ASC 
    LIMIT 1
""")
    suspend fun getWorstHour(reminderId: Long): PreferredTime?

    /**
     * Get the best-performing hours for a task
     * (Highest completion rates)
     */
    @Query("""
    SELECT * FROM preferred_times 
    WHERE reminderId = :reminderId 
    AND completionRate >= :minCompletionRate
    ORDER BY completionRate DESC 
    LIMIT :limit
""")
    suspend fun getBestHours(
        reminderId: Long,
        minCompletionRate: Float = 0.5f,
        limit: Int = 3
    ): List<PreferredTime>


// ========== DATA CLASSES FOR QUERY RESULTS ==========

    /**
     * Result from average stats query
     */
    data class TaskAverageStatsEntity(
        @ColumnInfo(name = "avgCompletionRate")
        val avgCompletionRate: Float,

        @ColumnInfo(name = "avgConfidence")
        val avgConfidence: Float,

        @ColumnInfo(name = "totalNotifications")
        val totalNotifications: Int,

        @ColumnInfo(name = "totalCompletions")
        val totalCompletions: Int,

        @ColumnInfo(name = "dataPoints")
        val dataPoints: Int
    )
}

/**
 * Result class for global hour statistics
 */
data class GlobalHourStat(
    val hourOfDay: Int,
    val avgRate: Float
)
