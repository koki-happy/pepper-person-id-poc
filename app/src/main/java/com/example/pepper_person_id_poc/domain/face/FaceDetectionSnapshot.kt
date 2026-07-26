package com.example.pepper_person_id_poc.domain.face

data class FaceDetectionSnapshot(
    val status: FaceDetectionStatus = FaceDetectionStatus.NOT_STARTED,
    val faces: List<DetectedFace> = emptyList(),
    val processingTimeMillis: Long? = null,
    val analysisFramesPerSecond: Float = 0f,
    val modelName: String = "YuNet 2026may",
    val orientedFrameWidth: Int = 0,
    val orientedFrameHeight: Int = 0,
    val error: String? = null,
)

data class DetectedFace(
    val trackId: String,
    val boundingBox: NormalizedBoundingBox,
    val detectionScore: Float?,
    val landmarks: List<FaceLandmark> = emptyList(),
    val inputBoundingBox: PixelBoundingBox? = null,
    val inputLandmarks: List<PixelFaceLandmark> = emptyList(),
    val trackDurationMillis: Long = 0L,
    val detectedLandmarkCount: Int = inputLandmarks.size,
    val qualityAssessment: FaceQualityAssessment? = null,
)

data class PixelBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class PixelFaceLandmark(
    val type: FaceLandmarkType,
    val x: Float,
    val y: Float,
)

data class FaceLandmark(
    val type: FaceLandmarkType,
    val x: Float,
    val y: Float,
) {
    init {
        require(x in 0f..1f && y in 0f..1f)
    }
}

enum class FaceLandmarkType(val displayName: String) {
    RIGHT_EYE("右目"),
    LEFT_EYE("左目"),
    NOSE_TIP("鼻"),
    RIGHT_MOUTH_CORNER("右口角"),
    LEFT_MOUTH_CORNER("左口角"),
}

enum class FaceDetectionStatus {
    NOT_STARTED,
    NO_FACE,
    FACE_DETECTED,
    MULTIPLE_FACES,
    MODEL_UNAVAILABLE,
    ERROR,
}
