package com.example.pepper_person_id_poc.domain.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PocSettingsTest {
    @Test
    fun defaults_areValid() {
        assertTrue(PocSettings().isValid())
        assertEquals(FaceModelOption.SFACE_2021DEC, PocSettings().faceModel)
        assertEquals(SpeakerModelOption.ERES2NET, PocSettings().speakerModel)
        assertEquals(0.70323396f, PocSettings().speakerThreshold)
        assertEquals(0.0f, PocSettings().speakerMargin)
    }

    @Test
    fun scoreOutsideRange_isInvalid() {
        assertFalse(PocSettings(faceThreshold = 1.01f).isValid())
        assertFalse(PocSettings(speakerMargin = 2.01f).isValid())
    }

    @Test
    fun observationWindowOutsideRange_isInvalid() {
        assertFalse(PocSettings(observationWindowMillis = 100L).isValid())
    }

    @Test
    fun faceGuidanceDefaults_matchCanonicalSpecification() {
        val settings = PocSettings()

        assertEquals(0.0f, settings.faceMargin)
        assertEquals(200L, settings.faceRegistrationAnalysisIntervalMillis)
        assertEquals(1_000L, settings.faceIdentificationAnalysisIntervalMillis)
        assertEquals(1_000L, settings.facePoseStableDurationMillis)
        assertEquals(8f, settings.faceFrontYawDegrees)
        assertEquals(8f, settings.faceFrontPitchDegrees)
        assertEquals(18f, settings.faceSideMinimumYawDegrees)
        assertEquals(32f, settings.faceSideMaximumYawDegrees)
        assertEquals(5, settings.faceSmoothingSampleCount)
    }

    @Test
    fun invalidFaceGuidanceRanges_areRejected() {
        assertFalse(PocSettings(faceMargin = 2.01f).isValid())
        assertFalse(PocSettings(faceRegistrationAnalysisIntervalMillis = 99L).isValid())
        assertFalse(PocSettings(facePoseStableDurationMillis = 249L).isValid())
        assertFalse(PocSettings(faceSideMinimumYawDegrees = 32f, faceSideMaximumYawDegrees = 18f).isValid())
        assertFalse(PocSettings(faceSmoothingSampleCount = 0).isValid())
    }

    @Test
    fun modelOptions_haveDistinctAssetNames() {
        assertEquals(FaceModelOption.entries.size, FaceModelOption.entries.map { it.modelFileName }.toSet().size)
        assertEquals(SpeakerModelOption.entries.size, SpeakerModelOption.entries.map { it.modelFileName }.toSet().size)
        assertEquals(SpeakerModelOption.entries.size, SpeakerModelOption.entries.map { it.configModelId }.toSet().size)
        assertEquals("campplus-en", SpeakerModelOption.CAM_PLUS_PLUS.configModelId)
        assertEquals("campplus-zh-en", SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN.configModelId)
        assertEquals("eres2net-en", SpeakerModelOption.ERES2NET.configModelId)
    }

    @Test
    fun withSpeakerModel_appliesItsJvsCandidateOperatingPoint() {
        val updated = PocSettings().withSpeakerModel(SpeakerModelOption.CAM_PLUS_PLUS)

        assertEquals(SpeakerModelOption.CAM_PLUS_PLUS, updated.speakerModel)
        assertEquals(0.3625838f, updated.speakerThreshold)
        assertEquals(0.1168099f, updated.speakerMargin)
    }
}
