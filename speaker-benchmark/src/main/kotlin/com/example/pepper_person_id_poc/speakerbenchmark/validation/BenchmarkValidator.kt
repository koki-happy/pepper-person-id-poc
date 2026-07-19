package com.example.pepper_person_id_poc.speakerbenchmark.validation

import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavReader
import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavAudio
import com.example.pepper_person_id_poc.speakerbenchmark.config.ResolvedBenchmarkConfig
import com.example.pepper_person_id_poc.speakerbenchmark.config.ThresholdSelectionStrategy
import com.example.pepper_person_id_poc.speakerbenchmark.config.ValidationProfile
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetEntry
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetManifest
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetSplit
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetManifestParser
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import com.example.pepper_person_id_poc.speakerbenchmark.matchConfiguredDuration
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@Serializable
data class ValidatedModelArtifact(
    val name: String,
    val path: String,
    val sha256: String,
    val fileSizeBytes: Long,
)

@Serializable
data class ValidationSummary(
    val configPath: String,
    val manifestPath: String,
    val manifestSha256: String,
    val entryCount: Int,
    val splitCounts: Map<String, Int>,
    val totalDurationSeconds: Double,
    val wavEncodingCounts: Map<String, Int>,
    val models: List<ValidatedModelArtifact>,
)

data class ValidatedBenchmark(
    val config: ResolvedBenchmarkConfig,
    val manifest: DatasetManifest,
    val summary: ValidationSummary,
)

class ValidationException(val errors: List<String>) : IllegalArgumentException(
    buildString {
        append("Benchmark validation failed with ${errors.size} error")
        if (errors.size != 1) append('s')
        append(':')
        errors.forEach { append("\n- ").append(it) }
    },
)

object BenchmarkValidator {
    fun validate(config: ResolvedBenchmarkConfig): ValidatedBenchmark {
        val errors = mutableListOf<String>()
        val manifest = try {
            DatasetManifestParser.parse(config.manifestPath)
        } catch (exception: Exception) {
            throw ValidationException(listOf(exception.message ?: exception::class.simpleName.orEmpty()))
        }

        if (Files.exists(config.outputDirectory) && !Files.isDirectory(config.outputDirectory)) {
            errors += "outputDirectory exists but is not a directory: ${config.outputDirectory}"
        }

        validateManifestSemantics(config, manifest, errors)

        val validatedModels = config.models.mapNotNull { model ->
            if (!Files.isRegularFile(model.modelPath)) {
                errors += "Model '${model.name}' does not exist or is not a regular file: ${model.modelPath}"
                null
            } else {
                try {
                    val actualSha256 = Sha256.digest(model.modelPath)
                    if (!actualSha256.equals(model.expectedSha256, ignoreCase = true)) {
                        errors += "Model '${model.name}' SHA-256 mismatch: expected ${model.expectedSha256}, " +
                            "actual $actualSha256"
                    }
                    ValidatedModelArtifact(
                        name = model.name,
                        path = model.modelPath.toString(),
                        sha256 = actualSha256,
                        fileSizeBytes = Files.size(model.modelPath),
                    )
                } catch (exception: Exception) {
                    errors += "Could not read model '${model.name}' at ${model.modelPath}: ${exception.message}"
                    null
                }
            }
        }

        val encodingCounts = linkedMapOf<String, Int>()
        val resolvedPaths = linkedMapOf<String, MutableList<DatasetEntry>>()
        val contentHashes = linkedMapOf<String, MutableList<DatasetEntry>>()
        manifest.entries.forEach { entry ->
            try {
                val audio = WavReader.read(entry.path)
                val durationDifference = abs(audio.durationSeconds - entry.durationSeconds)
                if (durationDifference > DURATION_TOLERANCE_SECONDS) {
                    errors += "Manifest row ${entry.rowNumber} duration_sec=${entry.durationSeconds} does not match " +
                        "WAV duration=${audio.durationSeconds} for ${entry.path} " +
                        "(tolerance $DURATION_TOLERANCE_SECONDS seconds)"
                }
                encodingCounts.compute(audio.encoding.name) { _, count -> (count ?: 0) + 1 }
                val realPath = entry.path.toRealPath().toString()
                resolvedPaths.getOrPut(realPath) { mutableListOf() } += entry
                val contentSha256 = decodedAudioSha256(audio)
                contentHashes.getOrPut(contentSha256) { mutableListOf() } += entry
            } catch (exception: Exception) {
                errors += "Manifest row ${entry.rowNumber} WAV validation failed for ${entry.path}: ${exception.message}"
            }
        }
        duplicateAudioErrors("resolved WAV path", resolvedPaths, errors)
        duplicateAudioErrors("WAV content SHA-256", contentHashes, errors)

        if (errors.isNotEmpty()) throw ValidationException(errors)

        val splitCounts = manifest.entries
            .groupingBy { it.split.csvValue }
            .eachCount()
            .toSortedMap()
        return ValidatedBenchmark(
            config = config,
            manifest = manifest,
            summary = ValidationSummary(
                configPath = config.sourcePath.toString(),
                manifestPath = manifest.sourcePath.toString(),
                manifestSha256 = Sha256.digest(manifest.sourcePath),
                entryCount = manifest.entries.size,
                splitCounts = splitCounts,
                totalDurationSeconds = manifest.entries.sumOf { it.durationSeconds },
                wavEncodingCounts = encodingCounts.toSortedMap(),
                models = validatedModels,
            ),
        )
    }

