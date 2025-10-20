package com.example.caresync.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "profile_prefs")

class ProfileDataStore(private val context: Context) {
    private val PROFILE_COMPLETED = booleanPreferencesKey("profile_completed")
    private val USERNAME = stringPreferencesKey("username")
    private val AGE = stringPreferencesKey("age")
    private val PURPOSE = stringPreferencesKey("purpose")

    // ✅ NEW: Track if permissions granted
    private val PERMISSIONS_GRANTED = booleanPreferencesKey("permissions_granted")

    // ✅ Save profile data
    suspend fun saveProfile(username: String, age: String, purpose: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = username
            prefs[AGE] = age
            prefs[PURPOSE] = purpose
            prefs[PROFILE_COMPLETED] = true
        }
    }

    // ✅ Read profile data
    val profileData: Flow<Triple<String, String, String>> =
        context.dataStore.data.map { prefs ->
            Triple(
                prefs[USERNAME] ?: "",
                prefs[AGE] ?: "",
                prefs[PURPOSE] ?: ""
            )
        }

    // ✅ Check if profile completed
    val isProfileCompleted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PROFILE_COMPLETED] ?: false
        }

    // ✅ NEW: Save permissions granted
    suspend fun setPermissionsGranted() {
        context.dataStore.edit { prefs ->
            prefs[PERMISSIONS_GRANTED] = true
        }
    }

    // ✅ NEW: Check if permissions granted
    val arePermissionsGranted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[PERMISSIONS_GRANTED] ?: false
        }
}
