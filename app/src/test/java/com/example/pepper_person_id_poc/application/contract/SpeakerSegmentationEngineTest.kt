package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SpeakerSegmentationEngineTest {
    @Test
    fun inputAcceptsOnlyStrict16KhzAndCalculatesAbsoluteEndSample() {
        val input = SpeakerSegmentationInput(
            windowId = "window-001",
            startSample = 32_000,
            pcm16 = ShortArray(16_000),
        )

        assertThat(input.sampleRate).isEqualTo(16_000)
        assertThat(input.endSample).isEqualTo(48_000)
    }

    @Test
    fun non16KhzInputIsRejectedWithoutFallback() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerSegmentationInput(
                windowId = "window-001",
                startSample = 0,
                pcm16 = ShortArray(1),
                sampleRate = 44_100,
            )
        }
    }

    @Test
    fun runtimeContractExposesExactArtifactAndRuntime() {
        val engine = object : SpeakerSegmentationEngine {
            override val artifactId = ArtifactId("pyannote-segmentation-3.0-onnx")
            override val runtimeId = RuntimeId("onnxruntime-cpu")
            override fun prepare() = Unit
            override fun segment(input: SpeakerSegmentationInput) = DiarizationWindow(
                windowId = input.windowId,
                startSample = input.startSample,
                endSample = input.endSample,
                sampleRate = input.sampleRate,
                frames = emptyList(),
                overlapRatio = 0f,
                runtimeId = runtimeId.value,
                inferenceTimeMillis = 1,
            )
            override fun close() = Unit
        }

        val output = engine.segment(
            SpeakerSegmentationInput("window-001", 0, ShortArray(16_000)),
        )

        assertThat(engine.artifactId.value).isEqualTo("pyannote-segmentation-3.0-onnx")
        assertThat(output.runtimeId).isEqualTo(engine.runtimeId.value)
        assertThat(output.endSample).isEqualTo(16_000)
    }
}
