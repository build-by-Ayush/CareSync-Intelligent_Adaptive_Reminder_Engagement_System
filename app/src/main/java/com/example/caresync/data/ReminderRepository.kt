package com.example.caresync.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.caresync.domain.ReminderSettings

class ReminderRepository(context: Context) {
    private val reminderDao = AppDatabase.get(context).reminderDao()
    private val eventDao = AppDatabase.get(context).reminderEventDao()

    fun observeAll(): Flow<List<ReminderSettings>> =
        reminderDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): ReminderSettings? =
        reminderDao.getById(id)?.toDomain()

    suspend fun upsert(settings: ReminderSettings): Long =
        reminderDao.upsert(settings.toEntity())

    suspend fun delete(id: Long) = reminderDao.deleteById(id)

    suspend fun logEvent(reminderId: Long, type: String, metadata: String? = null) {
        eventDao.insert(
            ReminderEventEntity(
                reminderId = reminderId,
                eventType = type,
                timestamp = System.currentTimeMillis(),
                metadataJson = metadata
            )
        )
    }
}
