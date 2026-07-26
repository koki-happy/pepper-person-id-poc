package com.example.pepper_person_id_poc.domain.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeakerAudioQualityPolicyTest {
    private val thresholds = SpeakerAudioQualityThresholds(
        requiredSampleRate = 16_000,
        minimumDurationMillis = 1_000L,
        minimumVoicedRatio = 0.50f,
        minimumRms = 0.01f,
        maximumClippingRatio = 0.02f,
        maximumOverlapRatio = 0f,
        maximumActiveSpeakerCount = 1,
        updateMinimumDurationMillis = 1_500L,
        updateMinimumVoicedRatio = 0.65f,
    )
    private val policy = SpeakerAudioQualityPolicy(thresholds)

    @Test
    fun valuesAtAllBoundariesAreAcceptedForCreateAndUpdate() {
        val assessment = policy.assess(
            validInput().copy(
                sampleCount = 24_000,
                durationMillis = thresholds.updateMinimumDurationMillis,
                voicedRatio = thresholds.updateMinimumVoicedRatio,
                rms = thresholds.minimumRms,
                clippingRatio = thresholds.maximumClippingRatio,
                overlapRatio = thresholds.maximumOverlapRatio,
                activeSpeakerCount = thresholds.maximumActiveSpeakerCount,
            ),
        )

        assertThat(assessment.createEligible).isTrue()
        assertThat(assessment.updateEligible).isTrue()
        assertThat(assessment.rejectionReasons).isEmpty()
        assertThat(assessment.snr).isNull()
    }

    @Test
    fun non16KhzInputIsRejectedWithoutResamplingFallback() {
        val assessment = policy.assess(validInput().copy(sampleRate = 44_100))

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("UNSUPPORTED_SAMPLE_RATE")
    }

    @Test
    fun durationImmediatelyBelowBoundaryIsRejected() {
        val assessment = policy.assess(
            validInput().copy(
                sampleCount = 15_984,
                durationMillis = thresholds.minimumDurationMillis - 1L,
            ),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("AUDIO_TOO_SHORT")
    }

    @Test
    fun voicedRatioImmediatelyBelowBoundaryIsRejected() {
        val assessment = policy.assess(
            validInput().copy(voicedRatio = Math.nextDown(thresholds.minimumVoicedRatio)),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("INSUFFICIENT_VOICED_AUDIO")
    }

    @Test
    fun rmsImmediatelyBelowBoundaryIsRejected() {
        val assessment = policy.assess(
            validInput().copy(rms = Math.nextDown(thresholds.minimumRms)),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("SIGNAL_TOO_QUIET")
    }

    @Test
    fun clippingImmediatelyAboveBoundaryIsRejected() {
        val assessment = policy.assess(
            validInput().copy(
                clippingRatio = Math.nextUp(thresholds.maximumClippingRatio),
            ),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("EXCESSIVE_CLIPPING")
    }

    @Test
    fun anyOverlapIsRejectedFailClosed() {
        val assessment = policy.assess(
            validInput().copy(overlapRatio = Math.nextUp(thresholds.maximumOverlapRatio)),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("OVERLAPPED_SPEECH")
    }

    @Test
    fun multipleActiveSpeakersAreRejectedFailClosed() {
        val assessment = policy.assess(
            validInput().copy(activeSpeakerCount = 2),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("MULTIPLE_ACTIVE_SPEAKERS")
    }

    @Test
    fun updateBoundaryIsStricterThanCreateBoundary() {
        val assessment = policy.assess(
            validInput().copy(
                sampleCount = 16_000,
                durationMillis = thresholds.minimumDurationMillis,
                voicedRatio = thresholds.minimumVoicedRatio,
            ),
        )

        assertThat(assessment.createEligible).isTrue()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).isEmpty()
    }

    @Test
    fun nonFiniteMeasurementsAreRejected() {
        val assessment = policy.assess(
            validInput().copy(
                peak = Float.NaN,
                rms = Float.POSITIVE_INFINITY,
                voicedRatio = Float.NaN,
            ),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("INVALID_MEASUREMENT")
    }

    private fun validInput() = SpeakerAudioQualityInput(
        sampleRate = 16_000,
        sampleCount = 32_000,
        durationMillis = 2_000L,
        voicedRatio = 0.80f,
        peak = 0.80f,
        rms = 0.20f,
        clippingRatio = 0.01f,
        snr = null,
        overlapRatio = 0f,
        activeSpeakerCount = 1,
    )
}
