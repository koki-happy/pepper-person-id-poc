package com.example.pepper_person_id_poc.domain.config

data class PocSettings(
    val faceModel: FaceModelOption = FaceModelOption.SFACE_2021DEC,
    val speakerModel: SpeakerModelOption = SpeakerModelOption.ERES2NET,
    val faceThreshold: Float = DEFAULT_FACE_THRESHOLD,
    val faceMargin: Float = DEFAULT_FACE_MARGIN,
    val faceRegistrationAnalysisIntervalMillis: Long = DEFAULT_FACE_REGISTRATION_INTERVAL_MILLIS,
    val faceIdentificationAnalysisIntervalMillis: Long = DEFAULT_FACE_IDENTIFICATION_INTERVAL_MILLIS,
    val facePoseStableDurationMillis: Long = DEFAULT_FACE_POSE_STABLE_DURATION_MILLIS,
    val faceFrontYawDegrees: Float = DEFAULT_FACE_FRONT_YAW_DEGREES,
    val faceFrontPitchDegrees: Float = DEFAULT_FACE_FRONT_PITCH_DEGREES,
    val faceSideMinimumYawDegrees: Float = DEFAULT_FACE_SIDE_MINIMUM_YAW_DEGREES,
    val faceSideMaximumYawDegrees: Float = DEFAULT_FACE_SIDE_MAXIMUM_YAW_DEGREES,
    val faceSmoothingSampleCount: Int = DEFAULT_FACE_SMOOTHING_SAMPLE_COUNT,
    val speakerThreshold: Float = DEFAULT_SPEAKER_THRESHOLD,
    val combinedThreshold: Float = DEFAULT_COMBINED_THRESHOLD,
    val observationWindowMillis: Long = DEFAULT_OBSERVATION_WINDOW_MILLIS,
    val debugMode: Boolean = true,
) {
    fun isValid(): Boolean =
        faceThreshold in SCORE_RANGE &&
            faceMargin in MARGIN_RANGE &&
            faceRegistrationAnalysisIntervalMillis in FACE_REGISTRATION_INTERVAL_RANGE &&
            faceIdentificationAnalysisIntervalMillis in FACE_IDENTIFICATION_INTERVAL_RANGE &&
            facePoseStableDurationMillis in FACE_POSE_STABLE_DURATION_RANGE &&
            faceFrontYawDegrees in FACE_FRONT_ANGLE_RANGE &&
            faceFrontPitchDegrees in FACE_FRONT_ANGLE_RANGE &&
            faceSideMinimumYawDegrees in FACE_SIDE_ANGLE_RANGE &&
            faceSideMaximumYawDegrees in FACE_SIDE_ANGLE_RANGE &&
            faceSideMinimumYawDegrees < faceSideMaximumYawDegrees &&
            faceSmoothingSampleCount in FACE_SMOOTHING_SAMPLE_COUNT_RANGE &&
            speakerThreshold in SCORE_RANGE &&
            combinedThreshold in SCORE_RANGE &&
            observationWindowMillis in OBSERVATION_WINDOW_RANGE

    companion object {
        const val DEFAULT_FACE_THRESHOLD = 0.60f
        const val DEFAULT_FACE_MARGIN = 0.0f
        const val DEFAULT_FACE_REGISTRATION_INTERVAL_MILLIS = 200L
        const val DEFAULT_FACE_IDENTIFICATION_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_FACE_POSE_STABLE_DURATION_MILLIS = 1_000L
        const val DEFAULT_FACE_FRONT_YAW_DEGREES = 8f
        const val DEFAULT_FACE_FRONT_PITCH_DEGREES = 8f
        const val DEFAULT_FACE_SIDE_MINIMUM_YAW_DEGREES = 18f
        const val DEFAULT_FACE_SIDE_MAXIMUM_YAW_DEGREES = 32f
        const val DEFAULT_FACE_SMOOTHING_SAMPLE_COUNT = 5
        const val DEFAULT_SPEAKER_THRESHOLD = 0.60f
        const val DEFAULT_COMBINED_THRESHOLD = 0.70f
        const val DEFAULT_OBSERVATION_WINDOW_MILLIS = 3_000L

        val SCORE_RANGE = 0f..1f
        val MARGIN_RANGE = 0f..2f
        val FACE_REGISTRATION_INTERVAL_RANGE = 100L..2_000L
        val FACE_IDENTIFICATION_INTERVAL_RANGE = 200L..5_000L
        val FACE_POSE_STABLE_DURATION_RANGE = 250L..5_000L
        val FACE_FRONT_ANGLE_RANGE = 1f..30f
        val FACE_SIDE_ANGLE_RANGE = 5f..60f
        val FACE_SMOOTHING_SAMPLE_COUNT_RANGE = 1..15
        val OBSERVATION_WINDOW_RANGE = 500L..10_000L
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
    FACE_REIDENTIFICATION_RETAIL_0095(
        displayName = "face-reidentification-retail-0095",
        modelFileName = "face-reidentification-retail-0095.onnx",
        implementationStatus = ModelImplementationStatus.AVAILABLE,
    ),
}

enum class SpeakerModelOption(
    val displayName: String,
    val modelFileName: String,
) {
    CAM_PLUS_PLUS(
        displayName = "3D-Speaker CAM++",
        modelFileName = "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
    ),
    ERES2NET(
        displayName = "3D-Speaker ERes2Net",
        modelFileName = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx",
    ),
}

enum class ModelImplementationStatus {
    AVAILABLE,
    PENDING,
    UNAVAILABLE,
}
