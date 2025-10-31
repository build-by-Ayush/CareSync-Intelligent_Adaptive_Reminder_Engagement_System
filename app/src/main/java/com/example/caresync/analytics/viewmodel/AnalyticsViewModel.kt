package com.example.caresync.analytics.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.caresync.analytics.domain.*
import com.example.caresync.analytics.gamification.AchievementEngine
import com.example.caresync.analytics.repository.AnalyticsRepository
import com.example.caresync.data.AppDatabase
import com.example.caresync.intelligence.riskdetection.RiskDetectionFactory
import com.example.caresync.intelligence.riskdetection.RiskDetectionRepository
import com.example.caresync.intelligence.riskdetection.TaskAtRiskData
import com.example.caresync.intelligence.riskdetection.RiskAnalyzer
import com.example.caresync.intelligence.riskdetection.RiskSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * UI State for Analytics Dashboard
 * ✅ UPDATED: Added Risk Detection fields
 */
data class AnalyticsUiState(
    // Overall statistics
    val statistics: UserStatistics? = null,

    // Chart data
    val completionRateData: List<CompletionRateData> = emptyList(),
    val productivityHours: List<HourlyProductivity> = emptyList(),
    val weeklyData: List<DailyCompletion> = emptyList(),

    // Tone performance
    val toneStats: List<ToneStats> = emptyList(),

    // Achievements
    val achievements: List<Achievement> = emptyList(),
    val unlockedCount: Int = 0,

    // Insights
    val insights: List<ProductivityInsight> = emptyList(),

    // Level information
    val levelInfo: LevelInfo? = null,

    // ✅ NEW: Risk Detection fields
    val atRiskTasks: List<TaskAtRiskData> = emptyList(),
    val atRiskCount: Int = 0,
    val showRiskDetails: Boolean = false,

    // UI state
    val isLoading: Boolean = true,
    val error: String? = null,

    // Selected date range
    val selectedDateRange: DateRange = DateRange.THIRTY_DAYS
)

/**
 * Date range options for filtering
 */
