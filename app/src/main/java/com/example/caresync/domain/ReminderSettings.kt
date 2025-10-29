package com.example.caresync.domain

enum class RecurrenceType { NONE, DAILY, WEEKLY, INTERVAL, CUSTOM }
enum class IntervalUnit { MINUTE, HOUR, DAY }
enum class NotifyMethod { PUSH, VOICE, SMS }
enum class Priority { LOW, NORMAL, HIGH, CRITICAL }
enum class TriggerMode { FIXED_TIME, MODEL_ASSISTED, HYBRID, MANUAL }

data class EscalationPolicy(
    val afterSnoozes: Int,
    val escalateToVoice: Boolean
)

data class ReminderSettings(
    val id: Long = 0L,
    val title: String,
    val notes: String? = null,
    val enabled: Boolean = true,

    // scheduling
    val scheduledAtMillis: Long? = null,
    val timeOfDayMillis: Long? = null,
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    val repeatInterval: Int? = null,
    val repeatIntervalUnit: IntervalUnit? = null,
    val daysOfWeek: Set<Int> = emptySet(),
    val startAtMillis: Long? = null,
    val endAtMillis: Long? = null,
    val zoneId: String? = null,

    // actions & deep links
    val targetAppPackage: String? = null,
    val targetUri: String? = null,

    // notification & delivery
    val notifyMethods: Set<NotifyMethod> = setOf(NotifyMethod.PUSH),
    val toneUri: String? = null,
    val vibration: Boolean = true,
    val smsNumber: String? = null,

    // behaviour
    val priority: Priority = Priority.NORMAL,
    val triggerMode: TriggerMode = TriggerMode.FIXED_TIME,
    val modelConfidenceThreshold: Float = 0.5f,
    val allowedWindowStart: Int? = null,
    val allowedWindowEnd: Int? = null,

    // snooze / escalation
    val snoozeOptions: List<Int> = listOf(5, 10, 30),
    val maxSnoozes: Int = 3,
    val snoozeDurationMinutes: Int = 10,  // ✅ NEW: How long to snooze

    val escalationPolicy: EscalationPolicy? = null,

    // audit
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // ✅ ADD THESE 3 LINES:
    val boostModeActive: Boolean = false,
    val boostModeEndTime: Long? = null,
    val boostModeFrequency: Int = 5,

    val allowedTimePeriods: List<TimePeriod> = listOf(
        TimePeriod.MORNING,
        TimePeriod.AFTERNOON,
        TimePeriod.EVENING
    ),

    val voiceModel: String? = null,

    val dueDate: Long? = null,

    // ✅ NEW: Share Progress fields
    val shareProgressEnabled: Boolean = false,
    val shareProgressContactName: String? = null,
    val shareProgressContactPhone: String? = null,
    val sendDailyReport: Boolean = false,
    val sendWeeklyReport: Boolean = false,
    val sendStrugglingAlerts: Boolean = false
)
