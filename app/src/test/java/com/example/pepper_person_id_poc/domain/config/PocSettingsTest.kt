package com.example.pepper_person_id_poc.domain.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocSettingsTest {
    @Test
    fun defaults_areValid() {
        assertTrue(PocSettings().isValid())
    }

    @Test
    fun scoreOutsideRange_isInvalid() {
        assertFalse(PocSettings(faceThreshold = 1.01f).isValid())
    }

    @Test
    fun observationWindowOutsideRange_isInvalid() {
        assertFalse(PocSettings(observationWindowMillis = 100L).isValid())
    }
}
