package com.example.caresync.data

import androidx.room.*

@Dao
interface BlacklistHourDao {

    @Query("SELECT * FROM blacklist_hours WHERE reminderId = :reminderId AND hourOfDay = :hour")
    suspend fun getBlacklist(reminderId: Long, hour: Int): BlacklistHour?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blacklist: BlacklistHour)

    @Query("""
        UPDATE blacklist_hours 
        SET dismissalCount = dismissalCount + 1, 
            lastDismissalTimestamp = :timestamp 
        WHERE reminderId = :reminderId AND hourOfDay = :hour
    """)
    suspend fun incrementDismissal(reminderId: Long, hour: Int, timestamp: Long): Int

    @Query("SELECT * FROM blacklist_hours WHERE reminderId = :reminderId AND dismissalCount >= :threshold")
    suspend fun getBlacklistedHours(reminderId: Long, threshold: Int = 5): List<BlacklistHour>

    @Query("DELETE FROM blacklist_hours WHERE reminderId = :reminderId")
    suspend fun deleteForReminder(reminderId: Long): Int

    @Query("DELETE FROM blacklist_hours WHERE lastDismissalTimestamp < :cutoffMillis")
    suspend fun deleteOldBlacklists(cutoffMillis: Long): Int
}
