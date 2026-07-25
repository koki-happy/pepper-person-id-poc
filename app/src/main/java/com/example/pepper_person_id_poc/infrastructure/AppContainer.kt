package com.example.pepper_person_id_poc.infrastructure

import android.content.Context
import com.example.pepper_person_id_poc.infrastructure.benchmark.JsonLinesBenchmarkLogger
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.infrastructure.repository.FileAnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.infrastructure.repository.FileAnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.infrastructure.repository.SharedPreferencesSettingsRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository by lazy { SharedPreferencesSettingsRepository(appContext) }
    val diagnosticsProvider by lazy { AndroidDeviceDiagnosticsProvider(appContext) }
    val benchmarkLogger by lazy { JsonLinesBenchmarkLogger(appContext) }
    val anonymousFaceClusterRepository by lazy { FileAnonymousFaceClusterRepository(appContext) }
    val anonymousSpeakerClusterRepository by lazy { FileAnonymousSpeakerClusterRepository(appContext) }
}
