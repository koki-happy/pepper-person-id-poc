package com.example.pepper_person_id_poc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pepper_person_id_poc.application.contract.DeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorOption
import com.example.pepper_person_id_poc.domain.config.FaceInferenceBackend
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
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
    private val benchmarkLogger: BenchmarkLogger,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        MainUiState(settings = settingsRepository.load()),
    )
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    fun showScreen(screen: AppScreen) {
        mutableUiState.update { it.copy(activeScreen = screen, settingsSaved = false) }
        if (screen == AppScreen.DeviceDiagnostics) refreshDiagnostics()
        if (screen == AppScreen.BenchmarkResults) refreshBenchmarkEvents()
    }

    fun returnToSettings() {
        showScreen(AppScreen.Settings)
    }

    fun updateFaceThreshold(value: Float) = updateSettings {
        copy(faceThreshold = value.coerceIn(PocSettings.SCORE_RANGE))
    }

    fun updateFaceMargin(value: Float) = updateSettings {
        copy(faceMargin = value.coerceIn(PocSettings.MARGIN_RANGE))
    }

    fun updateFaceRegistrationAnalysisIntervalMillis(value: Long) = updateSettings {
        copy(faceRegistrationAnalysisIntervalMillis = value.coerceIn(PocSettings.FACE_REGISTRATION_INTERVAL_RANGE))
    }

    fun updateFaceIdentificationAnalysisIntervalMillis(value: Long) = updateSettings {
        copy(faceIdentificationAnalysisIntervalMillis = value.coerceIn(PocSettings.FACE_IDENTIFICATION_INTERVAL_RANGE))
    }

    fun updateFacePoseStableDurationMillis(value: Long) = updateSettings {
        copy(facePoseStableDurationMillis = value.coerceIn(PocSettings.FACE_POSE_STABLE_DURATION_RANGE))
    }

    fun updateFaceFrontYawDegrees(value: Float) = updateSettings {
        copy(faceFrontYawDegrees = value.coerceIn(PocSettings.FACE_FRONT_ANGLE_RANGE))
    }

    fun updateFaceFrontPitchDegrees(value: Float) = updateSettings {
        copy(faceFrontPitchDegrees = value.coerceIn(PocSettings.FACE_FRONT_ANGLE_RANGE))
    }

    fun updateFaceSideMinimumYawDegrees(value: Float) = updateSettings {
        copy(faceSideMinimumYawDegrees = value.coerceIn(PocSettings.FACE_SIDE_ANGLE_RANGE).coerceAtMost(faceSideMaximumYawDegrees - 1f))
    }

    fun updateFaceSideMaximumYawDegrees(value: Float) = updateSettings {
        copy(faceSideMaximumYawDegrees = value.coerceIn(PocSettings.FACE_SIDE_ANGLE_RANGE).coerceAtLeast(faceSideMinimumYawDegrees + 1f))
    }

    fun updateFaceSmoothingSampleCount(value: Int) = updateSettings {
        copy(faceSmoothingSampleCount = value.coerceIn(PocSettings.FACE_SMOOTHING_SAMPLE_COUNT_RANGE))
    }

    fun updateFaceModel(value: FaceModelOption) = updateSettings {
        copy(
            faceModel = value,
            faceInferenceBackend = faceInferenceBackend.takeIf(value::supports)
                ?: FaceInferenceBackend.OPEN_CV,
        )
    }

    fun updateFaceDetector(value: FaceDetectorOption) = updateSettings {
        copy(faceDetector = value)
    }

    fun updateFaceInferenceBackend(value: FaceInferenceBackend) = updateSettings {
        if (faceModel.supports(value)) copy(faceInferenceBackend = value) else this
    }

    fun updateSpeakerModel(value: SpeakerModelOption) = updateSettings {
        withSpeakerModel(value)
    }

    fun updateSpeakerThreshold(value: Float) = updateSettings {
        copy(speakerThreshold = value.coerceIn(PocSettings.SCORE_RANGE))
    }

    fun updateSpeakerMargin(value: Float) = updateSettings {
        copy(speakerMargin = value.coerceIn(PocSettings.MARGIN_RANGE))
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
                appendBenchmarkEvent(
                    BenchmarkEvent(
                        event = "device_diagnostics",
                        timestampMillis = diagnostics.collectedAtMillis,
                        status = "SUCCESS",
                        attributes = mapOf(
                            "apiLevel" to diagnostics.apiLevel.toString(),
                            "abis" to diagnostics.supportedAbis.joinToString(","),
                            "availableProcessors" to diagnostics.availableProcessors.toString(),
                            "availableMemoryBytes" to diagnostics.availableMemoryBytes.toString(),
                            "frontCameraCount" to diagnostics.frontCameras.size.toString(),
                            "networkConnected" to diagnostics.networkConnected.toString(),
                        ),
                    ),
                )
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(
                        diagnosticsLoading = false,
                        diagnosticsError = throwable.message ?: throwable::class.java.simpleName,
                    )
                }
                appendBenchmarkEvent(
                    BenchmarkEvent(
                        event = "device_diagnostics",
                        timestampMillis = System.currentTimeMillis(),
                        status = "ERROR",
                        error = throwable.message ?: throwable::class.java.simpleName,
                    ),
                )
            }
        }
    }

    fun refreshBenchmarkEvents() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    benchmarkLogger.outputFile().absolutePath to benchmarkLogger.readRecent(RECENT_EVENT_LIMIT)
                }
            }.onSuccess { (path, events) ->
                mutableUiState.update {
                    it.copy(
                        benchmarkOutputPath = path,
                        recentBenchmarkEvents = events,
                        benchmarkError = null,
                    )
                }
            }.onFailure { throwable ->
                mutableUiState.update {
                    it.copy(benchmarkError = throwable.message ?: throwable::class.java.simpleName)
                }
            }
        }
    }

    private fun appendBenchmarkEvent(event: BenchmarkEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { benchmarkLogger.append(event) }
        }
    }

    private fun updateSettings(block: PocSettings.() -> PocSettings) {
        mutableUiState.update { state ->
            state.copy(settings = state.settings.block(), settingsSaved = false)
        }
    }

    private companion object {
        const val RECENT_EVENT_LIMIT = 20
    }
}
