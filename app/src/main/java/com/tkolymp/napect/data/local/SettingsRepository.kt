package com.tkolymp.napect.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

interface OpenAiConfig {
    val openAiModel: Flow<String>
    val openAiBaseUrl: Flow<String>
}

enum class ThemeMode { AUTO, LIGHT, DARK }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val defaultServings: Int = 4,
    val screenshotProtectionEnabled: Boolean = false,
)

class SettingsRepository(private val context: Context) : OpenAiConfig {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DEFAULT_SERVINGS = intPreferencesKey("default_servings")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        fun plannedCookKey(id: Long) = stringPreferencesKey("planned_cook_\$id")
    }

    val prefsFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME]?.let { try { ThemeMode.valueOf(it) } catch (_: Exception) { ThemeMode.AUTO } } ?: ThemeMode.AUTO
        val servings = prefs[Keys.DEFAULT_SERVINGS] ?: 4
        val screenshotProtection = prefs[Keys.SCREENSHOT_PROTECTION] ?: false
        UserPreferences(themeMode = theme, defaultServings = servings, screenshotProtectionEnabled = screenshotProtection)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.name }
    }

    suspend fun setDefaultServings(value: Int) {
        context.dataStore.edit { prefs -> prefs[Keys.DEFAULT_SERVINGS] = value }
    }

    suspend fun setScreenshotProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.SCREENSHOT_PROTECTION] = enabled }
    }

    suspend fun setPlannedCookDate(recipeId: Long, epochMillis: Long) {
        context.dataStore.edit { prefs -> prefs[Keys.plannedCookKey(recipeId)] = epochMillis.toString() }
    }

    override val openAiModel: kotlinx.coroutines.flow.Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.OPENAI_MODEL] ?: "gpt-4o-mini"
    }

    override val openAiBaseUrl: kotlinx.coroutines.flow.Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.OPENAI_BASE_URL] ?: "https://api.openai.com/v1"
    }

    suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit { prefs -> prefs[Keys.OPENAI_MODEL] = model }
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[Keys.OPENAI_BASE_URL] = url }
    }

    fun getPlannedCookDateFlow(recipeId: Long) = context.dataStore.data.map { prefs ->
        prefs[Keys.plannedCookKey(recipeId)]?.let { str -> try { str.toLong() } catch (_: Exception) { null } }
    }
}
