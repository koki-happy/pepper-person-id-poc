package com.example.pepper_person_id_poc.domain.audio

import java.io.ByteArrayOutputStream

object WavEncoder {
    fun encodeMonoPcm16(pcm16: ShortArray, sampleRate: Int): ByteArray {
        require(sampleRate > 0)
        val dataSize = pcm16.size * BYTES_PER_SAMPLE
        return ByteArrayOutputStream(HEADER_SIZE + dataSize).apply {
            writeAscii("RIFF")
            writeLittleEndianInt(36 + dataSize)
            writeAscii("WAVE")
            writeAscii("fmt ")
            writeLittleEndianInt(16)
            writeLittleEndianShort(1)
            writeLittleEndianShort(1)
            writeLittleEndianInt(sampleRate)
            writeLittleEndianInt(sampleRate * BYTES_PER_SAMPLE)
            writeLittleEndianShort(BYTES_PER_SAMPLE)
            writeLittleEndianShort(16)
            writeAscii("data")
            writeLittleEndianInt(dataSize)
            pcm16.forEach { writeLittleEndianShort(it.toInt()) }
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))

    private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
        repeat(4) { byteIndex -> write(value ushr (byteIndex * 8) and 0xFF) }
    }

    private fun ByteArrayOutputStream.writeLittleEndianShort(value: Int) {
        repeat(2) { byteIndex -> write(value ushr (byteIndex * 8) and 0xFF) }
    }

    private const val BYTES_PER_SAMPLE = 2
    private const val HEADER_SIZE = 44
}
