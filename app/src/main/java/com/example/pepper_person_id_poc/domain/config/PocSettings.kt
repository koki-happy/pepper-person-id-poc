package com.example.pepper_person_id_poc.domain.config

data class PocSettings(
    val faceModel: FaceModelOption = FaceModelOption.SFACE_2021DEC,
    val faceDetector: FaceDetectorOption = FaceDetectorOption.ML_KIT_BUNDLED,
    val faceInferenceBackend: FaceInferenceBackend = FaceInferenceBackend.OPEN_CV,
    val speakerModel: SpeakerModelOption = SpeakerModelOption.ERES2NET,
    val faceClusterJoinThreshold: Float = DEFAULT_FACE_CLUSTER_JOIN_THRESHOLD,
    val faceClusterMaxUpdateCount: Int = DEFAULT_CLUSTER_MAX_UPDATE_COUNT,
    val speakerClusterJoinThreshold: Float = speakerModel.jvsCandidateThreshold,
    val speakerClusterMaxUpdateCount: Int = DEFAULT_CLUSTER_MAX_UPDATE_COUNT,
) {
    fun isValid(): Boolean =
        faceClusterJoinThreshold in SCORE_RANGE &&
            speakerClusterJoinThreshold in SCORE_RANGE &&
            faceClusterMaxUpdateCount in CLUSTER_MAX_UPDATE_COUNT_RANGE &&
            speakerClusterMaxUpdateCount in CLUSTER_MAX_UPDATE_COUNT_RANGE

    /**
     * Selects a model together with its JVS-studio operating-point candidate.
     * These values are PoC starting points only; Pepper microphone data must be used for final tuning.
     */
    fun withSpeakerModel(model: SpeakerModelOption): PocSettings = copy(
        speakerModel = model,
        speakerClusterJoinThreshold = model.jvsCandidateThreshold,
    )

    companion object {
        const val DEFAULT_FACE_CLUSTER_JOIN_THRESHOLD = 0.60f
        const val DEFAULT_CLUSTER_MAX_UPDATE_COUNT = 20
        val SCORE_RANGE = 0f..1f
        val CLUSTER_MAX_UPDATE_COUNT_RANGE = 1..100
    }
}

enum class FaceModelOption(
    val displayName: String,
    val modelFileName: String,
    val implementationStatus: ModelImplementationStatus,
) {
    SFACE_2021DEC(
        displayName = "SFace 2021dec",
        modelFileName = "face_recognition_sface_2021dec.onnx",
        implementationStatus = ModelImplementationStatus.AVAILABLE,
    ),
    SFACE_2021DEC_INT8(
        displayName = "SFace 2021dec INT8",
        modelFileName = "face_recognition_sface_2021dec_int8.onnx",
        implementationStatus = ModelImplementationStatus.AVAILABLE,
    ),
    FACE_REIDENTIFICATION_RETAIL_0095(
        displayName = "face-reidentification-retail-0095",
        modelFileName = "face-reidentification-retail-0095.onnx",
        implementationStatus = ModelImplementationStatus.AVAILABLE,
    ),
    ;

    val isSFace: Boolean
        get() = this == SFACE_2021DEC || this == SFACE_2021DEC_INT8

    val inputSize: Int
        get() = if (isSFace) 112 else 128

    val embeddingSize: Int
        get() = if (isSFace) 128 else 256

    fun supports(backend: FaceInferenceBackend): Boolean =
        this != SFACE_2021DEC_INT8 || backend == FaceInferenceBackend.OPEN_CV
}

enum class FaceDetectorOption(
    val displayName: String,
    val description: String,
    val modelFileName: String?,
) {
    ML_KIT_BUNDLED(
        displayName = "ML Kit Face Detection 16.1.7 (Bundled)",
        description = "APK同梱モデル / 追跡ID・顔向き・5点変換",
        modelFileName = null,
    ),
    YUNET_OPEN_CV(
        displayName = "YuNet 2026may (OpenCV)",
        description = "従来比較用 / OpenCV FaceDetectorYN",
        modelFileName = "face_detection_yunet_2026may.onnx",
    ),
    YUNET_2023MAR_INT8_OPEN_CV(
        displayName = "YuNet 2023mar INT8 (OpenCV)",
        description = "INT8量子化モデル / OpenCV FaceDetectorYN",
        modelFileName = "face_detection_yunet_2023mar_int8.onnx",
    ),
}

enum class FaceInferenceBackend(
    val displayName: String,
    val description: String,
    val runtimeArtifact: String?,
) {
    OPEN_CV(
        displayName = "OpenCV 5.0.0 DNN",
        description = "基準実装 / Android CPU推論",
        runtimeArtifact = null,
    ),
    ONNX_RUNTIME(
        displayName = "ONNX Runtime 1.27.0",
        description = "API 23 / armeabi-v7a / 必要演算子のみの自前AAR",
        runtimeArtifact = "onnxruntime-mobile-1.27.0.aar",
    ),
    NCNN(
        displayName = "ncnn 20260526",
        description = "armeabi-v7a CPU版 / Vulkan・OpenMP無効",
        runtimeArtifact = "libncnn.so + ncnn model",
    ),
    MNN(
        displayName = "MNN 3.5.0",
        description = "Android ARMv7a CPU版 / CPUバックエンド",
        runtimeArtifact = "libMNN.so + MNN model",
    ),
}

enum class SpeakerModelOption(
    val configModelId: String,
    val displayName: String,
    val modelFileName: String,
    val jvsCandidateThreshold: Float,
    val jvsCandidateMargin: Float,
) {
    CAM_PLUS_PLUS(
        configModelId = "campplus-en",
        displayName = "3D-Speaker CAM++ English",
        modelFileName = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
        jvsCandidateThreshold = 0.3625838f,
        jvsCandidateMargin = 0.1168099f,
    ),
    CAM_PLUS_PLUS_ZH_EN(
        configModelId = "campplus-zh-en",
        displayName = "3D-Speaker CAM++ Chinese-English",
        modelFileName = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        jvsCandidateThreshold = 0.7508543f,
        jvsCandidateMargin = 0.0f,
    ),
    ERES2NET(
        configModelId = "eres2net-en",
        displayName = "3D-Speaker ERes2Net",
        modelFileName = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx",
        jvsCandidateThreshold = 0.70323396f,
        jvsCandidateMargin = 0.0f,
    ),
    ;

    companion object {
        /** Historical display-name keys written before stable config model IDs were introduced. */
        fun legacyPersistenceKeys(configModelId: String): Set<String> = when (configModelId) {
            "campplus-en" -> setOf("3D-Speaker CAM++", "3D-Speaker CAM++ English")
            "campplus-zh-en" -> setOf("3D-Speaker CAM++ Chinese-English")
            "eres2net-en" -> setOf("3D-Speaker ERes2Net")
            else -> emptySet()
        }
    }
}

enum class ModelImplementationStatus {
    AVAILABLE,
    PENDING,
    UNAVAILABLE,
}
