package com.example.pepper_person_id_poc.infrastructure.speaker

import android.content.Context
import android.os.SystemClock
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityFrame
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/**
 * Pepper-safe Segmentation 3.0 adapter.
 *
 * The previous adapter opened the pyannote model with the shared ONNX Runtime
 * Android AAR. That runtime faults on Pepper's ARMv7/API23 image while creating
 * the session. Sherpa's static-link AAR contains its own ARMv7 ONNX Runtime and
 * exposes the official pyannote diarization wrapper, so no shared libonnxruntime
 * session is created on this device.
 */
class SherpaPyannoteSegmentationEngine(
    context: Context,
    private val embeddingModelFilename: String,
    private val numThreads: Int = 1,
) : SpeakerSegmentationEngine {
    private val assets = context.applicationContext.assets
    private var diarizer: OfflineSpeakerDiarization? = null

    override val artifactId = ArtifactId(ARTIFACT_ID)
    override val runtimeId = RuntimeId(RUNTIME_ID)
    override val requiredWindowSamples = WINDOW_SAMPLES

    @Synchronized
    override fun prepare() {
        if (diarizer != null) return
        require(numThreads > 0) { "numThreads must be positive" }
        val segmentation = OfflineSpeakerSegmentationModelConfig(
            pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                model = "models/$MODEL_FILENAME",
            ),
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
        )
        val embedding = SpeakerEmbeddingExtractorConfig(
            model = "models/$embeddingModelFilename",
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
        )
        diarizer = OfflineSpeakerDiarization(
            assetManager = assets,
            config = OfflineSpeakerDiarizationConfig(
                segmentation = segmentation,
                embedding = embedding,
                // -1 lets sherpa determine the number of speakers in the window.
                clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
                minDurationOn = 0.2f,
                minDurationOff = 0.5f,
            ),
        )
    }

    @Synchronized
    override fun segment(input: SpeakerSegmentationInput): DiarizationWindow {
        require(input.pcm16.size == WINDOW_SAMPLES) {
            "Segmentation 3.0 requires an exact 10-second/160000-sample window; " +
                "actual=${input.pcm16.size}"
        }
        require(input.sampleRate == SAMPLE_RATE) {
            "Segmentation 3.0 requires $SAMPLE_RATE Hz PCM, actual=${input.sampleRate}"
        }
        prepare()
        val samples = FloatArray(input.pcm16.size) { index ->
            input.pcm16[index] / PCM_NORMALIZATION
        }
        val startedAt = SystemClock.elapsedRealtime()
        val segments = checkNotNull(diarizer).process(samples)
            .map { segment ->
                Segment(
                    startSample = (segment.start * SAMPLE_RATE).toLong().coerceIn(0L, WINDOW_SAMPLES.toLong()),
                    endSample = (segment.end * SAMPLE_RATE).toLong().coerceIn(0L, WINDOW_SAMPLES.toLong()),
                    speaker = segment.speaker,
                )
            }
            .filter { it.endSample > it.startSample }

        val frames = buildFrames(segments)
        val overlapFrames = frames.count { it.activeSpeakerCount > 1 }
        return DiarizationWindow(
            windowId = input.windowId,
            startSample = input.startSample,
            endSample = input.endSample,
            sampleRate = input.sampleRate,
            frames = frames,
            overlapRatio = overlapFrames.toFloat() / frames.size.coerceAtLeast(1),
            runtimeId = runtimeId.value,
            inferenceTimeMillis = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    @Synchronized
    override fun close() {
        diarizer?.release()
        diarizer = null
    }

    private fun buildFrames(segments: List<Segment>): List<SpeakerActivityFrame> =
        (0 until FRAME_COUNT).map { frameIndex ->
            val frameStart = frameIndex * FRAME_SAMPLES
            val frameEnd = (frameStart + FRAME_SAMPLES).coerceAtMost(WINDOW_SAMPLES)
            val active = segments.asSequence()
                .filter { it.startSample < frameEnd && it.endSample > frameStart }
                .map { it.speaker }
                .distinct()
                .sorted()
                .toList()
            val state = when {
                active.isEmpty() -> SpeakerActivityState.SILENCE
                active.size == 1 -> SpeakerActivityState.SINGLE_SPEAKER
                else -> SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS
            }
            SpeakerActivityFrame(
                activityState = state,
                activeSpeakerIndices = active,
                winningClassIndex = active.firstOrNull() ?: 0,
                winningScore = if (active.isEmpty()) 0f else 1f,
                overlapProbability = if (active.size > 1) 1f else 0f,
            )
        }

    private data class Segment(
        val startSample: Long,
        val endSample: Long,
        val speaker: Int,
    )

    companion object {
        const val ARTIFACT_ID = "pyannote-segmentation-3.0-sherpa-onnx-fp32"
        const val RUNTIME_ID = "sherpa-onnx-1.13.4-android-cpu"
        const val MODEL_FILENAME = "pyannote-segmentation-3.0.onnx"
        const val WINDOW_SAMPLES = 160_000
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 1_600
        private const val FRAME_COUNT = WINDOW_SAMPLES / FRAME_SAMPLES
        private const val PCM_NORMALIZATION = 32_768f
    }
}
