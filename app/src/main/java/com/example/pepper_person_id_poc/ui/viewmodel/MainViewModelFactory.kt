package com.example.pepper_person_id_poc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.application.contract.DeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator

class MainViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val modelSelectionCoordinator: ModelSelectionCoordinator,
    private val diagnosticsProvider: DeviceDiagnosticsProvider,
    private val benchmarkLogger: BenchmarkLogger,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(settingsRepository, modelSelectionCoordinator, diagnosticsProvider, benchmarkLogger) as T
    }
}
