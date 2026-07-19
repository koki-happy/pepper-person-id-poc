package com.example.pepper_person_id_poc.speakerbenchmark.manifest

import java.nio.charset.MalformedInputException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

enum class DatasetSplit(val csvValue: String) {
    ENROLLMENT("enrollment"),
    DEVELOPMENT("development"),
    TEST("test"),
    UNKNOWN("unknown");

    companion object {
        fun parse(value: String): DatasetSplit = entries.firstOrNull { it.csvValue == value }
            ?: throw ManifestException("Invalid split '$value'; expected enrollment, development, test, or unknown")
    }
}

enum class RecordingType(val csvValue: String) {
    PEPPER_LIVE("pepper_live"),
    PEPPER_REPLAY("pepper_replay"),
    PUBLIC_DATASET("public_dataset"),
    EXTERNAL_MICROPHONE("external_microphone");

    companion object {
        fun parse(value: String): RecordingType = entries.firstOrNull { it.csvValue == value }
            ?: throw ManifestException(
                "Invalid recording_type '$value'; expected pepper_live, pepper_replay, public_dataset, or external_microphone",
            )
    }
}

data class DatasetEntry(
    val speakerId: String,
    val utteranceId: String,
    val sourceGroupId: String,
    val split: DatasetSplit,
    val path: Path,
    val configuredPath: String,
    val language: String,
    val sampleRate: Int,
    val durationSeconds: Double,
    val recordingDevice: String,
    val recordingType: RecordingType,
    val rowNumber: Int,
)

data class DatasetManifest(
    val sourcePath: Path,
    val entries: List<DatasetEntry>,
)

class ManifestException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class SplitLeakage(
    val sourceGroupId: String,
    val splits: Set<DatasetSplit>,
    val utteranceIds: List<String>,
)

object SourceGroupLeakageDetector {
    fun detect(entries: List<DatasetEntry>): List<SplitLeakage> = entries
        .groupBy { it.sourceGroupId }
        .mapNotNull { (sourceGroupId, groupedEntries) ->
            val splits = groupedEntries.mapTo(linkedSetOf()) { it.split }
            if (splits.size <= 1) {
                null
            } else {
                SplitLeakage(
                    sourceGroupId = sourceGroupId,
                    splits = splits,
                    utteranceIds = groupedEntries.map { it.utteranceId }.sorted(),
                )
            }
        }
        .sortedBy { it.sourceGroupId }

    fun requireNone(entries: List<DatasetEntry>) {
        val leakages = detect(entries)
        if (leakages.isEmpty()) return

        val details = leakages.joinToString(separator = "; ") { leakage ->
            val splits = leakage.splits.map { it.csvValue }.sorted().joinToString("/")
            "${leakage.sourceGroupId} across $splits (${leakage.utteranceIds.joinToString()})"
        }
        throw ManifestException("source_group_id split leakage detected: $details")
    }
}

object DatasetManifestParser {
    val requiredHeader: List<String> = listOf(
        "speaker_id",
        "utterance_id",
        "source_group_id",
        "split",
        "path",
        "language",
        "sample_rate",
        "duration_sec",
        "recording_device",
        "recording_type",
    )

