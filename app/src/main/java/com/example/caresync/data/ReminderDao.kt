package com.example.caresync.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// REMINDER DAO (No Changes)
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

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): ReminderEntity?
}

// ==========================================
// REMINDER EVENT DAO (Updated with Snooze Queries)
// ==========================================
@Dao
interface ReminderEventDao {

    // ==========================================
    // BASIC CRUD
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
    // BLACKLIST ANALYSIS
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
    // HIGHLIGHT ANALYSIS
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
    // PROGRESS TRACKING
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
    // DATE RANGE QUERIES
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

    @Query("""
        SELECT * FROM reminder_events 
        WHERE eventType = :eventType 
        AND hourOfDay >= :startHour 
        AND hourOfDay <= :endHour
    """)
    suspend fun getEventsByTypeAndTimeRange(
        eventType: String,
        startHour: Int,
        endHour: Int
    ): List<ReminderEventEntity>

    @Query("SELECT * FROM reminder_events")
    suspend fun getAllEvents(): List<ReminderEventEntity>

    // ==========================================
    // STREAK TRACKING QUERIES
    // ==========================================

    /**
     * Get all dates that had at least one notification sent
     * Used to determine which days should count for streak tracking
     */
    @Query("""
        SELECT DISTINCT DATE(timestamp / 1000, 'unixepoch') as date
        FROM reminder_events
        WHERE timestamp >= :startDate 
        AND timestamp <= :endDate
        AND eventType = 'TRIGGERED'
        ORDER BY date ASC
    """)
    suspend fun getDatesWithNotifications(startDate: Long, endDate: Long): List<String>

    /**
     * Check if user completed at least one task on a specific date
     * Returns true if any completion exists on that date
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM reminder_events
            WHERE DATE(timestamp / 1000, 'unixepoch') = :date
            AND eventType = 'COMPLETED'
        )
    """)
    suspend fun hasCompletionOnDate(date: String): Boolean

    /**
     * Get count of completed tasks on a specific date
     * Used for detailed streak analysis
     */
    @Query("""
        SELECT COUNT(*) FROM reminder_events
        WHERE DATE(timestamp / 1000, 'unixepoch') = :date
        AND eventType = 'COMPLETED'
    """)
    suspend fun getCompletionCountOnDate(date: String): Int

    // ==========================================
    // POINTS CALCULATION QUERIES
    // ==========================================

    /**
     * Get the most recent SENT event before a completion
     * Used to calculate response time for points calculation
     *
     * @param reminderId The task ID
     * @param completionTimestamp When the task was completed
     * @return The SENT event that triggered this completion, or null
     */
    @Query("""
        SELECT * FROM reminder_events
        WHERE reminderId = :reminderId
        AND eventType = 'TRIGGERED'
        AND timestamp <= :completionTimestamp
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLastSentEventBeforeCompletion(
        reminderId: Long,
        completionTimestamp: Long
    ): ReminderEventEntity?

    /**
     * Get all SENT events for a reminder on a specific date
     * Useful if multiple notifications sent on same day
     */
    @Query("""
        SELECT * FROM reminder_events
        WHERE reminderId = :reminderId
        AND eventType = 'TRIGGERED'
        AND DATE(timestamp / 1000, 'unixepoch') = DATE(:completionTimestamp / 1000, 'unixepoch')
        ORDER BY timestamp DESC
    """)
    suspend fun getSentEventsOnDate(
        reminderId: Long,
        completionTimestamp: Long
    ): List<ReminderEventEntity>

    @Query("""
        SELECT COUNT(*) 
        FROM reminder_events 
        WHERE eventType = :eventType
        AND (triggerSource IS NULL OR triggerSource NOT LIKE '%BOOST%')
    """)
    suspend fun countByTypeExcludingBoost(eventType: String): Int

    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId AND timestamp >= :sinceMillis ORDER BY timestamp ASC")
    suspend fun getEventsForReminderSince(reminderId: Long, sinceMillis: Long): List<ReminderEventEntity>

    // ==========================================
    // ✅ NEW: SNOOZE-AWARE ANALYTICS QUERIES
    // ==========================================

    /**
     * Count events by type, EXCLUDING snooze re-triggers
     * This gives accurate notification count for analytics
     *
     * Use this for completion rate calculations instead of countByType()
     */
    @Query("""
        SELECT COUNT(*) 
        FROM reminder_events 
        WHERE eventType = :eventType 
        AND isSnoozedRetrigger = 0
        AND (triggerSource IS NULL OR triggerSource NOT LIKE '%BOOST%')
    """)
    suspend fun countByTypeExcludingSnoozeAndBoost(eventType: String): Int

    /**
     * Get total times user snoozed notifications
     * For snooze behavior analytics
     */
    @Query("""
        SELECT COUNT(*) 
        FROM reminder_events 
        WHERE eventType = 'SNOOZED'
    """)
    suspend fun getTotalSnoozedCount(): Int

    /**
     * Get snooze success rate
     * Returns (completedAfterSnooze, totalSnoozed)
     *
     * Success = task was eventually completed after being snoozed
     */
    @Query("""
        SELECT 
            COUNT(DISTINCT CASE 
                WHEN EXISTS(
                    SELECT 1 FROM reminder_events e2 
                    WHERE e2.reminderId = e.reminderId 
                    AND e2.eventType = 'COMPLETED'
                    AND e2.timestamp > e.timestamp
                ) THEN e.reminderId 
            END) as completed,
            COUNT(DISTINCT e.reminderId) as total
        FROM reminder_events e
        WHERE e.eventType = 'SNOOZED'
    """)
    suspend fun getSnoozeSuccessRate(): SnoozeSuccessStats

    /**
     * Get events for a reminder (used for achievement checks)
     */
    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId ORDER BY timestamp ASC")
    suspend fun getEventsForReminder(reminderId: Long): List<ReminderEventEntity>

    /**
     * Get snooze statistics for a specific reminder
     * Useful for per-task analytics
     */
    @Query("""
        SELECT 
            COUNT(*) as snoozeCount,
            AVG(snoozeDurationMinutes) as avgDuration,
            MAX(snoozeCount) as maxConsecutiveSnoozes
        FROM reminder_events
        WHERE reminderId = :reminderId 
        AND eventType = 'SNOOZED'
    """)
    suspend fun getSnoozeStatsForReminder(reminderId: Long): SnoozeStats?

    /**
     * Get total UNIQUE notifications (excludes snooze re-triggers)
     * This is the correct denominator for completion rate
     */
    @Query("""
        SELECT COUNT(*) 
        FROM reminder_events 
        WHERE eventType = 'TRIGGERED'
        AND isSnoozedRetrigger = 0
        AND (triggerSource IS NULL OR triggerSource NOT LIKE '%BOOST%')
    """)
    suspend fun getTotalUniqueNotifications(): Int
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

// ✅ NEW: Snooze-specific result classes
data class SnoozeSuccessStats(
    val completed: Int,  // How many snoozed tasks were eventually completed
    val total: Int       // Total tasks that were snoozed
) {
    val successRate: Float
        get() = if (total > 0) completed.toFloat() / total else 0f
}

data class SnoozeStats(
    val snoozeCount: Int,           // Total times snoozed
    val avgDuration: Float?,        // Average snooze duration
    val maxConsecutiveSnoozes: Int  // Max snoozes in a row
)