enum class DateRange(val days: Int, val label: String) {
    SEVEN_DAYS(7, "7 Days"),
    THIRTY_DAYS(30, "30 Days"),
    NINETY_DAYS(90, "90 Days")
}

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.get(application)
    private val analyticsDao = database.analyticsDao()
    private val reminderEventDao = database.reminderEventDao()
    private val reminderDao = database.reminderDao()

    private val achievementEngine = AchievementEngine(
        analyticsDao = analyticsDao,
        reminderEventDao = reminderEventDao
    )

    private val repository = AnalyticsRepository(
        analyticsDao = analyticsDao,
        reminderEventDao = reminderEventDao,
        reminderDao = reminderDao,
        achievementEngine = achievementEngine
    )

    // ✅ NEW: Risk Detection repository
    private val riskDetectionRepository: RiskDetectionRepository =
        RiskDetectionFactory.createRepository(application)

    // UI State
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    // ✅ NEW: Risk suggestions as StateFlow (derived from atRiskTasks)
    val riskSuggestions: StateFlow<List<RiskSuggestion>> = _uiState
        .map { state ->
            state.atRiskTasks.map { task ->
                RiskAnalyzer.generateSuggestions(task)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadAnalytics()
        seedAchievementsIfNeeded()
    }

    /**
     * Load all analytics data
     * ✅ UPDATED: Includes risk detection data
     */
    fun loadAnalytics(dateRange: DateRange = DateRange.THIRTY_DAYS) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load all existing data in parallel for better performance
                val statistics = repository.getUserStatistics(dateRange.days)
                val completionRateData = repository.getCompletionRateByDate(dateRange.days)
                val productivityHours = repository.getProductivityByHour()
                val weeklyData = repository.getWeeklyCompletions()
                val toneStats = repository.getToneEffectiveness()
                val achievements = repository.getAchievements()
                val insights = repository.generateInsights()

                // ✅ NEW: Load risk detection data
                val atRiskTasks = riskDetectionRepository.getTasksAtRisk()

                // Calculate level info
                val levelInfo = LevelInfo(
                    currentLevel = statistics.currentLevel,
                    currentPoints = statistics.totalPoints,
                    nextLevelPoints = statistics.nextLevelPoints,
                    progress = calculateLevelProgress(statistics)
                )

                Log.d("AnalyticsVM", "✅ Loaded ${atRiskTasks.size} at-risk tasks")

                _uiState.update {
                    it.copy(
                        statistics = statistics,
                        completionRateData = completionRateData,
                        productivityHours = productivityHours,
                        weeklyData = weeklyData,
                        toneStats = toneStats,
                        achievements = achievements,
                        unlockedCount = achievements.count { achievement -> achievement.isUnlocked },
                        insights = insights,
                        levelInfo = levelInfo,
                        // ✅ NEW: Add risk detection data
                        atRiskTasks = atRiskTasks,
                        atRiskCount = atRiskTasks.size,
                        isLoading = false,
                        selectedDateRange = dateRange
                    )
                }
            } catch (e: Exception) {
                Log.e("AnalyticsVM", "Error loading analytics", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load analytics: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Refresh all data
     * ✅ UPDATED: Includes risk detection refresh
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                // Validate streak before loading data
                repository.validateStreak()

                // Then load analytics (which now includes risk detection)
                loadAnalytics(_uiState.value.selectedDateRange)

                Log.d("AnalyticsVM", "✅ Data refreshed successfully")
            } catch (e: Exception) {
                Log.e("AnalyticsVM", "Error refreshing data", e)
                _uiState.update {
                    it.copy(error = "Failed to refresh: ${e.message}")
                }
            }
        }
    }

    /**
     * Change date range filter
     */
    fun changeDateRange(dateRange: DateRange) {
        if (dateRange != _uiState.value.selectedDateRange) {
            loadAnalytics(dateRange)
        }
    }

    /**
     * Record task completion and update progress
     */
    fun onTaskCompleted(pointsEarned: Int) {
        viewModelScope.launch {
            try {
                repository.updateUserProgress(
                    taskCompleted = true,
                    pointsEarned = pointsEarned
                )

                // Check for new achievement unlocks
                val newUnlocks = achievementEngine.checkAndUnlockAchievements()

                if (newUnlocks.isNotEmpty()) {
                    // Show achievement unlock notification/animation
                    newUnlocks.forEach { achievement ->
                        showAchievementUnlocked(achievement)
                    }
                }

                // Refresh data to show updates (including risk detection)
                refreshData()
            } catch (e: Exception) {
                Log.e("AnalyticsVM", "Error on task completion", e)
                _uiState.update {
                    it.copy(error = "Failed to update progress: ${e.message}")
                }
            }
        }
    }

    /**
     * ✅ NEW: Toggle risk details modal visibility
     */
    fun toggleRiskDetails() {
        _uiState.update { currentState ->
            currentState.copy(showRiskDetails = !currentState.showRiskDetails)
        }
        Log.d("AnalyticsVM", "Risk details toggled: ${_uiState.value.showRiskDetails}")
    }

    /**
     * ✅ NEW: Close risk details modal
     */
    fun closeRiskDetails() {
        _uiState.update { it.copy(showRiskDetails = false) }
    }

    /**
     * Get current level
     */
    fun getCurrentLevel(): Int {
        return _uiState.value.statistics?.currentLevel ?: 1
    }

    /**
     * Check if there are new achievements unlocked since last view
     */
    fun hasNewAchievements(): Boolean {
        // This would need to be tracked in SharedPreferences or database
        // For now, just check if any achievements are unlocked
        return _uiState.value.unlockedCount > 0
    }

    /**
     * Mark achievements as viewed (clear notification badge)
     */
    fun markAchievementsAsViewed() {
        // Save to SharedPreferences that user has viewed current achievements
        // This prevents the notification badge from showing again
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private fun calculateLevelProgress(stats: UserStatistics): Float {
        val currentLevelStart = when (stats.currentLevel) {
            1 -> 0
            2 -> 100
            3 -> 500
            4 -> 1500
            5 -> 5000
            else -> 0
        }

        val nextLevelStart = stats.nextLevelPoints

        if (nextLevelStart == Int.MAX_VALUE) return 1f  // Max level

        val pointsInLevel = stats.totalPoints - currentLevelStart
        val pointsNeeded = nextLevelStart - currentLevelStart

        return if (pointsNeeded > 0) {
            (pointsInLevel.toFloat() / pointsNeeded).coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    private fun seedAchievementsIfNeeded() {
        viewModelScope.launch {
            try {
                achievementEngine.seedAchievements()
            } catch (e: Exception) {
                // Silently fail - not critical
                Log.d("AnalyticsVM", "Achievements already seeded")
            }
        }
    }

    private fun showAchievementUnlocked(achievement: Achievement) {
        // This could trigger a UI event (like showing a dialog or toast)
        // For now, just log it
        // You can implement this with SharedFlow or LiveData events
        Log.d("AnalyticsVM", "🏆 Achievement unlocked: ${achievement.name}")
    }
}
