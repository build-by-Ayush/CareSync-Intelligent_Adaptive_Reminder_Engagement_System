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

    // Save profile data
    suspend fun saveProfile(username: String, age: String, purpose: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[AGE] = age
            prefs[PURPOSE] = purpose
            prefs[PROFILE_COMPLETED] = true
        }
    }

    // Read profile data
    val profileData: Flow<Triple<String, String, String>> =
        context.dataStore.data.map { prefs ->
            Triple(
                prefs[USERNAME] ?: "",
                prefs[AGE] ?: "",
                prefs[PURPOSE] ?: ""
            )
        }

    // Check if profile completed
    val isProfileCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PROFILE_COMPLETED] ?: false
        }

    // Save permissions granted
    suspend fun setPermissionsGranted() {
        context.dataStore.edit { prefs ->
            prefs[PERMISSIONS_GRANTED] = true
        }
    }

    // Check if permissions granted
    val arePermissionsGranted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PERMISSIONS_GRANTED] ?: false
        }

    // ✅ NEW: Clear all profile data (logout)
    suspend fun clearProfile() {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = ""
            prefs[AGE] = ""
            prefs[PURPOSE] = ""
            prefs[PROFILE_COMPLETED] = false
            prefs[PERMISSIONS_GRANTED] = false
        }
    }

    // Complete logout - clears profile AND database
    suspend fun fullLogout(context: Context) {
        // 1. Clear profile data
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = ""
            prefs[AGE] = ""
            prefs[PURPOSE] = ""
            prefs[PROFILE_COMPLETED] = false
            prefs[PERMISSIONS_GRANTED] = false
        }

        // 2. Clear Room database (all tasks, analytics, everything)
        withContext(Dispatchers.IO) {
            val database = AppDatabase.get(context)
            database.clearAllTables()  // ✅ Nukes everything
        }
    }

}
