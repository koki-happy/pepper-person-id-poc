package com.example.pepper_person_id_poc.domain.config

data class PocSettings(
    val faceDetectorModel: FaceDetectorModelOption = FaceDetectorModelOption.YUNET_2026MAY_FP32,
    val faceDetectorRuntime: FaceDetectorRuntime = FaceDetectorRuntime.OPEN_CV,
    val faceEmbeddingModel: FaceEmbeddingModelOption = FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
    val faceEmbeddingRuntime: FaceEmbeddingRuntime = FaceEmbeddingRuntime.OPEN_CV,
    val speakerModel: SpeakerModelOption = SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN,
    val speakerRuntime: SpeakerRuntime = SpeakerRuntime.SHERPA_ONNX,
    val vadModel: VadModelOption = VadModelOption.SILERO_VAD,
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

enum class FaceDetectorModelOption(
    val artifactId: String,
    val displayName: String,
    val modelFileName: String?,
    val description: String,
) {
    ML_KIT_BUNDLED(
        artifactId = "mlkit-face-detection-16.1.7-bundled",
        displayName = "ML Kit Face Detection 16.1.7 (Bundled)",
        modelFileName = null,
        description = "APK同梱モデル / 追跡ID・顔向き・5点変換",
    ),
    YUNET_2026MAY_FP32(
        artifactId = "yunet-2026may-onnx-fp32",
        displayName = "YuNet 2026may FP32",
        modelFileName = "face_detection_yunet_2026may.onnx",
        description = "YuNet FP32 ONNX",
    ),
    YUNET_2023MAR_INT8(
        artifactId = "yunet-2023mar-onnx-int8",
        displayName = "YuNet 2023mar INT8",
        modelFileName = "face_detection_yunet_2023mar_int8.onnx",
        description = "YuNet INT8 ONNX",
    ),
}

enum class FaceDetectorRuntime(
    val runtimeId: String,
    val displayName: String,
    val description: String,
) {
    ML_KIT(
        runtimeId = "mlkit-face-16.1.7-bundled",
        displayName = "ML Kit 16.1.7 bundled runtime",
        description = "ML Kit SDK内蔵の顔検出runtime",
    ),
    OPEN_CV(
        runtimeId = "opencv-5.0.0-android-cpu",
        displayName = "OpenCV 5.0.0",
        description = "Android CPU / FaceDetectorYN",
    ),
}

enum class FaceEmbeddingModelOption(
    val artifactId: String,
    val modelSpaceId: String,
    val displayName: String,
    val modelFileName: String,
    val format: FaceModelFormat,
    val embeddingSize: Int,
    val inputSize: Int,
) {
    SFACE_2021DEC_FP32(
        artifactId = "sface-2021dec-onnx-fp32",
        modelSpaceId = "face-sface-fp32-opencv-128-l2-v1",
        displayName = "SFace 2021dec FP32",
        modelFileName = "face_recognition_sface_2021dec.onnx",
        format = FaceModelFormat.ONNX,
        embeddingSize = 128,
        inputSize = 112,
    ),
    SFACE_2021DEC_INT8(
        artifactId = "sface-2021dec-onnx-int8",
        modelSpaceId = "face-sface-int8-opencv-128-l2-v1",
        displayName = "SFace 2021dec INT8",
        modelFileName = "face_recognition_sface_2021dec_int8.onnx",
        format = FaceModelFormat.ONNX,
        embeddingSize = 128,
        inputSize = 112,
    ),
    FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32(
        artifactId = "face-0095-onnx-fp32",
        modelSpaceId = "face-0095-256-l2-v1",
        displayName = "face-reidentification-retail-0095 ONNX FP32",
        modelFileName = "face-reidentification-retail-0095.onnx",
        format = FaceModelFormat.ONNX,
        embeddingSize = 256,
        inputSize = 128,
    ),
    SFACE_2021DEC_NCNN_FP32(
        artifactId = "sface-2021dec-ncnn-fp32",
        modelSpaceId = "face-sface-fp32-ncnn-128-l2-unverified",
        displayName = "SFace 2021dec ncnn FP32",
        modelFileName = "face_recognition_sface_2021dec.ncnn.param",
        format = FaceModelFormat.NCNN,
        embeddingSize = 128,
        inputSize = 112,
    ),
    SFACE_2021DEC_MNN_FP32(
        artifactId = "sface-2021dec-mnn-fp32",
        modelSpaceId = "face-sface-fp32-mnn-128-l2-unverified",
        displayName = "SFace 2021dec MNN FP32",
        modelFileName = "face_recognition_sface_2021dec.mnn",
        format = FaceModelFormat.MNN,
        embeddingSize = 128,
        inputSize = 112,
    ),
    FACE_0095_NCNN_FP32(
        artifactId = "face-0095-ncnn-fp32",
        modelSpaceId = "face-0095-ncnn-256-l2-unverified",
        displayName = "face-reidentification-retail-0095 ncnn FP32",
        modelFileName = "face-reidentification-retail-0095.ncnn.param",
        format = FaceModelFormat.NCNN,
        embeddingSize = 256,
        inputSize = 128,
    ),
    FACE_0095_MNN_FP32(
        artifactId = "face-0095-mnn-fp32",
        modelSpaceId = "face-0095-mnn-256-l2-unverified",
        displayName = "face-reidentification-retail-0095 MNN FP32",
        modelFileName = "face-reidentification-retail-0095.mnn",
        format = FaceModelFormat.MNN,
        embeddingSize = 256,
        inputSize = 128,
    ),
    ;

    val isSFace: Boolean
        get() = name.startsWith("SFACE_")
}

enum class FaceModelFormat {
    ONNX,
    NCNN,
    MNN,
}

enum class FaceEmbeddingRuntime(
    val runtimeId: String,
    val displayName: String,
    val description: String,
) {
    OPEN_CV(
        runtimeId = "opencv-5.0.0-android-cpu",
        displayName = "OpenCV 5.0.0 DNN",
        description = "基準実装 / Android CPU推論",
    ),
    ONNX_RUNTIME(
        runtimeId = "onnxruntime-mobile-1.27.0-android-cpu",
        displayName = "ONNX Runtime 1.27.0",
        description = "API 23 / ARMv7 AARが未配置の場合はBLOCKED",
    ),
    NCNN(
        runtimeId = "ncnn-20260526-android-cpu",
        displayName = "ncnn 20260526",
        description = "armeabi-v7a CPU版 / Vulkan・OpenMP無効",
    ),
    MNN(
        runtimeId = "mnn-3.5.0-android-cpu",
        displayName = "MNN 3.5.0",
        description = "Android ARMv7a CPU版 / CPUバックエンド",
    ),
}

enum class SpeakerRuntime(val runtimeId: String, val displayName: String) {
    SHERPA_ONNX(
        runtimeId = "sherpa-onnx-1.13.4-android-cpu",
        displayName = "sherpa-onnx 1.13.4",
    ),
}

enum class VadModelOption(val artifactId: String, val displayName: String) {
    SILERO_VAD(
        artifactId = "silero-vad-onnx-fp32",
        displayName = "Silero VAD",
    ),
}

enum class SpeakerModelOption(
    val configModelId: String,
    val artifactId: String,
    val modelSpaceId: String,
    val displayName: String,
    val modelFileName: String,
    val jvsCandidateThreshold: Float,
    val jvsCandidateMargin: Float,
) {
    CAM_PLUS_PLUS(
        configModelId = "campplus-en",
        artifactId = "campplus-en-onnx-fp32",
        modelSpaceId = "speaker-campplus-en-512-l2-v1",
        displayName = "3D-Speaker CAM++ English",
        modelFileName = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
        jvsCandidateThreshold = 0.3625838f,
        jvsCandidateMargin = 0.1168099f,
    ),
    CAM_PLUS_PLUS_ZH_EN(
        configModelId = "campplus-zh-en",
        artifactId = "campplus-zh-en-onnx-fp32",
        modelSpaceId = "speaker-campplus-zh-en-192-l2-v1",
        displayName = "3D-Speaker CAM++ Chinese-English",
        modelFileName = "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
        jvsCandidateThreshold = 0.7508543f,
        jvsCandidateMargin = 0.0f,
    ),
    ERES2NET(
        configModelId = "eres2net-en",
        artifactId = "eres2net-en-onnx-fp32",
        modelSpaceId = "speaker-eres2net-en-192-l2-v1",
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
