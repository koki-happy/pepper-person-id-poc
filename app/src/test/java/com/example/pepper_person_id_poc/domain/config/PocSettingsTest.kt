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
    }

    @Test
    fun scoreOutsideRange_isInvalid() {
        assertFalse(PocSettings(faceThreshold = 1.01f).isValid())
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
    }
}
