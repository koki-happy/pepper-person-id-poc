package com.example.pepper_person_id_poc.speakerbenchmark.report

import com.example.pepper_person_id_poc.speakerbenchmark.measurement.EnvironmentSnapshot
import com.example.pepper_person_id_poc.speakerbenchmark.measurement.MemorySnapshot
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkReportWriterTest {
    @Test
    fun `writes complete stable report set with escaped CSV and Markdown`() {
        val directory = createTempDirectory("benchmark-report-test")
        val results = sampleResults()

        BenchmarkReportWriter.write(directory, results)

        val actualNames = Files.list(directory).use { files ->
            files.map { it.fileName.toString() }.sorted().toList()
        }
        assertEquals(
            listOf(
                "different-speaker-scores.csv",
                "environment.json",
                "metrics.csv",
                "models.json",
                "predictions.csv",
                "same-speaker-scores.csv",
                "summary.md",
                "thresholds.json",
                "windows-parity.json",
            ),
            actualNames,
        )
        assertTrue(Files.readString(directory.resolve("metrics.csv")).contains("\"model, \"\"quoted\"\"\""))
        assertTrue(Files.readString(directory.resolve("summary.md")).contains("model, \"quoted\""))
        val summary = Files.readString(directory.resolve("summary.md"))
        assertTrue(summary.contains("2s accuracy"))
        assertTrue(summary.contains("Peak process committed virtual bytes"))
        assertTrue(summary.contains("not RSS or PSS"))
        assertTrue(summary.contains("identity confusions `2`"))
        Json.parseToJsonElement(Files.readString(directory.resolve("environment.json")))
        val thresholds = Files.readString(directory.resolve("thresholds.json"))
        Json.parseToJsonElement(thresholds)
        assertTrue(thresholds.contains("\"registeredSelectionSampleCount\": 5"))
        assertTrue(thresholds.contains("\"unknownSelectionSampleCount\": 3"))
        assertTrue(thresholds.contains("\"selectedFalseAcceptRate\": 0.1"))
        assertTrue(thresholds.contains("\"selectedFalseRejectRate\": 0.2"))
        assertTrue(thresholds.contains("\"selectedFinalDecisionAccuracy\": 0.75"))
        Json.parseToJsonElement(Files.readString(directory.resolve("models.json")))
        Json.parseToJsonElement(Files.readString(directory.resolve("windows-parity.json")))
    }

    @Test
    fun `metrics CSV follows configured durations instead of a fixed duration set`() {
        val results = sampleResults().copy(configuredDurationsSeconds = listOf(2, 7))
        val csv = BenchmarkReportWriter.metricsCsv(results.metrics, results.configuredDurationsSeconds)

        val header = csv.lineSequence().first()
        assertTrue("accuracy_2_sec" in header)
        assertTrue("accuracy_7_sec" in header)
        assertTrue("accuracy_3_sec" !in header)
        assertTrue("accuracy_5_sec" !in header)
    }

    private fun sampleResults(): BenchmarkResults {
        val modelName = "model, \"quoted\""
        return BenchmarkResults(
            generatedAtUtc = "2026-07-12T00:00:00Z",
            environment = EnvironmentSnapshot(
                capturedAtUtc = "2026-07-12T00:00:00Z",
                operatingSystemName = "Windows 11",
                operatingSystemVersion = "10.0",
                operatingSystemArchitecture = "amd64",
                availableProcessors = 8,
                javaVersion = "17",
                javaVendor = "test",
                javaVmName = "test-vm",
                kotlinVersion = "2.2.10",
                processId = 123,
                memory = MemorySnapshot(10, 20, 30, 40),
            ),
            metrics = listOf(
                ModelMetrics(
                    modelName = modelName,
                    top1Accuracy = 0.9,
                    trueAcceptRate = 0.91,
                    falseAcceptRate = 0.04,
                    falseRejectRate = 0.09,
                    equalErrorRate = 0.065,
                    unknownAccuracy = 0.96,
                    unknownFalseAcceptRate = 0.04,
                    identityConfusions = 2,
                    durationMetrics = listOf(
                        DurationMetric(2, 2, 0.8),
                        DurationMetric(3, 2, 0.9),
                        DurationMetric(5, 1, 0.95),
                    ),
                    accuracy2Seconds = 0.8,
                    accuracy3Seconds = 0.9,
                    accuracy5Seconds = 0.95,
                    modelLoadMilliseconds = 10.0,
                    embeddingP50Milliseconds = 20.0,
                    embeddingP95Milliseconds = 30.0,
                    modelFileSizeBytes = 100,
                    embeddingDimension = 192,
                    peakObservedHeapBytes = 1_000,
                    evaluationSampleCount = 5,
                ),
            ),
            predictions = listOf(
                PredictionRecord(
                    modelName = modelName,
                    utteranceId = "utt1",
                    actualSpeakerId = "speaker1",
                    predictedSpeakerId = "speaker1",
                    split = "test",
                    durationSeconds = 3.0,
                    highestScore = 0.8,
                    secondHighestScore = 0.4,
                    scoreMargin = 0.4,
                    accepted = true,
                    inferenceMilliseconds = 20.0,
                    embeddingSha256 = "0".repeat(64),
                ),
            ),
            thresholds = listOf(
                ThresholdRecord(
                    modelName = modelName,
                    threshold = 0.6,
                    margin = 0.1,
                    selectedOnSplit = "development",
                    selectionObjective = "maximize final-decision accuracy",
                    selectionSampleCount = 8,
                    registeredSelectionSampleCount = 5,
                    unknownSelectionSampleCount = 3,
                    selectedFalseAcceptRate = 0.1,
                    selectedFalseRejectRate = 0.2,
                    selectedIdentityConfusions = 2,
                    selectedFinalDecisionAccuracy = 0.75,
                ),
            ),
            sameSpeakerScores = listOf(ScoreRecord(modelName, "utt1", "speaker1", "speaker1", 0.8, 3.0)),
            differentSpeakerScores = listOf(ScoreRecord(modelName, "utt1", "speaker2", "speaker1", 0.2, 3.0)),
            models = listOf(ModelArtifactRecord(modelName, "camp-plus", "model.onnx", "0".repeat(64), 100, 16_000, 192, 10.0)),
        )
    }
}
