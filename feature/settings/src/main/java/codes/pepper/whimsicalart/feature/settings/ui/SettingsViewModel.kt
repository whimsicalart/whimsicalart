package codes.pepper.whimsicalart.feature.settings.ui

import androidx.lifecycle.ViewModel
import codes.pepper.whimsicalart.core.common.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val saveQuality: Int = 85,
    val saveFormat: String = "JPEG",
    val darkMode: Boolean = false,
    val hapticFeedback: Boolean = true,
    val autoSave: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            saveQuality = preferencesManager.saveQuality,
            saveFormat = preferencesManager.saveFormat,
            darkMode = preferencesManager.isDarkMode,
            hapticFeedback = preferencesManager.hapticFeedbackEnabled,
            autoSave = preferencesManager.autoSaveEnabled
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateSaveQuality(quality: Int) {
        preferencesManager.saveQuality = quality
        _uiState.value = _uiState.value.copy(saveQuality = quality)
    }

    fun updateSaveFormat(format: String) {
        preferencesManager.saveFormat = format
        _uiState.value = _uiState.value.copy(saveFormat = format)
    }

    fun updateDarkMode(enabled: Boolean) {
        preferencesManager.isDarkMode = enabled
        _uiState.value = _uiState.value.copy(darkMode = enabled)
    }

    fun updateHapticFeedback(enabled: Boolean) {
        preferencesManager.hapticFeedbackEnabled = enabled
        _uiState.value = _uiState.value.copy(hapticFeedback = enabled)
    }

    fun updateAutoSave(enabled: Boolean) {
        preferencesManager.autoSaveEnabled = enabled
        _uiState.value = _uiState.value.copy(autoSave = enabled)
    }
}
