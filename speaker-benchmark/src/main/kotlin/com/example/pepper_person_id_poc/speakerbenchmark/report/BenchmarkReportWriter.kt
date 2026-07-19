package com.example.pepper_person_id_poc.speakerbenchmark.report

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object BenchmarkReportWriter {
    private val json = Json {
        prettyPrint = true
    }

    fun write(outputDirectory: Path, results: BenchmarkResults) {
        val absoluteOutputDirectory = outputDirectory.toAbsolutePath().normalize()
        Files.createDirectories(absoluteOutputDirectory)

        writeAtomically(absoluteOutputDirectory.resolve("summary.md"), summaryMarkdown(results))
        writeAtomically(
            absoluteOutputDirectory.resolve("metrics.csv"),
            metricsCsv(results.metrics, results.configuredDurationsSeconds),
        )
        writeAtomically(absoluteOutputDirectory.resolve("predictions.csv"), predictionsCsv(results.predictions))
        writeAtomically(
            absoluteOutputDirectory.resolve("thresholds.json"),
            json.encodeToString(ThresholdsDocument(results.generatedAtUtc, results.thresholds)) + "\n",
        )
        writeAtomically(
            absoluteOutputDirectory.resolve("same-speaker-scores.csv"),
            scoresCsv(results.sameSpeakerScores),
        )
        writeAtomically(
            absoluteOutputDirectory.resolve("different-speaker-scores.csv"),
            scoresCsv(results.differentSpeakerScores),
        )
        writeAtomically(
            absoluteOutputDirectory.resolve("environment.json"),
            json.encodeToString(results.environment) + "\n",
        )
        writeAtomically(
            absoluteOutputDirectory.resolve("models.json"),
            json.encodeToString(ModelsDocument(results.generatedAtUtc, results.models)) + "\n",
        )
        writeAtomically(
            absoluteOutputDirectory.resolve("windows-parity.json"),
            json.encodeToString(ParityDocument(results.generatedAtUtc, results.parityRecords)) + "\n",
        )
    }

    internal fun metricsCsv(
        metrics: List<ModelMetrics>,
        configuredDurationsSeconds: List<Int> = listOf(2, 3, 5),
    ): String = csv(
        header = listOf(
            "model_name",
            "top1_accuracy",
            "true_accept_rate",
            "far",
            "frr",
            "eer",
            "unknown_accuracy",
            "unknown_false_accept_rate",
            "identity_confusions",
        ) + configuredDurationsSeconds.map { "accuracy_${it}_sec" } +
            configuredDurationsSeconds.map { "sample_count_${it}_sec" } + listOf(
            "model_load_ms",
            "embedding_p50_ms",
            "embedding_p95_ms",
            "model_file_size_bytes",
            "embedding_dimension",
            "peak_observed_heap_bytes",
            "peak_process_committed_virtual_memory_bytes",
            "evaluation_sample_count",
        ),
        rows = metrics.map { metric ->
            listOf(
                metric.modelName,
                metric.top1Accuracy,
                metric.trueAcceptRate,
                metric.falseAcceptRate,
                metric.falseRejectRate,
                metric.equalErrorRate,
                metric.unknownAccuracy,
                metric.unknownFalseAcceptRate,
                metric.identityConfusions,
            ) + configuredDurationsSeconds.map { duration ->
                metric.durationMetrics.firstOrNull { it.durationSeconds == duration }?.finalDecisionAccuracy
            } + configuredDurationsSeconds.map { duration ->
                metric.durationMetrics.firstOrNull { it.durationSeconds == duration }?.sampleCount ?: 0
            } + listOf(
                metric.modelLoadMilliseconds,
                metric.embeddingP50Milliseconds,
                metric.embeddingP95Milliseconds,
                metric.modelFileSizeBytes,
                metric.embeddingDimension,
                metric.peakObservedHeapBytes,
                metric.peakObservedProcessCommittedVirtualMemoryBytes,
                metric.evaluationSampleCount,
            )
        },
    )

    internal fun predictionsCsv(predictions: List<PredictionRecord>): String = csv(
        header = listOf(
            "model_name",
            "utterance_id",
            "actual_speaker_id",
            "predicted_speaker_id",
            "split",
            "duration_sec",
            "highest_score",
            "second_highest_score",
            "score_margin",
            "accepted",
            "inference_ms",
            "embedding_sha256",
        ),
        rows = predictions.map { prediction ->
            listOf(
                prediction.modelName,
                prediction.utteranceId,
                prediction.actualSpeakerId,
                prediction.predictedSpeakerId,
                prediction.split,
                prediction.durationSeconds,
                prediction.highestScore,
                prediction.secondHighestScore,
                prediction.scoreMargin,
                prediction.accepted,
                prediction.inferenceMilliseconds,
                prediction.embeddingSha256,
            )
        },
    )

    internal fun scoresCsv(scores: List<ScoreRecord>): String = csv(
        header = listOf(
            "model_name",
            "utterance_id",
            "reference_speaker_id",
            "query_speaker_id",
            "score",
            "duration_sec",
        ),
        rows = scores.map { score ->
            listOf(
                score.modelName,
                score.utteranceId,
                score.referenceSpeakerId,
                score.querySpeakerId,
                score.score,
                score.durationSeconds,
            )
        },
    )

    internal fun summaryMarkdown(results: BenchmarkResults): String = buildString {
        appendLine("# Windows speaker benchmark summary")
        appendLine()
        appendLine("Generated at `${escapeMarkdown(results.generatedAtUtc)}`.")
        appendLine()
        if (results.limitations.isNotEmpty()) {
            appendLine("## Limitations")
            appendLine()
            results.limitations.forEach { appendLine("- ${escapeMarkdown(it)}") }
            appendLine()
        }
        val durationHeaders = results.configuredDurationsSeconds.joinToString(separator = "") { " ${it}s accuracy |" }
        val durationAlignments = results.configuredDurationsSeconds.joinToString(separator = "") { "---:|" }
        appendLine(
            "| Model | Top-1 | FAR | FRR | Confusions | EER | Unknown false accept |" +
                durationHeaders +
                " Load (ms) | p50 (ms) | p95 (ms) | Model bytes | Embedding dim | " +
                "Peak heap bytes | Peak process committed virtual bytes* |",
        )
        appendLine("|---|---:|---:|---:|---:|---:|---:|$durationAlignments---:|---:|---:|---:|---:|---:|---:|")
        results.metrics.forEach { metric ->
            val durationCells = results.configuredDurationsSeconds.joinToString(separator = "") { duration ->
                " ${metric.durationMetrics.firstOrNull { it.durationSeconds == duration }?.finalDecisionAccuracy} |"
            }
            appendLine(
                "| ${escapeMarkdown(metric.modelName)} | ${metric.top1Accuracy} | " +
                    "${metric.falseAcceptRate} | ${metric.falseRejectRate} | ${metric.identityConfusions} | " +
                    "${metric.equalErrorRate} | " +
                    "${metric.unknownFalseAcceptRate} |" + durationCells +
                    " ${metric.modelLoadMilliseconds} | ${metric.embeddingP50Milliseconds} | " +
                    "${metric.embeddingP95Milliseconds} | ${metric.modelFileSizeBytes} | " +
                    "${metric.embeddingDimension} | ${metric.peakObservedHeapBytes} | " +
                    "${metric.peakObservedProcessCommittedVirtualMemoryBytes} |",
            )
        }
        appendLine()
        appendLine("* Process committed virtual memory is an address-space commitment measurement; it is not RSS or PSS.")
        appendLine()
        appendLine("## Configured-duration evaluation")
        appendLine()
        appendLine("Only test and final-unknown samples within +/-0.05 seconds of a configured duration are grouped.")
        appendLine()
        appendLine("| Model | Duration (s) | Samples | Final-decision accuracy |")
        appendLine("|---|---:|---:|---:|")
        results.metrics.forEach { metric ->
            metric.durationMetrics.forEach { duration ->
                appendLine(
                    "| ${escapeMarkdown(metric.modelName)} | ${duration.durationSeconds} | " +
                        "${duration.sampleCount} | ${duration.finalDecisionAccuracy} |",
                )
            }
        }
        appendLine()
        appendLine("## Threshold selection")
        appendLine()
        results.thresholds.forEach { threshold ->
            appendLine(
                "- ${escapeMarkdown(threshold.modelName)}: `${threshold.selectedOnSplit}`, " +
                    "samples `${threshold.selectionSampleCount}`, registered " +
                    "`${threshold.registeredSelectionSampleCount}`, unknown " +
                    "`${threshold.unknownSelectionSampleCount}`, FAR `${threshold.selectedFalseAcceptRate}`, " +
                    "FRR `${threshold.selectedFalseRejectRate}`, identity confusions " +
                    "`${threshold.selectedIdentityConfusions}`; objective: " +
                    escapeMarkdown(threshold.selectionObjective.orEmpty()),
            )
        }
        appendLine()
        appendLine("Evaluation samples: `${results.metrics.sumOf { it.evaluationSampleCount }}` across model runs.")
        appendLine()
        appendLine("Environment: `${escapeMarkdown(results.environment.operatingSystemName)}` " +
            "`${escapeMarkdown(results.environment.operatingSystemVersion)}` / " +
            "`${escapeMarkdown(results.environment.operatingSystemArchitecture)}`, " +
            "Java `${escapeMarkdown(results.environment.javaVersion)}`.")
    }

    private fun csv(header: List<String>, rows: List<List<Any?>>): String = buildString {
        appendLine(header.joinToString(",") { escapeCsv(it) })
        rows.forEach { row ->
            require(row.size == header.size) { "CSV row has ${row.size} fields but header has ${header.size}" }
            appendLine(row.joinToString(",") { value -> escapeCsv(value?.toString().orEmpty()) })
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\r' || it == '\n' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun escapeMarkdown(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r", " ")
        .replace("\n", " ")

    private fun writeAtomically(path: Path, content: String) {
        val temporary = Files.createTempFile(path.parent, ".${path.fileName}.", ".tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            try {
                Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
