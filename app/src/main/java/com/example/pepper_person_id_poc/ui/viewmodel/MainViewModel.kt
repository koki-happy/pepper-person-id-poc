package com.example.pepper_person_id_poc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pepper_person_id_poc.application.contract.DeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.state.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val diagnosticsProvider: DeviceDiagnosticsProvider,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        MainUiState(settings = settingsRepository.load()),
    )
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    fun showScreen(screen: AppScreen) {
        mutableUiState.update { it.copy(activeScreen = screen, settingsSaved = false) }
        if (screen == AppScreen.DeviceDiagnostics) refreshDiagnostics()
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

    fun refreshDiagnostics() {
        if (mutableUiState.value.diagnosticsLoading) return
        mutableUiState.update { it.copy(diagnosticsLoading = true, diagnosticsError = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { diagnosticsProvider.collect() }
            }.onSuccess { diagnostics ->
                mutableUiState.update {
                    it.copy(
                        diagnostics = diagnostics,
                        diagnosticsLoading = false,
                        diagnosticsError = null,
                    )
                }
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(
                        diagnosticsLoading = false,
                        diagnosticsError = throwable.message ?: throwable::class.java.simpleName,
                    )
                }
            }
        }
    }

    private fun updateSettings(block: PocSettings.() -> PocSettings) {
        mutableUiState.update { state ->
            state.copy(settings = state.settings.block(), settingsSaved = false)
        }
    }
}
