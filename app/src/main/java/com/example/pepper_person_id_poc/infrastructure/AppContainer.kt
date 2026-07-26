package com.example.pepper_person_id_poc.infrastructure

import android.content.Context
import com.example.pepper_person_id_poc.infrastructure.benchmark.LogcatBenchmarkLogger
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.infrastructure.metrics.BenchmarkLogGate
import com.example.pepper_person_id_poc.infrastructure.metrics.benchmarkDistributionForPackage
import com.example.pepper_person_id_poc.infrastructure.model.AndroidModelSelectionCoordinatorFactory
import com.example.pepper_person_id_poc.infrastructure.repository.InMemoryAnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.infrastructure.repository.InMemoryAnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.infrastructure.repository.LegacyAnonymousClusterFiles
import com.example.pepper_person_id_poc.infrastructure.repository.SharedPreferencesSettingsRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    init {
        LegacyAnonymousClusterFiles.deleteAll(appContext)
    }

    val settingsRepository by lazy { SharedPreferencesSettingsRepository(appContext) }
    val modelSelectionCoordinator by lazy { AndroidModelSelectionCoordinatorFactory.create(appContext) }
    val diagnosticsProvider by lazy { AndroidDeviceDiagnosticsProvider(appContext) }
    val metricsBenchmarkLogGate by lazy {
        BenchmarkLogGate(
            distribution = benchmarkDistributionForPackage(appContext.packageName),
        )
    }
    val benchmarkLogger by lazy {
        LogcatBenchmarkLogger(
            context = appContext,
            logcatEnabled = metricsBenchmarkLogGate::isLogcatEnabled,
        )
    }
    val metricsBenchmarkLogger by lazy {
        com.example.pepper_person_id_poc.infrastructure.metrics.LogcatBenchmarkLogger(
            context = appContext,
            gate = metricsBenchmarkLogGate,
        )
    }
    val anonymousFaceClusterRepository by lazy { InMemoryAnonymousFaceClusterRepository() }
    val anonymousSpeakerClusterRepository by lazy { InMemoryAnonymousSpeakerClusterRepository() }

    fun deleteLegacyAnonymousClusterFiles() {
        LegacyAnonymousClusterFiles.deleteAll(appContext)
    }
}
