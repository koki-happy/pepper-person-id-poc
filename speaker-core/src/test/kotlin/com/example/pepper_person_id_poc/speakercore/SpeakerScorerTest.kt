package com.example.pepper_person_id_poc.speakercore

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SpeakerScorerTest {
    private val centroids = listOf(
        SpeakerCentroid("speaker-a", floatArrayOf(1f, 0f)),
        SpeakerCentroid("speaker-b", floatArrayOf(0.8f, 0.6f)),
        SpeakerCentroid("speaker-c", floatArrayOf(0f, 1f)),
    )

    @Test
    fun score_returnsAllScoresTopTwoAndAcceptedIdentity() {
        val result = SpeakerScorer(threshold = 0.7f, minimumMargin = 0.1f)
            .score(floatArrayOf(1f, 0f), centroids.reversed())

        assertThat(result.decision).isEqualTo(SpeakerDecision.IDENTIFIED)
        assertThat(result.identifiedSpeakerId).isEqualTo("speaker-a")
        assertThat(result.scores.map(SpeakerScore::speakerId))
            .containsExactly("speaker-a", "speaker-b", "speaker-c")
            .inOrder()
        assertThat(result.top1?.score).isWithin(1e-6f).of(1f)
        assertThat(result.top2?.score).isWithin(1e-6f).of(0.8f)
        assertThat(result.margin).isWithin(1e-6f).of(0.2f)
        assertThat(result.unknownReasons).isEmpty()
    }

    @Test
    fun score_acceptsInclusiveThresholdAndMarginBoundaries() {
        val result = SpeakerScorer(threshold = 1f, minimumMargin = 0.2f)
            .score(floatArrayOf(1f, 0f), centroids)

        assertThat(result.decision).isEqualTo(SpeakerDecision.IDENTIFIED)
        assertThat(result.identifiedSpeakerId).isEqualTo("speaker-a")
    }

    @Test
    fun score_rejectsScoreImmediatelyBelowThreshold() {
        val candidate = SpeakerCentroid("speaker", floatArrayOf(0.8f, 0.6f))
        val score = EmbeddingMath.cosineSimilarity(floatArrayOf(1f, 0f), candidate.embedding)
        val result = SpeakerScorer(
            threshold = Math.nextUp(score),
            minimumMargin = 0f,
        ).score(floatArrayOf(1f, 0f), listOf(candidate))

        assertThat(result.top1?.score).isEqualTo(score)
        assertThat(result.decision).isEqualTo(SpeakerDecision.UNKNOWN)
        assertThat(result.unknownReasons).containsExactly(UnknownReason.BELOW_THRESHOLD)
    }

    @Test
    fun score_belowThresholdReturnsUnknownButKeepsDebugCandidates() {
        val result = SpeakerScorer(threshold = 0.9f, minimumMargin = 0f)
            .score(floatArrayOf(-1f, 0f), centroids)

        assertThat(result.decision).isEqualTo(SpeakerDecision.UNKNOWN)
        assertThat(result.identifiedSpeakerId).isNull()
        assertThat(result.top1).isNotNull()
        assertThat(result.scores).hasSize(3)
        assertThat(result.unknownReasons).containsExactly(UnknownReason.BELOW_THRESHOLD)
    }

    @Test
    fun score_insufficientMarginReturnsUnknown() {
        val result = SpeakerScorer(threshold = 0.5f, minimumMargin = 0.3f)
            .score(floatArrayOf(1f, 0f), centroids)

        assertThat(result.decision).isEqualTo(SpeakerDecision.UNKNOWN)
        assertThat(result.unknownReasons).containsExactly(UnknownReason.INSUFFICIENT_MARGIN)
    }

    @Test
    fun score_reportsBothFailedDecisionRules() {
        val result = SpeakerScorer(threshold = 0.9f, minimumMargin = 0.3f)
            .score(floatArrayOf(-0.6f, -0.8f), centroids)

        assertThat(result.decision).isEqualTo(SpeakerDecision.UNKNOWN)
        assertThat(result.unknownReasons).containsExactly(
            UnknownReason.BELOW_THRESHOLD,
            UnknownReason.INSUFFICIENT_MARGIN,
        ).inOrder()
    }

    @Test
    fun score_singleCandidateDoesNotInventAMargin() {
        val result = SpeakerScorer(threshold = 0.5f, minimumMargin = 2f)
            .score(floatArrayOf(1f, 0f), listOf(centroids.first()))

        assertThat(result.decision).isEqualTo(SpeakerDecision.IDENTIFIED)
        assertThat(result.top2).isNull()
        assertThat(result.margin).isNull()
    }

    @Test
    fun score_withoutCandidatesReturnsUnknown() {
        val result = SpeakerScorer(threshold = 0f, minimumMargin = 0f)
            .score(floatArrayOf(1f), emptyList())

        assertThat(result.scores).isEmpty()
        assertThat(result.top1).isNull()
        assertThat(result.unknownReasons).containsExactly(UnknownReason.NO_CANDIDATES)
    }

    @Test
    fun score_rejectsInvalidConfigDuplicateIdsAndDimensionMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerScorer(threshold = Float.NaN, minimumMargin = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerScorer(threshold = 0f, minimumMargin = 2.1f)
        }

        val scorer = SpeakerScorer(threshold = 0f, minimumMargin = 0f)
        assertThrows(IllegalArgumentException::class.java) {
            scorer.score(
                floatArrayOf(1f, 0f),
                listOf(
                    SpeakerCentroid("duplicate", floatArrayOf(1f, 0f)),
                    SpeakerCentroid("duplicate", floatArrayOf(0f, 1f)),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            scorer.score(
                floatArrayOf(1f, 0f),
                listOf(SpeakerCentroid("speaker", floatArrayOf(1f, 0f, 0f))),
            )
        }
    }
}
