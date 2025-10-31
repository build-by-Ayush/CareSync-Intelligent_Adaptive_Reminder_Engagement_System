package com.example.caresync.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Preferred Times - Tracks hours when user frequently COMPLETES tasks
 *
 * Opposite of BlacklistHour - these are the GOOD hours for notifications.
 * Used by adaptive intelligence layer to learn best notification times.
 *
 * Example:
 * - Task "Gym": Hour 6 (80% completion rate) → Preferred
 * - Task "Study": Hour 20 (75% completion rate) → Preferred
 */
@Entity(
    tableName = "preferred_times",
    indices = [
        Index(value = ["reminderId", "hourOfDay"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PreferredTime(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val reminderId: Long,          // Which task this belongs to
    val hourOfDay: Int,            // Hour (0-23)

    // Statistics
    val completionRate: Float,     // Completion rate (0.0 - 1.0)
    val totalNotifications: Int,   // Total notifications sent at this hour
    val totalCompletions: Int,     // Total completions at this hour
    val totalDismissals: Int,      // Total dismissals at this hour

    // Confidence scoring
    val confidence: Float,         // Statistical confidence (0.0 - 1.0)

    // Audit
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Is this hour a strong preferred time?
     * (High completion rate + sufficient sample size)
     */
    val isStrongPreferred: Boolean
        get() = completionRate >= 0.7f && confidence >= 0.6f

    /**
     * Is this hour a weak preferred time?
     * (Decent completion rate but low confidence)
     */
    val isWeakPreferred: Boolean
        get() = completionRate >= 0.5f && confidence < 0.6f
}
