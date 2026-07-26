package com.example.pepper_person_id_poc.infrastructure.speaker

import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityFrame
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState

/**
 * Treats each utterance already emitted by Silero VAD as one active speaker.
 *
 * This keeps the live identification path on sherpa-onnx only and avoids loading
 * a second ONNX Runtime implementation into the same ARM process.
 */
class VadGatedSingleSpeakerSegmentationEngine : SpeakerSegmentationEngine {
    override val artifactId = ArtifactId("silero-vad-gated-single-speaker")
    override val runtimeId = RuntimeId("kotlin-single-speaker-segmentation-v1")

    override fun prepare() = Unit

    override fun segment(input: SpeakerSegmentationInput): DiarizationWindow =
        DiarizationWindow(
            windowId = input.windowId,
            startSample = input.startSample,
            endSample = input.endSample,
            sampleRate = input.sampleRate,
            frames = listOf(
                SpeakerActivityFrame(
                    activityState = SpeakerActivityState.SINGLE_SPEAKER,
                    activeSpeakerIndices = listOf(0),
                    winningClassIndex = 0,
                    winningScore = 1f,
                    overlapProbability = 0f,
                ),
            ),
            overlapRatio = 0f,
            runtimeId = runtimeId.value,
            inferenceTimeMillis = 0L,
        )

    override fun close() = Unit
}
