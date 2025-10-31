package com.example.caresync.intelligence.riskdetection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

/**
 * ViewModel for Risk Detection Dashboard
 */
class RiskDetectionViewModel(
    private val repository: RiskDetectionRepository
) : ViewModel() {

    private val _atRiskTasks = MutableStateFlow<List<TaskAtRiskData>>(emptyList())
    val atRiskTasks: StateFlow<List<TaskAtRiskData>> = _atRiskTasks.asStateFlow()

    private val _taskCount = MutableStateFlow(0)
    val taskCount: StateFlow<Int> = _taskCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadAtRiskTasks()
    }

    /**
     * Load all at-risk tasks from repository
     */
    fun loadAtRiskTasks() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val tasks = repository.getTasksAtRisk()
                _atRiskTasks.value = tasks
                _taskCount.value = tasks.size

                Log.d("RiskDetectionVM", "Loaded ${tasks.size} at-risk tasks")

            } catch (e: Exception) {
                Log.e("RiskDetectionVM", "Error loading tasks", e)
                _error.value = e.message ?: "Unknown error"
                _atRiskTasks.value = emptyList()

            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Refresh data
     */
    fun refresh() {
        loadAtRiskTasks()
    }

    /**
     * Get single task details for detailed view
     */
    fun getTaskDetails(reminderId: Long): TaskAtRiskData? {
        return _atRiskTasks.value.find { it.reminderId == reminderId }
    }
}
