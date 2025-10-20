package com.example.caresync.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey


/**
 * Blacklist Hours - Tracks hours when user frequently dismisses notifications
 *
 * Used by NotificationDecisionPipeline to avoid sending notifications at bad times.
 * Applies to Model Mode (ML checks) and random-time modes (Days, Weekdays).
 */
@Entity(
    tableName = "blacklist_hours",
    indices = [
        Index(value = ["reminderId", "hourOfDay"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE  // ✅ ADD THIS - Auto-delete blacklist when task deleted
        )
    ]
)
data class BlacklistHour(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val reminderId: Long,
    val hourOfDay: Int,  // 0-23
    val dismissalCount: Int,
    val lastDismissalTimestamp: Long,
    val createdAt: Long = System.currentTimeMillis()
)
