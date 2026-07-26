package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceQualityAssessmentTest {
    private val thresholds = FaceQualityThresholds(
        minimumFaceWidthPixels = 80,
        minimumFaceHeightPixels = 80,
        minimumBlurScore = 100f,
        minimumBrightnessMean = 40f,
        maximumBrightnessMean = 220f,
        maximumClippedRatio = 0.02f,
        maximumEdgeTruncationRatio = 0.05f,
        maximumAbsoluteYawDegrees = 25f,
        maximumAbsolutePitchDegrees = 20f,
        maximumAbsoluteRollDegrees = 20f,
        requiredLandmarkCount = 5,
        minimumTrackDurationMillis = 500L,
        updateMinimumFaceWidthPixels = 80,
        updateMinimumFaceHeightPixels = 80,
        updateMinimumTrackDurationMillis = 500L,
        minimumDetectionConfidenceWhenAvailable = 0.75f,
    )
    private val policy = FaceQualityPolicy(thresholds)

    @Test
    fun unavailableMlKitConfidence_remainsNullAndDoesNotPretendToBePerfect() {
        val assessment = policy.assess(validInput().copy(detectionConfidence = null))

        assertThat(assessment.detectionConfidence).isNull()
        assertThat(assessment.detectionConfidence).isNotEqualTo(1f)
        assertThat(assessment.rejectionReasons).doesNotContain("DETECTION_CONFIDENCE_TOO_LOW")
        assertThat(assessment.createEligible).isTrue()
        assertThat(assessment.updateEligible).isTrue()
    }

    @Test
    fun availableConfidence_isPreservedExactly() {
        val confidence = 0.83f

        val assessment = policy.assess(validInput().copy(detectionConfidence = confidence))

        assertThat(assessment.detectionConfidence).isEqualTo(confidence)
        assertThat(assessment.createEligible).isTrue()
    }

    @Test
    fun availableConfidenceBelowBoundary_isRejected() {
        val assessment = policy.assess(
            validInput().copy(
                detectionConfidence = Math.nextDown(
                    thresholds.minimumDetectionConfidenceWhenAvailable,
                ),
            ),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.updateEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("DETECTION_CONFIDENCE_TOO_LOW")
    }

    @Test
    fun availableConfidenceAtBoundary_isAccepted() {
        val assessment = policy.assess(
            validInput().copy(
                detectionConfidence = thresholds.minimumDetectionConfidenceWhenAvailable,
            ),
        )

        assertThat(assessment.createEligible).isTrue()
        assertThat(assessment.updateEligible).isTrue()
    }

    private fun validInput() = FaceQualityInput(
        faceWidthPixels = 120,
        faceHeightPixels = 120,
        detectionConfidence = 0.95f,
        landmarkCount = 5,
        blurScore = 150f,
        brightnessMean = 128f,
        clippedRatio = 0.01f,
        yawDegrees = 0f,
        pitchDegrees = 0f,
        rollDegrees = 0f,
        edgeTruncationRatio = 0f,
        trackDurationMillis = 1_500L,
    )
}
