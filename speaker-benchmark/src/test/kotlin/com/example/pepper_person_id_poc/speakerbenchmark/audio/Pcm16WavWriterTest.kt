package com.example.pepper_person_id_poc.speakerbenchmark.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pcm16WavWriterTest {
    @Test
    fun `writes canonical mono PCM16 that the strict reader accepts`() {
        val encoded = Pcm16WavWriter.encode(floatArrayOf(-2.0f, -0.5f, 0.0f, 0.5f, 2.0f), 16_000)

        val decoded = WavReader.decode(encoded)

        assertEquals(WavEncoding.PCM_SIGNED_16, decoded.encoding)
        assertContentEquals(
            floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 32_767 / 32_768.0f),
            decoded.samples,
        )
    }

    @Test
    fun `rejects non-finite output samples`() {
        assertFailsWith<IllegalArgumentException> {
            Pcm16WavWriter.encode(floatArrayOf(Float.NaN), 16_000)
        }
    }
}
