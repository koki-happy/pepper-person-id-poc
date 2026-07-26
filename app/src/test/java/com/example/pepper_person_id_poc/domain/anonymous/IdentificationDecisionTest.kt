package com.example.pepper_person_id_poc.domain.anonymous

import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IdentificationDecisionTest {
    @Test
    fun scoreAtThresholdWithSufficientLead_matchesExistingCandidate() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(
                cluster("anonymous-face-001", floatArrayOf(0.75f, 0.6614378f)),
                cluster("anonymous-face-002", floatArrayOf(0.5f, 0.8660254f)),
            ),
            threshold = 0.75f,
            minimumLead = 0.25f,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.MATCHED_EXISTING)
        assertThat(evaluation.highestScore).isWithin(0.0001f).of(0.75f)
        assertThat(evaluation.secondHighestScore).isWithin(0.0001f).of(0.5f)
        assertThat(evaluation.highestCandidateLead).isWithin(0.0001f).of(0.25f)
        assertThat(evaluation.candidates.single { it.selected }.anonymousId)
            .isEqualTo("anonymous-face-001")
    }

    @Test
    fun oneCandidate_aboveThresholdPassesWithoutSyntheticLead() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(cluster("anonymous-face-001", floatArrayOf(1f, 0f))),
            threshold = 0.8f,
            minimumLead = 0.9f,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.MATCHED_EXISTING)
        assertThat(evaluation.secondHighestScore).isNull()
        assertThat(evaluation.highestCandidateLead).isNull()
        assertThat(evaluation.candidates.single().selected).isTrue()
    }

    @Test
    fun leadBelowMinimum_isAmbiguousAndSelectsNoCandidate() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(
                cluster("anonymous-face-001", floatArrayOf(0.9f, 0.4358899f)),
                cluster("anonymous-face-002", floatArrayOf(0.86f, 0.510294f)),
            ),
            threshold = 0.8f,
            minimumLead = 0.05f,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.AMBIGUOUS)
        assertThat(evaluation.highestCandidateLead).isWithin(0.0001f).of(0.04f)
        assertThat(evaluation.candidates.none { it.selected }).isTrue()
    }

    @Test
    fun allScoresBelowThreshold_requestsNewClusterWithoutSelectingCandidate() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(
                cluster("anonymous-face-001", floatArrayOf(0.7f, 0.71414286f)),
                cluster("anonymous-face-002", floatArrayOf(0.6f, 0.8f)),
            ),
            threshold = 0.8f,
            minimumLead = 0.05f,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.CREATED_NEW)
        assertThat(evaluation.candidates.none { it.selected }).isTrue()
    }

    @Test
    fun noCompatibleCandidate_isUnknown() {
        val evaluation = evaluate(
            query = floatArrayOf(1f, 0f),
            clusters = listOf(
                cluster(
                    anonymousId = "anonymous-face-001",
                    centroid = floatArrayOf(1f, 0f),
                    modelId = "different-model-space",
                ),
            ),
            threshold = 0.8f,
            minimumLead = 0.05f,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.UNKNOWN)
        assertThat(evaluation.candidates).isEmpty()
        assertThat(evaluation.highestScore).isNull()
    }

    @Test
    fun nonFiniteEmbedding_isUnknownAndProducesNoCandidates() {
        val evaluation = evaluate(
            query = floatArrayOf(Float.NaN, 0f),
            clusters = listOf(cluster("anonymous-face-001", floatArrayOf(1f, 0f))),
            threshold = 0.8f,
            minimumLead = 0.05f,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.UNKNOWN)
        assertThat(evaluation.candidates).isEmpty()
        assertThat(evaluation.highestScore).isNull()
        assertThat(evaluation.secondHighestScore).isNull()
        assertThat(evaluation.highestCandidateLead).isNull()
    }

    private fun evaluate(
        query: FloatArray,
        clusters: List<AnonymousCluster>,
        threshold: Float,
        minimumLead: Float,
    ): IdentificationEvaluation = IdentificationEvaluator.evaluate(
        embedding = query,
        clusters = clusters,
        modelSpaceId = MODEL_SPACE,
        threshold = threshold,
        minimumLead = minimumLead,
    )

    private fun cluster(
        anonymousId: String,
        centroid: FloatArray,
        modelId: String = MODEL_SPACE.value,
    ) = AnonymousCluster(
        anonymousId = anonymousId,
        modelId = modelId,
        embeddingDimension = centroid.size,
        normalizedEmbeddingSum = centroid.copyOf(),
        centroid = centroid,
        updateCount = 1,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private companion object {
        val MODEL_SPACE = ModelSpaceId("face-sface-v1")
    }
}
