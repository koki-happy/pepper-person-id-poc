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
    fun modelOptions_haveDistinctAssetNames() {
        assertEquals(FaceModelOption.entries.size, FaceModelOption.entries.map { it.modelFileName }.toSet().size)
        assertEquals(SpeakerModelOption.entries.size, SpeakerModelOption.entries.map { it.modelFileName }.toSet().size)
    }
}
