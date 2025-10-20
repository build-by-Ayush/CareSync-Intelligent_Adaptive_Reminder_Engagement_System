package com.example.caresync.domain

enum class TimePeriod(val displayName: String, val startHour: Int, val endHour: Int) {
    MORNING("Morning", 6, 12),
    AFTERNOON("Afternoon", 12, 18),
    EVENING("Evening", 18, 24);

    fun isWithinPeriod(hour: Int): Boolean {
        return hour in startHour until endHour
    }

    companion object {
        fun fromHour(hour: Int): TimePeriod {
            return when (hour) {
                in 6 until 12 -> MORNING
                in 12 until 18 -> AFTERNOON
                else -> EVENING
            }
        }
    }
}
