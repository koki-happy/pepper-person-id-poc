package com.example.pepper_person_id_poc.speakerbenchmark.report

import com.example.pepper_person_id_poc.speakerbenchmark.measurement.EnvironmentSnapshot
import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkResults(
    val generatedAtUtc: String,
    val environment: EnvironmentSnapshot,
    val metrics: List<ModelMetrics>,
    val predictions: List<PredictionRecord>,
    val thresholds: List<ThresholdRecord>,
    val sameSpeakerScores: List<ScoreRecord>,
    val differentSpeakerScores: List<ScoreRecord>,
    val models: List<ModelArtifactRecord>,
    val configuredDurationsSeconds: List<Int> = listOf(2, 3, 5),
    val limitations: List<String> = emptyList(),
    val parityRecords: List<ParityRecord> = emptyList(),
)

@Serializable
data class ModelMetrics(
    val modelName: String,
    val top1Accuracy: Double?,
    val trueAcceptRate: Double?,
    val falseAcceptRate: Double?,
    val falseRejectRate: Double?,
    val equalErrorRate: Double?,
    val unknownAccuracy: Double?,
    val unknownFalseAcceptRate: Double?,
    /** Registered trials accepted as the wrong enrolled identity. */
    val identityConfusions: Int = 0,
    /** Exact evaluation results for every duration requested by the benchmark config. */
    val durationMetrics: List<DurationMetric> = emptyList(),
    val accuracy2Seconds: Double? = null,
    val accuracy3Seconds: Double? = null,
    val accuracy5Seconds: Double? = null,
    val modelLoadMilliseconds: Double,
    val embeddingP50Milliseconds: Double,
    val embeddingP95Milliseconds: Double,
    val modelFileSizeBytes: Long,
    val embeddingDimension: Int,
    val peakObservedHeapBytes: Long,
    val peakObservedProcessCommittedVirtualMemoryBytes: Long? = null,
    val evaluationSampleCount: Int,
)

@Serializable
data class DurationMetric(
    val durationSeconds: Int,
    val sampleCount: Int,
    val finalDecisionAccuracy: Double? = null,
)

@Serializable
data class PredictionRecord(
    val modelName: String,
    val utteranceId: String,
    val actualSpeakerId: String,
    val predictedSpeakerId: String? = null,
    val split: String,
    val durationSeconds: Double,
    val highestScore: Double,
    val secondHighestScore: Double? = null,
    val scoreMargin: Double? = null,
    val accepted: Boolean,
    val inferenceMilliseconds: Double,
    val embeddingSha256: String,
)

@Serializable
data class ThresholdRecord(
    val modelName: String,
    val threshold: Double,
    val margin: Double,
    val selectedOnSplit: String,
    val selectionObjective: String? = null,
    val selectionSampleCount: Int? = null,
    val registeredSelectionSampleCount: Int? = null,
    val unknownSelectionSampleCount: Int? = null,
    val selectedFalseAcceptRate: Double? = null,
    val selectedFalseRejectRate: Double? = null,
    val selectedIdentityConfusions: Int? = null,
    val selectedFinalDecisionAccuracy: Double? = null,
)

@Serializable
data class ScoreRecord(
    val modelName: String,
    val utteranceId: String,
    val referenceSpeakerId: String,
    val querySpeakerId: String,
    val score: Double,
    val durationSeconds: Double,
)

@Serializable
data class ModelArtifactRecord(
    val modelName: String,
    val adapter: String,
    val fileName: String,
    val sha256: String,
    val fileSizeBytes: Long,
    val sampleRate: Int,
    val embeddingDimension: Int,
    val loadMilliseconds: Double,
    val runtimeVersion: String = "unknown",
)

@Serializable
data class ParityRecord(
    val utteranceId: String,
    val modelName: String,
    val modelSha256: String,
    val sampleRate: Int,
    val numSamples: Int,
    val durationSec: Double,
    val embeddingDim: Int,
    val embeddingNorm: Double,
    val embeddingSha256: String,
    /** Parity-only vector. The containing results directory is excluded from Git as biometric data. */
    val embedding: List<Float>,
    val speakerScores: Map<String, Double>,
    val threshold: Double,
    val margin: Double,
    val predictedSpeakerId: String? = null,
    val isUnknown: Boolean,
)

@Serializable
internal data class ThresholdsDocument(
    val generatedAtUtc: String,
    val thresholds: List<ThresholdRecord>,
)

@Serializable
internal data class ModelsDocument(
    val generatedAtUtc: String,
    val models: List<ModelArtifactRecord>,
)

@Serializable
internal data class ParityDocument(
    val generatedAtUtc: String,
    val records: List<ParityRecord>,
)
