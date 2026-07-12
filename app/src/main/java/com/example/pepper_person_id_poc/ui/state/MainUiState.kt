package com.example.pepper_person_id_poc.ui.state

import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.ui.navigation.AppScreen

data class MainUiState(
    val activeScreen: AppScreen = AppScreen.Settings,
    val settings: PocSettings = PocSettings(),
    val settingsSaved: Boolean = false,
)
