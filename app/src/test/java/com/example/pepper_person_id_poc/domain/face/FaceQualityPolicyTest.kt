package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceQualityPolicyTest {
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
        updateMinimumFaceWidthPixels = 100,
        updateMinimumFaceHeightPixels = 100,
        updateMinimumTrackDurationMillis = 1_000L,
    )
    private val policy = FaceQualityPolicy(thresholds)

    @Test
    fun exactCreateBoundaries_areCreateEligible() {
        val assessment = policy.assess(
            validInput().copy(
                faceWidthPixels = thresholds.minimumFaceWidthPixels,
                faceHeightPixels = thresholds.minimumFaceHeightPixels,
                blurScore = thresholds.minimumBlurScore,
                brightnessMean = thresholds.minimumBrightnessMean,
                clippedRatio = thresholds.maximumClippedRatio,
                edgeTruncationRatio = thresholds.maximumEdgeTruncationRatio,
                yawDegrees = thresholds.maximumAbsoluteYawDegrees,
                pitchDegrees = -thresholds.maximumAbsolutePitchDegrees,
                rollDegrees = thresholds.maximumAbsoluteRollDegrees,
                landmarkCount = thresholds.requiredLandmarkCount,
                trackDurationMillis = thresholds.minimumTrackDurationMillis,
            ),
        )

        assertThat(assessment.createEligible).isTrue()
    }

    @Test
    fun exactUpdateBoundaries_areUpdateEligible() {
        val assessment = policy.assess(
            validInput().copy(
                faceWidthPixels = thresholds.updateMinimumFaceWidthPixels,
                faceHeightPixels = thresholds.updateMinimumFaceHeightPixels,
                trackDurationMillis = thresholds.updateMinimumTrackDurationMillis,
            ),
        )

        assertThat(assessment.createEligible).isTrue()
        assertThat(assessment.updateEligible).isTrue()
    }

    @Test
    fun faceWidthOrHeightBelowBoundary_isRejected() {
        val tooNarrow = policy.assess(
            validInput().copy(faceWidthPixels = thresholds.minimumFaceWidthPixels - 1),
        )
        val tooShort = policy.assess(
            validInput().copy(faceHeightPixels = thresholds.minimumFaceHeightPixels - 1),
        )

        assertThat(tooNarrow.createEligible).isFalse()
        assertThat(tooNarrow.rejectionReasons).contains("FACE_TOO_SMALL")
        assertThat(tooShort.createEligible).isFalse()
        assertThat(tooShort.rejectionReasons).contains("FACE_TOO_SMALL")
    }

    @Test
    fun blurBelowBoundary_isRejected() {
        val assessment = policy.assess(
            validInput().copy(blurScore = Math.nextDown(thresholds.minimumBlurScore)),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("INSUFFICIENT_SHARPNESS")
    }

    @Test
    fun brightnessOutsideInclusiveRange_isRejectedWithDirectionalReason() {
        val tooDark = policy.assess(
            validInput().copy(brightnessMean = Math.nextDown(thresholds.minimumBrightnessMean)),
        )
        val tooBright = policy.assess(
            validInput().copy(brightnessMean = Math.nextUp(thresholds.maximumBrightnessMean)),
        )

        assertThat(tooDark.rejectionReasons).contains("TOO_DARK")
        assertThat(tooBright.rejectionReasons).contains("TOO_BRIGHT")
    }

    @Test
    fun clippedRatioAboveBoundary_isRejected() {
        val assessment = policy.assess(
            validInput().copy(clippedRatio = Math.nextUp(thresholds.maximumClippedRatio)),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("EXCESSIVE_CLIPPING")
    }

    @Test
    fun edgeTruncationAboveBoundary_isRejected() {
        val assessment = policy.assess(
            validInput().copy(edgeTruncationRatio = Math.nextUp(thresholds.maximumEdgeTruncationRatio)),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("EDGE_TRUNCATED")
    }

    @Test
    fun eachPoseAxisOutsideBoundary_isRejected() {
        val invalidPoses = listOf(
            validInput().copy(yawDegrees = Math.nextUp(thresholds.maximumAbsoluteYawDegrees)),
            validInput().copy(pitchDegrees = -Math.nextUp(thresholds.maximumAbsolutePitchDegrees)),
            validInput().copy(rollDegrees = Math.nextUp(thresholds.maximumAbsoluteRollDegrees)),
        )

        invalidPoses.forEach { input ->
            val assessment = policy.assess(input)
            assertThat(assessment.createEligible).isFalse()
            assertThat(assessment.rejectionReasons).contains("POSE_OUT_OF_RANGE")
        }
    }

    @Test
    fun missingAnyRequiredLandmark_isRejected() {
        val assessment = policy.assess(
            validInput().copy(landmarkCount = thresholds.requiredLandmarkCount - 1),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("LANDMARKS_INCOMPLETE")
    }

    @Test
    fun trackDurationBelowBoundary_isRejected() {
        val assessment = policy.assess(
            validInput().copy(trackDurationMillis = thresholds.minimumTrackDurationMillis - 1L),
        )

        assertThat(assessment.createEligible).isFalse()
        assertThat(assessment.rejectionReasons).contains("TRACK_TOO_SHORT")
    }

    @Test
    fun createEligibleInputBelowStricterUpdateBoundaries_isNotUpdateEligible() {
        val assessment = policy.assess(
            validInput().copy(
                faceWidthPixels = thresholds.minimumFaceWidthPixels,
                faceHeightPixels = thresholds.minimumFaceHeightPixels,
                trackDurationMillis = thresholds.minimumTrackDurationMillis,
            ),
        )

        assertThat(assessment.createEligible).isTrue()
        assertThat(assessment.updateEligible).isFalse()
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
