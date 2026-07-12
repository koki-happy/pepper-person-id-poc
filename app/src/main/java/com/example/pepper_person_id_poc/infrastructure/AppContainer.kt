package com.example.pepper_person_id_poc.infrastructure

import android.content.Context
import com.example.pepper_person_id_poc.infrastructure.benchmark.JsonLinesBenchmarkLogger
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.infrastructure.repository.FilePersonRepository
import com.example.pepper_person_id_poc.infrastructure.repository.SharedPreferencesSettingsRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository by lazy { SharedPreferencesSettingsRepository(appContext) }
    val diagnosticsProvider by lazy { AndroidDeviceDiagnosticsProvider(appContext) }
    val benchmarkLogger by lazy { JsonLinesBenchmarkLogger(appContext) }
    val personRepository by lazy { FilePersonRepository(appContext) }
}
