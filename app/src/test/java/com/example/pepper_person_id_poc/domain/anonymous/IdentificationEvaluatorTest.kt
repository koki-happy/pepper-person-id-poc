package com.example.pepper_person_id_poc.domain.anonymous

import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IdentificationEvaluatorTest {
    @Test
    fun evaluate_returnsEveryCompatibleCandidateInDescendingScoreOrder() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(
                cluster("anonymous-face-003", floatArrayOf(0.6f, 0.8f), updateCount = 3),
                cluster("anonymous-face-001", floatArrayOf(1f, 0f), updateCount = 1),
                cluster("anonymous-face-002", floatArrayOf(0.8f, 0.6f), updateCount = 2),
            ),
        )

        assertThat(evaluation.candidates.map { it.anonymousId })
            .containsExactly(
                "anonymous-face-001",
                "anonymous-face-002",
                "anonymous-face-003",
            )
            .inOrder()
        assertThat(evaluation.candidates.map { it.rank })
            .containsExactly(1, 2, 3)
            .inOrder()
        assertThat(evaluation.candidates.map { it.updateCount })
            .containsExactly(1, 2, 3)
            .inOrder()
        assertThat(evaluation.candidates).hasSize(3)
    }

    @Test
    fun evaluate_breaksEqualScoreTiesByAnonymousIdAscending() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(
                cluster("anonymous-face-010", floatArrayOf(0.8f, 0.6f)),
                cluster("anonymous-face-002", floatArrayOf(0.8f, -0.6f)),
                cluster("anonymous-face-001", floatArrayOf(0f, 1f)),
            ),
        )

        assertThat(evaluation.candidates.map { it.anonymousId })
            .containsExactly(
                "anonymous-face-002",
                "anonymous-face-010",
                "anonymous-face-001",
            )
            .inOrder()
        assertThat(evaluation.candidates.map { it.score })
            .containsExactly(0.8f, 0.8f, 0f)
            .inOrder()
    }

    private fun evaluate(
        query: FloatArray,
        clusters: List<AnonymousCluster>,
    ): IdentificationEvaluation = IdentificationEvaluator.evaluate(
        embedding = query,
        clusters = clusters,
        modelSpaceId = MODEL_SPACE,
        threshold = 0.99f,
        minimumLead = 0.1f,
    )

    private fun cluster(
        anonymousId: String,
        centroid: FloatArray,
        updateCount: Int = 1,
    ) = AnonymousCluster(
        anonymousId = anonymousId,
        modelId = MODEL_SPACE.value,
        embeddingDimension = centroid.size,
        normalizedEmbeddingSum = centroid.copyOf(),
        centroid = centroid,
        updateCount = updateCount,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private companion object {
        val MODEL_SPACE = ModelSpaceId("face-sface-v1")
    }
}
