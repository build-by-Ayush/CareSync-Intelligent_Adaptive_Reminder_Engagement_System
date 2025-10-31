package com.example.caresync.data

import com.example.caresync.domain.*

fun ReminderEntity.toDomain(): ReminderSettings = ReminderSettings(
    id = id,
    title = title,
    notes = notes,
    enabled = enabled,
    scheduledAtMillis = scheduledAtMillis,
    timeOfDayMillis = timeOfDayMillis,
    recurrenceType = RecurrenceType.valueOf(recurrenceType),
    repeatInterval = repeatInterval,
    repeatIntervalUnit = repeatIntervalUnit?.let(IntervalUnit::valueOf),
    daysOfWeek = parseIntSet(daysOfWeekJson),
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    zoneId = zoneId,
    targetAppPackage = targetAppPackage,
    targetUri = targetUri,
    notifyMethods = parseEnumSet(notifyMethodsJson, NotifyMethod::valueOf),
    toneUri = toneUri,
    vibration = vibration,
    smsNumber = smsNumber,
    priority = Priority.valueOf(priority),
    triggerMode = TriggerMode.valueOf(triggerMode),
    modelConfidenceThreshold = modelConfidenceThreshold,
    allowedWindowStart = allowedWindowStart,
    allowedWindowEnd = allowedWindowEnd,
    snoozeOptions = parseIntList(snoozeOptionsJson),
    maxSnoozes = maxSnoozes,
    snoozeDurationMinutes = snoozeDurationMinutes,
    boostModeActive = boostModeActive,
    boostModeEndTime = boostModeEndTime,
    boostModeFrequency = boostModeFrequency,
    dueDate = dueDate,  // ✅ ADD
    allowedTimePeriods = parseTimePeriods(allowedTimePeriodsJson),  // ✅ ADD THIS
    escalationPolicy = parseEscalation(escalationPolicyJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
    voiceModel = voiceModel,
    shareProgressEnabled = shareProgressEnabled,
    shareProgressContactName = shareProgressContactName,
    shareProgressContactPhone = shareProgressContactPhone,
    sendDailyReport = sendDailyReport,
    sendWeeklyReport = sendWeeklyReport,
    sendStrugglingAlerts = sendStrugglingAlerts,
    autoOptimizeEnabled = autoOptimizeEnabled,
    lastOptimizedAt = lastOptimizedAt,
    originalMinOccurrence = originalMinOccurrence,
    frequencyMultiplier = frequencyMultiplier,
    lastFrequencyOptimization = lastFrequencyOptimization,
    originalPriority = originalPriority, // String? → map as needed
    priorityAutoAdjusted = priorityAutoAdjusted // Converts int/boolean: 0/false
)

fun ReminderSettings.toEntity(): ReminderEntity = ReminderEntity(
    id = id,
    title = title,
    notes = notes,
    enabled = enabled,
    scheduledAtMillis = scheduledAtMillis,
    timeOfDayMillis = timeOfDayMillis,
    recurrenceType = recurrenceType.name,
    repeatInterval = repeatInterval,
    repeatIntervalUnit = repeatIntervalUnit?.name,
    daysOfWeekJson = toJson(daysOfWeek.toList()),
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    zoneId = zoneId,
    targetAppPackage = targetAppPackage,
    targetUri = targetUri,
    notifyMethodsJson = toJson(notifyMethods.map { it.name }),
    toneUri = toneUri,
    vibration = vibration,
    smsNumber = smsNumber,
    priority = priority.name,
    triggerMode = triggerMode.name,
    modelConfidenceThreshold = modelConfidenceThreshold,
    allowedWindowStart = allowedWindowStart,
    allowedWindowEnd = allowedWindowEnd,
    snoozeOptionsJson = toJson(snoozeOptions),
    maxSnoozes = maxSnoozes,
    snoozeDurationMinutes = snoozeDurationMinutes,
    boostModeActive = boostModeActive,
    boostModeEndTime = boostModeEndTime,
    boostModeFrequency = boostModeFrequency,
    dueDate = dueDate,  // ✅ ADD
    allowedTimePeriodsJson = allowedTimePeriods.joinToString(",") { it.name },  // ✅ ADD THIS
    escalationPolicyJson = escalationPolicy?.let { toJson(it) },
    createdAt = createdAt,
    updatedAt = updatedAt,
    voiceModel = voiceModel,
    shareProgressEnabled = shareProgressEnabled,
    shareProgressContactName = shareProgressContactName,
    shareProgressContactPhone = shareProgressContactPhone,
    sendDailyReport = sendDailyReport,
    sendWeeklyReport = sendWeeklyReport,
    sendStrugglingAlerts = sendStrugglingAlerts,
    autoOptimizeEnabled = autoOptimizeEnabled,
    lastOptimizedAt = lastOptimizedAt,
    originalMinOccurrence = originalMinOccurrence,
    frequencyMultiplier = frequencyMultiplier,
    lastFrequencyOptimization = lastFrequencyOptimization,
    originalPriority = originalPriority,
    priorityAutoAdjusted = priorityAutoAdjusted // (Room handles boolean → int automatically if needed)
)

// ✅ ADD THIS HELPER FUNCTION AT THE BOTTOM
private fun parseTimePeriods(json: String): List<TimePeriod> {
    return try {
        json.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .mapNotNull { name ->
                try {
                    TimePeriod.valueOf(name)
                } catch (e: Exception) {
                    null
                }
            }
    } catch (e: Exception) {
        // Default: all periods allowed
        listOf(TimePeriod.MORNING, TimePeriod.AFTERNOON, TimePeriod.EVENING)
    }
}