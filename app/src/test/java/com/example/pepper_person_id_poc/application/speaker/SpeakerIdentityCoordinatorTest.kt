package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerEmbeddingAggregator
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityFrame
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.example.pepper_person_id_poc.domain.speaker.SpeakerSegmentEmbedding
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.testsupport.FakeBenchmarkLogger
import com.example.pepper_person_id_poc.testsupport.FakeSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousSpeakerRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SpeakerIdentityCoordinatorTest {
    @Test
    fun missingActivityEngine_holdsWithoutEmbeddingOrRepositoryMutation() = runBlocking {
        val repository = InMemoryAnonymousSpeakerRepository()
        val embeddingEngine = FakeSpeakerEmbeddingEngine(floatArrayOf(1f, 0f))
        val benchmarkLogger = FakeBenchmarkLogger()
        val coordinator = SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = embeddingEngine,
            threshold = 0.8f,
            maximumUpdateCount = 20,
            benchmarkLogger = benchmarkLogger,
            segmentationEngine = null,
        )

        try {
            coordinator.onUtterance(validUtterance())
            val state = coordinator.state.first()

            assertThat(repository.count()).isEqualTo(0)
            assertThat(state.results).isEmpty()
            assertThat(state.activityHoldReasons).contains("UNSUPPORTED_ACTIVITY_INFERENCE")
            assertThat(benchmarkLogger.events).hasSize(1)
            assertThat(benchmarkLogger.events.single().status).isEqualTo("HOLD")
            assertThat(benchmarkLogger.events.single().attributes["vadMillis"]).isEqualTo("null")
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun close_doesNotDeleteRepository() {
        val repository = InMemoryAnonymousSpeakerRepository()
        repository.identify("speaker-model", floatArrayOf(1f, 0f), 0.8f, 20)
        val coordinator = SpeakerIdentityCoordinator(
            repository,
            FakeSpeakerEmbeddingEngine(floatArrayOf(1f, 0f)),
            0.8f,
            20,
            FakeBenchmarkLogger(),
        )

        coordinator.close()

        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun aggregatesSegmentEmbeddingsPerLocalSpeakerWithoutMixingSpeakers() {
        val aggregates = LocalSpeakerEmbeddingAggregator().aggregate(
            listOf(
                SpeakerSegmentEmbedding(
                    localSpeakerId = "local-speaker-002",
                    embedding = floatArrayOf(-1f, 0f),
                    durationMillis = 2_000L,
                ),
                SpeakerSegmentEmbedding(
                    localSpeakerId = "local-speaker-001",
                    embedding = floatArrayOf(1f, 0f),
                    durationMillis = 1_000L,
                ),
                SpeakerSegmentEmbedding(
                    localSpeakerId = "local-speaker-001",
                    embedding = floatArrayOf(0f, 1f),
                    durationMillis = 3_000L,
                ),
            ),
        )

        assertThat(aggregates.map { it.localSpeakerId }).containsExactly(
            "local-speaker-001",
            "local-speaker-002",
        ).inOrder()
        assertThat(aggregates[0].segmentCount).isEqualTo(2)
        assertThat(aggregates[0].totalDurationMillis).isEqualTo(4_000L)
        assertThat(aggregates[0].embedding[0]).isWithin(0.0001f).of(0.31622776f)
        assertThat(aggregates[0].embedding[1]).isWithin(0.0001f).of(0.9486833f)
        assertThat(aggregates[1].segmentCount).isEqualTo(1)
        assertThat(aggregates[1].embedding.asList()).containsExactly(-1f, 0f).inOrder()
    }

    @Test
    fun returnsEveryCompatibleCandidateInStableScoreOrder() = runBlocking {
        val repository = InMemoryAnonymousSpeakerRepository()
        val modelSpaceId = ModelSpaceId("speaker-model")
        listOf(
            floatArrayOf(1f, 0f),
            floatArrayOf(0f, 1f),
            floatArrayOf(-1f, 0f),
        ).forEachIndexed { index, embedding ->
            repository.apply(
                operation = PersistenceOperation.CREATE,
                modelSpaceId = modelSpaceId,
                embedding = embedding,
                selectedAnonymousId = null,
                maximumUpdateCount = 20,
                nowElapsedRealtime = index.toLong(),
            )
        }
        val benchmarkLogger = FakeBenchmarkLogger()
        val coordinator = SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = FakeSpeakerEmbeddingEngine(floatArrayOf(0.8f, 0.6f)),
            threshold = 0.7f,
            maximumUpdateCount = 20,
            benchmarkLogger = benchmarkLogger,
            segmentationEngine = SingleSpeakerSegmentationEngine(),
        )

        try {
            coordinator.onUtterance(validUtterance())
            val state = withTimeout(5_000L) {
                coordinator.state.first { !it.processing && it.result != null }
            }
            val result = state.result!!

            assertThat(result.candidateScores.map { it.anonymousId }).containsExactly(
                "anonymous-speaker-001",
                "anonymous-speaker-002",
                "anonymous-speaker-003",
            ).inOrder()
            assertThat(result.candidateScores.map { it.score }).containsExactly(
                0.8f,
                0.6f,
                -0.8f,
            ).inOrder()
            assertThat(result.evaluation!!.secondHighestScore).isWithin(0.0001f).of(0.6f)
            assertThat(result.evaluation.highestCandidateLead).isWithin(0.0001f).of(0.2f)
            assertThat(result.candidateScores.count { it.selected }).isEqualTo(1)
            assertThat(result.candidateScores.first().selected).isTrue()
            val timings = state.stageTimings!!
            assertThat(timings.recorderMillis).isNull()
            assertThat(timings.vadMillis).isNull()
            listOf(
                timings.activityMillis,
                timings.trackingMillis,
                timings.qualityMillis,
                timings.embeddingMillis,
                timings.aggregationMillis,
                timings.scoringMillis,
                timings.policyMillis,
                timings.repositoryMillis,
                timings.totalMillis,
            ).forEach { assertThat(checkNotNull(it)).isAtLeast(0L) }
            assertThat(timings.audioDurationMillis).isEqualTo(2_000L)
            assertThat(timings.realTimeFactor).isNotNull()
            assertThat(benchmarkLogger.events).hasSize(1)
            assertThat(benchmarkLogger.events.single().attributes["recorderMillis"]).isEqualTo("null")
            assertThat(benchmarkLogger.events.single().attributes["realTimeFactor"]).isNotNull()
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun duplicateSelectionStillReportsTheIdentifiedAnonymousIdWhenPersistenceIsHeld() = runBlocking {
        val repository = InMemoryAnonymousSpeakerRepository()
        val coordinator = SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = FakeSpeakerEmbeddingEngine(floatArrayOf(1f, 0f)),
            threshold = 0.8f,
            maximumUpdateCount = 20,
            benchmarkLogger = FakeBenchmarkLogger(),
            segmentationEngine = TwoSoloSegmentSegmentationEngine(),
        )

        try {
            coordinator.onUtterance(longValidUtterance())
            val state = withTimeout(5_000L) {
                coordinator.state.first { !it.processing && it.results.size == 2 }
            }
            val results = state.results.values.toList()

            assertThat(repository.count()).isEqualTo(1)
            assertThat(results.map { it.anonymousId })
                .containsExactly("anonymous-speaker-001", "anonymous-speaker-001")
                .inOrder()
            assertThat(results[0].persistenceOperation).isEqualTo(PersistenceOperation.CREATE)
            assertThat(results[1].persistenceOperation).isEqualTo(PersistenceOperation.HOLD)
            assertThat(results[1].bestExistingScore).isWithin(0.0001f).of(1f)
            assertThat(results[1].candidateScores.single().selected).isTrue()
            assertThat(state.activityHoldReasons).contains("ANONYMOUS_ID_ALREADY_SELECTED")
        } finally {
            coordinator.close()
        }
    }

    private fun validUtterance() = PcmUtterance(
        pcm16 = ShortArray(16_000) { 1_000 },
        sampleRate = 16_000,
        startedAtMillis = 1_000L,
        endedAtMillis = 3_000L,
        voicedDurationMillis = 2_000L,
    )

    private fun longValidUtterance() = PcmUtterance(
        pcm16 = ShortArray(32_000) { 1_000 },
        sampleRate = 16_000,
        startedAtMillis = 1_000L,
        endedAtMillis = 5_000L,
        voicedDurationMillis = 4_000L,
    )

    private class SingleSpeakerSegmentationEngine : SpeakerSegmentationEngine {
        override val artifactId = ArtifactId("test-segmentation")
        override val runtimeId = RuntimeId("test-runtime")

        override fun prepare() = Unit

        override fun segment(input: SpeakerSegmentationInput) = DiarizationWindow(
            windowId = input.windowId,
            startSample = input.startSample,
            endSample = input.endSample,
            sampleRate = input.sampleRate,
            frames = listOf(
                SpeakerActivityFrame(
                    activityState = SpeakerActivityState.SINGLE_SPEAKER,
                    activeSpeakerIndices = listOf(0),
                    winningClassIndex = 1,
                    winningScore = 1f,
                    overlapProbability = 0f,
                ),
            ),
            overlapRatio = 0f,
            runtimeId = runtimeId.value,
            inferenceTimeMillis = 1L,
        )

        override fun close() = Unit
    }

    private class TwoSoloSegmentSegmentationEngine : SpeakerSegmentationEngine {
        override val artifactId = ArtifactId("test-segmentation-two-segments")
        override val runtimeId = RuntimeId("test-runtime")

        override fun prepare() = Unit

        override fun segment(input: SpeakerSegmentationInput) = DiarizationWindow(
            windowId = input.windowId,
            startSample = input.startSample,
            endSample = input.endSample,
            sampleRate = input.sampleRate,
            frames = listOf(
                SpeakerActivityFrame(
                    activityState = SpeakerActivityState.SINGLE_SPEAKER,
                    activeSpeakerIndices = listOf(0),
                    winningClassIndex = 1,
                    winningScore = 1f,
                    overlapProbability = 0f,
                ),
                SpeakerActivityFrame(
                    activityState = SpeakerActivityState.SINGLE_SPEAKER,
                    activeSpeakerIndices = listOf(1),
                    winningClassIndex = 2,
                    winningScore = 1f,
                    overlapProbability = 0f,
                ),
            ),
            overlapRatio = 0f,
            runtimeId = runtimeId.value,
            inferenceTimeMillis = 1L,
        )

        override fun close() = Unit
    }
}
