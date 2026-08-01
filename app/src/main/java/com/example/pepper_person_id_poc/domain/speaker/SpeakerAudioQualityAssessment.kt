package com.example.pepper_person_id_poc.domain.speaker

data class SpeakerAudioQualityThresholds(
    val requiredSampleRate: Int,
    val minimumDurationMillis: Long,
    val minimumVoicedRatio: Float,
    val minimumRms: Float,
    val maximumClippingRatio: Float,
    val maximumOverlapRatio: Float,
    val maximumActiveSpeakerCount: Int,
    val updateMinimumDurationMillis: Long,
    val updateMinimumVoicedRatio: Float,
) {
    init {
        require(requiredSampleRate > 0)
        require(minimumDurationMillis > 0L)
        require(minimumVoicedRatio in 0f..1f)
        require(minimumRms.isFinite() && minimumRms >= 0f)
        require(maximumClippingRatio in 0f..1f)
        require(maximumOverlapRatio in 0f..1f)
        require(maximumActiveSpeakerCount >= 1)
        require(updateMinimumDurationMillis > 0L)
        require(updateMinimumVoicedRatio in 0f..1f)
    }
}

data class SpeakerAudioQualityInput(
    val sampleRate: Int,
    val sampleCount: Int,
    val durationMillis: Long,
    val voicedRatio: Float,
    val peak: Float,
    val rms: Float,
    val clippingRatio: Float,
    val snr: Float?,
    val overlapRatio: Float,
    val activeSpeakerCount: Int,
)

data class SpeakerAudioQualityAssessment(
    val createEligible: Boolean,
    val updateEligible: Boolean,
    val rejectionReasons: List<String>,
    val snr: Float?,
)

class SpeakerAudioQualityPolicy(
    private val thresholds: SpeakerAudioQualityThresholds,
) {
    fun assess(input: SpeakerAudioQualityInput): SpeakerAudioQualityAssessment {
        val reasons = linkedSetOf<String>()
        if (input.sampleRate != thresholds.requiredSampleRate) {
            reasons += "UNSUPPORTED_SAMPLE_RATE"
        }
        if (input.sampleCount < 0 || input.durationMillis < thresholds.minimumDurationMillis) {
            reasons += "AUDIO_TOO_SHORT"
        }
        val invalidMeasurement =
            !input.voicedRatio.isFinite() ||
                !input.peak.isFinite() ||
                !input.rms.isFinite() ||
                !input.clippingRatio.isFinite() ||
                !input.overlapRatio.isFinite() ||
                input.snr?.isFinite() == false ||
                input.voicedRatio !in 0f..1f ||
                input.peak !in 0f..1f ||
                input.rms < 0f ||
                input.clippingRatio !in 0f..1f ||
                input.overlapRatio !in 0f..1f ||
                input.activeSpeakerCount < 0
        if (invalidMeasurement) {
            reasons += "INVALID_MEASUREMENT"
        } else {
            if (input.voicedRatio < thresholds.minimumVoicedRatio) {
                reasons += "INSUFFICIENT_VOICED_AUDIO"
            }
            if (input.rms < thresholds.minimumRms) {
                reasons += "SIGNAL_TOO_QUIET"
            }
            if (input.clippingRatio > thresholds.maximumClippingRatio) {
                reasons += "EXCESSIVE_CLIPPING"
            }
            if (input.overlapRatio > thresholds.maximumOverlapRatio) {
                reasons += "OVERLAPPED_SPEECH"
            }
            if (input.activeSpeakerCount > thresholds.maximumActiveSpeakerCount) {
                reasons += "MULTIPLE_ACTIVE_SPEAKERS"
            }
        }

        val createEligible = reasons.isEmpty()
        val updateEligible = createEligible &&
            input.durationMillis >= thresholds.updateMinimumDurationMillis &&
            input.voicedRatio >= thresholds.updateMinimumVoicedRatio
        return SpeakerAudioQualityAssessment(
            createEligible = createEligible,
            updateEligible = updateEligible,
            rejectionReasons = reasons.toList(),
            snr = input.snr,
        )
    }
}
