package com.example.pepper_person_id_poc.domain.speaker

data class AudioCaptureFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: String,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive" }
        require(channelCount > 0) { "channelCount must be positive" }
        require(encoding.isNotBlank()) { "encoding must not be blank" }
    }

    companion object {
        val STRICT_16_KHZ_MONO_PCM16 = AudioCaptureFormat(
            sampleRateHz = 16_000,
            channelCount = 1,
            encoding = "PCM_16BIT",
        )
    }
}

enum class AudioCaptureInitializationStatus {
    SUCCESS,
    FAILED,
}

enum class AudioCaptureInitializationStage {
    MIN_BUFFER_QUERY,
    AUDIO_RECORD_CONSTRUCTION,
    AUDIO_RECORD_INITIALIZATION,
    START_RECORDING,
}

enum class AudioCaptureInitializationFailure {
    MIN_BUFFER_QUERY_FAILED,
    AUDIO_RECORD_CONSTRUCTION_FAILED,
    AUDIO_RECORD_UNINITIALIZED,
    START_RECORDING_FAILED,
    RECORDING_STATE_NOT_RECORDING,
}

data class AudioCaptureInitialization(
    val status: AudioCaptureInitializationStatus,
    val stage: AudioCaptureInitializationStage,
    val requestedFormat: AudioCaptureFormat = AudioCaptureFormat.STRICT_16_KHZ_MONO_PCM16,
    val minBufferSizeBytes: Int? = null,
    val recorderBufferSizeBytes: Int? = null,
    val audioRecordState: Int? = null,
    val recordingState: Int? = null,
    val failure: AudioCaptureInitializationFailure? = null,
    val platformCode: Int? = null,
    val message: String? = null,
) {
    init {
        require(requestedFormat == AudioCaptureFormat.STRICT_16_KHZ_MONO_PCM16) {
            "Only strict 16 kHz mono PCM16 capture is supported"
        }
        when (status) {
            AudioCaptureInitializationStatus.SUCCESS -> {
                require(stage == AudioCaptureInitializationStage.START_RECORDING) {
                    "Successful initialization must complete START_RECORDING"
                }
                require(failure == null) { "Successful initialization must not have a failure" }
                require(minBufferSizeBytes != null && minBufferSizeBytes > 0) {
                    "Successful initialization requires a positive minBufferSizeBytes"
                }
                require(recorderBufferSizeBytes != null && recorderBufferSizeBytes > 0) {
                    "Successful initialization requires a positive recorderBufferSizeBytes"
                }
            }

            AudioCaptureInitializationStatus.FAILED -> {
                require(failure != null) { "Failed initialization requires a failure reason" }
            }
        }
    }

    val succeeded: Boolean
        get() = status == AudioCaptureInitializationStatus.SUCCESS

    fun diagnosticAttributes(): Map<String, String> = buildMap {
        put("captureStatus", status.name)
        put("captureStage", stage.name)
        put("sampleRate", requestedFormat.sampleRateHz.toString())
        put("channels", requestedFormat.channelCount.toString())
        put("encoding", requestedFormat.encoding)
        minBufferSizeBytes?.let { put("minBufferSizeBytes", it.toString()) }
        recorderBufferSizeBytes?.let { put("recorderBufferSizeBytes", it.toString()) }
        audioRecordState?.let { put("audioRecordState", it.toString()) }
        recordingState?.let { put("recordingState", it.toString()) }
        failure?.let { put("captureFailure", it.name) }
        platformCode?.let { put("platformCode", it.toString()) }
    }

    companion object {
        fun success(
            minBufferSizeBytes: Int,
            recorderBufferSizeBytes: Int,
            audioRecordState: Int,
            recordingState: Int,
        ) = AudioCaptureInitialization(
            status = AudioCaptureInitializationStatus.SUCCESS,
            stage = AudioCaptureInitializationStage.START_RECORDING,
            minBufferSizeBytes = minBufferSizeBytes,
            recorderBufferSizeBytes = recorderBufferSizeBytes,
            audioRecordState = audioRecordState,
            recordingState = recordingState,
        )

        fun failure(
            stage: AudioCaptureInitializationStage,
            failure: AudioCaptureInitializationFailure,
            minBufferSizeBytes: Int? = null,
            recorderBufferSizeBytes: Int? = null,
            audioRecordState: Int? = null,
            recordingState: Int? = null,
            platformCode: Int? = null,
            message: String? = null,
        ) = AudioCaptureInitialization(
            status = AudioCaptureInitializationStatus.FAILED,
            stage = stage,
            minBufferSizeBytes = minBufferSizeBytes,
            recorderBufferSizeBytes = recorderBufferSizeBytes,
            audioRecordState = audioRecordState,
            recordingState = recordingState,
            failure = failure,
            platformCode = platformCode,
            message = message,
        )
    }
}
