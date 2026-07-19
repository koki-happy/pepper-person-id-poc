package com.example.pepper_person_id_poc.speakerbenchmark.audio

import com.example.pepper_person_id_poc.speakerbenchmark.float32Wav
import com.example.pepper_person_id_poc.speakerbenchmark.pcm16Wav
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WavReaderTest {
    @Test
    fun `reads 16k mono PCM16 and normalizes by 32768`() {
        val wav = pcm16Wav(shortArrayOf(Short.MIN_VALUE, -16_384, 0, 16_384, Short.MAX_VALUE))

        val audio = WavReader.decode(wav)

        assertEquals(WavEncoding.PCM_SIGNED_16, audio.encoding)
        assertEquals(16_000, audio.sampleRate)
        assertEquals(1, audio.channelCount)
        assertContentEquals(
            floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 32_767 / 32_768.0f),
            audio.samples,
        )
    }

    @Test
    fun `reads 16k mono IEEE float32 without modifying samples`() {
        val expected = floatArrayOf(-1.0f, -0.125f, 0.0f, 0.75f, 1.25f)

        val audio = WavReader.decode(float32Wav(expected))

        assertEquals(WavEncoding.IEEE_FLOAT_32, audio.encoding)
        assertContentEquals(expected, audio.samples)
    }

    @Test
    fun `rejects stereo and non-16k WAV files`() {
        assertFailsWith<WavFormatException> {
            WavReader.decode(pcm16Wav(shortArrayOf(0, 0), channels = 2))
        }
        assertFailsWith<WavFormatException> {
            WavReader.decode(pcm16Wav(shortArrayOf(0), sampleRate = 8_000))
        }
    }

    @Test
    fun `accepts an explicitly required 24k PCM16 source WAV`() {
        val expected = shortArrayOf(Short.MIN_VALUE, 0, Short.MAX_VALUE)

        val audio = WavReader.decode(
            pcm16Wav(expected, sampleRate = 24_000),
            source = "jvs-source.wav",
            requiredSampleRate = 24_000,
        )

        assertEquals(24_000, audio.sampleRate)
        assertEquals(WavEncoding.PCM_SIGNED_16, audio.encoding)
        assertContentEquals(
            floatArrayOf(-1.0f, 0.0f, 32_767 / 32_768.0f),
            audio.samples,
        )
    }

    @Test
    fun `rejects non-finite float samples and corrupt RIFF sizes`() {
        assertFailsWith<WavFormatException> {
            WavReader.decode(float32Wav(floatArrayOf(Float.NaN)))
        }

        val corrupt = pcm16Wav(shortArrayOf(0)).copyOf()
        corrupt[4] = 0
        assertFailsWith<WavFormatException> {
            WavReader.decode(corrupt)
        }
    }
}
