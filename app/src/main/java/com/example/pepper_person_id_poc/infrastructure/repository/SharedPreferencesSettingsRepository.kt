package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption

class SharedPreferencesSettingsRepository(
    context: Context,
) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): PocSettings = PocSettings(
        faceModel = preferences.getString(KEY_FACE_MODEL, null)
            ?.let { saved -> FaceModelOption.entries.firstOrNull { it.name == saved } }
            ?: FaceModelOption.SFACE_2021DEC,
        speakerModel = preferences.getString(KEY_SPEAKER_MODEL, null)
            ?.let { saved -> SpeakerModelOption.entries.firstOrNull { it.name == saved } }
            ?: PocSettings().speakerModel,
        faceThreshold = preferences.getFloat(KEY_FACE_THRESHOLD, PocSettings.DEFAULT_FACE_THRESHOLD),
        faceMargin = preferences.getFloat(KEY_FACE_MARGIN, PocSettings.DEFAULT_FACE_MARGIN),
        faceRegistrationAnalysisIntervalMillis = preferences.getLong(
            KEY_FACE_REGISTRATION_INTERVAL,
            PocSettings.DEFAULT_FACE_REGISTRATION_INTERVAL_MILLIS,
        ),
        faceIdentificationAnalysisIntervalMillis = preferences.getLong(
            KEY_FACE_IDENTIFICATION_INTERVAL,
            PocSettings.DEFAULT_FACE_IDENTIFICATION_INTERVAL_MILLIS,
        ),
        facePoseStableDurationMillis = preferences.getLong(
            KEY_FACE_POSE_STABLE_DURATION,
            PocSettings.DEFAULT_FACE_POSE_STABLE_DURATION_MILLIS,
        ),
        faceFrontYawDegrees = preferences.getFloat(
            KEY_FACE_FRONT_YAW,
            PocSettings.DEFAULT_FACE_FRONT_YAW_DEGREES,
        ),
        faceFrontPitchDegrees = preferences.getFloat(
            KEY_FACE_FRONT_PITCH,
            PocSettings.DEFAULT_FACE_FRONT_PITCH_DEGREES,
        ),
        faceSideMinimumYawDegrees = preferences.getFloat(
            KEY_FACE_SIDE_MINIMUM_YAW,
            PocSettings.DEFAULT_FACE_SIDE_MINIMUM_YAW_DEGREES,
        ),
        faceSideMaximumYawDegrees = preferences.getFloat(
            KEY_FACE_SIDE_MAXIMUM_YAW,
            PocSettings.DEFAULT_FACE_SIDE_MAXIMUM_YAW_DEGREES,
        ),
        faceSmoothingSampleCount = preferences.getInt(
            KEY_FACE_SMOOTHING_SAMPLE_COUNT,
            PocSettings.DEFAULT_FACE_SMOOTHING_SAMPLE_COUNT,
        ),
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
            .putString(KEY_FACE_MODEL, settings.faceModel.name)
            .putString(KEY_SPEAKER_MODEL, settings.speakerModel.name)
            .putFloat(KEY_FACE_THRESHOLD, settings.faceThreshold)
            .putFloat(KEY_FACE_MARGIN, settings.faceMargin)
            .putLong(KEY_FACE_REGISTRATION_INTERVAL, settings.faceRegistrationAnalysisIntervalMillis)
            .putLong(KEY_FACE_IDENTIFICATION_INTERVAL, settings.faceIdentificationAnalysisIntervalMillis)
            .putLong(KEY_FACE_POSE_STABLE_DURATION, settings.facePoseStableDurationMillis)
            .putFloat(KEY_FACE_FRONT_YAW, settings.faceFrontYawDegrees)
            .putFloat(KEY_FACE_FRONT_PITCH, settings.faceFrontPitchDegrees)
            .putFloat(KEY_FACE_SIDE_MINIMUM_YAW, settings.faceSideMinimumYawDegrees)
            .putFloat(KEY_FACE_SIDE_MAXIMUM_YAW, settings.faceSideMaximumYawDegrees)
            .putInt(KEY_FACE_SMOOTHING_SAMPLE_COUNT, settings.faceSmoothingSampleCount)
            .putFloat(KEY_SPEAKER_THRESHOLD, settings.speakerThreshold)
            .putFloat(KEY_COMBINED_THRESHOLD, settings.combinedThreshold)
            .putLong(KEY_OBSERVATION_WINDOW_MILLIS, settings.observationWindowMillis)
            .putBoolean(KEY_DEBUG_MODE, settings.debugMode)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "poc_settings"
        const val KEY_FACE_MODEL = "face_model"
        const val KEY_SPEAKER_MODEL = "speaker_model"
        const val KEY_FACE_THRESHOLD = "face_threshold"
        const val KEY_FACE_MARGIN = "face_margin"
        const val KEY_FACE_REGISTRATION_INTERVAL = "face_registration_analysis_interval_millis"
        const val KEY_FACE_IDENTIFICATION_INTERVAL = "face_identification_analysis_interval_millis"
        const val KEY_FACE_POSE_STABLE_DURATION = "face_pose_stable_duration_millis"
        const val KEY_FACE_FRONT_YAW = "face_front_yaw_degrees"
        const val KEY_FACE_FRONT_PITCH = "face_front_pitch_degrees"
        const val KEY_FACE_SIDE_MINIMUM_YAW = "face_side_minimum_yaw_degrees"
        const val KEY_FACE_SIDE_MAXIMUM_YAW = "face_side_maximum_yaw_degrees"
        const val KEY_FACE_SMOOTHING_SAMPLE_COUNT = "face_smoothing_sample_count"
        const val KEY_SPEAKER_THRESHOLD = "speaker_threshold"
        const val KEY_COMBINED_THRESHOLD = "combined_threshold"
        const val KEY_OBSERVATION_WINDOW_MILLIS = "observation_window_millis"
        const val KEY_DEBUG_MODE = "debug_mode"
    }
}
