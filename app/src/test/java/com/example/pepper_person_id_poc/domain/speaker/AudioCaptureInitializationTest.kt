package com.example.pepper_person_id_poc.domain.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioCaptureInitializationTest {
    @Test
    fun successContainsStrictFormatAndPlatformDiagnostics() {
        val initialization = AudioCaptureInitialization.success(
            minBufferSizeBytes = 1_920,
            recorderBufferSizeBytes = 3_840,
            audioRecordState = 1,
            recordingState = 3,
        )

        assertThat(initialization.succeeded).isTrue()
        assertThat(initialization.requestedFormat)
            .isEqualTo(AudioCaptureFormat.STRICT_16_KHZ_MONO_PCM16)
        assertThat(initialization.diagnosticAttributes()).containsExactly(
            "captureStatus", "SUCCESS",
            "captureStage", "START_RECORDING",
            "sampleRate", "16000",
            "channels", "1",
            "encoding", "PCM_16BIT",
            "minBufferSizeBytes", "1920",
            "recorderBufferSizeBytes", "3840",
            "audioRecordState", "1",
            "recordingState", "3",
        )
    }

    @Test
    fun failurePreservesStageReasonAndPlatformCode() {
        val initialization = AudioCaptureInitialization.failure(
            stage = AudioCaptureInitializationStage.MIN_BUFFER_QUERY,
            failure = AudioCaptureInitializationFailure.MIN_BUFFER_QUERY_FAILED,
            platformCode = -2,
            message = "bad value",
        )

        assertThat(initialization.succeeded).isFalse()
        assertThat(initialization.diagnosticAttributes()).containsAtLeast(
            "captureStatus", "FAILED",
            "captureStage", "MIN_BUFFER_QUERY",
            "captureFailure", "MIN_BUFFER_QUERY_FAILED",
            "platformCode", "-2",
        )
    }

    @Test
    fun nonStrictFormatIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AudioCaptureInitialization(
                status = AudioCaptureInitializationStatus.FAILED,
                stage = AudioCaptureInitializationStage.MIN_BUFFER_QUERY,
                requestedFormat = AudioCaptureFormat(
                    sampleRateHz = 44_100,
                    channelCount = 1,
                    encoding = "PCM_16BIT",
                ),
                failure = AudioCaptureInitializationFailure.MIN_BUFFER_QUERY_FAILED,
            )
        }
    }
}
