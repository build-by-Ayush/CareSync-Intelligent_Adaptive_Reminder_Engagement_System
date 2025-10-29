package com.example.caresync.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.scheduler.TaskConfigurationEngine
import com.example.caresync.scheduler.ConfigurationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReminderRepository(application)
    private val engine = TaskConfigurationEngine(application)

    // ✅ ADD: Debounce protection for toggle
    private var lastToggleTime = 0L

    // All reminders - directly expose ReminderSettings
    val reminders: StateFlow<List<ReminderSettings>> =
        repo.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList()
            )

    // Current edit state for Task Settings page
    private val _editState = MutableStateFlow(ReminderSettings(title = ""))
    val editState: StateFlow<ReminderSettings> = _editState

    // Load reminder for editing
    fun load(id: Long?) = viewModelScope.launch {
        if (id != null) {
            repo.get(id)?.let { _editState.value = it }
        } else {
            // Reset to empty state for new task
            _editState.value = ReminderSettings(title = "")
        }
    }

    // Update edit state
    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        _editState.value = transform(_editState.value)
    }

    // Save reminder
    fun save(context: Context) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val current = _editState.value.copy(
            updatedAt = now,
            createdAt = _editState.value.createdAt.takeIf { it > 0 } ?: now
        )

        val result = engine.processTaskConfiguration(current)

        when (result) {
            is ConfigurationResult.Success -> {
                Log.d("REMINDER_VM", "Task saved successfully: ${result.taskId}")
                Log.d("REMINDER_VM", "Scheduling info: ${result.schedulingInfo}")

                // Update edit state with new ID
                _editState.value = current.copy(id = result.taskId)
            }
            is ConfigurationResult.ValidationError -> {
                Log.e("REMINDER_VM", "Validation error: ${result.message}")
            }
            is ConfigurationResult.Failure -> {
                Log.e("REMINDER_VM", "Failed to save task: ${result.reason}")
            }
        }
    }

    // Delete reminder
    fun delete(context: Context) = viewModelScope.launch {
        val id = _editState.value.id
        if (id > 0) {
            try {
                // 1. Cancel scheduled notifications
                engine.deleteTaskCompletely(id)  // ✅ Use new function for actual deletion

                // 2. Delete from database
                repo.delete(id)

                // 3. Clear edit state
                _editState.value = ReminderSettings(title = "")

                Log.d("REMINDER_VM", "Task deleted successfully: $id")
            } catch (e: Exception) {
                Log.e("REMINDER_VM", "Error deleting task: $id", e)
            }
        }
    }

    // Log events (for analytics)
    fun logEvent(reminderId: Long, type: String, metadata: String? = null) =
        viewModelScope.launch {
            repo.logEvent(reminderId, type, metadata)
        }

    /**
     * ✅ FIXED: Toggle reminder enabled/disabled state
     *
     * Protection:
     * 1. Debounce rapid toggles (< 500ms)
     * 2. Check if state actually changed
     * 3. Update database + reschedule
     */
    fun toggleReminder(reminderId: Long, enabled: Boolean, context: Context) {
        viewModelScope.launch {
            try {
                // ✅ Debounce: Ignore rapid toggles
                val now = System.currentTimeMillis()
                if (now - lastToggleTime < 500) {
                    Log.d("ReminderViewModel", "⏭️ Ignoring rapid toggle (${now - lastToggleTime}ms)")
                    return@launch
                }
                lastToggleTime = now

                // Get current state
                val current = repo.get(reminderId) ?: return@launch

                // ✅ Check if state actually changed
                if (current.enabled == enabled) {
                    Log.d("ReminderViewModel", "⏭️ State already $enabled, skipping")
                    return@launch
                }

                // Update state
                val updated = current.copy(
                    enabled = enabled,
                    updatedAt = System.currentTimeMillis()
                )

                repo.upsert(updated)

                if (enabled) {
                    // Reschedule when enabled
                    engine.processTaskConfiguration(updated)
                } else {
                    // Cancel when disabled
                    engine.cancelTaskConfiguration(reminderId)
                }

                Log.d("ReminderViewModel", "✅ Toggled reminder $reminderId to enabled=$enabled")
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "❌ Error toggling reminder", e)
            }
        }
    }
}
