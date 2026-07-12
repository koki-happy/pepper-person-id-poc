package com.example.pepper_person_id_poc.infrastructure.camera

data class CameraPreviewState(
    val status: CameraStatus = CameraStatus.Idle,
    val frameCount: Long = 0,
    val inputFramesPerSecond: Float = 0f,
    val resolution: String? = null,
    val error: String? = null,
)

enum class CameraStatus {
    Idle,
    Starting,
    Running,
    Stopped,
    Error,
}
