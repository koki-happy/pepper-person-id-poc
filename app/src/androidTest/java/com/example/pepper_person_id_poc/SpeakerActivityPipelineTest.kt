package com.example.pepper_person_id_poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityCoordinator
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityFrame
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.example.pepper_person_id_poc.infrastructure.repository.InMemoryAnonymousSpeakerClusterRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeakerActivityPipelineTest {
    @Test
    fun fixedAlternatingSpeakers_areKeptAsSeparateLocalAndAnonymousSpeakers() =
        runScenario("alternating.json")

    @Test
    fun fixedPartialOverlap_usesOnlySoloSpeech() =
        runScenario("partial-overlap.json")

    @Test
    fun fixedFullOverlap_holdsWithoutRepositoryMutation() =
        runScenario("full-overlap.json")

    private fun runScenario(assetName: String) = runBlocking {
        val fixture = readFixture(assetName)
        val repository = InMemoryAnonymousSpeakerClusterRepository()
        val embeddingEngine = FixtureEmbeddingEngine()
        val coordinator = SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = embeddingEngine,
            threshold = 0.80f,
            maximumUpdateCount = 20,
            benchmarkLogger = NoOpBenchmarkLogger,
            segmentationEngine = FixtureSegmentationEngine(fixture.frameClasses),
        )
        try {
            coordinator.onUtterance(fixture.utterance())
            val state = withTimeout(5_000L) {
                coordinator.state.first {
                    !it.processing &&
                        (
                            it.results.size == fixture.expectedLocalSpeakers ||
                                fixture.expectedHoldReason?.let(it.activityHoldReasons::contains) == true
                            )
                }
            }

            assertThat(state.results).hasSize(fixture.expectedLocalSpeakers)
            assertThat(repository.count()).isEqualTo(fixture.expectedRepositoryCount)
            fixture.expectedHoldReason?.let {
                assertThat(state.activityHoldReasons).contains(it)
            }
            if (fixture.scenarioId == "fixed-partial-overlap") {
                assertThat(state.activityState).isEqualTo(SpeakerActivityState.OVERLAPPED_SPEECH)
                assertThat(embeddingEngine.extractedSampleCounts).containsExactly(32_000, 16_000)
                    .inOrder()
            }
            if (fixture.scenarioId == "fixed-full-overlap") {
                assertThat(embeddingEngine.extractedSampleCounts).isEmpty()
            }
        } finally {
            coordinator.close()
        }
    }

    private fun readFixture(assetName: String): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().context
        val json = context.assets.open("speaker-activity/$assetName")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        val classes = json.getJSONArray("frameClasses")
        return Fixture(
            scenarioId = json.getString("scenarioId"),
            frameClasses = List(classes.length()) { classes.getInt(it) },
            expectedLocalSpeakers = json.getInt("expectedLocalSpeakers"),
            expectedRepositoryCount = json.getInt("expectedRepositoryCount"),
            expectedHoldReason = if (json.isNull("expectedHoldReason")) {
                null
            } else {
                json.getString("expectedHoldReason")
            },
        )
    }

    private data class Fixture(
        val scenarioId: String,
        val frameClasses: List<Int>,
        val expectedLocalSpeakers: Int,
        val expectedRepositoryCount: Int,
        val expectedHoldReason: String?,
    ) {
        fun utterance(): PcmUtterance {
            val samplesPerFrame = 16_000
            val pcm = ShortArray(samplesPerFrame * frameClasses.size)
            frameClasses.forEachIndexed { frameIndex, classIndex ->
                val amplitude = when (classIndex) {
                    1 -> 1_000
                    2 -> -1_000
                    else -> 0
                }
                for (sample in 0 until samplesPerFrame) {
                    pcm[frameIndex * samplesPerFrame + sample] = amplitude.toShort()
                }
            }
            return PcmUtterance(
                pcm16 = pcm,
                sampleRate = 16_000,
                startedAtMillis = 1_000L,
                endedAtMillis = 1_000L + frameClasses.size * 1_000L,
                voicedDurationMillis = frameClasses.size * 1_000L,
            )
        }
    }

    private class FixtureSegmentationEngine(
        private val frameClasses: List<Int>,
    ) : SpeakerSegmentationEngine {
        override val artifactId = ArtifactId("fixture-pyannote-powerset")
        override val runtimeId = RuntimeId("fixture-cpu")

        override fun prepare() = Unit

        override fun segment(input: SpeakerSegmentationInput) = DiarizationWindow(
            windowId = input.windowId,
            startSample = input.startSample,
            endSample = input.endSample,
            sampleRate = input.sampleRate,
            frames = frameClasses.map(::frame),
            overlapRatio = frameClasses.count { it >= 4 }.toFloat() / frameClasses.size,
            runtimeId = runtimeId.value,
            inferenceTimeMillis = 1L,
        )

        override fun close() = Unit

        private fun frame(classIndex: Int): SpeakerActivityFrame {
            val speakers = when (classIndex) {
                0 -> emptyList()
                1 -> listOf(0)
                2 -> listOf(1)
                3 -> listOf(2)
                4 -> listOf(0, 1)
                5 -> listOf(0, 2)
                6 -> listOf(1, 2)
                else -> error("Unsupported fixture class $classIndex")
            }
            return SpeakerActivityFrame(
                activityState = when (speakers.size) {
                    0 -> SpeakerActivityState.SILENCE
                    1 -> SpeakerActivityState.SINGLE_SPEAKER
                    2 -> SpeakerActivityState.OVERLAPPED_SPEECH
                    else -> SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS
                },
                activeSpeakerIndices = speakers,
                winningClassIndex = classIndex,
                winningScore = 1f,
                overlapProbability = if (speakers.size > 1) 1f else 0f,
            )
        }
    }

    private class FixtureEmbeddingEngine : SpeakerEmbeddingEngine {
        override val modelName = "fixture-speaker-space"
        override val embeddingDimension = 2
        val extractedSampleCounts = mutableListOf<Int>()

        override fun prepare() = Unit

        override fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray {
            assertThat(sampleRate).isEqualTo(16_000)
            extractedSampleCounts += pcm16.size
            val sum = pcm16.sumOf { it.toLong() }
            return if (sum >= 0L) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
        }

        override fun close() = Unit
    }

    private object NoOpBenchmarkLogger : BenchmarkLogger {
        override fun append(event: BenchmarkEvent) = Unit
    }
}
