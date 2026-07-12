package com.example.pepper_person_id_poc.domain.audio

import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WavEncoderTest {
    @Test
    fun encodeMonoPcm16_writesValidHeaderAndLittleEndianSamples() {
        val bytes = WavEncoder.encodeMonoPcm16(shortArrayOf(0x1234, -2), sampleRate = 16_000)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertThat(String(bytes, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(bytes, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(buffer.getInt(4)).isEqualTo(bytes.size - 8)
        assertThat(buffer.getShort(22).toInt()).isEqualTo(1)
        assertThat(buffer.getInt(24)).isEqualTo(16_000)
        assertThat(buffer.getInt(40)).isEqualTo(4)
        assertThat(buffer.getShort(44).toInt()).isEqualTo(0x1234)
        assertThat(buffer.getShort(46).toInt()).isEqualTo(-2)
    }

    @Test
    fun encoderOutput_decodesWithoutChangingSamples() {
        val original = shortArrayOf(Short.MIN_VALUE, -2, 0, 2, Short.MAX_VALUE)

        val decoded = WavPcm16Decoder.decode(WavEncoder.encodeMonoPcm16(original, 16_000))

        assertThat(decoded.sampleRate).isEqualTo(16_000)
        assertThat(decoded.samples.toList()).containsExactlyElementsIn(original.toList()).inOrder()
    }
}