    private const val DURATION_TOLERANCE_SECONDS = 0.02

    private fun duplicateAudioErrors(
        identity: String,
        groups: Map<String, List<DatasetEntry>>,
        errors: MutableList<String>,
    ) {
        groups.filterValues { it.size > 1 }.forEach { (value, entries) ->
            val uses = entries.joinToString { entry ->
                "row ${entry.rowNumber} ${entry.split.csvValue}/${entry.utteranceId}"
            }
            errors += "Duplicate audio $identity '$value' is used by multiple manifest rows: $uses"
        }
    }

    /** Hashes canonical decoded audio, independent of WAV chunks, headers, and source encoding. */
    private fun decodedAudioSha256(audio: WavAudio): String {
        val canonical = ByteBuffer.allocate(Int.SIZE_BYTES * 2 + Float.SIZE_BYTES * audio.samples.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(audio.sampleRate)
            .putInt(audio.samples.size)
        audio.samples.forEach { canonical.putInt(it.toRawBits()) }
        return Sha256.digest(canonical.array())
    }

    private fun validateManifestSemantics(
        config: ResolvedBenchmarkConfig,
        manifest: DatasetManifest,
        errors: MutableList<String>,
    ) {
        val enrolledSpeakers = manifest.entries
            .filter { it.split == DatasetSplit.ENROLLMENT }
            .mapTo(sortedSetOf()) { it.speakerId }
        val testSpeakers = manifest.entries
            .filter { it.split == DatasetSplit.TEST }
            .mapTo(sortedSetOf()) { it.speakerId }
        val finalUnknownSpeakers = manifest.entries
            .filter { it.split == DatasetSplit.UNKNOWN }
            .mapTo(sortedSetOf()) { it.speakerId }
        val developmentSpeakers = manifest.entries
            .filter { it.split == DatasetSplit.DEVELOPMENT }
            .mapTo(sortedSetOf()) { it.speakerId }
        val registeredDevelopmentSpeakers = developmentSpeakers.intersect(enrolledSpeakers)
        val unregisteredDevelopmentSpeakers = developmentSpeakers - enrolledSpeakers

        val unenrolledTestSpeakers = testSpeakers - enrolledSpeakers
        if (unenrolledTestSpeakers.isNotEmpty()) {
            errors += "test split may contain only enrollment speakers; unenrolled speakers: " +
                unenrolledTestSpeakers.joinToString()
        }

        val enrolledUnknownSpeakers = finalUnknownSpeakers.intersect(enrolledSpeakers)
        if (enrolledUnknownSpeakers.isNotEmpty()) {
            errors += "unknown split speakers must not overlap enrollment speakers: " +
                enrolledUnknownSpeakers.joinToString()
        }

        val developmentFinalUnknownOverlap = unregisteredDevelopmentSpeakers.intersect(finalUnknownSpeakers)
        if (developmentFinalUnknownOverlap.isNotEmpty()) {
            errors += "unregistered development speakers must not overlap final unknown speakers: " +
                developmentFinalUnknownOverlap.joinToString()
        }

        if (config.thresholdSelection == ThresholdSelectionStrategy.DEVELOPMENT) {
            if (registeredDevelopmentSpeakers.isEmpty()) {
                errors += "development threshold selection requires development samples from an enrollment speaker"
            }
            if (unregisteredDevelopmentSpeakers.isEmpty()) {
                errors += "development threshold selection requires development samples from an unregistered speaker"
            }
        }

        if (config.validationProfile == ValidationProfile.FULL) {
            validateFullProfile(
                config = config,
                manifest = manifest,
                enrolledSpeakers = enrolledSpeakers,
                finalUnknownSpeakers = finalUnknownSpeakers,
                unregisteredDevelopmentSpeakers = unregisteredDevelopmentSpeakers,
                errors = errors,
            )
        }
    }

    private fun validateFullProfile(
        config: ResolvedBenchmarkConfig,
        manifest: DatasetManifest,
        enrolledSpeakers: Set<String>,
        finalUnknownSpeakers: Set<String>,
        unregisteredDevelopmentSpeakers: Set<String>,
        errors: MutableList<String>,
    ) {
        if (enrolledSpeakers.size < MIN_SPEAKERS || finalUnknownSpeakers.size < MIN_SPEAKERS) {
            errors += "FULL validation requires at least $MIN_SPEAKERS enrolled and $MIN_SPEAKERS final-unknown " +
                "speakers; found enrolled=${enrolledSpeakers.size}, final-unknown=${finalUnknownSpeakers.size}"
        }

        enrolledSpeakers.forEach { speakerId ->
            val enrollment = manifest.count(speakerId, DatasetSplit.ENROLLMENT)
            val development = manifest.count(speakerId, DatasetSplit.DEVELOPMENT)
            val test = manifest.count(speakerId, DatasetSplit.TEST)
            if (enrollment < MIN_ENROLLMENT_CLIPS || development < MIN_DEVELOPMENT_CLIPS || test < MIN_TEST_CLIPS) {
                errors += "FULL validation requires each enrolled speaker to have at least " +
                    "$MIN_ENROLLMENT_CLIPS enrollment, $MIN_DEVELOPMENT_CLIPS development, and $MIN_TEST_CLIPS test clips; " +
                    "$speakerId has enrollment=$enrollment, development=$development, test=$test"
            }
        }

        finalUnknownSpeakers.forEach { speakerId ->
            val unknown = manifest.count(speakerId, DatasetSplit.UNKNOWN)
            if (unknown < MIN_UNKNOWN_CLIPS) {
                errors += "FULL validation requires at least $MIN_UNKNOWN_CLIPS final-unknown clips per speaker; " +
                    "$speakerId has $unknown"
            }
        }

        if (unregisteredDevelopmentSpeakers.size < MIN_SPEAKERS) {
            errors += "FULL validation requires at least $MIN_SPEAKERS development-unknown speakers; " +
                "found ${unregisteredDevelopmentSpeakers.size}"
        }
        unregisteredDevelopmentSpeakers.forEach { speakerId ->
            val development = manifest.count(speakerId, DatasetSplit.DEVELOPMENT)
            if (development < MIN_DEVELOPMENT_CLIPS) {
                errors += "FULL validation requires at least $MIN_DEVELOPMENT_CLIPS development clips per " +
                    "development-unknown speaker; $speakerId has $development"
            }
        }

        val nonJapanese = manifest.entries.filterNot { it.language.equals(REQUIRED_LANGUAGE, ignoreCase = true) }
        if (nonJapanese.isNotEmpty()) {
            errors += "FULL validation requires all speech to use language '$REQUIRED_LANGUAGE'; non-matching rows: " +
                nonJapanese.joinToString { it.rowNumber.toString() }
        }

        if (config.durationsSeconds.toSet() != REQUIRED_DURATIONS_SECONDS) {
            errors += "FULL validation requires durationsSeconds=${REQUIRED_DURATIONS_SECONDS.sorted()}; " +
                "found ${config.durationsSeconds}"
        }
        val evaluationEntries = manifest.entries.filter {
            it.split == DatasetSplit.TEST || it.split == DatasetSplit.UNKNOWN
        }
        evaluationEntries.forEach { entry ->
            if (matchConfiguredDuration(entry.durationSeconds, config.durationsSeconds) == null) {
                errors += "FULL validation evaluation row ${entry.rowNumber} duration_sec=${entry.durationSeconds} " +
                    "does not match a configured duration within +/-0.05 seconds"
            }
        }
        config.durationsSeconds.forEach { duration ->
            listOf(DatasetSplit.TEST, DatasetSplit.UNKNOWN).forEach { split ->
                if (evaluationEntries.none {
                        it.split == split && matchConfiguredDuration(it.durationSeconds, listOf(duration)) == duration
                    }
                ) {
                    errors += "FULL validation requires ${split.csvValue} samples in the ${duration}s duration group"
                }
            }
        }
    }

    private fun DatasetManifest.count(speakerId: String, split: DatasetSplit): Int =
        entries.count { it.speakerId == speakerId && it.split == split }

    private const val MIN_SPEAKERS = 4
    private const val MIN_ENROLLMENT_CLIPS = 5
    private const val MIN_DEVELOPMENT_CLIPS = 5
    private const val MIN_TEST_CLIPS = 10
    private const val MIN_UNKNOWN_CLIPS = 10
    private const val REQUIRED_LANGUAGE = "ja"
    private val REQUIRED_DURATIONS_SECONDS = setOf(2, 3, 5)
}
