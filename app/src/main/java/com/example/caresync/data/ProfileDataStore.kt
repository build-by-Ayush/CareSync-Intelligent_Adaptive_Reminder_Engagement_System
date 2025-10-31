package com.example.caresync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val Context.dataStore by preferencesDataStore(name = "profile_prefs")

class ProfileDataStore(private val context: Context) {
    private val PROFILE_COMPLETED = booleanPreferencesKey("profile_completed")
    private val USERNAME = stringPreferencesKey("username")
    private val AGE = stringPreferencesKey("age")
    private val PURPOSE = stringPreferencesKey("purpose")
    private val PERMISSIONS_GRANTED = booleanPreferencesKey("permissions_granted")
    private val ADAPTIVE_LAYER = booleanPreferencesKey("adaptive_layer_enabled")  // ✅ NEW

    /**
     * Save profile data
     * ✅ UPDATED: Added adaptiveLayerEnabled parameter
     */
    suspend fun saveProfile(
        username: String,
        age: String,
        purpose: String,
        adaptiveLayerEnabled: Boolean = true  // ✅ NEW: Default ON
    ) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[AGE] = age
            prefs[PURPOSE] = purpose
            prefs[PROFILE_COMPLETED] = true
            prefs[ADAPTIVE_LAYER] = adaptiveLayerEnabled  // ✅ NEW
        }
    }

    /**
     * Read profile data (legacy - returns Triple)
     * Keep for backward compatibility
     */
    val profileData: Flow<Triple<String, String, String>> =
        context.dataStore.data.map { prefs ->
            Triple(
                prefs[USERNAME] ?: "",
                prefs[AGE] ?: "",
                prefs[PURPOSE] ?: ""
            )
        }

    /**
     * ✅ NEW: Read full profile data including adaptive layer
     */
    val userProfileFlow: Flow<UserProfile?> =
        context.dataStore.data.map { prefs ->
            val username = prefs[USERNAME]
            val age = prefs[AGE]
            val purpose = prefs[PURPOSE]
            val adaptiveLayer = prefs[ADAPTIVE_LAYER] ?: true  // ✅ Default ON

            if (username != null && age != null && purpose != null) {
                UserProfile(username, age, purpose, adaptiveLayer)
            } else null
        }

    /**
     * ✅ NEW: Read only adaptive layer setting
     */
    val isAdaptiveLayerEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[ADAPTIVE_LAYER] ?: true  // ✅ Default ON
        }

    /**
     * Check if profile completed
     */
    val isProfileCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PROFILE_COMPLETED] ?: false
        }

    /**
     * Save permissions granted
     */
    suspend fun setPermissionsGranted() {
        context.dataStore.edit { prefs ->
            prefs[PERMISSIONS_GRANTED] = true
        }
    }

    /**
     * Check if permissions granted
     */
    val arePermissionsGranted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PERMISSIONS_GRANTED] ?: false
        }

    /**
     * ✅ NEW: Update adaptive layer setting only
     */
    suspend fun setAdaptiveLayerEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[ADAPTIVE_LAYER] = enabled
        }
    }

    /**
     * Clear all profile data (logout)
     */
    suspend fun clearProfile() {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = ""
            prefs[AGE] = ""
            prefs[PURPOSE] = ""
            prefs[PROFILE_COMPLETED] = false
            prefs[PERMISSIONS_GRANTED] = false
            prefs[ADAPTIVE_LAYER] = true  // ✅ Reset to default
        }
    }

    /**
     * Complete logout - clears profile AND database
     */
    suspend fun fullLogout(context: Context) {
        // 1. Clear profile data
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = ""
            prefs[AGE] = ""
            prefs[PURPOSE] = ""
            prefs[PROFILE_COMPLETED] = false
            prefs[PERMISSIONS_GRANTED] = false
            prefs[ADAPTIVE_LAYER] = true  // ✅ Reset to default
        }

        // 2. Clear Room database (all tasks, analytics, everything)
        withContext(Dispatchers.IO) {
            val database = AppDatabase.get(context)
            database.clearAllTables()  // Nukes everything
        }
    }
}

/**
 * ✅ NEW: User profile data class
 */
data class UserProfile(
    val username: String,
    val age: String,
    val purpose: String,
    val adaptiveLayerEnabled: Boolean = true
)
