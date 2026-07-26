package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.pepper_person_id_poc.application.contract.SettingsRepository
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.example.pepper_person_id_poc.domain.config.SpeakerRuntime
import com.example.pepper_person_id_poc.domain.config.VadModelOption

class SharedPreferencesSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): PocSettings {
        val settings = SettingsSchemaMigration.migrate(preferences.all)
        persist(settings)
        return settings
    }

    override fun save(settings: PocSettings) {
        require(settings.isValid())
        persist(settings)
    }

    private fun persist(settings: PocSettings) {
        preferences.edit()
            .putInt(KEY_SCHEMA_VERSION, SettingsSchemaMigration.CURRENT_SCHEMA_VERSION)
            .putString(KEY_FACE_DETECTOR_MODEL, settings.faceDetectorModel.name)
            .putString(KEY_FACE_DETECTOR_RUNTIME, settings.faceDetectorRuntime.name)
            .putString(KEY_FACE_EMBEDDING_MODEL, settings.faceEmbeddingModel.name)
            .putString(KEY_FACE_EMBEDDING_RUNTIME, settings.faceEmbeddingRuntime.name)
            .putString(KEY_SPEAKER_MODEL, settings.speakerModel.name)
            .putString(KEY_SPEAKER_RUNTIME, settings.speakerRuntime.name)
            .putString(KEY_VAD_MODEL, settings.vadModel.name)
            .putFloat(KEY_FACE_THRESHOLD, settings.faceClusterJoinThreshold)
            .putInt(KEY_FACE_MAX_UPDATES, settings.faceClusterMaxUpdateCount)
            .putFloat(KEY_SPEAKER_THRESHOLD, settings.speakerClusterJoinThreshold)
            .putInt(KEY_SPEAKER_MAX_UPDATES, settings.speakerClusterMaxUpdateCount)
            .remove(LEGACY_KEY_FACE_MODEL)
            .remove(LEGACY_KEY_FACE_DETECTOR)
            .remove(LEGACY_KEY_FACE_BACKEND)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "poc_settings"
    }
}

class SettingsMigrationException(message: String) : IllegalStateException(message)

internal object SettingsSchemaMigration {
    const val CURRENT_SCHEMA_VERSION = 2

    fun migrate(raw: Map<String, *>): PocSettings {
        if (raw.isEmpty()) return PocSettings()
        val schemaVersion = raw[KEY_SCHEMA_VERSION].asInt(KEY_SCHEMA_VERSION) ?: 1
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw SettingsMigrationException(
                "Unsupported settings schemaVersion=$schemaVersion; current=$CURRENT_SCHEMA_VERSION",
            )
        }
        if (schemaVersion < 1) {
            throw SettingsMigrationException("Invalid settings schemaVersion=$schemaVersion")
        }

        val defaults = PocSettings()
        val modelSelections = if (schemaVersion == CURRENT_SCHEMA_VERSION) {
            ModelSelections(
                faceDetectorModel = raw.requiredEnum(KEY_FACE_DETECTOR_MODEL),
                faceDetectorRuntime = raw.requiredEnum(KEY_FACE_DETECTOR_RUNTIME),
                faceEmbeddingModel = raw.requiredEnum(KEY_FACE_EMBEDDING_MODEL),
                faceEmbeddingRuntime = raw.requiredEnum(KEY_FACE_EMBEDDING_RUNTIME),
                speakerModel = raw.requiredEnum(KEY_SPEAKER_MODEL),
                speakerRuntime = raw.requiredEnum(KEY_SPEAKER_RUNTIME),
                vadModel = raw.requiredEnum(KEY_VAD_MODEL),
            )
        } else {
            migrateLegacySelections(raw, defaults)
        }

