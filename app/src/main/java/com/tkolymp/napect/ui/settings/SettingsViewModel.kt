package com.tkolymp.napect.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.napect.data.local.SettingsRepository
import com.tkolymp.napect.data.local.ThemeMode
import com.tkolymp.napect.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = settingsRepository.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDefaultServings(value: Int) = viewModelScope.launch { settingsRepository.setDefaultServings(value) }
    fun setScreenshotProtectionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setScreenshotProtectionEnabled(enabled) }
    fun setPlannedCookDate(recipeId: Long, epochMillis: Long) = viewModelScope.launch { settingsRepository.setPlannedCookDate(recipeId, epochMillis) }
    fun getPlannedCookDateFlow(recipeId: Long) = settingsRepository.getPlannedCookDateFlow(recipeId)
}
