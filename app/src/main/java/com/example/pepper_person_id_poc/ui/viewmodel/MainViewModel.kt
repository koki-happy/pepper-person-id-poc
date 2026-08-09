package com.example.pepper_person_id_poc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.DeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.ModelRuntimeRole
import com.example.pepper_person_id_poc.domain.config.ModelRuntimeSetOption
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
    private val modelSelectionCoordinator: ModelSelectionCoordinator,
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
    fun updateSettings(value: PocSettings) = update { value }
    fun updateFaceClusterJoinThreshold(value: Float) = update { copy(faceClusterJoinThreshold = value.coerceIn(PocSettings.SCORE_RANGE)) }
    fun updateFaceClusterMaxUpdateCount(value: Int) = update { copy(faceClusterMaxUpdateCount = value.coerceIn(PocSettings.CLUSTER_MAX_UPDATE_COUNT_RANGE)) }
    fun updateSpeakerClusterJoinThreshold(value: Float) = update { copy(speakerClusterJoinThreshold = value.coerceIn(PocSettings.SCORE_RANGE)) }
    fun updateSpeakerClusterMaxUpdateCount(value: Int) = update { copy(speakerClusterMaxUpdateCount = value.coerceIn(PocSettings.CLUSTER_MAX_UPDATE_COUNT_RANGE)) }
    fun updateMultipleSamplesEnabled(value: Boolean) = update { copy(multipleSamplesEnabled = value) }
    fun updateFaceDetectorModel(value: FaceDetectorModelOption) = update { copy(faceDetectorModel = value) }
    fun updateFaceDetectorRuntime(value: FaceDetectorRuntime) = update { copy(faceDetectorRuntime = value) }
    fun updateFaceEmbeddingModel(value: FaceEmbeddingModelOption) = update { copy(faceEmbeddingModel = value) }
    fun updateFaceEmbeddingRuntime(value: FaceEmbeddingRuntime) = update { copy(faceEmbeddingRuntime = value) }
    fun updateSpeakerModel(value: SpeakerModelOption) = update { withSpeakerModel(value) }
    fun updateModelRuntimeSet(value: ModelRuntimeSetOption) = update {
        when (value.role) {
            ModelRuntimeRole.FACE_DETECTOR -> copy(
                faceDetectorModel = requireNotNull(value.faceDetectorModel),
                faceDetectorRuntime = requireNotNull(value.faceDetectorRuntime),
            )
            ModelRuntimeRole.FACE_EMBEDDING -> copy(
                faceEmbeddingModel = requireNotNull(value.faceEmbeddingModel),
                faceEmbeddingRuntime = requireNotNull(value.faceEmbeddingRuntime),
            )
            ModelRuntimeRole.SPEAKER_EMBEDDING -> withSpeakerModel(requireNotNull(value.speakerModel)).copy(
                speakerRuntime = requireNotNull(value.speakerRuntime),
            )
            ModelRuntimeRole.VAD -> copy(
                vadModel = requireNotNull(value.vadModel),
                vadRuntime = requireNotNull(value.vadRuntime),
            )
        }
    }

    fun saveSettings() {
        val settings = mutableUiState.value.settings
        if (!settings.isValid() || !modelSelectionCoordinator.resolve(settings).selectable) return
        settingsRepository.save(settings)
        mutableUiState.update { it.copy(settingsSaved = true) }
    }

    fun resetSettings() {
        val defaults = PocSettings()
        settingsRepository.save(defaults)
        mutableUiState.update { it.copy(settings = defaults, settingsSaved = false) }
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
