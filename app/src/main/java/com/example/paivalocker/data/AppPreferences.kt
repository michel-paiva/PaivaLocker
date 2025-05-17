package com.example.paivalocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    private val LOCKED_APPS = stringSetPreferencesKey("locked_apps")

    val lockedApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[LOCKED_APPS] ?: emptySet()
        }

    suspend fun addLockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[LOCKED_APPS] ?: emptySet()
            preferences[LOCKED_APPS] = currentSet + packageName
        }
    }

    suspend fun removeLockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[LOCKED_APPS] ?: emptySet()
            preferences[LOCKED_APPS] = currentSet - packageName
        }
    }

    suspend fun setLockedApps(apps: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[LOCKED_APPS] = apps
        }
    }
} 