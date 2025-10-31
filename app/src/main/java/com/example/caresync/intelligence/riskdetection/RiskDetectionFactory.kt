package com.example.caresync.intelligence.riskdetection

import android.content.Context
import com.example.caresync.data.AppDatabase

/**
 * Factory to create RiskDetection components without dependency injection
 */
object RiskDetectionFactory {

    /**
     * Create RiskDetectionRepository
     */
    fun createRepository(context: Context): RiskDetectionRepository {
        val database = AppDatabase.get(context)
        return RiskDetectionRepository(
            preferredTimesDao = database.preferredTimesDao(),
            reminderEventDao = database.reminderEventDao(),  // ✅ ADDED
            reminderDao = database.reminderDao()
        )
    }

    /**
     * Create RiskDetectionViewModel
     */
    fun createViewModel(context: Context): RiskDetectionViewModel {
        val repository = createRepository(context)
        return RiskDetectionViewModel(repository)
    }
}
