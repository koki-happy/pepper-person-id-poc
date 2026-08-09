package com.example.pepper_person_id_poc

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pepper_person_id_poc.application.session.AnonymousSessionStartupState
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
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
                Box(modifier = Modifier.fillMaxSize()) {
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
                                AppScreen.AnonymousFaceIdentification -> CameraPreviewScreen(
                                    settings = uiState.settings,
                                    repository = container.anonymousFaceClusterRepository,
                                    speakerRepository = container.anonymousSpeakerClusterRepository,
                                    benchmarkLogger = container.benchmarkLogger,
                                    onOpenSettings = viewModel::returnToSettings,
                                    onOpenModels = { viewModel.showScreen(AppScreen.ModelSelection) },
                                    onReset = ::resetAnonymousSession,
                                )
                            }
                        }
                    }
                    HiddenExitGesture(onExit = ::clearSessionAndExit)
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
        val returnScreen = viewModel.uiState.value.activeScreen
        viewModel.resetSettings()
        viewModel.showScreen(AppScreen.Settings)
        lifecycleScope.launch {
            kotlinx.coroutines.yield()
            withContext(Dispatchers.IO) {
                container.anonymousFaceClusterRepository.deleteAll()
                container.anonymousSpeakerClusterRepository.deleteAll()
                container.deleteLegacyAnonymousClusterFiles()
            }
            if (!isFinishing) viewModel.showScreen(returnScreen)
        }
    }
}

@androidx.compose.runtime.Composable
private fun BoxScope.HiddenExitGesture(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .width(48.dp)
            .height(48.dp)
            .pointerInput(onExit) {
                detectTapGestures(onLongPress = { onExit() })
            }
            .testTag("hidden-exit-button"),
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
