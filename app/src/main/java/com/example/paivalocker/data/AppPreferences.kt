package com.example.paivalocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    private val LOCKED_APPS = stringSetPreferencesKey("locked_apps")
    private val APP_AUTH_TIMES = stringPreferencesKey("app_auth_times")

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

    suspend fun updateAppAuthTime(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentTimes = preferences[APP_AUTH_TIMES] ?: "{}"
            val timesJson = JSONObject(currentTimes)
            timesJson.put(packageName, System.currentTimeMillis())
            preferences[APP_AUTH_TIMES] = timesJson.toString()
        }
    }

    suspend fun clearAppAuthTime(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentTimes = preferences[APP_AUTH_TIMES] ?: "{}"
            val timesJson = JSONObject(currentTimes)
            timesJson.remove(packageName)
            preferences[APP_AUTH_TIMES] = timesJson.toString()
        }
    }

    suspend fun clearAllAuthTimes() {
        context.dataStore.edit { preferences ->
            preferences[APP_AUTH_TIMES] = "{}"
        }
    }

    val appAuthTimes: Flow<Map<String, Long>> = context.dataStore.data
        .map { preferences ->
            val timesJson = preferences[APP_AUTH_TIMES] ?: "{}"
            val json = JSONObject(timesJson)
            val result = mutableMapOf<String, Long>()
            json.keys().forEach { key ->
                result[key] = json.getLong(key)
            }
            result
        }
} 