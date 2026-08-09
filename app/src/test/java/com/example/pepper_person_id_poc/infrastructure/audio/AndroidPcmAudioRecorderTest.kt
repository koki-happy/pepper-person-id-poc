package com.example.pepper_person_id_poc.infrastructure.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AndroidPcmAudioRecorderTest {
    @Test
    fun configuredCaptureRatesContainOnlyStrict16Khz() {
        val sampleRateField = AndroidPcmAudioRecorder::class.java.declaredFields.single { field ->
            field.name.contains("SAMPLE_RATE_CANDIDATES") &&
                List::class.java.isAssignableFrom(field.type)
        }
        sampleRateField.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val configuredRates = sampleRateField.get(null) as List<Int>

        assertThat(configuredRates).containsExactly(16_000)
    }

}