        return PocSettings(
            faceDetectorModel = modelSelections.faceDetectorModel,
            faceDetectorRuntime = modelSelections.faceDetectorRuntime,
            faceEmbeddingModel = modelSelections.faceEmbeddingModel,
            faceEmbeddingRuntime = modelSelections.faceEmbeddingRuntime,
            speakerModel = modelSelections.speakerModel,
            speakerRuntime = modelSelections.speakerRuntime,
            vadModel = modelSelections.vadModel,
            faceClusterJoinThreshold =
                raw[KEY_FACE_THRESHOLD].asFloat(KEY_FACE_THRESHOLD) ?: defaults.faceClusterJoinThreshold,
            faceClusterMaxUpdateCount =
                raw[KEY_FACE_MAX_UPDATES].asInt(KEY_FACE_MAX_UPDATES) ?: defaults.faceClusterMaxUpdateCount,
            speakerClusterJoinThreshold =
                raw[KEY_SPEAKER_THRESHOLD].asFloat(KEY_SPEAKER_THRESHOLD)
                    ?: modelSelections.speakerModel.jvsCandidateThreshold,
            speakerClusterMaxUpdateCount =
                raw[KEY_SPEAKER_MAX_UPDATES].asInt(KEY_SPEAKER_MAX_UPDATES)
                    ?: defaults.speakerClusterMaxUpdateCount,
        ).also {
            if (!it.isValid()) throw SettingsMigrationException("Migrated settings contain invalid threshold or update bounds")
        }
    }

    private fun migrateLegacySelections(raw: Map<String, *>, defaults: PocSettings): ModelSelections {
        val detector = when (val saved = raw.optionalString(LEGACY_KEY_FACE_DETECTOR)) {
            null -> defaults.faceDetectorModel to defaults.faceDetectorRuntime
            "ML_KIT_BUNDLED" -> FaceDetectorModelOption.ML_KIT_BUNDLED to FaceDetectorRuntime.ML_KIT
            "YUNET_OPEN_CV" ->
                FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.OPEN_CV
            "YUNET_2023MAR_INT8_OPEN_CV" ->
                FaceDetectorModelOption.YUNET_2023MAR_INT8 to FaceDetectorRuntime.OPEN_CV
            else -> throw SettingsMigrationException(
                "Cannot migrate $LEGACY_KEY_FACE_DETECTOR=$saved",
            )
        }
        val legacyEmbeddingModel = when (val saved = raw.optionalString(LEGACY_KEY_FACE_MODEL)) {
            null -> defaults.faceEmbeddingModel
            "SFACE_2021DEC" -> FaceEmbeddingModelOption.SFACE_2021DEC_FP32
            "SFACE_2021DEC_INT8" -> FaceEmbeddingModelOption.SFACE_2021DEC_INT8
            "FACE_REIDENTIFICATION_RETAIL_0095" ->
                FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32
            else -> throw SettingsMigrationException("Cannot migrate $LEGACY_KEY_FACE_MODEL=$saved")
        }
        val embeddingRuntime = when (val saved = raw.optionalString(LEGACY_KEY_FACE_BACKEND)) {
            null -> defaults.faceEmbeddingRuntime
            "OPEN_CV" -> FaceEmbeddingRuntime.OPEN_CV
            "ONNX_RUNTIME" -> FaceEmbeddingRuntime.ONNX_RUNTIME
            "NCNN" -> FaceEmbeddingRuntime.NCNN
            "MNN" -> FaceEmbeddingRuntime.MNN
            else -> throw SettingsMigrationException("Cannot migrate $LEGACY_KEY_FACE_BACKEND=$saved")
        }
        val embeddingModel = exactLegacyEmbeddingArtifact(legacyEmbeddingModel, embeddingRuntime)
        return ModelSelections(
            faceDetectorModel = detector.first,
            faceDetectorRuntime = detector.second,
            faceEmbeddingModel = embeddingModel,
            faceEmbeddingRuntime = embeddingRuntime,
            speakerModel = raw.optionalEnum<SpeakerModelOption>(LEGACY_KEY_SPEAKER_MODEL) ?: defaults.speakerModel,
            speakerRuntime = defaults.speakerRuntime,
            vadModel = defaults.vadModel,
        )
    }

    private fun exactLegacyEmbeddingArtifact(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ): FaceEmbeddingModelOption = when (model to runtime) {
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.NCNN ->
            FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.MNN ->
            FaceEmbeddingModelOption.SFACE_2021DEC_MNN_FP32
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.NCNN ->
            FaceEmbeddingModelOption.FACE_0095_NCNN_FP32
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.MNN ->
            FaceEmbeddingModelOption.FACE_0095_MNN_FP32
        else -> model
    }

    private data class ModelSelections(
        val faceDetectorModel: FaceDetectorModelOption,
        val faceDetectorRuntime: FaceDetectorRuntime,
        val faceEmbeddingModel: FaceEmbeddingModelOption,
        val faceEmbeddingRuntime: FaceEmbeddingRuntime,
        val speakerModel: SpeakerModelOption,
        val speakerRuntime: SpeakerRuntime,
        val vadModel: VadModelOption,
    )
}

private inline fun <reified T : Enum<T>> Map<String, *>.requiredEnum(key: String): T {
    val value = optionalString(key)
        ?: throw SettingsMigrationException("Current settings schema requires $key")
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw SettingsMigrationException("Unknown $key=$value")
}

private inline fun <reified T : Enum<T>> Map<String, *>.optionalEnum(key: String): T? {
    val value = optionalString(key) ?: return null
    return enumValues<T>().firstOrNull { it.name == value }
        ?: throw SettingsMigrationException("Unknown $key=$value")
}

private fun Map<String, *>.optionalString(key: String): String? {
    val value = get(key) ?: return null
    return value as? String ?: throw SettingsMigrationException("$key must be a string")
}

private fun Any?.asFloat(key: String): Float? = when (this) {
    null -> null
    is Float -> this
    is Number -> toFloat()
    else -> throw SettingsMigrationException("$key must be numeric")
}

private fun Any?.asInt(key: String): Int? = when (this) {
    null -> null
    is Int -> this
    is Number -> toInt()
    else -> throw SettingsMigrationException("$key must be an integer")
}

private const val KEY_SCHEMA_VERSION = "settings_schema_version"
private const val KEY_FACE_DETECTOR_MODEL = "face_detector_model"
private const val KEY_FACE_DETECTOR_RUNTIME = "face_detector_runtime"
private const val KEY_FACE_EMBEDDING_MODEL = "face_embedding_model"
private const val KEY_FACE_EMBEDDING_RUNTIME = "face_embedding_runtime"
private const val KEY_SPEAKER_MODEL = "speaker_model"
private const val KEY_SPEAKER_RUNTIME = "speaker_runtime"
private const val KEY_VAD_MODEL = "vad_model"
private const val KEY_FACE_THRESHOLD = "face_cluster_join_threshold"
private const val KEY_FACE_MAX_UPDATES = "face_cluster_max_update_count"
private const val KEY_SPEAKER_THRESHOLD = "speaker_cluster_join_threshold"
private const val KEY_SPEAKER_MAX_UPDATES = "speaker_cluster_max_update_count"
private const val LEGACY_KEY_FACE_MODEL = "face_model"
private const val LEGACY_KEY_FACE_DETECTOR = "face_detector"
private const val LEGACY_KEY_FACE_BACKEND = "face_inference_backend"
private const val LEGACY_KEY_SPEAKER_MODEL = KEY_SPEAKER_MODEL
