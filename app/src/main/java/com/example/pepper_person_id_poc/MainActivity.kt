package com.example.pepper_person_id_poc

import android.Manifest
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pepper_person_id_poc.application.session.AnonymousSessionStartupState
import com.example.pepper_person_id_poc.domain.config.FaceDetectorArtifactResolver
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingArtifactResolver
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkDevice
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkInput
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkModelSelection
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkRunMetadata
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkStatus
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkThresholds
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.screen.AudioRecordingScreen
import com.example.pepper_person_id_poc.ui.screen.BenchmarkConfigurationUiState
import com.example.pepper_person_id_poc.ui.screen.BenchmarkScreen
import com.example.pepper_person_id_poc.ui.screen.CameraPreviewScreen
import com.example.pepper_person_id_poc.ui.screen.DeviceDiagnosticsScreen
import com.example.pepper_person_id_poc.ui.screen.ModelSelectionScreen
import com.example.pepper_person_id_poc.ui.screen.SettingsScreen
import com.example.pepper_person_id_poc.ui.theme.PepperpersonidpocTheme
import com.example.pepper_person_id_poc.ui.viewmodel.MainViewModel
import com.example.pepper_person_id_poc.ui.viewmodel.MainViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val app by lazy { application as PepperPersonIdApplication }
    private val container get() = app.container
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            container.settingsRepository,
            container.modelSelectionCoordinator,
            container.diagnosticsProvider,
            container.benchmarkLogger,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.uiState.value.activeScreen == AppScreen.Settings) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else viewModel.returnToSettings()
            }
        })
        setContent {
            PepperpersonidpocTheme {
                val startup by app.startupState.collectAsState()
                val uiState by viewModel.uiState.collectAsState()
                var benchmarkConfiguration by remember {
                    mutableStateOf(
                        BenchmarkConfigurationUiState(
                            scenarioId = "A-COMPARE-001",
                            inputDescriptor = "fixed-input",
                            preprocessingId = "explicit-preprocessing-id",
                            datasetId = "local-fixed-inputs",
                            repetitions = "10",
                        ),
                    )
                }
                var benchmarkStatus by remember { mutableStateOf<String?>(null) }
                var candidateExportEnabled by remember {
                    mutableStateOf(container.metricsBenchmarkLogGate.isLogcatEnabled())
                }
                when (val state = startup) {
                    AnonymousSessionStartupState.Initializing -> StartupMessage("セッションを初期化しています")
                    is AnonymousSessionStartupState.Error -> StartupMessage(
                        message = "初期化エラー: ${state.message}",
                        retry = app::initializeSession,
                    )
                    AnonymousSessionStartupState.Ready -> {
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestMultiplePermissions(),
                        ) { viewModel.refreshDiagnostics() }
                        when (uiState.activeScreen) {
                            AppScreen.Settings -> SettingsScreen(
                                settings = uiState.settings,
                                settingsSaved = uiState.settingsSaved,
                                onSettingsChanged = viewModel::updateSettings,
                                onOpenModelSelection = { viewModel.showScreen(AppScreen.ModelSelection) },
                                onSave = viewModel::saveSettings,
                                onOpenScreen = viewModel::showScreen,
                                onBackToIdentification = { viewModel.showScreen(AppScreen.AnonymousFaceIdentification) },
                                onExit = ::clearSessionAndExit,
                            )
                            AppScreen.ModelSelection -> ModelSelectionScreen(
                                settings = uiState.settings,
                                selectionCoordinator = container.modelSelectionCoordinator,
                                settingsSaved = uiState.settingsSaved,
                                onModelRuntimeSetChanged = viewModel::updateModelRuntimeSet,
                                onSave = viewModel::saveSettings,
                                onBackToSettings = viewModel::returnToSettings,
                            )
                            AppScreen.DeviceDiagnostics -> DeviceDiagnosticsScreen(
                                diagnostics = uiState.diagnostics,
                                isLoading = uiState.diagnosticsLoading,
                                error = uiState.diagnosticsError,
                                onBackToSettings = viewModel::returnToSettings,
                                onRequestPermissions = {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                },
                                onRefresh = viewModel::refreshDiagnostics,
                            )
                            AppScreen.Benchmark -> {
                                val modelSelections = uiState.settings.benchmarkModelSelections()
                                BenchmarkScreen(
                                    configuration = benchmarkConfiguration,
                                    modelSelections = modelSelections,
                                    distribution = container.metricsBenchmarkLogGate.distribution,
                                    candidateExportEnabled = candidateExportEnabled,
                                    statusMessage = benchmarkStatus,
                                    onConfigurationChange = {
                                        benchmarkConfiguration = it
                                        benchmarkStatus = null
                                    },
                                    onRun = {
                                        val written = container.metricsBenchmarkLogger.append(
                                            createBenchmarkConfigurationEvent(
                                                configuration = benchmarkConfiguration,
                                                settings = uiState.settings,
                                                modelSelections = modelSelections,
                                            ),
                                        )
                                        benchmarkStatus = if (written) {
                                            "構成をLogcatへ出力しました"
                                        } else {
                                            "candidateのLogcat出力を有効にしてください"
                                        }
                                    },
                                    onEnableCandidateExport = {
                                        container.metricsBenchmarkLogGate.enableExplicitCandidateExport()
                                        candidateExportEnabled =
                                            container.metricsBenchmarkLogGate.isLogcatEnabled()
                                        benchmarkStatus = "candidateのLogcat出力を有効にしました"
                                    },
                                    onBack = viewModel::returnToSettings,
                                )
                            }
                            AppScreen.AnonymousFaceIdentification -> CameraPreviewScreen(
                                settings = uiState.settings,
                                repository = container.anonymousFaceClusterRepository,
                                benchmarkLogger = container.benchmarkLogger,
                                onOpenSettings = viewModel::returnToSettings,
                                onOpenModels = { viewModel.showScreen(AppScreen.ModelSelection) },
                                onOpenSpeaker = { viewModel.showScreen(AppScreen.AnonymousSpeakerIdentification) },
                                onReset = ::resetAnonymousSession,
                            )
                            AppScreen.AnonymousSpeakerIdentification -> AudioRecordingScreen(
                                settings = uiState.settings,
                                repository = container.anonymousSpeakerClusterRepository,
                                benchmarkLogger = container.benchmarkLogger,
                                onOpenSettings = viewModel::returnToSettings,
                                onOpenModels = { viewModel.showScreen(AppScreen.ModelSelection) },
                                onOpenFace = { viewModel.showScreen(AppScreen.AnonymousFaceIdentification) },
                                onReset = ::resetAnonymousSession,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun clearSessionAndExit() {
        viewModel.returnToSettings()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                container.anonymousFaceClusterRepository.deleteAll()
                container.anonymousSpeakerClusterRepository.deleteAll()
                container.deleteLegacyAnonymousClusterFiles()
            }
            finishAndRemoveTask()
        }
    }

    private fun resetAnonymousSession() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                container.anonymousFaceClusterRepository.deleteAll()
                container.anonymousSpeakerClusterRepository.deleteAll()
                container.deleteLegacyAnonymousClusterFiles()
            }
            val current = viewModel.uiState.value.activeScreen
            viewModel.showScreen(AppScreen.Settings)
            viewModel.showScreen(current)
        }
    }

    private fun createBenchmarkConfigurationEvent(
        configuration: BenchmarkConfigurationUiState,
        settings: PocSettings,
        modelSelections: List<BenchmarkModelSelection>,
    ): BenchmarkEvent {
        val epochMillis = System.currentTimeMillis()
        val elapsedRealtimeMillis = SystemClock.elapsedRealtime()
        val distribution = container.metricsBenchmarkLogGate.distribution.name.lowercase()
        val buildType = if (
            applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) {
            "Debug"
        } else {
            "Release"
        }
        return BenchmarkEvent(
            eventType = "benchmark_configuration",
            run = BenchmarkRunMetadata(
                runId = UUID.randomUUID().toString(),
                scenarioId = configuration.scenarioId,
                timestampEpochMillis = epochMillis,
                timestampElapsedRealtimeMillis = elapsedRealtimeMillis,
                device = BenchmarkDevice(
                    manufacturer = Build.MANUFACTURER.ifBlank { "unknown" },
                    model = Build.MODEL.ifBlank { "unknown" },
                    apiLevel = Build.VERSION.SDK_INT,
                    abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" },
                ),
                buildVariant = distribution + buildType,
                input = BenchmarkInput(
                    descriptor = configuration.inputDescriptor,
                    sha256 = configuration.inputSha256,
                    preprocessingId = configuration.preprocessingId,
                    datasetId = configuration.datasetId,
                    repetition = configuration.repetitions.toInt(),
                ),
                models = modelSelections,
                thresholds = BenchmarkThresholds(
                    identification = settings.faceClusterJoinThreshold.toDouble(),
                    minimumLead = settings.speakerModel.jvsCandidateMargin.toDouble(),
                ),
            ),
            status = BenchmarkStatus.SUCCESS,
        )
    }
}

