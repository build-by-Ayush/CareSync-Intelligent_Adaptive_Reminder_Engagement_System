package com.example.caresync.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val notes: String? = null,
    val enabled: Boolean = true,

    // scheduling
    val scheduledAtMillis: Long? = null,
    val timeOfDayMillis: Long? = null,
    val recurrenceType: String = "NONE",
    val repeatInterval: Int? = null,
    val repeatIntervalUnit: String? = null,
    val daysOfWeekJson: String = "[]",
    val startAtMillis: Long? = null,
    val endAtMillis: Long? = null,
    val zoneId: String? = null,

    // actions & deep links
    val targetAppPackage: String? = null,
    val targetUri: String? = null,

    // notification & delivery
    val notifyMethodsJson: String = "[\"PUSH\"]",
    val toneUri: String? = null,
    val vibration: Boolean = true,
    val smsNumber: String? = null,

    // behaviour
    val priority: String = "NORMAL",
    val triggerMode: String = "FIXED_TIME",
    val modelConfidenceThreshold: Float = 0.5f,
    val allowedWindowStart: Int? = null,
    val allowedWindowEnd: Int? = null,

    // snooze / escalation
    val snoozeOptionsJson: String = "[5,10,30]",
    val maxSnoozes: Int = 3,
    val snoozeDurationMinutes: Int = 10,  // ✅ NEW: How long to snooze
    val escalationPolicyJson: String? = null,

    // ✅ ADD THESE 3 LINES:
    val boostModeActive: Boolean = false,
    val boostModeEndTime: Long? = null,
    val boostModeFrequency: Int = 5,

    val allowedTimePeriodsJson: String = "[\"MORNING\",\"AFTERNOON\",\"EVENING\"]",

    val dueDate: Long? = null,

    // audit
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ==========================================
// INTERACTION LOG TABLE (EXPANDED FOR ANALYTICS)
// ==========================================
@Entity(
    tableName = "reminder_events",
    indices = [
        Index("reminderId"),
        Index("timestamp"),
        Index("eventType"),
        Index(value = ["reminderId", "timestamp"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE  // Auto-delete events when task deleted
        )
    ]
)
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    // ==========================================
    // CORE FIELDS (Keep Existing)
    // ==========================================
    val reminderId: Long,
    val eventType: String,           // TRIGGERED, COMPLETED, SNOOZED, DISMISSED, IGNORED
    val timestamp: Long,
    val metadataJson: String? = null,

    // ==========================================
    // TIME CONTEXT (For Blacklist/Highlight)
    // ==========================================
    val hourOfDay: Int = 0,          // 0-23 (extracted for fast queries)
    val dayOfWeek: Int = 0,          // 0-6 (0=Sunday)
    val isWeekend: Boolean = false,

    // ==========================================
    // USER BEHAVIOR TRACKING
    // ==========================================
    val responseTimeMillis: Long? = null,    // Time between TRIGGERED and action
    val snoozeDurationMinutes: Int? = null,  // If snoozed, duration
    val snoozeCount: Int = 0,                // Cumulative snooze count for this notification

    // ==========================================
    // DEVICE CONTEXT (For ML Features)
    // ==========================================
    val deviceState: String? = null,         // "SCREEN_ON", "SCREEN_OFF", "LOCKED"
    val activeAppPackage: String? = null,    // Which app was open
    val activeAppCategory: String? = null,   // Category of active app
    val screenTimeMinutes: Int? = null,      // Screen time in last hour
    val batteryLevel: Int? = null,           // Battery % (0-100)

    // ==========================================
    // NOTIFICATION DETAILS (Copy from Reminder)
    // ==========================================
    val notificationPriority: String? = null,
    val notificationMethod: String? = null,  // "PUSH", "VOICE", "SMS"
    val toneUsed: String? = null,
    val vibrationUsed: Boolean = false,

    // ==========================================
    // ML MODEL DATA
    // ==========================================
    val modelConfidence: Float? = null,      // Model's confidence score
    val triggerSource: String = "SCHEDULER"  // "SCHEDULER", "MODEL", "USER_MANUAL"
)