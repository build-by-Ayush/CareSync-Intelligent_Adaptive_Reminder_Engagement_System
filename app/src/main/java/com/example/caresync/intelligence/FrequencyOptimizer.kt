package com.example.caresync.intelligence

import android.content.Context
import android.util.Log
import com.example.caresync.data.AppDatabase
import com.example.caresync.data.toEntity
import com.example.caresync.domain.EventTypes
import com.example.caresync.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FrequencyOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "FrequencyOptimizer"
        private const val WINDOW_DAYS = 14
        private const val MIN_DATA = 10 // Minimum number of notifications before optimizing
        private const val MIN_FREQUENCY = 1
        private const val MAX_FREQUENCY = 10
    }

    suspend fun optimizeTaskFrequency(reminder: ReminderSettings) = withContext(Dispatchers.IO) {
        if (!reminder.autoOptimizeEnabled) {
            Log.d(TAG, "⏭️ Skipping: Adaptive layer disabled for '${reminder.title}'")
            return@withContext
        }

        val database = AppDatabase.get(context)
        val eventDao = database.reminderEventDao()
        val reminderDao = database.reminderDao()

        // Calculate completion rate in last WINDOW_DAYS
        val now = System.currentTimeMillis()
        val windowStart = now - (WINDOW_DAYS * 24 * 60 * 60 * 1000L)
        val events = eventDao.getEventsBetween(reminder.id, windowStart, now)

        val triggered = events.count { it.eventType == EventTypes.TRIGGERED }
        val completed = events.count { it.eventType == EventTypes.COMPLETED }

        // Not enough data? Don't adjust.
        if (triggered < MIN_DATA || reminder.originalMinOccurrence == null) {
            Log.d(TAG, "⏳ Not enough data to optimize. Triggered: $triggered (needs $MIN_DATA)")
            return@withContext
        }

        val completionRate = if (triggered > 0) completed.toFloat() / triggered else 0f
        val newFrequency = calculateOptimalFrequency(reminder.originalMinOccurrence, completionRate, triggered)

        val newMultiplier = newFrequency.toFloat() / reminder.originalMinOccurrence
        val boundedMultiplier = newMultiplier.coerceIn(0.2f, 3.0f) // safety limits

        val lastOpt = reminder.lastFrequencyOptimization
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        if (now - lastOpt < sevenDays) {
            Log.d(TAG, "🕒 Optimization cooldown active for '${reminder.title}'")
            return@withContext // Don't optimize more than once/week
        }

        // Only update if changed
        if (reminder.frequencyMultiplier != boundedMultiplier) {
            reminderDao.upsert(
                reminder.copy(
                    frequencyMultiplier = boundedMultiplier,
                    lastFrequencyOptimization = now
                ).toEntity()
            )
            Log.d(TAG, "🤖 Optimized '${reminder.title}': completionRate=${(completionRate * 100).toInt()}%, originalMin=${reminder.originalMinOccurrence}, newMultiplier=$boundedMultiplier")
        } else {
            Log.d(TAG, "🔁 No change for '${reminder.title}': Multiplier is optimal ($boundedMultiplier)")
        }
    }

    private fun calculateOptimalFrequency(
        originalMin: Int,
        completionRate: Float,
        totalNotifications: Int
    ): Int {
        if (totalNotifications < MIN_DATA) return originalMin

        val adjustment = when {
            completionRate >= 0.85f -> -1
            completionRate >= 0.70f -> 0
            completionRate >= 0.50f -> 0
            completionRate >= 0.30f -> +1
            else -> +2
        }

        val newFrequency = originalMin + adjustment
        return newFrequency.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
    }
}
