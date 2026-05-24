package com.tkolymp.napect.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.napect.data.ai.openai.OpenAiKeyStore
import com.tkolymp.napect.data.ai.openai.OpenAiService
import com.tkolymp.napect.data.local.SettingsRepository
import com.tkolymp.napect.data.local.ThemeMode
import com.tkolymp.napect.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TestConnectionResult {
    object Success : TestConnectionResult
    data class Failure(val message: String) : TestConnectionResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val keyStore: OpenAiKeyStore,
    private val openAiService: OpenAiService,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = settingsRepository.prefsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val openAiModel: StateFlow<String> = settingsRepository.openAiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-4o-mini")

    val openAiBaseUrl: StateFlow<String> = settingsRepository.openAiBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1")

    private val _openAiKeyIsSet = MutableStateFlow(keyStore.getKey() != null)
    val openAiKeyIsSet: StateFlow<Boolean> = _openAiKeyIsSet.asStateFlow()

    private val _testConnectionResult = MutableStateFlow<TestConnectionResult?>(null)
    val testConnectionResult: StateFlow<TestConnectionResult?> = _testConnectionResult.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDefaultServings(value: Int) = viewModelScope.launch { settingsRepository.setDefaultServings(value) }
    fun setScreenshotProtectionEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setScreenshotProtectionEnabled(enabled) }
    fun setPlannedCookDate(recipeId: Long, epochMillis: Long) = viewModelScope.launch { settingsRepository.setPlannedCookDate(recipeId, epochMillis) }
    fun getPlannedCookDateFlow(recipeId: Long) = settingsRepository.getPlannedCookDateFlow(recipeId)

    fun setOpenAiKey(key: String) {
        keyStore.setKey(key)
        _openAiKeyIsSet.value = true
    }

    fun clearOpenAiKey() {
        keyStore.clear()
        _openAiKeyIsSet.value = false
    }

    fun setOpenAiModel(model: String) = viewModelScope.launch { settingsRepository.setOpenAiModel(model) }
    fun setOpenAiBaseUrl(url: String) = viewModelScope.launch { settingsRepository.setOpenAiBaseUrl(url) }

    fun testConnection() = viewModelScope.launch {
        _testConnectionResult.value = null
        val result = openAiService.testConnection()
        _testConnectionResult.value = if (result.isSuccess) {
            TestConnectionResult.Success
        } else {
            TestConnectionResult.Failure(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    fun clearTestConnectionResult() {
        _testConnectionResult.value = null
    }
}
