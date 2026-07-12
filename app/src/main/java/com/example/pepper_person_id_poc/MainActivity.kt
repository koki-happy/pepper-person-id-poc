package com.example.pepper_person_id_poc

import android.os.Bundle
import android.Manifest
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.pepper_person_id_poc.infrastructure.repository.SharedPreferencesSettingsRepository
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.infrastructure.benchmark.JsonLinesBenchmarkLogger
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.screen.FeaturePlaceholderScreen
import com.example.pepper_person_id_poc.ui.screen.DeviceDiagnosticsScreen
import com.example.pepper_person_id_poc.ui.screen.BenchmarkResultsScreen
import com.example.pepper_person_id_poc.ui.screen.SettingsScreen
import com.example.pepper_person_id_poc.ui.theme.PepperpersonidpocTheme
import com.example.pepper_person_id_poc.ui.viewmodel.MainViewModel
import com.example.pepper_person_id_poc.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(
            settingsRepository = SharedPreferencesSettingsRepository(applicationContext),
            diagnosticsProvider = AndroidDeviceDiagnosticsProvider(applicationContext),
            benchmarkLogger = JsonLinesBenchmarkLogger(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.uiState.value.activeScreen == AppScreen.Settings) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        viewModel.returnToSettings()
                    }
                }
            },
        )

        setContent {
            PepperpersonidpocTheme {
                val uiState by viewModel.uiState.collectAsState()
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    viewModel.refreshDiagnostics()
                }
                if (uiState.activeScreen == AppScreen.Settings) {
                    SettingsScreen(
                        settings = uiState.settings,
                        settingsSaved = uiState.settingsSaved,
                        onFaceThresholdChanged = viewModel::updateFaceThreshold,
                        onSpeakerThresholdChanged = viewModel::updateSpeakerThreshold,
                        onCombinedThresholdChanged = viewModel::updateCombinedThreshold,
                        onObservationWindowChanged = viewModel::updateObservationWindowMillis,
                        onDebugModeChanged = viewModel::updateDebugMode,
                        onSave = viewModel::saveSettings,
                        onOpenScreen = viewModel::showScreen,
                    )
                } else if (uiState.activeScreen == AppScreen.DeviceDiagnostics) {
                    DeviceDiagnosticsScreen(
                        diagnostics = uiState.diagnostics,
                        isLoading = uiState.diagnosticsLoading,
                        error = uiState.diagnosticsError,
                        onBackToSettings = viewModel::returnToSettings,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                            )
                        },
                        onRefresh = viewModel::refreshDiagnostics,
                    )
                } else if (uiState.activeScreen == AppScreen.BenchmarkResults) {
                    BenchmarkResultsScreen(
                        outputPath = uiState.benchmarkOutputPath,
                        recentEvents = uiState.recentBenchmarkEvents,
                        error = uiState.benchmarkError,
                        onBackToSettings = viewModel::returnToSettings,
                        onRefresh = viewModel::refreshBenchmarkEvents,
                    )
                } else {
                    FeaturePlaceholderScreen(
                        screen = uiState.activeScreen,
                        onBackToSettings = viewModel::returnToSettings,
                    )
                }
            }
        }
    }
}
