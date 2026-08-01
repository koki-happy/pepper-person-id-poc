package com.example.pepper_person_id_poc.ui.state

import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.device.DeviceDiagnostics
import com.example.pepper_person_id_poc.ui.navigation.AppScreen

data class MainUiState(
    val activeScreen: AppScreen = AppScreen.AnonymousFaceIdentification,
    val settings: PocSettings = PocSettings(),
    val settingsSaved: Boolean = false,
    val diagnostics: DeviceDiagnostics? = null,
    val diagnosticsLoading: Boolean = false,
    val diagnosticsError: String? = null,
)
