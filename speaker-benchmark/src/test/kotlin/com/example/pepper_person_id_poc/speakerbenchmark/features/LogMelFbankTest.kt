package com.example.pepper_person_id_poc.speakerbenchmark.features

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogMelFbankTest {
    private val sine = FloatArray(16_000) { index ->
        (0.25 * sin(2.0 * PI * 440.0 * index / 16_000.0)).toFloat()
    }

    @Test
    fun threeDSpeakerPath_usesReflectedEdgesAndGlobalMean() {
        val result = LogMelFbank(LogMelFbankConfig()).compute(sine)

        assertEquals(100, result.numFrames)
        assertEquals(80, result.numBins)
        repeat(result.numBins) { bin ->
            val mean = (0 until result.numFrames)
                .sumOf { frame -> result.values[frame * result.numBins + bin].toDouble() } /
                result.numFrames
            assertTrue(abs(mean) < 1e-4, "bin=$bin mean=$mean")
        }
    }

}
