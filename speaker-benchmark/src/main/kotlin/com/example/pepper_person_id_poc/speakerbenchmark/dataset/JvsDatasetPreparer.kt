package com.example.pepper_person_id_poc.speakerbenchmark.dataset

import com.example.pepper_person_id_poc.speakerbenchmark.audio.AudioTransforms
import com.example.pepper_person_id_poc.speakerbenchmark.audio.Pcm16WavWriter
import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavAudio
import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavEncoding
import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavReader
import com.example.pepper_person_id_poc.speakerbenchmark.audio.WindowedSincResampler
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetManifestParser
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetSplit
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.name

data class JvsSplitProfile(
    val speakerIds: List<String>,
    val split: DatasetSplit,
    val candidateUtteranceNumbers: IntRange,
    val clipsPerDuration: Int,
)

data class JvsPreparationPlan(
    val durationsSeconds: List<Int>,
    val profiles: List<JvsSplitProfile>,
) {
    init {
        require(durationsSeconds.isNotEmpty()) { "JVS preparation durations must not be empty" }
        require(durationsSeconds.all { it > 0 }) { "JVS preparation durations must all be positive" }
        require(durationsSeconds.distinct().size == durationsSeconds.size) {
            "JVS preparation durations must be unique"
        }
        require(profiles.isNotEmpty()) { "JVS preparation profiles must not be empty" }

        val candidateOwners = mutableMapOf<Pair<String, Int>, DatasetSplit>()
        profiles.forEach { profile ->
            require(profile.speakerIds.isNotEmpty()) { "A JVS split profile has no speakers" }
            require(profile.speakerIds.distinct().size == profile.speakerIds.size) {
                "A JVS split profile contains duplicate speakers"
            }
            require(profile.speakerIds.all(JVS_SPEAKER_PATTERN::matches)) {
                "JVS speaker ids must use jvsNNN: ${profile.speakerIds.joinToString()}"
            }
            require(!profile.candidateUtteranceNumbers.isEmpty()) { "A JVS candidate range must not be empty" }
            require(profile.candidateUtteranceNumbers.first >= 1 && profile.candidateUtteranceNumbers.last <= 100) {
                "JVS parallel100 utterance numbers must be between 1 and 100"
            }
            require(profile.clipsPerDuration > 0) { "clipsPerDuration must be greater than zero" }

            profile.speakerIds.forEach { speakerId ->
                profile.candidateUtteranceNumbers.forEach { utteranceNumber ->
                    val key = speakerId to utteranceNumber
                    val previous = candidateOwners.putIfAbsent(key, profile.split)
                    require(previous == null) {
                        "JVS source $speakerId/$utteranceNumber is assigned more than once ($previous and ${profile.split})"
                    }
                }
            }
        }
    }

    companion object {
        fun production(): JvsPreparationPlan {
            val registered = (1..4).map(::speakerId)
            return JvsPreparationPlan(
                durationsSeconds = listOf(2, 3, 5),
                profiles = listOf(
                    JvsSplitProfile(registered, DatasetSplit.ENROLLMENT, 1..33, clipsPerDuration = 2),
                    JvsSplitProfile(registered, DatasetSplit.DEVELOPMENT, 34..66, clipsPerDuration = 2),
                    JvsSplitProfile(registered, DatasetSplit.TEST, 67..100, clipsPerDuration = 4),
                    JvsSplitProfile((5..8).map(::speakerId), DatasetSplit.DEVELOPMENT, 1..100, clipsPerDuration = 2),
                    JvsSplitProfile((9..12).map(::speakerId), DatasetSplit.UNKNOWN, 1..100, clipsPerDuration = 4),
                ),
            )
        }

        private fun speakerId(number: Int): String = "jvs%03d".format(number)
    }
}

data class JvsPreparationOptions(
    val archivePath: Path,
    val outputDirectory: Path,
    val force: Boolean = false,
    val expectedArchiveSizeBytes: Long? = null,
    val expectedArchiveSha256: String? = null,
)

data class JvsPreparationResult(
    val outputDirectory: Path,
    val manifestPath: Path,
    val entryCount: Int,
    val archiveSha256: String,
)

object JvsCorpusPin {
    const val FILE_NAME = "jvs_ver1.zip"
    const val FILE_SIZE_BYTES = 3_536_595_425L
    const val SHA256 = "37180e2f87bd1a3e668d7c020378f77cebf61dd57d4d74c71eb0114f386a3999"
    const val PIN_NOTE =
        "Reproducibility pin measured from the archive downloaded on 2026-07-13; not an official publisher checksum."
}

