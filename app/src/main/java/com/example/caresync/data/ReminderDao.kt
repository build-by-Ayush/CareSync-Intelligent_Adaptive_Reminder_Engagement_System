package com.example.caresync.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// REMINDER DAO (No Changes from Your Original)
// ==========================================
@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders ORDER BY priority DESC, CASE WHEN scheduledAtMillis IS NULL THEN 1 ELSE 0 END, scheduledAtMillis ASC, updatedAt DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ==========================================
// REMINDER EVENT DAO (Expanded for Analytics)
// ==========================================
@Dao
interface ReminderEventDao {

    // ==========================================
    // BASIC CRUD (From Your Original)
    // ==========================================

    @Insert
    suspend fun insert(event: ReminderEventEntity): Long

    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId ORDER BY timestamp DESC")
    fun observeForReminder(reminderId: Long): Flow<List<ReminderEventEntity>>

    @Query("SELECT COUNT(*) FROM reminder_events WHERE eventType = :type")
    suspend fun countByType(type: String): Int

    // ==========================================
    // CLEANUP QUERIES
    // ==========================================

    @Query("DELETE FROM reminder_events WHERE timestamp < :cutoffMillis")
    suspend fun deleteOldEvents(cutoffMillis: Long): Int

    @Query("DELETE FROM reminder_events WHERE reminderId = :reminderId")
    suspend fun deleteEventsForReminder(reminderId: Long): Int

    // ==========================================
    // BLACKLIST ANALYSIS (For Decision Pipeline)
    // ==========================================

    @Query("""
        SELECT hourOfDay, COUNT(*) as count 
        FROM reminder_events 
        WHERE reminderId = :reminderId 
          AND eventType IN ('DISMISSED', 'IGNORED')
          AND timestamp > :sinceMillis
        GROUP BY hourOfDay
        HAVING count >= :threshold
        ORDER BY count DESC
    """)
    suspend fun getBlacklistedHours(
        reminderId: Long,
        sinceMillis: Long,
        threshold: Int = 5
    ): List<HourCount>

    // ==========================================
    // HIGHLIGHT ANALYSIS (Good Times)
    // ==========================================

    @Query("""
        SELECT 
            hourOfDay,
            SUM(CASE WHEN eventType = 'TRIGGERED' THEN 1 ELSE 0 END) as triggered,
            SUM(CASE WHEN eventType = 'COMPLETED' THEN 1 ELSE 0 END) as completed
        FROM reminder_events
        WHERE reminderId = :reminderId 
          AND timestamp > :sinceMillis
        GROUP BY hourOfDay
        HAVING triggered > 0
        ORDER BY hourOfDay
    """)
    suspend fun getCompletionRateByHour(reminderId: Long, sinceMillis: Long): List<HourStats>

    @Query("""
        SELECT 
            hourOfDay,
            SUM(CASE WHEN eventType = 'TRIGGERED' THEN 1 ELSE 0 END) as triggered,
            SUM(CASE WHEN eventType = 'COMPLETED' THEN 1 ELSE 0 END) as completed
        FROM reminder_events
        WHERE reminderId = :reminderId 
          AND timestamp > :sinceMillis
        GROUP BY hourOfDay
        HAVING triggered >= 3
        ORDER BY (CAST(completed AS FLOAT) / triggered) DESC
        LIMIT 3
    """)
    suspend fun getBestHours(reminderId: Long, sinceMillis: Long): List<HourStats>

    // ==========================================
    // PROGRESS TRACKING (Dashboard)
    // ==========================================

    @Query("""
        SELECT 
            COUNT(CASE WHEN eventType = 'TRIGGERED' THEN 1 END) as triggered,
            COUNT(CASE WHEN eventType = 'COMPLETED' THEN 1 END) as completed,
            COUNT(CASE WHEN eventType = 'SNOOZED' THEN 1 END) as snoozed,
            COUNT(CASE WHEN eventType = 'DISMISSED' THEN 1 END) as dismissed,
            COUNT(CASE WHEN eventType = 'IGNORED' THEN 1 END) as ignored
        FROM reminder_events
        WHERE reminderId = :reminderId 
          AND timestamp BETWEEN :startMillis AND :endMillis
    """)
    suspend fun getProgressStats(
        reminderId: Long,
        startMillis: Long,
        endMillis: Long
    ): ProgressStats

    @Query("""
        SELECT AVG(responseTimeMillis) 
        FROM reminder_events
        WHERE reminderId = :reminderId 
          AND responseTimeMillis IS NOT NULL
          AND timestamp > :sinceMillis
    """)
    suspend fun getAverageResponseTime(reminderId: Long, sinceMillis: Long): Long?

    // ==========================================
    // DATE RANGE QUERIES (For Pipeline & Charts)
    // ==========================================

    @Query("""
        SELECT * FROM reminder_events
        WHERE reminderId = :reminderId 
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY timestamp ASC
    """)
    suspend fun getEventsBetween(
        reminderId: Long,
        startMillis: Long,
        endMillis: Long
    ): List<ReminderEventEntity>
}

// ==========================================
// DATA CLASSES FOR QUERY RESULTS
// ==========================================

data class HourCount(
    val hourOfDay: Int,
    val count: Int
)

data class HourStats(
    val hourOfDay: Int,
    val triggered: Int,
    val completed: Int
) {
    val completionRate: Float
        get() = if (triggered > 0) completed.toFloat() / triggered else 0f
}

data class ProgressStats(
    val triggered: Int,
    val completed: Int,
    val snoozed: Int,
    val dismissed: Int,
    val ignored: Int
) {
    val completionRate: Int
        get() = if (triggered > 0) (completed * 100 / triggered) else 0

    val engagementRate: Int
        get() = if (triggered > 0) ((completed + snoozed + dismissed) * 100 / triggered) else 0
}
