package com.example.pepper_person_id_poc.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PocSettingsTest {
    @Test
    fun defaultsUseChineseEnglishCamPlusPlusAndItsThreshold() {
        val defaults = PocSettings()
        assertThat(defaults.isValid()).isTrue()
        assertThat(defaults.speakerModel).isEqualTo(SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN)
        assertThat(defaults.speakerClusterJoinThreshold)
            .isEqualTo(SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN.jvsCandidateThreshold)
        assertThat(defaults.faceAnalysisIntervalMillis).isEqualTo(1_000L)
        assertThat(defaults.faceDetectionScoreThreshold).isEqualTo(0.80f)
        assertThat(defaults.faceNmsThreshold).isEqualTo(0.30f)
        assertThat(defaults.faceMaximumDetectionCandidates).isEqualTo(5_000)
        assertThat(defaults.mlKitMinimumFaceSize).isEqualTo(0.10f)
        assertThat(defaults.faceLabelContinuationIou).isEqualTo(0.30f)
        assertThat(defaults.faceLabelMaximumMissingFrames).isEqualTo(4)
        assertThat(defaults.vadThreshold).isEqualTo(0.35f)
        assertThat(defaults.vadMinimumSilenceMillis).isEqualTo(400L)
        assertThat(defaults.vadMinimumSpeechMillis).isEqualTo(300L)
        assertThat(defaults.vadMaximumSpeechMillis).isEqualTo(30_000L)
        assertThat(defaults.utteranceEndSilenceMillis).isEqualTo(600L)
        assertThat(defaults.maximumUtteranceMillis).isEqualTo(30_000L)
        assertThat(defaults.speakerMinimumAudioMillis).isEqualTo(1_000L)
        assertThat(defaults.speakerMinimumVoicedRatio).isEqualTo(0.50f)
        assertThat(defaults.speakerMinimumRms).isEqualTo(0f)
        assertThat(defaults.clippingAmplitudeThreshold).isEqualTo(0.999f)
        assertThat(defaults.maximumClippingRatio).isEqualTo(0.05f)
        assertThat(defaults.speakerUpdateMinimumAudioMillis).isEqualTo(1_000L)
        assertThat(defaults.speakerUpdateMinimumVoicedRatio).isEqualTo(0.50f)
        assertThat(defaults.speakerLabelContinuationSimilarity).isEqualTo(0.50f)
        assertThat(defaults.speakerLabelMaximumMissingSegments).isEqualTo(2)
    }

    @Test
    fun clusterBoundsAreValidated() {
        assertThat(PocSettings(faceClusterMaxUpdateCount = 0).isValid()).isFalse()
        assertThat(PocSettings(speakerClusterJoinThreshold = 1.1f).isValid()).isFalse()
    }

    @Test
    fun detailedParameterBoundsAreValidated() {
        assertThat(PocSettings(faceAnalysisIntervalMillis = 99L).isValid()).isFalse()
        assertThat(PocSettings(faceMaximumDetectionCandidates = 5_001).isValid()).isFalse()
        assertThat(PocSettings(mlKitMinimumFaceSize = 0.51f).isValid()).isFalse()
        assertThat(PocSettings(faceLabelMaximumMissingFrames = 31).isValid()).isFalse()
        assertThat(PocSettings(vadMinimumSilenceMillis = 2_001L).isValid()).isFalse()
        assertThat(PocSettings(vadMaximumSpeechMillis = 999L).isValid()).isFalse()
        assertThat(PocSettings(utteranceEndSilenceMillis = 99L).isValid()).isFalse()
        assertThat(PocSettings(speakerMinimumAudioMillis = 10_001L).isValid()).isFalse()
        assertThat(PocSettings(clippingAmplitudeThreshold = 0.799f).isValid()).isFalse()
        assertThat(PocSettings(speakerLabelContinuationSimilarity = -1.01f).isValid()).isFalse()
        assertThat(PocSettings(speakerLabelMaximumMissingSegments = 21).isValid()).isFalse()
    }

    @Test
    fun allDetailedBoundaryValuesAreAccepted() {
        assertThat(
            PocSettings(
                faceAnalysisIntervalMillis = 100L,
                faceMaximumDetectionCandidates = 100,
                mlKitMinimumFaceSize = 0.05f,
                faceLabelMaximumMissingFrames = 0,
                vadMinimumSilenceMillis = 100L,
                vadMinimumSpeechMillis = 100L,
                vadMaximumSpeechMillis = 30_000L,
                utteranceEndSilenceMillis = 3_000L,
                maximumUtteranceMillis = 30_000L,
                speakerMinimumAudioMillis = 100L,
                clippingAmplitudeThreshold = 1.0f,
                speakerUpdateMinimumAudioMillis = 10_000L,
                speakerLabelContinuationSimilarity = -1f,
                speakerLabelMaximumMissingSegments = 20,
            ).isValid(),
        ).isTrue()
    }

    @Test
    fun selectingSpeakerModelUpdatesJoinThreshold() {
        val updated = PocSettings().withSpeakerModel(SpeakerModelOption.WESPEAKER_RESNET34_LM)
        assertThat(updated.speakerModel).isEqualTo(SpeakerModelOption.WESPEAKER_RESNET34_LM)
        assertThat(updated.speakerClusterJoinThreshold)
            .isEqualTo(SpeakerModelOption.WESPEAKER_RESNET34_LM.jvsCandidateThreshold)
    }

    @Test
    fun multipleSamplesAreDisabledByDefaultAndLimitUpdatesToFirstSample() {
        val defaults = PocSettings(faceClusterMaxUpdateCount = 20, speakerClusterMaxUpdateCount = 20)

        assertThat(defaults.multipleSamplesEnabled).isFalse()
        assertThat(defaults.showFaceLandmarks).isFalse()
        assertThat(defaults.loadTestVideo).isEqualTo(LoadTestVideoOption.OFF)
        assertThat(defaults.effectiveFaceMaximumUpdateCount).isEqualTo(1)
        assertThat(defaults.effectiveSpeakerMaximumUpdateCount).isEqualTo(1)
    }

    @Test
    fun enablingMultipleSamplesUsesConfiguredUpdateLimits() {
        val enabled = PocSettings(
            multipleSamplesEnabled = true,
            faceClusterMaxUpdateCount = 12,
            speakerClusterMaxUpdateCount = 34,
        )

        assertThat(enabled.effectiveFaceMaximumUpdateCount).isEqualTo(12)
        assertThat(enabled.effectiveSpeakerMaximumUpdateCount).isEqualTo(34)
    }
}
