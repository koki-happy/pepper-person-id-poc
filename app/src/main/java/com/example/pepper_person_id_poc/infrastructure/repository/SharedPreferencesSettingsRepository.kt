package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.PocSettings

class SharedPreferencesSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): PocSettings = PocSettings(
        faceThreshold = preferences.getFloat(KEY_FACE_THRESHOLD, PocSettings.DEFAULT_FACE_THRESHOLD),
        speakerThreshold = preferences.getFloat(KEY_SPEAKER_THRESHOLD, PocSettings.DEFAULT_SPEAKER_THRESHOLD),
        combinedThreshold = preferences.getFloat(KEY_COMBINED_THRESHOLD, PocSettings.DEFAULT_COMBINED_THRESHOLD),
        observationWindowMillis = preferences.getLong(
            KEY_OBSERVATION_WINDOW_MILLIS,
            PocSettings.DEFAULT_OBSERVATION_WINDOW_MILLIS,
        ),
        debugMode = preferences.getBoolean(KEY_DEBUG_MODE, true),
    )

    override fun save(settings: PocSettings) {
        require(settings.isValid()) { "Invalid PoC settings" }
        preferences.edit()
            .putFloat(KEY_FACE_THRESHOLD, settings.faceThreshold)
            .putFloat(KEY_SPEAKER_THRESHOLD, settings.speakerThreshold)
            .putFloat(KEY_COMBINED_THRESHOLD, settings.combinedThreshold)
            .putLong(KEY_OBSERVATION_WINDOW_MILLIS, settings.observationWindowMillis)
            .putBoolean(KEY_DEBUG_MODE, settings.debugMode)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "poc_settings"
        const val KEY_FACE_THRESHOLD = "face_threshold"
        const val KEY_SPEAKER_THRESHOLD = "speaker_threshold"
        const val KEY_COMBINED_THRESHOLD = "combined_threshold"
        const val KEY_OBSERVATION_WINDOW_MILLIS = "observation_window_millis"
        const val KEY_DEBUG_MODE = "debug_mode"
    }
}
