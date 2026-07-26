package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerEmbeddingAggregator
import com.example.pepper_person_id_poc.domain.speaker.SpeakerSegmentEmbedding
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
        val coordinator = SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = FakeSpeakerEmbeddingEngine(floatArrayOf(0.8f, 0.6f)),
            threshold = 0.7f,
            maximumUpdateCount = 20,
            benchmarkLogger = FakeBenchmarkLogger(),
        )

        try {
            coordinator.onUtterance(validUtterance())
            val result = withTimeout(5_000L) {
                coordinator.state.first { !it.processing && it.result != null }.result!!
            }

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
        } finally {
            coordinator.close()
        }
    }

    private fun validUtterance() = PcmUtterance(
        pcm16 = ShortArray(16_000),
        sampleRate = 16_000,
        startedAtMillis = 1_000L,
        endedAtMillis = 3_000L,
        voicedDurationMillis = 2_000L,
    )
}
