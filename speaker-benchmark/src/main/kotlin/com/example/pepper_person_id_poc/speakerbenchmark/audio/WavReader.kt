package com.example.pepper_person_id_poc.speakerbenchmark.audio

import java.nio.file.Files
import java.nio.file.Path

enum class WavEncoding {
    PCM_SIGNED_16,
    IEEE_FLOAT_32,
}

data class WavAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val encoding: WavEncoding,
) {
    val channelCount: Int = 1
    val durationSeconds: Double = samples.size.toDouble() / sampleRate
}

class WavFormatException(message: String) : IllegalArgumentException(message)

object WavReader {
    fun read(path: Path, requiredSampleRate: Int = DEFAULT_SAMPLE_RATE): WavAudio {
        val absolutePath = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absolutePath)) {
            throw WavFormatException("WAV file does not exist or is not a regular file: $absolutePath")
        }
        val bytes = Files.readAllBytes(absolutePath)
        return decode(bytes, absolutePath.toString(), requiredSampleRate)
    }

    internal fun decode(
        bytes: ByteArray,
        source: String = "<memory>",
        requiredSampleRate: Int = DEFAULT_SAMPLE_RATE,
    ): WavAudio {
        if (requiredSampleRate <= 0) {
            throw WavFormatException("Required WAV sample rate must be greater than zero: $requiredSampleRate")
        }
        if (bytes.size < RIFF_HEADER_SIZE) {
            throw WavFormatException("WAV is shorter than the 12-byte RIFF header: $source")
        }
        if (ascii(bytes, 0, 4) != "RIFF") {
            throw WavFormatException("WAV must use little-endian RIFF (missing RIFF signature): $source")
        }
        if (ascii(bytes, 8, 4) != "WAVE") {
            throw WavFormatException("RIFF file is not WAVE: $source")
        }
        val declaredFileSize = readUInt32(bytes, 4) + 8L
        if (declaredFileSize != bytes.size.toLong()) {
            throw WavFormatException(
                "RIFF size mismatch in $source: header declares $declaredFileSize bytes, actual size is ${bytes.size}",
            )
        }

        var format: FormatChunk? = null
        var audioBytes: ByteArray? = null
        var offset = RIFF_HEADER_SIZE
        while (offset < bytes.size) {
            if (bytes.size - offset < CHUNK_HEADER_SIZE) {
                throw WavFormatException("Truncated WAV chunk header at byte $offset in $source")
            }
            val chunkId = ascii(bytes, offset, 4)
            val chunkSizeLong = readUInt32(bytes, offset + 4)
            if (chunkSizeLong > Int.MAX_VALUE) {
                throw WavFormatException("WAV chunk '$chunkId' is too large to read in $source")
            }
            val chunkSize = chunkSizeLong.toInt()
            val contentStart = offset + CHUNK_HEADER_SIZE
            val contentEndLong = contentStart.toLong() + chunkSizeLong
            if (contentEndLong > bytes.size) {
                throw WavFormatException("Truncated WAV chunk '$chunkId' in $source")
            }
            val contentEnd = contentEndLong.toInt()

            when (chunkId) {
                "fmt " -> {
                    if (format != null) throw WavFormatException("WAV contains multiple fmt chunks: $source")
                    format = parseFormatChunk(bytes, contentStart, chunkSize, source, requiredSampleRate)
                }

                "data" -> {
                    if (audioBytes != null) throw WavFormatException("WAV contains multiple data chunks: $source")
                    audioBytes = bytes.copyOfRange(contentStart, contentEnd)
                }
            }

            val paddedEnd = contentEndLong + (chunkSize and 1)
            if (paddedEnd > bytes.size) {
                throw WavFormatException("Missing pad byte after odd-sized WAV chunk '$chunkId' in $source")
            }
            offset = paddedEnd.toInt()
        }

        val requiredFormat = format ?: throw WavFormatException("WAV is missing a fmt chunk: $source")
        val requiredAudioBytes = audioBytes ?: throw WavFormatException("WAV is missing a data chunk: $source")
        if (requiredAudioBytes.isEmpty()) {
            throw WavFormatException("WAV data chunk is empty: $source")
        }
        if (requiredAudioBytes.size % requiredFormat.blockAlign != 0) {
            throw WavFormatException("WAV data size is not aligned to complete samples: $source")
        }

        val samples = when (requiredFormat.encoding) {
            WavEncoding.PCM_SIGNED_16 -> decodePcm16(requiredAudioBytes)
            WavEncoding.IEEE_FLOAT_32 -> decodeFloat32(requiredAudioBytes, source)
        }
        return WavAudio(samples, requiredFormat.sampleRate, requiredFormat.encoding)
    }

    private fun parseFormatChunk(
        bytes: ByteArray,
        offset: Int,
        size: Int,
        source: String,
        requiredSampleRate: Int,
    ): FormatChunk {
        if (size < PCM_FORMAT_CHUNK_SIZE) {
            throw WavFormatException("WAV fmt chunk must contain at least 16 bytes: $source")
        }
        val formatCode = readUInt16(bytes, offset)
        val channelCount = readUInt16(bytes, offset + 2)
        val sampleRateLong = readUInt32(bytes, offset + 4)
        val byteRateLong = readUInt32(bytes, offset + 8)
        val blockAlign = readUInt16(bytes, offset + 12)
        val bitsPerSample = readUInt16(bytes, offset + 14)

        if (channelCount != REQUIRED_CHANNEL_COUNT) {
            throw WavFormatException("WAV must be mono; found $channelCount channels in $source")
        }
        if (sampleRateLong != requiredSampleRate.toLong()) {
            throw WavFormatException("WAV sample rate must be $requiredSampleRate Hz; found $sampleRateLong in $source")
        }

        val encoding = when {
            formatCode == PCM_FORMAT_CODE && bitsPerSample == PCM_BITS_PER_SAMPLE -> WavEncoding.PCM_SIGNED_16
            formatCode == IEEE_FLOAT_FORMAT_CODE && bitsPerSample == FLOAT_BITS_PER_SAMPLE -> WavEncoding.IEEE_FLOAT_32
            else -> throw WavFormatException(
                "Unsupported WAV encoding in $source: format code $formatCode, $bitsPerSample bits; " +
                    "expected PCM16 or IEEE float32",
            )
        }
        val expectedBlockAlign = channelCount * (bitsPerSample / 8)
        if (blockAlign != expectedBlockAlign) {
            throw WavFormatException(
                "Invalid WAV blockAlign in $source: expected $expectedBlockAlign, found $blockAlign",
            )
        }
        val expectedByteRate = sampleRateLong * expectedBlockAlign
        if (byteRateLong != expectedByteRate) {
            throw WavFormatException(
                "Invalid WAV byteRate in $source: expected $expectedByteRate, found $byteRateLong",
            )
        }
        return FormatChunk(encoding, sampleRateLong.toInt(), blockAlign)
    }

    private fun decodePcm16(bytes: ByteArray): FloatArray = FloatArray(bytes.size / 2) { sampleIndex ->
        val offset = sampleIndex * 2
        val unsigned = (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
        val signed = if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
        signed / 32768.0f
    }

    private fun decodeFloat32(bytes: ByteArray, source: String): FloatArray = FloatArray(bytes.size / 4) { sampleIndex ->
        val offset = sampleIndex * 4
        val bits = (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
        val sample = Float.fromBits(bits)
        if (!sample.isFinite()) {
            throw WavFormatException("WAV contains a non-finite float sample at index $sampleIndex in $source")
        }
        sample
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24))

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String =
        bytes.copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

    private data class FormatChunk(
        val encoding: WavEncoding,
        val sampleRate: Int,
        val blockAlign: Int,
    )

    private const val RIFF_HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8
    private const val PCM_FORMAT_CHUNK_SIZE = 16
    private const val PCM_FORMAT_CODE = 1
    private const val IEEE_FLOAT_FORMAT_CODE = 3
    private const val PCM_BITS_PER_SAMPLE = 16
    private const val FLOAT_BITS_PER_SAMPLE = 32
    private const val REQUIRED_CHANNEL_COUNT = 1
    private const val DEFAULT_SAMPLE_RATE = 16_000
}
