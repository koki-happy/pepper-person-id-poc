package com.example.pepper_person_id_poc.speakercore

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SpeakerEvaluatorTest {
    private val centroids = listOf(
        SpeakerCentroid("speaker-a", floatArrayOf(1f, 0f)),
        SpeakerCentroid("speaker-b", floatArrayOf(0f, 1f)),
    )
    private val scorer = SpeakerScorer(threshold = 0.9f, minimumMargin = 0.1f)

    @Test
    fun evaluate_separatesTop1FarFrrConfusionAndUnknownMetrics() {
        val trials = listOf(
            trial("known-correct", "speaker-a", 2, floatArrayOf(1f, 0f)),
            trial("known-rejected", "speaker-b", 3, floatArrayOf(-0.6f, 0.8f)),
            trial("known-confused", "speaker-a", 5, floatArrayOf(0f, 1f)),
            trial("unknown-rejected", null, 2, floatArrayOf(-1f, -1f)),
            trial("unknown-accepted", null, 3, floatArrayOf(1f, 0f)),
        )

        val metrics = SpeakerEvaluator.evaluate(trials)

        assertThat(metrics.totalTrials).isEqualTo(5)
        assertThat(metrics.registeredTrials).isEqualTo(3)
        assertThat(metrics.unknownTrials).isEqualTo(2)
        assertThat(metrics.top1Correct).isEqualTo(2)
        assertThat(metrics.top1Accuracy).isWithin(1e-12).of(2.0 / 3.0)
        assertThat(metrics.correctlyIdentified).isEqualTo(1)
        assertThat(metrics.falseRejections).isEqualTo(1)
        assertThat(metrics.falseRejectionRate).isWithin(1e-12).of(1.0 / 3.0)
        assertThat(metrics.identityConfusions).isEqualTo(1)
        assertThat(metrics.falseAcceptances).isEqualTo(1)
        assertThat(metrics.falseAcceptanceRate).isWithin(1e-12).of(0.5)
        assertThat(metrics.unknownFalseAcceptanceRate).isEqualTo(metrics.falseAcceptanceRate)
        assertThat(metrics.unknownDetectionRate).isWithin(1e-12).of(0.5)
        assertThat(metrics.finalDecisionAccuracy).isWithin(1e-12).of(0.4)
        assertThat(metrics.equalErrorRate).isNotNull()
    }

    @Test
    fun evaluateByDuration_returnsTwoThreeFiveSecondRowsIncludingEmptyRows() {
        val trials = listOf(
            trial("two-known", "speaker-a", 2, floatArrayOf(1f, 0f)),
            trial("three-unknown", null, 3, floatArrayOf(-1f, -1f)),
        )

        val byDuration = SpeakerEvaluator.evaluateByDuration(trials)

        assertThat(byDuration.keys).containsExactly(2, 3, 5).inOrder()
        assertThat(byDuration.getValue(2).registeredTrials).isEqualTo(1)
        assertThat(byDuration.getValue(3).unknownTrials).isEqualTo(1)
        assertThat(byDuration.getValue(5).totalTrials).isEqualTo(0)
        assertThat(byDuration.getValue(5).top1Accuracy).isNull()
    }

    @Test
    fun verificationMetrics_acceptsScoresEqualToThreshold() {
        val metrics = SpeakerEvaluator.verificationMetrics(
            trials = listOf(
                VerificationTrial(score = 0.5f, isGenuine = true),
                VerificationTrial(score = 0.5f, isGenuine = false),
                VerificationTrial(score = 0.4f, isGenuine = true),
                VerificationTrial(score = 0.4f, isGenuine = false),
            ),
            threshold = 0.5f,
        )

        assertThat(metrics.falseAcceptances).isEqualTo(1)
        assertThat(metrics.falseRejections).isEqualTo(1)
        assertThat(metrics.falseAcceptanceRate).isWithin(1e-12).of(0.5)
        assertThat(metrics.falseRejectionRate).isWithin(1e-12).of(0.5)
    }

    @Test
    fun verificationMetrics_rejectsScoresImmediatelyBelowThreshold() {
        val threshold = 0.5f
        val immediatelyBelow = Math.nextDown(threshold)
        val metrics = SpeakerEvaluator.verificationMetrics(
            trials = listOf(
                VerificationTrial(score = immediatelyBelow, isGenuine = true),
                VerificationTrial(score = immediatelyBelow, isGenuine = false),
            ),
            threshold = threshold,
        )

        assertThat(metrics.falseAcceptances).isEqualTo(0)
        assertThat(metrics.falseRejections).isEqualTo(1)
    }

    @Test
    fun equalErrorRate_isZeroForSeparatedScores() {
        val eer = SpeakerEvaluator.equalErrorRate(
            listOf(
                VerificationTrial(score = 0.9f, isGenuine = true),
                VerificationTrial(score = 0.8f, isGenuine = true),
                VerificationTrial(score = 0.3f, isGenuine = false),
                VerificationTrial(score = 0.2f, isGenuine = false),
            ),
        )

        assertThat(eer).isNotNull()
        assertThat(eer!!.rate).isWithin(1e-12).of(0.0)
    }

    @Test
    fun equalErrorRate_interpolatesTiedGenuineAndImpostorScores() {
        val eer = SpeakerEvaluator.equalErrorRate(
            listOf(
                VerificationTrial(score = 0.5f, isGenuine = true),
                VerificationTrial(score = 0.5f, isGenuine = false),
            ),
        )

        assertThat(eer).isNotNull()
        assertThat(eer!!.rate).isWithin(1e-12).of(0.5)
        assertThat(eer.threshold).isWithin(1e-6f).of(0.5f)
    }

    @Test
    fun equalErrorRate_requiresBothTrialClasses() {
        assertThat(
            SpeakerEvaluator.equalErrorRate(
                listOf(VerificationTrial(score = 0.5f, isGenuine = true)),
            ),
        ).isNull()
        assertThat(SpeakerEvaluator.equalErrorRate(emptyList())).isNull()
    }

    @Test
    fun evaluationDataRejectsInvalidBoundaries() {
        val result = scorer.score(floatArrayOf(1f, 0f), centroids)
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerEvaluationTrial("", "speaker-a", 2, result)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerEvaluationTrial("trial", " ", 2, result)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerEvaluationTrial("trial", null, 0, result)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerEvaluator.evaluateByDuration(emptyList(), emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            SpeakerEvaluator.verificationMetrics(emptyList(), Float.NaN)
        }
    }

    private fun trial(
        id: String,
        expectedSpeakerId: String?,
        durationSeconds: Int,
        embedding: FloatArray,
    ) = SpeakerEvaluationTrial(
        trialId = id,
        expectedSpeakerId = expectedSpeakerId,
        durationSeconds = durationSeconds,
        scoringResult = scorer.score(embedding, centroids),
    )
}
