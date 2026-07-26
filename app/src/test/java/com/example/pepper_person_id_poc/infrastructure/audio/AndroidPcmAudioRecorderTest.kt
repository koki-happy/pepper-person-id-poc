package com.example.pepper_person_id_poc.infrastructure.audio

import com.google.common.truth.Truth.assertThat
import java.nio.charset.StandardCharsets
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

    @Test
    fun recorderDoesNotContainEnergyVadFallback() {
        val classResource = "/" +
            AndroidPcmAudioRecorder::class.java.name.replace('.', '/') +
            ".class"
        val bytecode = checkNotNull(
            AndroidPcmAudioRecorder::class.java.getResourceAsStream(classResource),
        ) {
            "Missing compiled recorder class resource: $classResource"
        }.use { input ->
            String(input.readBytes(), StandardCharsets.ISO_8859_1)
        }

        assertThat(bytecode).doesNotContain("EnergyVoiceActivityDetector")
    }
}
