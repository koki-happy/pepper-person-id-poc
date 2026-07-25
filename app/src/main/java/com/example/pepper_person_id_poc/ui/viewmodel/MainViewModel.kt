package com.example.pepper_person_id_poc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.DeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.config.FaceDetectorOption
import com.example.pepper_person_id_poc.domain.config.FaceInferenceBackend
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.state.MainUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val diagnosticsProvider: DeviceDiagnosticsProvider,
    private val benchmarkLogger: BenchmarkLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MainUiState(settings = settingsRepository.load()))
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    fun showScreen(screen: AppScreen) {
        mutableUiState.update { it.copy(activeScreen = screen, settingsSaved = false) }
        if (screen == AppScreen.DeviceDiagnostics) refreshDiagnostics()
    }

    fun returnToSettings() = showScreen(AppScreen.Settings)
    fun updateFaceClusterJoinThreshold(value: Float) = update { copy(faceClusterJoinThreshold = value.coerceIn(PocSettings.SCORE_RANGE)) }
    fun updateFaceClusterMaxUpdateCount(value: Int) = update { copy(faceClusterMaxUpdateCount = value.coerceIn(PocSettings.CLUSTER_MAX_UPDATE_COUNT_RANGE)) }
    fun updateSpeakerClusterJoinThreshold(value: Float) = update { copy(speakerClusterJoinThreshold = value.coerceIn(PocSettings.SCORE_RANGE)) }
    fun updateSpeakerClusterMaxUpdateCount(value: Int) = update { copy(speakerClusterMaxUpdateCount = value.coerceIn(PocSettings.CLUSTER_MAX_UPDATE_COUNT_RANGE)) }
    fun updateFaceModel(value: FaceModelOption) = update {
        copy(faceModel = value, faceInferenceBackend = faceInferenceBackend.takeIf(value::supports) ?: FaceInferenceBackend.OPEN_CV)
    }
    fun updateFaceDetector(value: FaceDetectorOption) = update { copy(faceDetector = value) }
    fun updateFaceInferenceBackend(value: FaceInferenceBackend) = update {
        if (faceModel.supports(value)) copy(faceInferenceBackend = value) else this
    }
    fun updateSpeakerModel(value: SpeakerModelOption) = update { withSpeakerModel(value) }

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
            runCatching { withContext(Dispatchers.IO) { diagnosticsProvider.collect() } }
                .onSuccess { diagnostics ->
                    mutableUiState.update { it.copy(diagnostics = diagnostics, diagnosticsLoading = false) }
                    withContext(Dispatchers.IO) {
                        benchmarkLogger.append(
                            BenchmarkEvent("device_diagnostics", diagnostics.collectedAtMillis, status = "SUCCESS"),
                        )
                    }
                }
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(diagnosticsLoading = false, diagnosticsError = error.message ?: error::class.java.simpleName)
                    }
                }
        }
    }

    private fun update(block: PocSettings.() -> PocSettings) {
        mutableUiState.update { it.copy(settings = it.settings.block(), settingsSaved = false) }
    }
}