private fun PocSettings.benchmarkModelSelections(): List<BenchmarkModelSelection> {
    val detectorArtifact = requireNotNull(
        FaceDetectorArtifactResolver.resolve(faceDetectorModel, faceDetectorRuntime),
    ) {
        "No exact face detector artifact for ${faceDetectorModel.artifactId}/${faceDetectorRuntime.runtimeId}"
    }
    val embeddingArtifact = requireNotNull(
        FaceEmbeddingArtifactResolver.resolve(faceEmbeddingModel, faceEmbeddingRuntime),
    ) {
        "No exact face embedding artifact for ${faceEmbeddingModel.artifactId}/${faceEmbeddingRuntime.runtimeId}"
    }
    return listOf(
        BenchmarkModelSelection(
            role = "FACE_DETECTOR",
            artifactId = detectorArtifact.artifactId,
            runtimeId = faceDetectorRuntime.runtimeId,
        ),
        BenchmarkModelSelection(
            role = "FACE_EMBEDDING",
            modelSpaceId = embeddingArtifact.modelSpaceId,
            artifactId = embeddingArtifact.artifactId,
            runtimeId = faceEmbeddingRuntime.runtimeId,
        ),
        BenchmarkModelSelection(
            role = "SPEAKER_EMBEDDING",
            modelSpaceId = speakerModel.modelSpaceId,
            artifactId = speakerModel.artifactId,
            runtimeId = speakerRuntime.runtimeId,
        ),
        BenchmarkModelSelection(
            role = "VAD",
            artifactId = vadModel.artifactId,
            runtimeId = vadRuntime.runtimeId,
        ),
    )
}

@androidx.compose.runtime.Composable
private fun StartupMessage(message: String, retry: (() -> Unit)? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(message)
        retry?.let { Button(onClick = it) { Text("再試行") } }
    }
}
