package com.example.pepper_person_id_poc.speakerbenchmark

import java.io.ByteArrayOutputStream

internal fun pcm16Wav(
    samples: ShortArray,
    sampleRate: Int = 16_000,
    channels: Int = 1,
): ByteArray {
    val data = ByteArrayOutputStream().apply {
        samples.forEach { sample -> writeUInt16(sample.toInt() and 0xffff) }
    }.toByteArray()
    return wav(formatCode = 1, bitsPerSample = 16, sampleRate = sampleRate, channels = channels, data = data)
}

internal fun float32Wav(
    samples: FloatArray,
    sampleRate: Int = 16_000,
    channels: Int = 1,
): ByteArray {
    val data = ByteArrayOutputStream().apply {
        samples.forEach { sample -> writeUInt32(sample.toBits().toLong() and 0xffffffffL) }
    }.toByteArray()
    return wav(formatCode = 3, bitsPerSample = 32, sampleRate = sampleRate, channels = channels, data = data)
}

private fun wav(
    formatCode: Int,
    bitsPerSample: Int,
    sampleRate: Int,
    channels: Int,
    data: ByteArray,
): ByteArray {
    val blockAlign = channels * bitsPerSample / 8
    val padSize = data.size and 1
    val riffSize = 4 + (8 + 16) + (8 + data.size + padSize)
    return ByteArrayOutputStream().apply {
        write("RIFF".toByteArray(Charsets.US_ASCII))
        writeUInt32(riffSize.toLong())
        write("WAVE".toByteArray(Charsets.US_ASCII))
        write("fmt ".toByteArray(Charsets.US_ASCII))
        writeUInt32(16)
        writeUInt16(formatCode)
        writeUInt16(channels)
        writeUInt32(sampleRate.toLong())
        writeUInt32(sampleRate.toLong() * blockAlign)
        writeUInt16(blockAlign)
        writeUInt16(bitsPerSample)
        write("data".toByteArray(Charsets.US_ASCII))
        writeUInt32(data.size.toLong())
        write(data)
        if (padSize != 0) write(0)
    }.toByteArray()
}

private fun ByteArrayOutputStream.writeUInt16(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}

private fun ByteArrayOutputStream.writeUInt32(value: Long) {
    write((value and 0xff).toInt())
    write(((value ushr 8) and 0xff).toInt())
    write(((value ushr 16) and 0xff).toInt())
    write(((value ushr 24) and 0xff).toInt())
}
