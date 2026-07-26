package com.example.pepper_person_id_poc.domain.face

import kotlin.math.abs

data class FaceQualityThresholds(
    val minimumFaceWidthPixels: Int,
    val minimumFaceHeightPixels: Int,
    val minimumBlurScore: Float,
    val minimumBrightnessMean: Float,
    val maximumBrightnessMean: Float,
    val maximumClippedRatio: Float,
    val maximumEdgeTruncationRatio: Float,
    val maximumAbsoluteYawDegrees: Float,
    val maximumAbsolutePitchDegrees: Float,
    val maximumAbsoluteRollDegrees: Float,
    val requiredLandmarkCount: Int,
    val minimumTrackDurationMillis: Long,
    val updateMinimumFaceWidthPixels: Int,
    val updateMinimumFaceHeightPixels: Int,
    val updateMinimumTrackDurationMillis: Long,
    val minimumDetectionConfidenceWhenAvailable: Float = 0f,
) {
    init {
        require(minimumFaceWidthPixels > 0)
        require(minimumFaceHeightPixels > 0)
        require(minimumBlurScore.isFinite() && minimumBlurScore >= 0f)
        require(minimumBrightnessMean.isFinite())
        require(maximumBrightnessMean.isFinite())
        require(minimumBrightnessMean <= maximumBrightnessMean)
        require(maximumClippedRatio in 0f..1f)
        require(maximumEdgeTruncationRatio in 0f..1f)
        require(maximumAbsoluteYawDegrees.isFinite() && maximumAbsoluteYawDegrees >= 0f)
        require(maximumAbsolutePitchDegrees.isFinite() && maximumAbsolutePitchDegrees >= 0f)
        require(maximumAbsoluteRollDegrees.isFinite() && maximumAbsoluteRollDegrees >= 0f)
        require(requiredLandmarkCount >= 0)
        require(minimumTrackDurationMillis >= 0L)
        require(updateMinimumFaceWidthPixels >= minimumFaceWidthPixels)
        require(updateMinimumFaceHeightPixels >= minimumFaceHeightPixels)
        require(updateMinimumTrackDurationMillis >= minimumTrackDurationMillis)
        require(minimumDetectionConfidenceWhenAvailable in 0f..1f)
    }

    companion object {
        val DEFAULT = FaceQualityThresholds(
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
            minimumDetectionConfidenceWhenAvailable = 0.75f,
        )
    }
}

data class FaceQualityInput(
    val faceWidthPixels: Int,
    val faceHeightPixels: Int,
    val detectionConfidence: Float?,
    val landmarkCount: Int,
    val blurScore: Float,
    val brightnessMean: Float,
    val clippedRatio: Float,
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
    val edgeTruncationRatio: Float,
    val trackDurationMillis: Long,
)

data class FaceQualityAssessment(
    val createEligible: Boolean,
    val updateEligible: Boolean,
    val rejectionReasons: List<String>,
    val detectionConfidence: Float?,
    val input: FaceQualityInput? = null,
)

class FaceQualityPolicy(
    private val thresholds: FaceQualityThresholds,
) {
    fun assess(input: FaceQualityInput): FaceQualityAssessment {
        val reasons = linkedSetOf<String>()
        if (
            input.faceWidthPixels < thresholds.minimumFaceWidthPixels ||
            input.faceHeightPixels < thresholds.minimumFaceHeightPixels
        ) {
            reasons += "FACE_TOO_SMALL"
        }
        if (!input.blurScore.isFinite() || input.blurScore < thresholds.minimumBlurScore) {
            reasons += "INSUFFICIENT_SHARPNESS"
        }
        if (!input.brightnessMean.isFinite()) {
            reasons += "INVALID_MEASUREMENT"
        } else {
            if (input.brightnessMean < thresholds.minimumBrightnessMean) reasons += "TOO_DARK"
            if (input.brightnessMean > thresholds.maximumBrightnessMean) reasons += "TOO_BRIGHT"
        }
        if (!input.clippedRatio.isFinite()) {
            reasons += "INVALID_MEASUREMENT"
        } else if (input.clippedRatio > thresholds.maximumClippedRatio || input.clippedRatio < 0f) {
            reasons += "EXCESSIVE_CLIPPING"
        }
        if (!input.edgeTruncationRatio.isFinite()) {
            reasons += "INVALID_MEASUREMENT"
        } else if (
            input.edgeTruncationRatio > thresholds.maximumEdgeTruncationRatio ||
            input.edgeTruncationRatio < 0f
        ) {
            reasons += "EDGE_TRUNCATED"
        }
        if (
            !input.yawDegrees.isFinite() ||
            !input.pitchDegrees.isFinite() ||
            !input.rollDegrees.isFinite() ||
            abs(input.yawDegrees) > thresholds.maximumAbsoluteYawDegrees ||
            abs(input.pitchDegrees) > thresholds.maximumAbsolutePitchDegrees ||
            abs(input.rollDegrees) > thresholds.maximumAbsoluteRollDegrees
        ) {
            reasons += "POSE_OUT_OF_RANGE"
        }
        if (input.landmarkCount < thresholds.requiredLandmarkCount) {
            reasons += "LANDMARKS_INCOMPLETE"
        }
        if (input.trackDurationMillis < thresholds.minimumTrackDurationMillis) {
            reasons += "TRACK_TOO_SHORT"
        }
        input.detectionConfidence?.let { confidence ->
            if (
                !confidence.isFinite() ||
                confidence < thresholds.minimumDetectionConfidenceWhenAvailable ||
                confidence > 1f
            ) {
                reasons += "DETECTION_CONFIDENCE_TOO_LOW"
            }
        }

        val createEligible = reasons.isEmpty()
        val updateEligible = createEligible &&
            input.faceWidthPixels >= thresholds.updateMinimumFaceWidthPixels &&
            input.faceHeightPixels >= thresholds.updateMinimumFaceHeightPixels &&
            input.trackDurationMillis >= thresholds.updateMinimumTrackDurationMillis
        return FaceQualityAssessment(
            createEligible = createEligible,
            updateEligible = updateEligible,
            rejectionReasons = reasons.toList(),
            detectionConfidence = input.detectionConfidence,
            input = input,
        )
    }
}
