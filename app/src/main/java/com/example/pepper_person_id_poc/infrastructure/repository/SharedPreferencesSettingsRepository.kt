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
import com.example.pepper_person_id_poc.domain.config.VadRuntime

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
            .putString(KEY_VAD_RUNTIME, settings.vadRuntime.name)
            .putFloat(KEY_FACE_THRESHOLD, settings.faceClusterJoinThreshold)
            .putInt(KEY_FACE_MAX_UPDATES, settings.faceClusterMaxUpdateCount)
            .putFloat(KEY_SPEAKER_THRESHOLD, settings.speakerClusterJoinThreshold)
            .putFloat(KEY_SPEAKER_OVERLAP_DISPLAY_THRESHOLD, settings.speakerOverlapDisplayThreshold)
            .putInt(KEY_SPEAKER_MAX_UPDATES, settings.speakerClusterMaxUpdateCount)
            .putBoolean(KEY_MULTIPLE_SAMPLES_ENABLED, settings.multipleSamplesEnabled)
            .putLong(KEY_FACE_ANALYSIS_INTERVAL, settings.faceAnalysisIntervalMillis)
            .putFloat(KEY_FACE_DETECTION_SCORE, settings.faceDetectionScoreThreshold)
            .putFloat(KEY_FACE_NMS, settings.faceNmsThreshold)
            .putInt(KEY_FACE_MAX_CANDIDATES, settings.faceMaximumDetectionCandidates)
            .putFloat(KEY_ML_KIT_MIN_FACE_SIZE, settings.mlKitMinimumFaceSize)
            .putFloat(KEY_FACE_LABEL_IOU, settings.faceLabelContinuationIou)
            .putInt(KEY_FACE_LABEL_MISSING_FRAMES, settings.faceLabelMaximumMissingFrames)
            .putFloat(KEY_VAD_THRESHOLD, settings.vadThreshold)
            .putLong(KEY_VAD_MIN_SILENCE, settings.vadMinimumSilenceMillis)
            .putLong(KEY_VAD_MIN_SPEECH, settings.vadMinimumSpeechMillis)
            .putLong(KEY_VAD_MAX_SPEECH, settings.vadMaximumSpeechMillis)
            .putLong(KEY_UTTERANCE_END_SILENCE, settings.utteranceEndSilenceMillis)
            .putLong(KEY_MAX_UTTERANCE, settings.maximumUtteranceMillis)
            .putLong(KEY_SPEAKER_MIN_AUDIO, settings.speakerMinimumAudioMillis)
            .putFloat(KEY_SPEAKER_MIN_VOICED_RATIO, settings.speakerMinimumVoicedRatio)
            .putFloat(KEY_SPEAKER_MIN_RMS, settings.speakerMinimumRms)
            .putFloat(KEY_CLIPPING_AMPLITUDE, settings.clippingAmplitudeThreshold)
            .putFloat(KEY_MAX_CLIPPING_RATIO, settings.maximumClippingRatio)
            .putLong(KEY_SPEAKER_UPDATE_MIN_AUDIO, settings.speakerUpdateMinimumAudioMillis)
            .putFloat(KEY_SPEAKER_UPDATE_MIN_VOICED_RATIO, settings.speakerUpdateMinimumVoicedRatio)
            .remove(REMOVED_KEY_SHOW_FACE_LANDMARKS)
            .remove(REMOVED_KEY_LOAD_TEST_VIDEO)
            .remove(REMOVED_KEY_FACE_SPEAKER_OVERLAP_THRESHOLD)
            .remove(REMOVED_KEY_SPEAKER_LABEL_SIMILARITY)
            .remove(REMOVED_KEY_SPEAKER_LABEL_MISSING_SEGMENTS)
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
    const val CURRENT_SCHEMA_VERSION = 7

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
        val modelSelections = if (schemaVersion >= 2) {
            ModelSelections(
                faceDetectorModel = raw.requiredFaceDetectorModel(),
                faceDetectorRuntime = raw.requiredEnum(KEY_FACE_DETECTOR_RUNTIME),
                faceEmbeddingModel = raw.requiredEnum(KEY_FACE_EMBEDDING_MODEL),
                faceEmbeddingRuntime = raw.requiredEnum(KEY_FACE_EMBEDDING_RUNTIME),
                speakerModel = raw.requiredSpeakerModel(defaults.speakerModel),
                speakerRuntime = if (raw.optionalString(KEY_SPEAKER_RUNTIME) == "ONNX_RUNTIME") {
                    defaults.speakerRuntime
                } else {
                    raw.requiredEnum(KEY_SPEAKER_RUNTIME)
                },
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
            vadRuntime = raw.optionalEnum<VadRuntime>(KEY_VAD_RUNTIME) ?: VadRuntime.SHERPA_ONNX,
            faceClusterJoinThreshold =
                raw[KEY_FACE_THRESHOLD].asFloat(KEY_FACE_THRESHOLD) ?: defaults.faceClusterJoinThreshold,
            faceClusterMaxUpdateCount =
                raw[KEY_FACE_MAX_UPDATES].asInt(KEY_FACE_MAX_UPDATES) ?: defaults.faceClusterMaxUpdateCount,
            speakerClusterJoinThreshold =
                raw[KEY_SPEAKER_THRESHOLD].asFloat(KEY_SPEAKER_THRESHOLD)
                    ?: modelSelections.speakerModel.jvsCandidateThreshold,
            speakerOverlapDisplayThreshold =
                raw[KEY_SPEAKER_OVERLAP_DISPLAY_THRESHOLD].asFloat(KEY_SPEAKER_OVERLAP_DISPLAY_THRESHOLD)
                    ?: defaults.speakerOverlapDisplayThreshold,
            speakerClusterMaxUpdateCount =
                raw[KEY_SPEAKER_MAX_UPDATES].asInt(KEY_SPEAKER_MAX_UPDATES)
                    ?: defaults.speakerClusterMaxUpdateCount,
            multipleSamplesEnabled = raw[KEY_MULTIPLE_SAMPLES_ENABLED].asBoolean(KEY_MULTIPLE_SAMPLES_ENABLED)
                ?: false,
            faceAnalysisIntervalMillis = raw[KEY_FACE_ANALYSIS_INTERVAL].asLong(KEY_FACE_ANALYSIS_INTERVAL) ?: defaults.faceAnalysisIntervalMillis,
            faceDetectionScoreThreshold = raw[KEY_FACE_DETECTION_SCORE].asFloat(KEY_FACE_DETECTION_SCORE) ?: defaults.faceDetectionScoreThreshold,
            faceNmsThreshold = raw[KEY_FACE_NMS].asFloat(KEY_FACE_NMS) ?: defaults.faceNmsThreshold,
            faceMaximumDetectionCandidates = raw[KEY_FACE_MAX_CANDIDATES].asInt(KEY_FACE_MAX_CANDIDATES) ?: defaults.faceMaximumDetectionCandidates,
            mlKitMinimumFaceSize = raw[KEY_ML_KIT_MIN_FACE_SIZE].asFloat(KEY_ML_KIT_MIN_FACE_SIZE) ?: defaults.mlKitMinimumFaceSize,
            faceLabelContinuationIou = raw[KEY_FACE_LABEL_IOU].asFloat(KEY_FACE_LABEL_IOU) ?: defaults.faceLabelContinuationIou,
            faceLabelMaximumMissingFrames = raw[KEY_FACE_LABEL_MISSING_FRAMES].asInt(KEY_FACE_LABEL_MISSING_FRAMES) ?: defaults.faceLabelMaximumMissingFrames,
            vadThreshold = raw[KEY_VAD_THRESHOLD].asFloat(KEY_VAD_THRESHOLD) ?: defaults.vadThreshold,
            vadMinimumSilenceMillis = raw[KEY_VAD_MIN_SILENCE].asLong(KEY_VAD_MIN_SILENCE) ?: defaults.vadMinimumSilenceMillis,
            vadMinimumSpeechMillis = raw[KEY_VAD_MIN_SPEECH].asLong(KEY_VAD_MIN_SPEECH) ?: defaults.vadMinimumSpeechMillis,
            vadMaximumSpeechMillis = raw[KEY_VAD_MAX_SPEECH].asLong(KEY_VAD_MAX_SPEECH) ?: defaults.vadMaximumSpeechMillis,
            utteranceEndSilenceMillis = raw[KEY_UTTERANCE_END_SILENCE].asLong(KEY_UTTERANCE_END_SILENCE) ?: defaults.utteranceEndSilenceMillis,
            maximumUtteranceMillis = (raw[KEY_MAX_UTTERANCE].asLong(KEY_MAX_UTTERANCE)
                ?: defaults.maximumUtteranceMillis)
                .coerceAtMost(PocSettings.MAXIMUM_UTTERANCE_RANGE.last),
            speakerMinimumAudioMillis = raw[KEY_SPEAKER_MIN_AUDIO].asLong(KEY_SPEAKER_MIN_AUDIO) ?: defaults.speakerMinimumAudioMillis,
            speakerMinimumVoicedRatio = raw[KEY_SPEAKER_MIN_VOICED_RATIO].asFloat(KEY_SPEAKER_MIN_VOICED_RATIO) ?: defaults.speakerMinimumVoicedRatio,
            speakerMinimumRms = raw[KEY_SPEAKER_MIN_RMS].asFloat(KEY_SPEAKER_MIN_RMS) ?: defaults.speakerMinimumRms,
            clippingAmplitudeThreshold = raw[KEY_CLIPPING_AMPLITUDE].asFloat(KEY_CLIPPING_AMPLITUDE) ?: defaults.clippingAmplitudeThreshold,
            maximumClippingRatio = raw[KEY_MAX_CLIPPING_RATIO].asFloat(KEY_MAX_CLIPPING_RATIO) ?: defaults.maximumClippingRatio,
            speakerUpdateMinimumAudioMillis = raw[KEY_SPEAKER_UPDATE_MIN_AUDIO].asLong(KEY_SPEAKER_UPDATE_MIN_AUDIO) ?: defaults.speakerUpdateMinimumAudioMillis,
            speakerUpdateMinimumVoicedRatio = raw[KEY_SPEAKER_UPDATE_MIN_VOICED_RATIO].asFloat(KEY_SPEAKER_UPDATE_MIN_VOICED_RATIO) ?: defaults.speakerUpdateMinimumVoicedRatio,
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
                FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.OPEN_CV
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
            speakerModel = raw.optionalSpeakerModel(defaults.speakerModel),
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

private fun Map<String, *>.requiredSpeakerModel(defaultModel: SpeakerModelOption): SpeakerModelOption {
    val value = optionalString(KEY_SPEAKER_MODEL)
        ?: throw SettingsMigrationException("Current settings schema requires $KEY_SPEAKER_MODEL")
    return value.toSpeakerModelOrDefault(defaultModel)
}

private fun Map<String, *>.optionalSpeakerModel(defaultModel: SpeakerModelOption): SpeakerModelOption =
    optionalString(LEGACY_KEY_SPEAKER_MODEL)?.toSpeakerModelOrDefault(defaultModel) ?: defaultModel

private fun String.toSpeakerModelOrDefault(defaultModel: SpeakerModelOption): SpeakerModelOption {
    if (this in REMOVED_SPEAKER_MODEL_VALUES) return defaultModel
    return enumValues<SpeakerModelOption>().firstOrNull { it.name == this }
        ?: throw SettingsMigrationException("Unknown $KEY_SPEAKER_MODEL=$this")
}

private fun Map<String, *>.requiredFaceDetectorModel(): FaceDetectorModelOption {
    val value = optionalString(KEY_FACE_DETECTOR_MODEL)
        ?: throw SettingsMigrationException("Current settings schema requires $KEY_FACE_DETECTOR_MODEL")
    if (value == "YUNET_2023MAR_INT8") {
        return FaceDetectorModelOption.YUNET_2026MAY_FP32
    }
    return enumValues<FaceDetectorModelOption>().firstOrNull { it.name == value }
        ?: throw SettingsMigrationException("Unknown $KEY_FACE_DETECTOR_MODEL=$value")
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

private fun Any?.asLong(key: String): Long? = when (this) {
    null -> null
    is Long -> this
    is Number -> toLong()
    else -> throw SettingsMigrationException("$key must be an integer")
}

private fun Any?.asBoolean(key: String): Boolean? = when (this) {
    null -> null
    is Boolean -> this
    else -> throw SettingsMigrationException("$key must be a boolean")
}

private const val KEY_SCHEMA_VERSION = "settings_schema_version"
private const val KEY_FACE_DETECTOR_MODEL = "face_detector_model"
private const val KEY_FACE_DETECTOR_RUNTIME = "face_detector_runtime"
private const val KEY_FACE_EMBEDDING_MODEL = "face_embedding_model"
private const val KEY_FACE_EMBEDDING_RUNTIME = "face_embedding_runtime"
private const val KEY_SPEAKER_MODEL = "speaker_model"
private const val KEY_SPEAKER_RUNTIME = "speaker_runtime"
private const val KEY_VAD_MODEL = "vad_model"
private const val KEY_VAD_RUNTIME = "vad_runtime"
private const val KEY_FACE_THRESHOLD = "face_cluster_join_threshold"
private const val KEY_FACE_MAX_UPDATES = "face_cluster_max_update_count"
private const val KEY_SPEAKER_THRESHOLD = "speaker_cluster_join_threshold"
private const val KEY_SPEAKER_OVERLAP_DISPLAY_THRESHOLD = "speaker_overlap_display_threshold"
private const val KEY_SPEAKER_MAX_UPDATES = "speaker_cluster_max_update_count"
private const val KEY_MULTIPLE_SAMPLES_ENABLED = "multiple_samples_enabled"
private const val KEY_FACE_ANALYSIS_INTERVAL = "face_analysis_interval_millis"
private const val KEY_FACE_DETECTION_SCORE = "face_detection_score_threshold"
private const val KEY_FACE_NMS = "face_nms_threshold"
private const val KEY_FACE_MAX_CANDIDATES = "face_maximum_detection_candidates"
private const val KEY_ML_KIT_MIN_FACE_SIZE = "ml_kit_minimum_face_size"
private const val KEY_FACE_LABEL_IOU = "face_label_continuation_iou"
private const val KEY_FACE_LABEL_MISSING_FRAMES = "face_label_maximum_missing_frames"
private const val KEY_VAD_THRESHOLD = "vad_threshold"
private const val KEY_VAD_MIN_SILENCE = "vad_minimum_silence_millis"
private const val KEY_VAD_MIN_SPEECH = "vad_minimum_speech_millis"
private const val KEY_VAD_MAX_SPEECH = "vad_maximum_speech_millis"
private const val KEY_UTTERANCE_END_SILENCE = "utterance_end_silence_millis"
private const val KEY_MAX_UTTERANCE = "maximum_utterance_millis"
private const val KEY_SPEAKER_MIN_AUDIO = "speaker_minimum_audio_millis"
private const val KEY_SPEAKER_MIN_VOICED_RATIO = "speaker_minimum_voiced_ratio"
private const val KEY_SPEAKER_MIN_RMS = "speaker_minimum_rms"
private const val KEY_CLIPPING_AMPLITUDE = "clipping_amplitude_threshold"
private const val KEY_MAX_CLIPPING_RATIO = "maximum_clipping_ratio"
private const val KEY_SPEAKER_UPDATE_MIN_AUDIO = "speaker_update_minimum_audio_millis"
private const val KEY_SPEAKER_UPDATE_MIN_VOICED_RATIO = "speaker_update_minimum_voiced_ratio"
private const val REMOVED_KEY_SPEAKER_LABEL_SIMILARITY = "speaker_label_continuation_similarity"
private const val REMOVED_KEY_SPEAKER_LABEL_MISSING_SEGMENTS = "speaker_label_maximum_missing_segments"
private const val REMOVED_KEY_SHOW_FACE_LANDMARKS = "show_face_landmarks"
private const val REMOVED_KEY_LOAD_TEST_VIDEO = "load_test_video"
private const val REMOVED_KEY_FACE_SPEAKER_OVERLAP_THRESHOLD = "face_speaker_overlap_threshold"
private const val LEGACY_KEY_FACE_MODEL = "face_model"
private const val LEGACY_KEY_FACE_DETECTOR = "face_detector"
private const val LEGACY_KEY_FACE_BACKEND = "face_inference_backend"
private const val LEGACY_KEY_SPEAKER_MODEL = KEY_SPEAKER_MODEL
private val REMOVED_SPEAKER_MODEL_VALUES = setOf(
    "REDIMNET2_B1",
    "CAM_PLUS_PLUS",
    "campplus-en",
    "3D-Speaker CAM++",
    "3D-Speaker CAM++ English",
    "ERES2NET",
    "eres2net-en",
    "3D-Speaker ERes2Net",
    "SPEAKERNET_M",
    "speakernet-m",
    "NeMo SpeakerNet-M",
    "TITANET_S",
    "titanet-s",
    "NeMo TitaNet-S",
)
