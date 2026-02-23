package com.example.caresync.analytics.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    // ==========================================
    // USER PROGRESS OPERATIONS
    // ==========================================

    @Query("SELECT * FROM user_progress WHERE id = 1")
    suspend fun getUserProgress(): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProgress(progress: UserProgressEntity)

    @Update
    suspend fun updateUserProgress(progress: UserProgressEntity)

    @Query("SELECT * FROM user_progress WHERE id = 1")
    fun getUserProgressFlow(): Flow<UserProgressEntity?>

    // ==========================================
    // ACHIEVEMENT OPERATIONS
    // ==========================================

    @Query("SELECT * FROM achievements ORDER BY pointsRequired ASC")
    suspend fun getAllAchievements(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE isUnlocked = 1")
    suspend fun getUnlockedAchievements(): List<AchievementEntity>

    @Query("UPDATE achievements SET isUnlocked = 1, unlockedAt = :timestamp WHERE id = :achievementId")
    suspend fun unlockAchievement(achievementId: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    // ==========================================
    // ANALYTICS QUERIES
    // ==========================================

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
               SUM(CASE WHEN eventType = 'TRIGGERED' 
                    AND isSnoozedRetrigger = 0 
                    AND (triggerSource IS NULL OR triggerSource NOT LIKE '%BOOST%') 
                    THEN 1 ELSE 0 END) as totalSent,
               SUM(CASE WHEN eventType = 'COMPLETED' THEN 1 ELSE 0 END) as completed
        FROM reminder_events
        WHERE toneUsed IS NOT NULL
        GROUP BY toneUsed
    """)
    suspend fun getToneStats(): List<ToneStatsRaw>

    @Query("""
        SELECT 
            DATE(timestamp / 1000, 'unixepoch') as date,
            COUNT(CASE WHEN eventType = 'TRIGGERED' AND isSnoozedRetrigger = 0 THEN 1 END) as totalNotifications,
            COUNT(CASE WHEN eventType = 'COMPLETED' AND isSnoozedRetrigger = 0 THEN 1 END) as totalCompletions
        FROM reminder_events
        WHERE timestamp >= :startDate 
          AND timestamp <= :endDate
          AND (triggerSource IS NULL OR triggerSource NOT LIKE '%BOOST%')
        GROUP BY DATE(timestamp / 1000, 'unixepoch')
        ORDER BY date ASC
    """)
    suspend fun getDailyCompletionStats(
        startDate: Long,
        endDate: Long
    ): List<DailyCompletionStats>

    @Query("""
        SELECT COUNT(*) 
        FROM reminder_events 
        WHERE eventType = 'COMPLETED' AND isSnoozedRetrigger = 0
    """)
    suspend fun getTotalCompletionsCount(): Int

}

// ==========================================
// DATA CLASSES
// ==========================================

data class HourCount(val hourOfDay: Int, val count: Int)

data class DayCount(val date: String, val count: Int)

data class ToneStatsRaw(val toneUsed: String, val totalSent: Int, val completed: Int)

data class DailyCompletionStats(
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "totalNotifications")
    val totalNotifications: Int,

    @ColumnInfo(name = "totalCompletions")
    val totalCompletions: Int
) {
    val completionRate: Float
        get() = if (totalNotifications > 0) {
            totalCompletions.toFloat() / totalNotifications
        } else {
            0f
        }
}