    fun parse(path: Path): DatasetManifest {
        val absolutePath = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(absolutePath)) {
            throw ManifestException("Manifest does not exist or is not a regular file: $absolutePath")
        }
        val text = try {
            Files.readString(absolutePath, StandardCharsets.UTF_8)
        } catch (exception: MalformedInputException) {
            throw ManifestException("Manifest must be valid UTF-8: $absolutePath", exception)
        }
        return parseText(text, absolutePath)
    }

    internal fun parseText(text: String, sourcePath: Path): DatasetManifest {
        val rows = StrictCsvParser.parse(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) {
            throw ManifestException("Manifest is empty: $sourcePath")
        }

        val actualHeader = rows.first().values
        if (actualHeader != requiredHeader) {
            throw ManifestException(
                "Manifest header must exactly match '${requiredHeader.joinToString(",")}', " +
                    "but was '${actualHeader.joinToString(",")}'",
            )
        }
        if (rows.size == 1) {
            throw ManifestException("Manifest must contain at least one data row")
        }

        val baseDirectory = sourcePath.toAbsolutePath().normalize().parent
            ?: Path.of(".").toAbsolutePath().normalize()
        val entries = rows.drop(1).map { row -> parseRow(row, baseDirectory) }

        val duplicateUtteranceIds = entries
            .groupingBy { it.utteranceId }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicateUtteranceIds.isNotEmpty()) {
            throw ManifestException("utterance_id values must be unique; duplicates: ${duplicateUtteranceIds.joinToString()}")
        }

        SourceGroupLeakageDetector.requireNone(entries)
        return DatasetManifest(sourcePath.toAbsolutePath().normalize(), entries)
    }

    private fun parseRow(row: CsvRow, baseDirectory: Path): DatasetEntry {
        if (row.values.size != requiredHeader.size) {
            throw ManifestException(
                "Manifest row ${row.lineNumber} has ${row.values.size} fields; expected ${requiredHeader.size}",
            )
        }
        row.values.forEachIndexed { index, value ->
            val column = requiredHeader[index]
            if (value.isBlank()) {
                throw ManifestException("Manifest row ${row.lineNumber}, column '$column' must not be blank")
            }
            if (value != value.trim()) {
                throw ManifestException(
                    "Manifest row ${row.lineNumber}, column '$column' must not have leading or trailing whitespace",
                )
            }
            if ('\r' in value || '\n' in value) {
                throw ManifestException("Manifest row ${row.lineNumber}, column '$column' must be a single line")
            }
        }

        val sampleRate = row.values[6].toIntOrNull()
            ?: throw ManifestException("Manifest row ${row.lineNumber}, sample_rate must be an integer")
        if (sampleRate != REQUIRED_SAMPLE_RATE) {
            throw ManifestException("Manifest row ${row.lineNumber}, sample_rate must be $REQUIRED_SAMPLE_RATE")
        }
        val duration = row.values[7].toDoubleOrNull()
            ?: throw ManifestException("Manifest row ${row.lineNumber}, duration_sec must be a number")
        if (!duration.isFinite() || duration <= 0.0) {
            throw ManifestException("Manifest row ${row.lineNumber}, duration_sec must be finite and greater than zero")
        }

        val configuredPath = row.values[4]
        val path = try {
            Path.of(configuredPath).let { configured ->
                (if (configured.isAbsolute) configured else baseDirectory.resolve(configured)).toAbsolutePath().normalize()
            }
        } catch (exception: InvalidPathException) {
            throw ManifestException(
                "Manifest row ${row.lineNumber}, path is not valid: ${exception.message}",
                exception,
            )
        }

        return DatasetEntry(
            speakerId = row.values[0],
            utteranceId = row.values[1],
            sourceGroupId = row.values[2],
            split = parseAtRow(row.lineNumber) { DatasetSplit.parse(row.values[3]) },
            path = path,
            configuredPath = configuredPath,
            language = row.values[5],
            sampleRate = sampleRate,
            durationSeconds = duration,
            recordingDevice = row.values[8],
            recordingType = parseAtRow(row.lineNumber) { RecordingType.parse(row.values[9]) },
            rowNumber = row.lineNumber,
        )
    }

    private inline fun <T> parseAtRow(rowNumber: Int, block: () -> T): T = try {
        block()
    } catch (exception: ManifestException) {
        throw ManifestException("Manifest row $rowNumber: ${exception.message}", exception)
    }

    private const val REQUIRED_SAMPLE_RATE = 16_000
}

private data class CsvRow(
    val values: List<String>,
    val lineNumber: Int,
)

private object StrictCsvParser {
    fun parse(text: String): List<CsvRow> {
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<CsvRow>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotedField = false
        var afterClosingQuote = false
        var line = 1
        var rowStartLine = 1
        var index = 0
        var justEndedRecord = false

        fun endField() {
            fields += field.toString()
            field.setLength(0)
            afterClosingQuote = false
        }

        fun endRecord() {
            endField()
            rows += CsvRow(fields.toList(), rowStartLine)
            fields.clear()
            justEndedRecord = true
        }

        while (index < text.length) {
            val character = text[index]
            if (inQuotedField) {
                when {
                    character == '"' && text.getOrNull(index + 1) == '"' -> {
                        field.append('"')
                        index += 2
                    }

                    character == '"' -> {
                        inQuotedField = false
                        afterClosingQuote = true
                        index += 1
                    }

                    character == '\r' && text.getOrNull(index + 1) == '\n' -> {
                        field.append("\r\n")
                        line += 1
                        index += 2
                    }

                    else -> {
                        field.append(character)
                        if (character == '\n' || character == '\r') line += 1
                        index += 1
                    }
                }
                continue
            }

            if (afterClosingQuote) {
                when (character) {
                    ',' -> {
                        endField()
                        justEndedRecord = false
                        index += 1
                    }

                    '\n' -> {
                        endRecord()
                        line += 1
                        rowStartLine = line
                        index += 1
                    }

                    '\r' -> {
                        if (text.getOrNull(index + 1) != '\n') {
                            throw ManifestException("CSV line $line uses a bare carriage return")
                        }
                        endRecord()
                        line += 1
                        rowStartLine = line
                        index += 2
                    }

                    else -> throw ManifestException(
                        "CSV line $line has unexpected character '$character' after a closing quote",
                    )
                }
                continue
            }

            when (character) {
                '"' -> {
                    if (field.isNotEmpty()) {
                        throw ManifestException("CSV line $line has a quote inside an unquoted field")
                    }
                    inQuotedField = true
                    justEndedRecord = false
                    index += 1
                }

                ',' -> {
                    endField()
                    justEndedRecord = false
                    index += 1
                }

                '\n' -> {
                    endRecord()
                    line += 1
                    rowStartLine = line
                    index += 1
                }

                '\r' -> {
                    if (text.getOrNull(index + 1) != '\n') {
                        throw ManifestException("CSV line $line uses a bare carriage return")
                    }
                    endRecord()
                    line += 1
                    rowStartLine = line
                    index += 2
                }

                else -> {
                    field.append(character)
                    justEndedRecord = false
                    index += 1
                }
            }
        }

        if (inQuotedField) {
            throw ManifestException("CSV row beginning on line $rowStartLine has an unterminated quoted field")
        }
        if (!justEndedRecord || field.isNotEmpty() || fields.isNotEmpty()) {
            endRecord()
        }
        return rows
    }
}
