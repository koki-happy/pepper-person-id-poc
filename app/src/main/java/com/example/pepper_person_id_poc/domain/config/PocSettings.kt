package com.example.pepper_person_id_poc.domain.config

data class PocSettings(
    val faceModel: FaceModelOption = FaceModelOption.SFACE_2021DEC,
    val speakerModel: SpeakerModelOption = SpeakerModelOption.CAM_PLUS_PLUS,
    val faceThreshold: Float = DEFAULT_FACE_THRESHOLD,
    val speakerThreshold: Float = DEFAULT_SPEAKER_THRESHOLD,
    val combinedThreshold: Float = DEFAULT_COMBINED_THRESHOLD,
    val observationWindowMillis: Long = DEFAULT_OBSERVATION_WINDOW_MILLIS,
    val debugMode: Boolean = true,
) {
    fun isValid(): Boolean =
        faceThreshold in SCORE_RANGE &&
            speakerThreshold in SCORE_RANGE &&
            combinedThreshold in SCORE_RANGE &&
            observationWindowMillis in OBSERVATION_WINDOW_RANGE

    companion object {
        const val DEFAULT_FACE_THRESHOLD = 0.60f
        const val DEFAULT_SPEAKER_THRESHOLD = 0.60f
        const val DEFAULT_COMBINED_THRESHOLD = 0.70f
        const val DEFAULT_OBSERVATION_WINDOW_MILLIS = 3_000L

        val SCORE_RANGE = 0f..1f
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
        modelFileName = "face-reidentification-retail-0095.xml",
        implementationStatus = ModelImplementationStatus.UNAVAILABLE,
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
