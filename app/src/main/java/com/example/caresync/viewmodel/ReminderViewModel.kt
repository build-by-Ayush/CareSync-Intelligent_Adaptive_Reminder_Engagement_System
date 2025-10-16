package com.example.caresync.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.caresync.data.ReminderRepository
import com.example.caresync.domain.ReminderSettings
import com.example.caresync.scheduler.NotificationScheduler
import com.example.caresync.scheduler.NotificationSchedulerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReminderRepository(application)
    private val scheduler: NotificationScheduler = NotificationSchedulerImpl()

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
        val id = repo.upsert(current)
        val saved = current.copy(id = if (current.id == 0L) id else current.id)
        scheduler.scheduleReminder(context, saved)
    }

    // Delete reminder
    fun delete(context: Context) = viewModelScope.launch {
        val id = _editState.value.id
        if (id > 0) {
            repo.delete(id)
            scheduler.cancelReminder(context, id)
        }
    }

    // Log events (for analytics)
    fun logEvent(reminderId: Long, type: String, metadata: String? = null) =
        viewModelScope.launch {
            repo.logEvent(reminderId, type, metadata)
        }
}
