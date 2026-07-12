package com.example.pepper_person_id_poc.domain.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavPcm16Decoder {
    fun decode(bytes: ByteArray): DecodedPcm16 {
        require(bytes.size >= 44 && bytes.asAscii(0, 4) == "RIFF" && bytes.asAscii(8, 4) == "WAVE") {
            "Not a RIFF/WAVE file"
        }
        var offset = 12
        var format: Format? = null
        var pcm: ShortArray? = null
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.asAscii(offset, 4)
            val chunkSize = bytes.littleEndianInt(offset + 4)
            require(chunkSize >= 0 && offset + 8L + chunkSize <= bytes.size) { "Invalid WAV chunk size" }
            val dataOffset = offset + 8
            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16)
                    format = Format(
                        audioFormat = bytes.littleEndianShort(dataOffset),
                        channels = bytes.littleEndianShort(dataOffset + 2),
                        sampleRate = bytes.littleEndianInt(dataOffset + 4),
                        bitsPerSample = bytes.littleEndianShort(dataOffset + 14),
                    )
                }
                "data" -> {
                    require(chunkSize % 2 == 0) { "PCM16 data size must be even" }
                    pcm = ShortArray(chunkSize / 2) { index ->
                        bytes.littleEndianShort(dataOffset + index * 2).toShort()
                    }
                }
            }
            offset = dataOffset + chunkSize + (chunkSize and 1)
        }
        val actualFormat = requireNotNull(format) { "WAV fmt chunk is missing" }
        require(actualFormat.audioFormat == 1) { "Only integer PCM WAV is supported" }
        require(actualFormat.channels == 1) { "Only mono WAV is supported" }
        require(actualFormat.bitsPerSample == 16) { "Only PCM 16-bit WAV is supported" }
        return DecodedPcm16(
            samples = requireNotNull(pcm) { "WAV data chunk is missing" },
            sampleRate = actualFormat.sampleRate,
        )
    }

    private fun ByteArray.asAscii(offset: Int, length: Int) =
        String(this, offset, length, Charsets.US_ASCII)

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun ByteArray.littleEndianShort(offset: Int): Int =
        ByteBuffer.wrap(this, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff

    private data class Format(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
    )
}

data class DecodedPcm16(
    val samples: ShortArray,
    val sampleRate: Int,
)