object JvsDatasetPreparer {
    fun prepare(
        options: JvsPreparationOptions,
        plan: JvsPreparationPlan = JvsPreparationPlan.production(),
    ): JvsPreparationResult {
        val archive = options.archivePath.toAbsolutePath().normalize()
        val output = options.outputDirectory.toAbsolutePath().normalize()
        require(Files.isRegularFile(archive)) { "JVS archive does not exist or is not a regular file: $archive" }
        require(archive != output && !archive.startsWith(output)) {
            "JVS archive must not be located inside the generated output directory"
        }
        validateDestination(output, options.force)

        val archiveSize = Files.size(archive)
        options.expectedArchiveSizeBytes?.let { expected ->
            require(archiveSize == expected) {
                "JVS archive size mismatch: expected=$expected, actual=$archiveSize, path=$archive"
            }
        }
        val archiveSha256 = Sha256.digest(archive)
        options.expectedArchiveSha256?.let { expected ->
            require(archiveSha256.equals(expected, ignoreCase = true)) {
                "JVS archive SHA-256 mismatch: expected=${expected.lowercase()}, actual=$archiveSha256, path=$archive"
            }
        }

        val outputParent = output.parent ?: error("JVS output directory must have a parent: $output")
        Files.createDirectories(outputParent)
        val staging = outputParent.resolve(".${output.name}.preparing-${UUID.randomUUID()}")
        Files.createDirectories(staging)

        try {
            val generatedEntries = ZipFile(archive.toFile()).use { zip ->
                val sourceIndex = JvsArchiveIndex.create(zip)
                prepareProfiles(zip, sourceIndex, plan, staging)
            }
            val manifest = writeManifest(staging, generatedEntries)
            val parsedManifest = DatasetManifestParser.parse(manifest)
            check(parsedManifest.entries.size == generatedEntries.size) {
                "Generated manifest entry count changed during validation"
            }

            val metadata = PreparationMetadata(
                schemaVersion = 1,
                dataset = "JVS Corpus parallel100",
                sourceArchiveFile = archive.fileName.toString(),
                sourceArchiveSizeBytes = archiveSize,
                sourceArchiveSha256 = archiveSha256,
                sourceArchivePinNote = JvsCorpusPin.PIN_NOTE,
                sourcePathPattern = JVS_ENTRY_PATTERN.pattern,
                sourceSampleRate = SOURCE_SAMPLE_RATE,
                targetSampleRate = TARGET_SAMPLE_RATE,
                resampler = "Blackman-windowed sinc, 49 taps, 0.95 Nyquist rolloff",
                crop = "exact center crop after resampling",
                manifestSha256 = Sha256.digest(manifest),
                profiles = plan.profiles.map { profile ->
                    ProfileMetadata(
                        speakers = profile.speakerIds,
                        split = profile.split.csvValue,
                        candidateUtteranceFirst = profile.candidateUtteranceNumbers.first,
                        candidateUtteranceLast = profile.candidateUtteranceNumbers.last,
                        clipsPerDuration = profile.clipsPerDuration,
                    )
                },
                durationsSeconds = plan.durationsSeconds,
                entries = generatedEntries.map(GeneratedEntry::metadata),
            )
            Files.writeString(
                staging.resolve(METADATA_FILE_NAME),
                JSON.encodeToString(metadata) + "\n",
                StandardCharsets.UTF_8,
            )
            Files.writeString(
                staging.resolve(MARKER_FILE_NAME),
                "JVS generated evaluation data. Do not redistribute.\n",
                StandardCharsets.UTF_8,
            )

            replaceDestination(staging, output)
            return JvsPreparationResult(
                outputDirectory = output,
                manifestPath = output.resolve(MANIFEST_FILE_NAME),
                entryCount = generatedEntries.size,
                archiveSha256 = archiveSha256,
            )
        } catch (exception: Throwable) {
            if (Files.exists(staging)) deleteRecursively(staging)
            throw exception
        }
    }

    private fun prepareProfiles(
        zip: ZipFile,
        sourceIndex: JvsArchiveIndex,
        plan: JvsPreparationPlan,
        staging: Path,
    ): List<GeneratedEntry> = buildList {
        plan.profiles.forEach { profile ->
            profile.speakerIds.forEach { speakerId ->
                val candidates = profile.candidateUtteranceNumbers.mapNotNull { utteranceNumber ->
                    sourceIndex.find(speakerId, utteranceNumber)
                }
                val missingCount = profile.candidateUtteranceNumbers.count() - candidates.size
                require(missingCount == 0) {
                    "JVS archive is missing $missingCount parallel100 WAV files for $speakerId " +
                        "in ${profile.candidateUtteranceNumbers}"
                }
                addAll(prepareSpeakerSplit(zip, speakerId, profile, plan.durationsSeconds, candidates, staging))
            }
        }
    }

