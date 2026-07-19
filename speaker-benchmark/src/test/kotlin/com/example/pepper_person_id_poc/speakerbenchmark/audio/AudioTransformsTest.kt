package com.example.pepper_person_id_poc.speakerbenchmark.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioTransformsTest {
    @Test
    fun `center crop takes the exact middle samples`() {
        assertContentEquals(
            floatArrayOf(2.0f, 3.0f, 4.0f),
            AudioTransforms.centerCrop(floatArrayOf(0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f), 3),
        )
    }

    @Test
    fun `center crop rejects an insufficient input`() {
        assertFailsWith<IllegalArgumentException> {
            AudioTransforms.centerCrop(floatArrayOf(1.0f), 2)
        }
    }

    @Test
    fun `24k to 16k resampling has exact duration and preserves passband sine`() {
        val sourceRate = 24_000
        val targetRate = 16_000
        val frequency = 1_000.0
        val source = FloatArray(sourceRate) { index ->
            (0.7 * sin(2.0 * PI * frequency * index / sourceRate)).toFloat()
        }

        val output = WindowedSincResampler.resample(source, sourceRate, targetRate)

        assertEquals(targetRate, output.size)
        val comparisonStart = 64
        val maxError = (comparisonStart until output.size - comparisonStart).maxOf { index ->
            val expected = 0.7 * sin(2.0 * PI * frequency * index / targetRate)
            abs(output[index] - expected)
        }
        assertTrue(maxError < 0.002, "Passband maximum error was $maxError")
    }

    @Test
    fun `24k to 16k low pass strongly attenuates content above output Nyquist`() {
        val sourceRate = 24_000
        val targetRate = 16_000
        val source = FloatArray(sourceRate) { index ->
            sin(2.0 * PI * 10_000.0 * index / sourceRate).toFloat()
        }

        val output = WindowedSincResampler.resample(source, sourceRate, targetRate)
        val margin = 64
        val rms = sqrt(
            (margin until output.size - margin).sumOf { index -> output[index].toDouble() * output[index] } /
                (output.size - margin * 2),
        )

        assertTrue(rms < 0.01, "Stopband RMS was $rms")
    }

    @Test
    fun `resampling preserves a constant level including boundaries`() {
        val output = WindowedSincResampler.resample(FloatArray(2_400) { 0.25f }, 24_000, 16_000)

        assertEquals(1_600, output.size)
        assertTrue(output.all { abs(it - 0.25f) < 1e-6 })
    }
}
