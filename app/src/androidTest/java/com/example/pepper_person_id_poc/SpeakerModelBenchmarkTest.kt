package com.example.pepper_person_id_poc

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeakerModelBenchmarkTest {
    @Test
    fun bothModels_benchmarkLombardGridEnglishSamples() {
        benchmarkDataset(
            dataset = "LombardGRID-English",
            enrollmentAsset = "lombard-s22-plain.wav",
            sameSpeakerAsset = "lombard-s22-lombard.wav",
            differentSpeakerAsset = "lombard-s16-plain.wav",
        )
    }

    @Test
    fun bothModels_benchmarkSherpaChineseSamples() {
        benchmarkDataset(
            dataset = "sherpa-Chinese",
            enrollmentAsset = "fangjun-sr-1.wav",
            sameSpeakerAsset = "fangjun-test-sr-1.wav",
            differentSpeakerAsset = "leijun-test-sr-1.wav",
        )
    }

    private fun benchmarkDataset(
        dataset: String,
        enrollmentAsset: String,
        sameSpeakerAsset: String,
        differentSpeakerAsset: String,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val testAssets = instrumentation.context.assets
        val enrollment = decodeWav(testAssets.open("speaker-test/$enrollmentAsset").use { it.readBytes() })
        val sameSpeaker = decodeWav(testAssets.open("speaker-test/$sameSpeakerAsset").use { it.readBytes() })
        val differentSpeaker = decodeWav(testAssets.open("speaker-test/$differentSpeakerAsset").use { it.readBytes() })
        assertEquals(16_000, enrollment.sampleRate)
        assertEquals(16_000, sameSpeaker.sampleRate)
        assertEquals(16_000, differentSpeaker.sampleRate)

        SpeakerModelOption.entries.forEach { model ->
            val engine = SherpaOnnxSpeakerEmbeddingEngine(targetContext, model, numThreads = 1)
            try {
                val initStarted = SystemClock.elapsedRealtime()
                engine.prepare()
                val initMillis = SystemClock.elapsedRealtime() - initStarted
                val enrollmentResult = timed { engine.extract(enrollment.samples, enrollment.sampleRate) }
                val sameResult = timed { engine.extract(sameSpeaker.samples, sameSpeaker.sampleRate) }
                val differentResult = timed { engine.extract(differentSpeaker.samples, differentSpeaker.sampleRate) }
                val sameScore = cosine(enrollmentResult.value, sameResult.value)
                val differentScore = cosine(enrollmentResult.value, differentResult.value)
                val nativeHeapBytes = Debug.getNativeHeapAllocatedSize()

                Log.i(
                    TAG,
                    "dataset=$dataset model=${model.displayName} dimension=${enrollmentResult.value.size} " +
                        "initMillis=$initMillis enrollmentMillis=${enrollmentResult.millis} " +
                        "sameMillis=${sameResult.millis} differentMillis=${differentResult.millis} " +
                        "sameScore=$sameScore differentScore=$differentScore nativeHeapBytes=$nativeHeapBytes",
                )
                assertTrue(enrollmentResult.value.isNotEmpty())
                assertTrue(enrollmentResult.value.all(Float::isFinite))
                assertTrue(sameScore.isFinite())
                assertTrue(differentScore.isFinite())
                if (model == SpeakerModelOption.ERES2NET) {
                    assertTrue(
                        "ERes2Net same speaker must be accepted at threshold $SPEAKER_THRESHOLD: $sameScore",
                        sameScore >= SPEAKER_THRESHOLD,
                    )
                    assertTrue(
                        "ERes2Net different speaker must be rejected at threshold $SPEAKER_THRESHOLD: $differentScore",
                        differentScore < SPEAKER_THRESHOLD,
                    )
                    assertTrue("ERes2Net same-speaker score must exceed different-speaker score", sameScore > differentScore)
                }
            } finally {
                engine.close()
            }
        }
    }

    private fun <T> timed(block: () -> T): Timed<T> {
        val started = SystemClock.elapsedRealtime()
        return Timed(block(), SystemClock.elapsedRealtime() - started)
    }

    private fun decodeWav(bytes: ByteArray): TestPcm {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        var offset = 12
        var sampleRate = 0
        var samples: ShortArray? = null
        var audioFormat = 0
        var bitsPerSample = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = buffer.getInt(offset + 4)
            if (id == "fmt ") {
                audioFormat = buffer.getShort(offset + 8).toInt()
                require(buffer.getShort(offset + 10).toInt() == 1)
                sampleRate = buffer.getInt(offset + 12)
                bitsPerSample = buffer.getShort(offset + 22).toInt()
            } else if (id == "data") {
                samples = when {
                    audioFormat == 1 && bitsPerSample == 16 ->
                        ShortArray(size / 2) { index -> buffer.getShort(offset + 8 + index * 2) }
                    audioFormat == 3 && bitsPerSample == 32 ->
                        ShortArray(size / 4) { index ->
                            (buffer.getFloat(offset + 8 + index * 4).coerceIn(-1f, 1f) * 32_767f).toInt().toShort()
                        }
                    else -> error("Unsupported WAV format=$audioFormat bits=$bitsPerSample")
                }
            }
            offset += 8 + size + (size and 1)
        }
        return TestPcm(requireNotNull(samples), sampleRate)
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
    }

    private data class Timed<T>(val value: T, val millis: Long)
    private data class TestPcm(val samples: ShortArray, val sampleRate: Int)

    private companion object {
        const val TAG = "SpeakerBenchmark"
        const val SPEAKER_THRESHOLD = 0.60f
    }
}