    private fun prepareSpeakerSplit(
        zip: ZipFile,
        speakerId: String,
        profile: JvsSplitProfile,
        durationsSeconds: List<Int>,
        candidates: List<JvsSource>,
        staging: Path,
    ): List<GeneratedEntry> {
        val decoded = mutableMapOf<Int, WavAudio>()
        val usedSources = mutableSetOf<Int>()
        val selections = mutableListOf<Pair<JvsSource, Int>>()

        durationsSeconds.sortedDescending().forEach { durationSeconds ->
            val requiredOutputSamples = durationSeconds * TARGET_SAMPLE_RATE
            repeat(profile.clipsPerDuration) {
                val selected = candidates.firstOrNull { candidate ->
                    candidate.utteranceNumber !in usedSources &&
                        decoded.getOrPut(candidate.utteranceNumber) { readSource(zip, candidate) }
                            .samples.size.toLong() * TARGET_SAMPLE_RATE / SOURCE_SAMPLE_RATE >= requiredOutputSamples
                } ?: throw IllegalArgumentException(
                    "JVS does not contain enough ${durationSeconds}s sources for $speakerId/${profile.split.csvValue}; " +
                        "need ${profile.clipsPerDuration} distinct clips per duration in " +
                        "${profile.candidateUtteranceNumbers}",
                )
                usedSources += selected.utteranceNumber
                selections += selected to durationSeconds
            }
        }

        return selections
            .sortedWith(compareBy<Pair<JvsSource, Int>>({ it.second }, { it.first.utteranceNumber }))
            .map { (source, durationSeconds) ->
                val sourceAudio = decoded.getValue(source.utteranceNumber)
                val resampled = WindowedSincResampler.resample(
                    sourceAudio.samples,
                    sourceSampleRate = SOURCE_SAMPLE_RATE,
                    targetSampleRate = TARGET_SAMPLE_RATE,
                )
                val cropped = AudioTransforms.centerCrop(resampled, durationSeconds * TARGET_SAMPLE_RATE)
                val sourceNumber = "%03d".format(source.utteranceNumber)
                val fileName = "${speakerId}_parallel100_${sourceNumber}_${durationSeconds}s.wav"
                val relativePath = Path.of(speakerId, profile.split.csvValue, fileName)
                val outputPath = staging.resolve(relativePath)
                Pcm16WavWriter.write(outputPath, cropped, TARGET_SAMPLE_RATE)

                GeneratedEntry(
                    speakerId = speakerId,
                    utteranceId = "${speakerId}_parallel100_${sourceNumber}_${profile.split.csvValue}_${durationSeconds}s",
                    sourceGroupId = "${speakerId}_parallel100_$sourceNumber",
                    split = profile.split,
                    relativePath = relativePath.toString().replace('\\', '/'),
                    durationSeconds = durationSeconds,
                    sourceZipEntry = source.entry.name.replace('\\', '/'),
                    outputSha256 = Sha256.digest(outputPath),
                )
            }
    }

