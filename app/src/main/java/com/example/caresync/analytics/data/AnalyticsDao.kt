package com.example.caresync.analytics.data

import androidx.room.*

@Dao
interface AnalyticsDao {

    // User Progress Operations
    @Query("SELECT * FROM user_progress WHERE id = 1")
    suspend fun getUserProgress(): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProgress(progress: UserProgressEntity)

    @Update
    suspend fun updateUserProgress(progress: UserProgressEntity)

    // Achievement Operations
    @Query("SELECT * FROM achievements ORDER BY pointsRequired ASC")
    suspend fun getAllAchievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE isUnlocked = 1")
    suspend fun getUnlockedAchievements(): List<AchievementEntity>

    @Query("UPDATE achievements SET isUnlocked = 1, unlockedAt = :timestamp WHERE id = :achievementId")
    suspend fun unlockAchievement(achievementId: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    // Analytics Queries (using existing reminder_events table)
    @Query("""
        SELECT COUNT(*) 
        FROM reminder_events 
        WHERE eventType = 'COMPLETED'
    """)
    suspend fun getTotalCompletionsCount(): Int

    @Query("""
        SELECT hourOfDay, COUNT(*) as count 
        FROM reminder_events 
        WHERE eventType = 'COMPLETED' 
        GROUP BY hourOfDay 
        ORDER BY count DESC
    """)
    suspend fun getCompletionsByHour(): List<HourCount>

    @Query("""
        SELECT DATE(timestamp / 1000, 'unixepoch') as date, COUNT(*) as count
        FROM reminder_events
        WHERE eventType = 'COMPLETED' AND timestamp >= :startDate
        GROUP BY date
        ORDER BY date DESC
    """)
    suspend fun getDailyCompletions(startDate: Long): List<DayCount>

    @Query("""
        SELECT toneUsed, 
               SUM(CASE WHEN eventType = 'TRIGGERED' THEN 1 ELSE 0 END) as totalSent,
               SUM(CASE WHEN eventType = 'COMPLETED' THEN 1 ELSE 0 END) as completed
        FROM reminder_events
        WHERE toneUsed IS NOT NULL
        GROUP BY toneUsed
    """)
    suspend fun getToneStats(): List<ToneStatsRaw>

}

// Data classes for query results
data class HourCount(val hourOfDay: Int, val count: Int)
data class DayCount(val date: String, val count: Int)
data class ToneStatsRaw(val toneUsed: String, val totalSent: Int, val completed: Int)
