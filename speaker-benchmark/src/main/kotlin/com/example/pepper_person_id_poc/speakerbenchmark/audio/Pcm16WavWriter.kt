package com.example.pepper_person_id_poc.speakerbenchmark.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

object Pcm16WavWriter {
    fun write(path: Path, samples: FloatArray, sampleRate: Int) {
        val absolutePath = path.toAbsolutePath().normalize()
        absolutePath.parent?.let(Files::createDirectories)
        Files.write(absolutePath, encode(samples, sampleRate))
    }

    internal fun encode(samples: FloatArray, sampleRate: Int): ByteArray {
        require(samples.isNotEmpty()) { "Cannot write an empty WAV file" }
        require(sampleRate > 0) { "WAV sample rate must be greater than zero" }
        require(samples.all(Float::isFinite)) { "WAV samples must all be finite" }
        require(samples.size <= (Int.MAX_VALUE - HEADER_SIZE) / BYTES_PER_SAMPLE) {
            "WAV contains too many samples: ${samples.size}"
        }

        val dataSize = samples.size * BYTES_PER_SAMPLE
        return ByteBuffer.allocate(HEADER_SIZE + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putAscii("RIFF")
                putInt(36 + dataSize)
                putAscii("WAVE")
                putAscii("fmt ")
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRate)
                putInt(sampleRate * BYTES_PER_SAMPLE)
                putShort(BYTES_PER_SAMPLE.toShort())
                putShort(BITS_PER_SAMPLE.toShort())
                putAscii("data")
                putInt(dataSize)
                samples.forEach { sample ->
                    val quantized = (sample.coerceIn(-1.0f, 1.0f) * 32_768.0f)
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    putShort(quantized.toShort())
                }
            }
            .array()
    }

    private fun ByteBuffer.putAscii(value: String) {
        put(value.toByteArray(Charsets.US_ASCII))
    }

    private const val HEADER_SIZE = 44
    private const val BYTES_PER_SAMPLE = 2
    private const val BITS_PER_SAMPLE = 16
}
