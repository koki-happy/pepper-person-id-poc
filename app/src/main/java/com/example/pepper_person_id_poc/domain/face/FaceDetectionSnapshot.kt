package com.example.pepper_person_id_poc.domain.face

data class FaceDetectionSnapshot(
    val status: FaceDetectionStatus = FaceDetectionStatus.NOT_STARTED,
    val faces: List<DetectedFace> = emptyList(),
    val processingTimeMillis: Long? = null,
    val analysisFramesPerSecond: Float = 0f,
    val modelName: String = "YuNet 2026may",
    val error: String? = null,
)

data class DetectedFace(
    val trackId: String,
    val boundingBox: NormalizedBoundingBox,
    val detectionScore: Float,
)

enum class FaceDetectionStatus {
    NOT_STARTED,
    NO_FACE,
    FACE_DETECTED,
    MULTIPLE_FACES,
    MODEL_UNAVAILABLE,
    ERROR,
}
