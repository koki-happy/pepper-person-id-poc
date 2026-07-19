package com.example.pepper_person_id_poc

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.speakercore.EmbeddingMath
import com.example.pepper_person_id_poc.speakercore.SpeakerCentroid
import com.example.pepper_person_id_poc.speakercore.SpeakerScorer
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
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
            enrollmentSpeakerId = "s22",
            enrollmentAsset = "lombard-s22-plain.wav",
            sameSpeakerAsset = "lombard-s22-lombard.wav",
            differentSpeakerAsset = "lombard-s16-plain.wav",
        )
    }

    @Test
    fun bothModels_benchmarkSherpaChineseSamples() {
        benchmarkDataset(
            dataset = "sherpa-Chinese",
            enrollmentSpeakerId = "fangjun",
            enrollmentAsset = "fangjun-sr-1.wav",
            sameSpeakerAsset = "fangjun-test-sr-1.wav",
            differentSpeakerAsset = "leijun-test-sr-1.wav",
        )
    }

    private fun benchmarkDataset(
        dataset: String,
        enrollmentSpeakerId: String,
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
                val enrollmentCentroid = SpeakerCentroid(
                    speakerId = enrollmentSpeakerId,
                    embedding = EmbeddingMath.centroid(listOf(enrollmentResult.value)),
                )
                val scorer = SpeakerScorer(SPEAKER_THRESHOLD, SPEAKER_MARGIN)
                val sameDecision = scorer.score(sameResult.value, listOf(enrollmentCentroid))
                val differentDecision = scorer.score(differentResult.value, listOf(enrollmentCentroid))
                val sameScore = requireNotNull(sameDecision.top1).score
                val differentScore = requireNotNull(differentDecision.top1).score
                val nativeHeapBytes = Debug.getNativeHeapAllocatedSize()

                Log.i(
                    TAG,
                    "dataset=$dataset model=${model.displayName} dimension=${enrollmentResult.value.size} " +
                        "initMillis=$initMillis enrollmentMillis=${enrollmentResult.millis} " +
                        "sameMillis=${sameResult.millis} differentMillis=${differentResult.millis} " +
                        "sameScore=$sameScore differentScore=$differentScore nativeHeapBytes=$nativeHeapBytes",
                )
                val modelSha256 = targetContext.assets
                    .open("models/${model.modelFileName}")
                    .use(::sha256)
                writeParityRecord(
                    outputDirectory = File(targetContext.filesDir, "benchmark-parity"),
                    dataset = dataset,
                    queryName = sameSpeakerAsset,
                    model = model,
                    modelSha256 = modelSha256,
                    query = sameSpeaker,
                    queryEmbedding = sameResult.value,
                    scoringResult = sameDecision,
                )
                writeParityRecord(
                    outputDirectory = File(targetContext.filesDir, "benchmark-parity"),
                    dataset = dataset,
                    queryName = differentSpeakerAsset,
                    model = model,
                    modelSha256 = modelSha256,
                    query = differentSpeaker,
                    queryEmbedding = differentResult.value,
                    scoringResult = differentDecision,
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
        return TestPcm(
            samples = requireNotNull(samples),
            sampleRate = sampleRate,
            wavSha256 = sha256(bytes.inputStream()),
        )
    }

    private fun writeParityRecord(
        outputDirectory: File,
        dataset: String,
        queryName: String,
        model: SpeakerModelOption,
        modelSha256: String,
        query: TestPcm,
        queryEmbedding: FloatArray,
        scoringResult: com.example.pepper_person_id_poc.speakercore.SpeakerScoringResult,
    ) {
        outputDirectory.mkdirs()
        val score = requireNotNull(scoringResult.top1).score
        val record = JSONObject()
            .put("modelId", model.configModelId)
            .put("modelName", model.displayName)
            .put("modelSha256", modelSha256)
            .put("queryWavSha256", query.wavSha256)
            .put("sampleRate", query.sampleRate)
            .put("numSamples", query.samples.size)
            .put("durationSec", query.samples.size.toDouble() / query.sampleRate)
            .put("embeddingDim", queryEmbedding.size)
            .put("embeddingNorm", l2Norm(queryEmbedding))
            .put("embeddingSha256", embeddingSha256(queryEmbedding))
            .put("embedding", JSONArray(queryEmbedding.map(Float::toDouble)))
            .put("speakerScores", JSONObject().put("enrollment", score.toDouble()))
            .put("threshold", SPEAKER_THRESHOLD.toDouble())
            .put("margin", SPEAKER_MARGIN.toDouble())
            .put("score", score.toDouble())
            .put("decision", scoringResult.decision.name)
            .put("predictedSpeakerId", scoringResult.identifiedSpeakerId ?: JSONObject.NULL)
            .put("isUnknown", scoringResult.identifiedSpeakerId == null)
            .put("dataset", dataset)
            .put("query", queryName)
        val safeName = "$dataset-${model.name}-$queryName"
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
        val output = File(outputDirectory, "$safeName.json")
        output.writeText(record.toString(2), Charsets.UTF_8)
        Log.i(TAG, "parityRecord=${output.absolutePath}")
    }

    private fun l2Norm(values: FloatArray): Double = sqrt(
        values.sumOf { value -> value.toDouble() * value.toDouble() },
    )

    private fun embeddingSha256(values: FloatArray): String {
        val bytes = ByteBuffer.allocate(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(bytes::putFloat)
        return sha256(bytes.array().inputStream())
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class Timed<T>(val value: T, val millis: Long)
    private data class TestPcm(
        val samples: ShortArray,
        val sampleRate: Int,
        val wavSha256: String,
    )

    private companion object {
        const val TAG = "SpeakerBenchmark"
        const val SPEAKER_THRESHOLD = 0.60f
        const val SPEAKER_MARGIN = 0.0f
    }
}
