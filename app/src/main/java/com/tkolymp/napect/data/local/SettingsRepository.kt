package com.tkolymp.napect.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { AUTO, LIGHT, DARK }

data class UserPreferences(val themeMode: ThemeMode = ThemeMode.AUTO, val defaultServings: Int = 4)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_SERVINGS = intPreferencesKey("default_servings")
        fun plannedCookKey(id: Long) = stringPreferencesKey("planned_cook_\$id")
    }

    val prefsFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME]?.let { try { ThemeMode.valueOf(it) } catch (_: Exception) { ThemeMode.AUTO } } ?: ThemeMode.AUTO
        val servings = prefs[Keys.DEFAULT_SERVINGS] ?: 4
        UserPreferences(themeMode = theme, defaultServings = servings)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.name }
    }

    suspend fun setDefaultServings(value: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_SERVINGS] = value }
    }

    suspend fun setPlannedCookDate(recipeId: Long, epochMillis: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.plannedCookKey(recipeId)] = epochMillis.toString() }
    }

    fun getPlannedCookDateFlow(recipeId: Long) = context.dataStore.data.map { prefs ->
        prefs[Keys.plannedCookKey(recipeId)]?.let { str -> try { str.toLong() } catch (_: Exception) { null } }
    }
}
