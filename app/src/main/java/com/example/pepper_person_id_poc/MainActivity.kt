package com.example.pepper_person_id_poc

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.pepper_person_id_poc.infrastructure.repository.SharedPreferencesSettingsRepository
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.screen.FeaturePlaceholderScreen
import com.example.pepper_person_id_poc.ui.screen.SettingsScreen
import com.example.pepper_person_id_poc.ui.theme.PepperpersonidpocTheme
import com.example.pepper_person_id_poc.ui.viewmodel.MainViewModel
import com.example.pepper_person_id_poc.ui.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(SharedPreferencesSettingsRepository(applicationContext))
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
