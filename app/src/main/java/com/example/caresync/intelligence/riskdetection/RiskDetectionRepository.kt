package com.example.caresync.intelligence.riskdetection

import android.util.Log
import com.example.caresync.data.PreferredTimesDao
import com.example.caresync.data.ReminderDao
import com.example.caresync.data.ReminderEventDao
import com.example.caresync.data.ReminderEventEntity
import com.example.caresync.domain.Priority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for Risk Detection Dashboard
 * Queries learning data to identify and suggest fixes for struggling tasks
 */
class RiskDetectionRepository(
    private val preferredTimesDao: PreferredTimesDao,
    private val reminderEventDao: ReminderEventDao,
    private val reminderDao: ReminderDao
) {

    /**
     * Get all tasks that are at risk (low completion rate)
     *
     * Filters:
     * - Completion rate < 40% (struggling)
     * - Confidence >= 0.4 (enough data)
     * - At least 5 data points (not noise)
     */
    suspend fun getTasksAtRisk(): List<TaskAtRiskData> {
        return try {
            val allTasks = reminderDao.getAllReminders()
            val atRiskTasks = mutableListOf<TaskAtRiskData>()

            allTasks.forEach { task ->
                // Query average stats for this task
                val stats = preferredTimesDao.getAverageStats(task.id) ?: run {
                    Log.d("RiskDetection", "No stats for task ${task.id}")
                    return@forEach
                }

                // ✅ Filter 1: Need minimum confidence (data quality)
                if (stats.avgConfidence < 0.4f) {
                    Log.d("RiskDetection", "Task ${task.id}: Low confidence ${stats.avgConfidence}")
                    return@forEach
                }

                // ✅ Filter 2: Need minimum samples
                if (stats.dataPoints < 5) {
                    Log.d("RiskDetection", "Task ${task.id}: Low samples ${stats.dataPoints}")
                    return@forEach
                }

                // ✅ Filter 3: Task must be struggling
                if (stats.avgCompletionRate >= 0.4f) {
                    Log.d("RiskDetection", "Task ${task.id}: Completion rate OK ${stats.avgCompletionRate}")
                    return@forEach
                }

                // ✅ Task passed all filters - get worst hour
                val worstHour = preferredTimesDao.getWorstHour(task.id)
                val bestHours = preferredTimesDao.getBestHours(task.id)

                // ✅ Get Priority - convert string to enum
                val priority = try {
                    Priority.valueOf(task.priority)
                } catch (e: Exception) {
                    Priority.NORMAL
                }

                atRiskTasks.add(
                    TaskAtRiskData(
                        reminderId = task.id,
                        taskTitle = task.title,
                        completionRate = stats.avgCompletionRate,
                        completionPercentage = (stats.avgCompletionRate * 100).toInt(),
                        confidence = stats.avgConfidence,
                        totalNotifications = stats.totalNotifications,
                        totalCompletions = stats.totalCompletions,
                        dataPoints = stats.dataPoints,
                        worstHour = worstHour?.hourOfDay ?: 0,
                        worstHourCompletion = worstHour?.completionRate ?: 0f,
                        bestHours = bestHours.map { entity ->
                            PreferredTimeData(
                                hourOfDay = entity.hourOfDay,
                                completionRate = entity.completionRate,
                                totalNotifications = entity.totalNotifications
                            )
                        },
                        currentPriority = priority,
                        frequencyMultiplier = task.frequencyMultiplier,
                        priorityAutoAdjusted = task.priorityAutoAdjusted
                    )
                )
            }

            Log.d("RiskDetection", "Found ${atRiskTasks.size} at-risk tasks")
            atRiskTasks

        } catch (e: Exception) {
            Log.e("RiskDetection", "Failed to get at-risk tasks", e)
            emptyList()
        }
    }

    /**
     * Get count of at-risk tasks (for banner)
     */
    suspend fun countTasksAtRisk(): Int {
        return getTasksAtRisk().size
    }

    /**
     * Get at-risk tasks as Flow (for reactive updates)
     */
    fun getTasksAtRiskFlow(): Flow<List<TaskAtRiskData>> = flow {
        try {
            val tasks = getTasksAtRisk()
            emit(tasks)
        } catch (e: Exception) {
            Log.e("RiskDetection", "Error in flow", e)
            emit(emptyList())
        }
    }
}


/**
 * Data class for at-risk task information
 */
data class TaskAtRiskData(
    val reminderId: Long,
    val taskTitle: String,
    val completionRate: Float,              // 0.0 to 1.0
    val completionPercentage: Int,          // 0 to 100 (for display)
    val confidence: Float,                   // 0.0 to 1.0 (data quality)
    val totalNotifications: Int,
    val totalCompletions: Int,
    val dataPoints: Int,                     // Number of hours analyzed
    val worstHour: Int,                     // Hour with lowest completion
    val worstHourCompletion: Float,         // Completion rate at worst hour
    val bestHours: List<PreferredTimeData>,  // Top 3 performing hours
    val currentPriority: Priority,
    val frequencyMultiplier: Float,
    val priorityAutoAdjusted: Boolean
)

/**
 * Simplified data class for best hours (mapped from database entity)
 */
data class PreferredTimeData(
    val hourOfDay: Int,
    val completionRate: Float,
    val totalNotifications: Int
)
