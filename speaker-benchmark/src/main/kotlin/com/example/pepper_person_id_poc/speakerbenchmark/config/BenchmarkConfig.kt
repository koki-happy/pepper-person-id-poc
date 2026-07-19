package com.example.pepper_person_id_poc.speakerbenchmark.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

@Serializable
data class BenchmarkConfig(
    val manifestPath: String,
    val outputDirectory: String,
    val models: List<ModelConfig>,
    val durationsSeconds: List<Int> = listOf(2, 3, 5),
    val warmupRuns: Int = 1,
    val measuredRuns: Int = 5,
    val vadEnabled: Boolean = false,
    val thresholdSelection: ThresholdSelectionStrategy = ThresholdSelectionStrategy.FIXED,
    val validationProfile: ValidationProfile = ValidationProfile.FULL,
)

@Serializable
enum class ValidationProfile {
    @SerialName("smoke")
    SMOKE,

    @SerialName("full")
    FULL,
}

@Serializable
enum class ThresholdSelectionStrategy {
    @SerialName("fixed")
    FIXED,

    @SerialName("development")
    DEVELOPMENT,
}

@Serializable
data class ModelConfig(
    val name: String,
    val adapter: String,
    val modelPath: String,
    val expectedSha256: String,
    val sampleRate: Int = 16_000,
    val embeddingDimension: Int? = null,
    val threshold: Double,
    val margin: Double,
)

data class ResolvedBenchmarkConfig(
    val sourcePath: Path,
    val manifestPath: Path,
    val outputDirectory: Path,
    val models: List<ResolvedModelConfig>,
    val durationsSeconds: List<Int>,
    val warmupRuns: Int,
    val measuredRuns: Int,
    val vadEnabled: Boolean,
    val thresholdSelection: ThresholdSelectionStrategy = ThresholdSelectionStrategy.FIXED,
    val validationProfile: ValidationProfile = ValidationProfile.FULL,
)

data class ResolvedModelConfig(
    val name: String,
    val adapter: String,
    val modelPath: Path,
    val expectedSha256: String,
    val sampleRate: Int,
    val embeddingDimension: Int?,
    val threshold: Double,
    val margin: Double,
)

class ConfigException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

object BenchmarkConfigLoader {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun load(path: Path): ResolvedBenchmarkConfig {
        val absolutePath = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absolutePath)) {
            throw ConfigException("Config file does not exist or is not a regular file: $absolutePath")
        }

        val text = try {
            Files.readString(absolutePath, StandardCharsets.UTF_8)
        } catch (exception: MalformedInputException) {
            throw ConfigException("Config must be valid UTF-8: $absolutePath", exception)
        }

        val config = try {
            json.decodeFromString<BenchmarkConfig>(text)
        } catch (exception: SerializationException) {
            throw ConfigException("Invalid benchmark config $absolutePath: ${exception.message}", exception)
        }
        validate(config)

        val baseDirectory = absolutePath.parent ?: Path.of(".").toAbsolutePath().normalize()
        return ResolvedBenchmarkConfig(
            sourcePath = absolutePath,
            manifestPath = resolve(baseDirectory, config.manifestPath, "manifestPath"),
            outputDirectory = resolve(baseDirectory, config.outputDirectory, "outputDirectory"),
            models = config.models.mapIndexed { index, model ->
                ResolvedModelConfig(
                    name = model.name,
                    adapter = model.adapter,
                    modelPath = resolve(baseDirectory, model.modelPath, "models[$index].modelPath"),
                    expectedSha256 = model.expectedSha256.lowercase(),
                    sampleRate = model.sampleRate,
                    embeddingDimension = model.embeddingDimension,
                    threshold = model.threshold,
                    margin = model.margin,
                )
            },
            durationsSeconds = config.durationsSeconds,
            warmupRuns = config.warmupRuns,
            measuredRuns = config.measuredRuns,
            vadEnabled = config.vadEnabled,
            thresholdSelection = config.thresholdSelection,
            validationProfile = config.validationProfile,
        )
    }

    private fun validate(config: BenchmarkConfig) {
        requireNonBlank("manifestPath", config.manifestPath)
        requireNonBlank("outputDirectory", config.outputDirectory)
        if (config.models.isEmpty()) {
            throw ConfigException("models must contain at least one model")
        }
        val duplicateNames = config.models.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            throw ConfigException("Model names must be unique; duplicates: ${duplicateNames.sorted().joinToString()}")
        }
        if (config.durationsSeconds.isEmpty() || config.durationsSeconds.any { it <= 0 }) {
            throw ConfigException("durationsSeconds must contain positive whole seconds")
        }
        if (config.durationsSeconds.distinct().size != config.durationsSeconds.size) {
            throw ConfigException("durationsSeconds must not contain duplicates")
        }
        if (config.warmupRuns < 0) {
            throw ConfigException("warmupRuns must be zero or greater")
        }
        if (config.measuredRuns <= 0) {
            throw ConfigException("measuredRuns must be greater than zero")
        }
        if (config.vadEnabled) {
            throw ConfigException("vadEnabled must be false for the initial fixed-WAV model comparison")
        }

        config.models.forEachIndexed { index, model ->
            val prefix = "models[$index]"
            requireNonBlank("$prefix.name", model.name)
            requireNonBlank("$prefix.adapter", model.adapter)
            requireNonBlank("$prefix.modelPath", model.modelPath)
            if (!SHA256_REGEX.matches(model.expectedSha256)) {
                throw ConfigException("$prefix.expectedSha256 must be exactly 64 hexadecimal characters")
            }
            if (model.sampleRate != REQUIRED_SAMPLE_RATE) {
                throw ConfigException("$prefix.sampleRate must be $REQUIRED_SAMPLE_RATE")
            }
            if (model.embeddingDimension != null && model.embeddingDimension <= 0) {
                throw ConfigException("$prefix.embeddingDimension must be greater than zero when supplied")
            }
            if (!model.threshold.isFinite() || model.threshold !in -1.0..1.0) {
                throw ConfigException("$prefix.threshold must be finite and between -1.0 and 1.0")
            }
            if (!model.margin.isFinite() || model.margin !in 0.0..2.0) {
                throw ConfigException("$prefix.margin must be finite and between 0.0 and 2.0")
            }
        }
    }

    private fun requireNonBlank(field: String, value: String) {
        if (value.isBlank()) {
            throw ConfigException("$field must not be blank")
        }
        if (value != value.trim()) {
            throw ConfigException("$field must not have leading or trailing whitespace")
        }
    }

    private fun resolve(baseDirectory: Path, configuredPath: String, field: String): Path {
        return try {
            val path = Path.of(configuredPath)
            (if (path.isAbsolute) path else baseDirectory.resolve(path)).toAbsolutePath().normalize()
        } catch (exception: InvalidPathException) {
            throw ConfigException("$field is not a valid path: ${exception.message}", exception)
        }
    }

    private const val REQUIRED_SAMPLE_RATE = 16_000
    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
}
