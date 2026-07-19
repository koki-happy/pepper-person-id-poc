package com.example.pepper_person_id_poc.speakerbenchmark.validation

import com.example.pepper_person_id_poc.speakerbenchmark.config.BenchmarkConfigLoader
import com.example.pepper_person_id_poc.speakerbenchmark.pcm16Wav
import com.example.pepper_person_id_poc.speakerbenchmark.float32Wav
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BenchmarkValidatorTest {
    @Test
    fun `validates model hash manifest and WAV metadata end to end`() {
        val directory = createTempDirectory("benchmark-validator-test")
        val modelPath = directory.resolve("model.onnx")
        Files.write(modelPath, "model".toByteArray())
        Files.write(directory.resolve("audio.wav"), pcm16Wav(ShortArray(320)))
        Files.writeString(
            directory.resolve("manifest.csv"),
            "speaker_id,utterance_id,source_group_id,split,path,language,sample_rate,duration_sec," +
                "recording_device,recording_type\n" +
                "spk1,utt1,group1,enrollment,audio.wav,ja,16000,0.02,Pepper,pepper_live\n",
        )
        val configPath = directory.resolve("config.json")
        Files.writeString(configPath, configJson(Sha256.digest(modelPath)))

        val validated = BenchmarkValidator.validate(BenchmarkConfigLoader.load(configPath))

        assertEquals(1, validated.summary.entryCount)
        assertEquals(mapOf("enrollment" to 1), validated.summary.splitCounts)
        assertEquals(mapOf("PCM_SIGNED_16" to 1), validated.summary.wavEncodingCounts)
        assertEquals(Sha256.digest(modelPath), validated.summary.models.single().sha256)
    }

    @Test
    fun `reports model hash and WAV duration errors together`() {
        val directory = createTempDirectory("benchmark-validator-test")
        val modelPath = directory.resolve("model.onnx")
        Files.write(modelPath, "model".toByteArray())
        Files.write(directory.resolve("audio.wav"), pcm16Wav(ShortArray(320)))
        Files.writeString(
            directory.resolve("manifest.csv"),
            "speaker_id,utterance_id,source_group_id,split,path,language,sample_rate,duration_sec," +
                "recording_device,recording_type\n" +
                "spk1,utt1,group1,enrollment,audio.wav,ja,16000,1.0,Pepper,pepper_live\n",
        )
        val configPath = directory.resolve("config.json")
        Files.writeString(configPath, configJson("0".repeat(64)))

        val exception = assertFailsWith<ValidationException> {
            BenchmarkValidator.validate(BenchmarkConfigLoader.load(configPath))
        }

        assertEquals(2, exception.errors.size)
    }

    @Test
    fun `rejects test speakers that are not enrolled and unknown speakers that are enrolled`() {
        val exception = validateRowsExpectingFailure(
            rows = listOf(
                row("known", "enroll", "enrollment"),
                row("not-enrolled", "test", "test"),
                row("known", "unknown", "unknown"),
            ),
        )

        assertTrue(exception.errors.any { "test split may contain only enrollment speakers" in it })
        assertTrue(exception.errors.any { "unknown split speakers must not overlap enrollment speakers" in it })
    }

    @Test
    fun `rejects unregistered development speakers reused as final unknown speakers`() {
        val exception = validateRowsExpectingFailure(
            rows = listOf(
                row("known", "enroll", "enrollment"),
                row("known", "test", "test"),
                row("held-out", "dev", "development"),
                row("held-out", "unknown", "unknown"),
            ),
        )

        assertTrue(exception.errors.any { "unregistered development speakers must not overlap final unknown" in it })
    }

    @Test
    fun `development threshold selection requires registered and unregistered development samples`() {
        val exception = validateRowsExpectingFailure(
            rows = listOf(
                row("known", "enroll", "enrollment"),
                row("known", "test", "test"),
                row("unknown", "final-unknown", "unknown"),
            ),
            thresholdSelection = "development",
        )

        assertTrue(exception.errors.any { "from an enrollment speaker" in it })
        assertTrue(exception.errors.any { "from an unregistered speaker" in it })
    }

    @Test
    fun `development threshold selection accepts speaker-disjoint registered and unknown development`() {
        val directory = createTempDirectory("benchmark-validator-semantics")
        val validated = validateRows(
            directory = directory,
            rows = listOf(
                row("known", "enroll", "enrollment"),
                row("known", "dev-known", "development"),
                row("dev-unknown", "dev-unknown", "development"),
                row("known", "test", "test"),
                row("final-unknown", "final-unknown", "unknown"),
            ),
            thresholdSelection = "development",
        )

        assertEquals(5, validated.summary.entryCount)
    }

    @Test
    fun `rejects one resolved WAV path used by multiple manifest rows`() {
        val directory = createTempDirectory("benchmark-validator-duplicate-path")
        val modelPath = directory.resolve("model.onnx")
        Files.write(modelPath, "model".toByteArray())
        Files.write(directory.resolve("shared.wav"), pcm16Wav(ShortArray(320) { 1 }))
        Files.writeString(
            directory.resolve("manifest.csv"),
            HEADER +
                "known,enroll,enroll-group,enrollment,shared.wav,ja,16000,0.02,Pepper,pepper_live\n" +
                "known,test,test-group,test,shared.wav,ja,16000,0.02,Pepper,pepper_live\n",
        )
        val configPath = directory.resolve("config.json")
        Files.writeString(configPath, configJson(Sha256.digest(modelPath)))

        val exception = assertFailsWith<ValidationException> {
            BenchmarkValidator.validate(BenchmarkConfigLoader.load(configPath))
        }

        assertTrue(exception.errors.any { "Duplicate audio resolved WAV path" in it })
    }

    @Test
    fun `rejects duplicate decoded audio content with different WAV encodings and headers`() {
        val directory = createTempDirectory("benchmark-validator-duplicate-content")
        val modelPath = directory.resolve("model.onnx")
        Files.write(modelPath, "model".toByteArray())
        val pcmSamples = ShortArray(320) { if (it % 2 == 0) 16_384 else -16_384 }
        Files.write(directory.resolve("first.wav"), pcm16Wav(pcmSamples))
        Files.write(
            directory.resolve("second.wav"),
            float32Wav(FloatArray(320) { if (it % 2 == 0) 0.5f else -0.5f }),
        )
        Files.writeString(
            directory.resolve("manifest.csv"),
            HEADER +
                "known,enroll,enroll-group,enrollment,first.wav,ja,16000,0.02,Pepper,pepper_live\n" +
                "known,test,test-group,test,second.wav,ja,16000,0.02,Pepper,pepper_live\n",
        )
        val configPath = directory.resolve("config.json")
        Files.writeString(configPath, configJson(Sha256.digest(modelPath)))

        val exception = assertFailsWith<ValidationException> {
            BenchmarkValidator.validate(BenchmarkConfigLoader.load(configPath))
        }

        assertTrue(exception.errors.any { "Duplicate audio WAV content SHA-256" in it })
    }

    @Test
    fun `full profile rejects insufficient non-Japanese and ungrouped evaluation data`() {
        val directory = createTempDirectory("benchmark-validator-full-profile")
        val modelPath = directory.resolve("model.onnx")
        Files.write(modelPath, "model".toByteArray())
        val rows = listOf(
            row("known", "enroll", "enrollment").replace(",ja,", ",zh,"),
            row("known", "test", "test").replace(",0.02,", ",4.0,"),
            row("unknown", "unknown", "unknown").replace(",0.02,", ",4.0,"),
        )
        rows.forEachIndexed { index, row ->
            val fields = row.split(',')
            val duration = fields[7].toDouble()
            Files.write(
                directory.resolve(fields[4]),
                pcm16Wav(ShortArray((duration * 16_000).toInt()) { (index + 1).toShort() }),
            )
        }
        Files.writeString(directory.resolve("manifest.csv"), HEADER + rows.joinToString("\n", postfix = "\n"))
        val configPath = directory.resolve("config.json")
        Files.writeString(
            configPath,
            configJson(Sha256.digest(modelPath), validationProfile = "full"),
        )

        val exception = assertFailsWith<ValidationException> {
            BenchmarkValidator.validate(BenchmarkConfigLoader.load(configPath))
        }

        assertTrue(exception.errors.any { "at least 4 enrolled" in it })
        assertTrue(exception.errors.any { "requires all speech to use language 'ja'" in it })
        assertTrue(exception.errors.any { "does not match a configured duration" in it })
        assertTrue(exception.errors.any { "at least 5 enrollment, 5 development, and 10 test" in it })
        assertTrue(exception.errors.any { "at least 10 final-unknown clips" in it })
    }

    private fun validateRowsExpectingFailure(
        rows: List<String>,
        thresholdSelection: String = "fixed",
    ): ValidationException {
        val directory = createTempDirectory("benchmark-validator-semantics")
        return assertFailsWith { validateRows(directory, rows, thresholdSelection) }
    }

    private fun validateRows(
        directory: java.nio.file.Path,
        rows: List<String>,
        thresholdSelection: String,
    ): ValidatedBenchmark {
        val modelPath = directory.resolve("model.onnx")
        Files.write(modelPath, "model".toByteArray())
        rows.forEachIndexed { index, row ->
            val fields = row.split(',')
            Files.write(directory.resolve(fields[4]), pcm16Wav(ShortArray(320) { (index + 1).toShort() }))
        }
        Files.writeString(
            directory.resolve("manifest.csv"),
            "speaker_id,utterance_id,source_group_id,split,path,language,sample_rate,duration_sec," +
                "recording_device,recording_type\n" + rows.joinToString("\n", postfix = "\n"),
        )
        val configPath = directory.resolve("config.json")
        Files.writeString(configPath, configJson(Sha256.digest(modelPath), thresholdSelection))
        return BenchmarkValidator.validate(BenchmarkConfigLoader.load(configPath))
    }

    private fun row(speakerId: String, utteranceId: String, split: String): String =
        "$speakerId,$utteranceId,group-$utteranceId,$split,$utteranceId.wav,ja,16000,0.02,Pepper,pepper_live"

    private fun configJson(
        sha256: String,
        thresholdSelection: String = "fixed",
        validationProfile: String = "smoke",
    ): String = """
        {
          "manifestPath": "manifest.csv",
          "outputDirectory": "results",
          "models": [{
            "name": "model",
            "adapter": "camp-plus",
            "modelPath": "model.onnx",
            "expectedSha256": "$sha256",
            "threshold": 0.6,
            "margin": 0.1
          }],
          "thresholdSelection": "$thresholdSelection",
          "validationProfile": "$validationProfile"
        }
    """.trimIndent()

    private companion object {
        const val HEADER =
            "speaker_id,utterance_id,source_group_id,split,path,language,sample_rate,duration_sec," +
                "recording_device,recording_type\n"
    }
}
