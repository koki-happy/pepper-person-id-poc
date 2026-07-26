package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.speaker.AudioCaptureInitialization
import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

interface PcmAudioRecorder : Closeable {
    val state: StateFlow<AudioRecordingState>
    fun start()
    fun stop()
}

data class AudioRecordingState(
    val status: AudioRecordingStatus = AudioRecordingStatus.IDLE,
    val sampleRate: Int? = null,
    val minBufferSizeBytes: Int? = null,
    val vadModelName: String? = null,
    val levelDbFs: Float = -90f,
    val speechActive: Boolean = false,
    val lastUtterance: PcmUtteranceMetadata? = null,
    val captureInitialization: AudioCaptureInitialization? = null,
    val error: String? = null,
)

data class PcmUtteranceMetadata(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val voicedDurationMillis: Long,
    val sufficientForSpeakerIdentification: Boolean,
) {
    companion object {
        fun from(utterance: PcmUtterance) = PcmUtteranceMetadata(
            startedAtMillis = utterance.startedAtMillis,
            endedAtMillis = utterance.endedAtMillis,
            durationMillis = utterance.durationMillis,
            voicedDurationMillis = utterance.voicedDurationMillis,
            sufficientForSpeakerIdentification = utterance.sufficientForSpeakerIdentification,
        )
    }
}

enum class AudioRecordingStatus {
    IDLE,
    STARTING,
    RECORDING,
    STOPPED,
    ERROR,
}
