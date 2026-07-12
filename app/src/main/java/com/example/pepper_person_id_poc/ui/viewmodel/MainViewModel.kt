package com.example.pepper_person_id_poc.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.state.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        MainUiState(settings = settingsRepository.load()),
    )
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    fun showScreen(screen: AppScreen) {
        mutableUiState.update { it.copy(activeScreen = screen, settingsSaved = false) }
    }

    fun returnToSettings() {
        showScreen(AppScreen.Settings)
    }

    fun updateFaceThreshold(value: Float) = updateSettings {
        copy(faceThreshold = value.coerceIn(PocSettings.SCORE_RANGE))
    }

    fun updateSpeakerThreshold(value: Float) = updateSettings {
        copy(speakerThreshold = value.coerceIn(PocSettings.SCORE_RANGE))
    }

    fun updateCombinedThreshold(value: Float) = updateSettings {
        copy(combinedThreshold = value.coerceIn(PocSettings.SCORE_RANGE))
    }

    fun updateObservationWindowMillis(value: Long) = updateSettings {
        copy(observationWindowMillis = value.coerceIn(PocSettings.OBSERVATION_WINDOW_RANGE))
    }

    fun updateDebugMode(enabled: Boolean) = updateSettings {
        copy(debugMode = enabled)
    }

    fun saveSettings() {
        val settings = mutableUiState.value.settings
        if (!settings.isValid()) return
        settingsRepository.save(settings)
        mutableUiState.update { it.copy(settingsSaved = true) }
    }

    private fun updateSettings(block: PocSettings.() -> PocSettings) {
        mutableUiState.update { state ->
            state.copy(settings = state.settings.block(), settingsSaved = false)
        }
    }
}
