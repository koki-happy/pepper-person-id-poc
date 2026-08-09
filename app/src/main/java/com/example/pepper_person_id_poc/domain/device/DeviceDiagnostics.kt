package com.example.pepper_person_id_poc.domain.device

data class DeviceDiagnostics(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildId: String,
    val supportedAbis: List<String>,
    val availableProcessors: Int,
    val maxHeapBytes: Long,
    val totalMemoryBytes: Long?,
    val availableMemoryBytes: Long?,
    val lowMemory: Boolean?,
    val screenWidthPixels: Int,
    val screenHeightPixels: Int,
    val densityDpi: Int,
    val frontCameras: List<CameraDiagnostic>,
    val audioConfigurations: List<AudioConfigurationDiagnostic>,
    val networkConnected: Boolean,
    val cameraPermissionGranted: Boolean,
    val recordAudioPermissionGranted: Boolean,
    val collectedAtMillis: Long,
)

data class CameraDiagnostic(
    val cameraId: String,
    val orientationDegrees: Int?,
    val previewSizes: List<String>,
)

data class AudioConfigurationDiagnostic(
    val sampleRate: Int,
    val minBufferSizeBytes: Int,
    val supported: Boolean,
)
