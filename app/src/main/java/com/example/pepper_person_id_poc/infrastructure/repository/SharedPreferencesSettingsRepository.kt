package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.FaceDetectorOption
import com.example.pepper_person_id_poc.domain.config.FaceInferenceBackend
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption

class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences("poc_settings", Context.MODE_PRIVATE)

    override fun load(): PocSettings {
        val speakerModel = enumValue(KEY_SPEAKER_MODEL, SpeakerModelOption.ERES2NET)
        return PocSettings(
            faceModel = enumValue(KEY_FACE_MODEL, FaceModelOption.SFACE_2021DEC),
            faceDetector = enumValue(KEY_FACE_DETECTOR, FaceDetectorOption.ML_KIT_BUNDLED),
            faceInferenceBackend = enumValue(KEY_FACE_BACKEND, FaceInferenceBackend.OPEN_CV),
            speakerModel = speakerModel,
            faceClusterJoinThreshold = preferences.getFloat(
                KEY_FACE_THRESHOLD,
                PocSettings.DEFAULT_FACE_CLUSTER_JOIN_THRESHOLD,
            ),
            faceClusterMaxUpdateCount = preferences.getInt(
                KEY_FACE_MAX_UPDATES,
                PocSettings.DEFAULT_CLUSTER_MAX_UPDATE_COUNT,
            ),
            speakerClusterJoinThreshold = preferences.getFloat(
                KEY_SPEAKER_THRESHOLD,
                speakerModel.jvsCandidateThreshold,
            ),
            speakerClusterMaxUpdateCount = preferences.getInt(
                KEY_SPEAKER_MAX_UPDATES,
                PocSettings.DEFAULT_CLUSTER_MAX_UPDATE_COUNT,
            ),
        )
    }

    override fun save(settings: PocSettings) {
        require(settings.isValid())
        preferences.edit()
            .putString(KEY_FACE_MODEL, settings.faceModel.name)
            .putString(KEY_FACE_DETECTOR, settings.faceDetector.name)
            .putString(KEY_FACE_BACKEND, settings.faceInferenceBackend.name)
            .putString(KEY_SPEAKER_MODEL, settings.speakerModel.name)
            .putFloat(KEY_FACE_THRESHOLD, settings.faceClusterJoinThreshold)
            .putInt(KEY_FACE_MAX_UPDATES, settings.faceClusterMaxUpdateCount)
            .putFloat(KEY_SPEAKER_THRESHOLD, settings.speakerClusterJoinThreshold)
            .putInt(KEY_SPEAKER_MAX_UPDATES, settings.speakerClusterMaxUpdateCount)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, fallback: T): T =
        preferences.getString(key, null)?.let { saved -> enumValues<T>().firstOrNull { it.name == saved } } ?: fallback

    private companion object {
        const val KEY_FACE_MODEL = "face_model"
        const val KEY_FACE_DETECTOR = "face_detector"
        const val KEY_FACE_BACKEND = "face_inference_backend"
        const val KEY_SPEAKER_MODEL = "speaker_model"
        const val KEY_FACE_THRESHOLD = "face_cluster_join_threshold"
        const val KEY_FACE_MAX_UPDATES = "face_cluster_max_update_count"
        const val KEY_SPEAKER_THRESHOLD = "speaker_cluster_join_threshold"
        const val KEY_SPEAKER_MAX_UPDATES = "speaker_cluster_max_update_count"
    }
}