    private fun readSource(zip: ZipFile, source: JvsSource): WavAudio {
        val size = source.entry.size
        require(size in 1..MAX_SOURCE_WAV_BYTES) {
            "JVS source WAV has an invalid uncompressed size $size: ${source.entry.name}"
        }
        val bytes = zip.getInputStream(source.entry).use { input ->
            val output = ByteArrayOutputStream(size.toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                require(total <= MAX_SOURCE_WAV_BYTES) { "JVS source WAV exceeds size limit: ${source.entry.name}" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val audio = WavReader.decode(bytes, source.entry.name, requiredSampleRate = SOURCE_SAMPLE_RATE)
        require(audio.encoding == WavEncoding.PCM_SIGNED_16) {
            "JVS source WAV must be PCM signed 16-bit: ${source.entry.name}"
        }
        return audio
    }

    private fun writeManifest(staging: Path, entries: List<GeneratedEntry>): Path {
        require(entries.isNotEmpty()) { "JVS preparation did not generate any entries" }
        val text = buildString {
            appendLine(DatasetManifestParser.requiredHeader.joinToString(","))
            entries.forEach { entry ->
                appendLine(
                    listOf(
                        entry.speakerId,
                        entry.utteranceId,
                        entry.sourceGroupId,
                        entry.split.csvValue,
                        entry.relativePath,
                        "ja",
                        TARGET_SAMPLE_RATE.toString(),
                        entry.durationSeconds.toString(),
                        "JVS corpus studio microphone",
                        "public_dataset",
                    ).joinToString(",", transform = ::escapeCsv),
                )
            }
        }
        val path = staging.resolve(MANIFEST_FILE_NAME)
        Files.writeString(path, text, StandardCharsets.UTF_8)
        return path
    }

    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun validateDestination(output: Path, force: Boolean) {
        if (!Files.exists(output)) return
        require(Files.isDirectory(output)) { "JVS output exists but is not a directory: $output" }
        val nonEmpty = Files.list(output).use { it.findAny().isPresent }
        if (!nonEmpty) return
        require(force) { "JVS output is not empty; rerun with --force to replace generated data: $output" }
        require(Files.isRegularFile(output.resolve(MARKER_FILE_NAME))) {
            "Refusing to replace an unmarked directory with --force: $output"
        }
    }

    private fun replaceDestination(staging: Path, output: Path) {
        if (Files.exists(output)) deleteRecursively(output)
        try {
            Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staging, output)
        }
    }

    private fun deleteRecursively(path: Path) {
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private val JSON = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    private const val SOURCE_SAMPLE_RATE = 24_000
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val MAX_SOURCE_WAV_BYTES = 64L * 1024 * 1024
    private const val MANIFEST_FILE_NAME = "manifest.csv"
    private const val METADATA_FILE_NAME = "preparation-metadata.json"
    private const val MARKER_FILE_NAME = ".jvs-prepared-dataset"
}

private data class JvsSource(
    val speakerId: String,
    val utteranceNumber: Int,
    val entry: ZipEntry,
)

private class JvsArchiveIndex private constructor(
    private val sources: Map<Pair<String, Int>, JvsSource>,
) {
    fun find(speakerId: String, utteranceNumber: Int): JvsSource? = sources[speakerId to utteranceNumber]

    companion object {
        fun create(zip: ZipFile): JvsArchiveIndex {
            val sources = linkedMapOf<Pair<String, Int>, JvsSource>()
            zip.entries().asSequence()
                .filterNot(ZipEntry::isDirectory)
                .forEach { entry ->
                    val normalizedName = entry.name.replace('\\', '/')
                    val match = JVS_ENTRY_PATTERN.find(normalizedName) ?: return@forEach
                    val source = JvsSource(
                        speakerId = match.groupValues[1].lowercase(),
                        utteranceNumber = match.groupValues[2].toInt(),
                        entry = entry,
                    )
                    val previous = sources.putIfAbsent(source.speakerId to source.utteranceNumber, source)
                    require(previous == null) {
                        "JVS archive contains duplicate parallel100 source ${source.speakerId}/${source.utteranceNumber}: " +
                            "${previous?.entry?.name} and ${entry.name}"
                    }
                }
            require(sources.isNotEmpty()) {
                "JVS archive contains no parallel100/wav24kHz16bit entries matching ${JVS_ENTRY_PATTERN.pattern}"
            }
            return JvsArchiveIndex(sources)
        }
    }
}

private data class GeneratedEntry(
    val speakerId: String,
    val utteranceId: String,
    val sourceGroupId: String,
    val split: DatasetSplit,
    val relativePath: String,
    val durationSeconds: Int,
    val sourceZipEntry: String,
    val outputSha256: String,
) {
    fun metadata(): GeneratedEntryMetadata = GeneratedEntryMetadata(
        speakerId = speakerId,
        utteranceId = utteranceId,
        sourceGroupId = sourceGroupId,
        split = split.csvValue,
        durationSeconds = durationSeconds,
        sourceZipEntry = sourceZipEntry,
        outputPath = relativePath,
        outputSha256 = outputSha256,
    )
}

@Serializable
private data class PreparationMetadata(
    val schemaVersion: Int,
    val dataset: String,
    val sourceArchiveFile: String,
    val sourceArchiveSizeBytes: Long,
    val sourceArchiveSha256: String,
    val sourceArchivePinNote: String,
    val sourcePathPattern: String,
    val sourceSampleRate: Int,
    val targetSampleRate: Int,
    val resampler: String,
    val crop: String,
    val manifestSha256: String,
    val profiles: List<ProfileMetadata>,
    val durationsSeconds: List<Int>,
    val entries: List<GeneratedEntryMetadata>,
)

@Serializable
private data class ProfileMetadata(
    val speakers: List<String>,
    val split: String,
    val candidateUtteranceFirst: Int,
    val candidateUtteranceLast: Int,
    val clipsPerDuration: Int,
)

@Serializable
private data class GeneratedEntryMetadata(
    val speakerId: String,
    val utteranceId: String,
    val sourceGroupId: String,
    val split: String,
    val durationSeconds: Int,
    val sourceZipEntry: String,
    val outputPath: String,
    val outputSha256: String,
)

private val JVS_SPEAKER_PATTERN = Regex("^jvs\\d{3}$")
private val JVS_ENTRY_PATTERN = Regex(
    "(?:^|/)(jvs\\d{3})/parallel100/wav24kHz16bit/VOICEACTRESS100_(\\d{3})\\.wav$",
    RegexOption.IGNORE_CASE,
)
