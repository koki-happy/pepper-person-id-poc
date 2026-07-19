package com.example.pepper_person_id_poc.speakerbenchmark.config

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BenchmarkConfigLoaderTest {
    @Test
    fun `loads strict JSON and resolves paths relative to config`() {
        val directory = createTempDirectory("benchmark-config-test")
        val configDirectory = Files.createDirectories(directory.resolve("config"))
        val configPath = configDirectory.resolve("benchmark.json")
        Files.writeString(configPath, validConfig())

        val config = BenchmarkConfigLoader.load(configPath)

        assertEquals(directory.resolve("manifest.csv").toAbsolutePath(), config.manifestPath)
        assertEquals(directory.resolve("results").toAbsolutePath(), config.outputDirectory)
        assertEquals(directory.resolve("models/model.onnx").toAbsolutePath(), config.models.single().modelPath)
        assertEquals(listOf(2, 3, 5), config.durationsSeconds)
        assertEquals(ValidationProfile.SMOKE, config.validationProfile)
        assertFalse(config.vadEnabled)
    }

    @Test
    fun `rejects unknown JSON keys`() {
        val directory = createTempDirectory("benchmark-config-test")
        val configPath = directory.resolve("benchmark.json")
        Files.writeString(configPath, validConfig().replace("\"vadEnabled\": false", "\"vadEnabled\": false, \"typo\": true"))

        assertFailsWith<ConfigException> {
            BenchmarkConfigLoader.load(configPath)
        }
    }

    @Test
    fun `rejects VAD and invalid SHA values`() {
        val directory = createTempDirectory("benchmark-config-test")
        val vadConfig = directory.resolve("vad.json")
        Files.writeString(vadConfig, validConfig().replace("\"vadEnabled\": false", "\"vadEnabled\": true"))
        val shaConfig = directory.resolve("sha.json")
        Files.writeString(shaConfig, validConfig().replace("0".repeat(64), "not-a-sha"))

        assertFailsWith<ConfigException> { BenchmarkConfigLoader.load(vadConfig) }
        assertFailsWith<ConfigException> { BenchmarkConfigLoader.load(shaConfig) }
    }

    private fun validConfig(): String = """
        {
          "manifestPath": "../manifest.csv",
          "outputDirectory": "../results",
          "models": [
            {
              "name": "campplus",
              "adapter": "camp-plus",
              "modelPath": "../models/model.onnx",
              "expectedSha256": "${"0".repeat(64)}",
              "sampleRate": 16000,
              "embeddingDimension": 192,
              "threshold": 0.6,
              "margin": 0.1
            }
          ],
          "durationsSeconds": [2, 3, 5],
          "warmupRuns": 1,
          "measuredRuns": 3,
          "vadEnabled": false,
          "validationProfile": "smoke"
        }
    """.trimIndent()
}
