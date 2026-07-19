package com.example.pepper_person_id_poc.speakerbenchmark

import com.example.pepper_person_id_poc.speakerbenchmark.audio.WavReader
import com.example.pepper_person_id_poc.speakerbenchmark.config.ResolvedModelConfig
import com.example.pepper_person_id_poc.speakerbenchmark.config.ThresholdSelectionStrategy
import com.example.pepper_person_id_poc.speakerbenchmark.config.ValidationProfile
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetEntry
import com.example.pepper_person_id_poc.speakerbenchmark.manifest.DatasetSplit
import com.example.pepper_person_id_poc.speakerbenchmark.measurement.MeasurementCollector
import com.example.pepper_person_id_poc.speakerbenchmark.model.SpeakerModelAdapterFactory
import com.example.pepper_person_id_poc.speakerbenchmark.report.BenchmarkResults
import com.example.pepper_person_id_poc.speakerbenchmark.report.DurationMetric
import com.example.pepper_person_id_poc.speakerbenchmark.report.ModelArtifactRecord
import com.example.pepper_person_id_poc.speakerbenchmark.report.ModelMetrics
import com.example.pepper_person_id_poc.speakerbenchmark.report.PredictionRecord
import com.example.pepper_person_id_poc.speakerbenchmark.report.ParityRecord
import com.example.pepper_person_id_poc.speakerbenchmark.report.ScoreRecord
import com.example.pepper_person_id_poc.speakerbenchmark.report.ThresholdRecord
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import com.example.pepper_person_id_poc.speakerbenchmark.validation.ValidatedBenchmark
import com.example.pepper_person_id_poc.speakercore.EmbeddingMath
import com.example.pepper_person_id_poc.speakercore.SpeakerCentroid
import com.example.pepper_person_id_poc.speakercore.SpeakerDecision
import com.example.pepper_person_id_poc.speakercore.SpeakerEvaluationTrial
import com.example.pepper_person_id_poc.speakercore.SpeakerEvaluator
import com.example.pepper_person_id_poc.speakercore.SpeakerScorer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.time.Instant
import kotlin.math.sqrt

/** CPU-only Windows benchmark implementation backed by ONNX Runtime Java. */
class OnnxBenchmarkRunner : BenchmarkRunner {
    override fun run(benchmark: ValidatedBenchmark): BenchmarkResults {
        val entries = benchmark.manifest.entries
        val enrollmentEntries = entries.filter { it.split == DatasetSplit.ENROLLMENT }
        val evaluationEntries = entries.filter {
            it.split == DatasetSplit.TEST || it.split == DatasetSplit.UNKNOWN
        }
        require(enrollmentEntries.isNotEmpty()) { "Manifest requires at least one enrollment row" }
        require(evaluationEntries.isNotEmpty()) { "Manifest requires at least one test or unknown row" }

        val audio = entries.associateWith { entry -> WavReader.read(entry.path) }
        val generatedAt = Instant.now().toString()
        val modelRuns = benchmark.config.models.map { model ->
            runModel(benchmark, model, entries, enrollmentEntries, evaluationEntries, audio)
        }
        return BenchmarkResults(
            generatedAtUtc = generatedAt,
            environment = MeasurementCollector.captureEnvironment(),
            metrics = modelRuns.map(ModelRun::metrics),
            predictions = modelRuns.flatMap(ModelRun::predictions),
            thresholds = modelRuns.map(ModelRun::threshold),
            sameSpeakerScores = modelRuns.flatMap(ModelRun::sameSpeakerScores),
            differentSpeakerScores = modelRuns.flatMap(ModelRun::differentSpeakerScores),
            models = modelRuns.map(ModelRun::modelArtifact),
            configuredDurationsSeconds = benchmark.config.durationsSeconds,
            limitations = if (benchmark.config.validationProfile == ValidationProfile.FULL) {
                emptyList()
            } else {
                benchmarkLimitations(entries, benchmark.config.durationsSeconds)
            },
            parityRecords = modelRuns.flatMap(ModelRun::parityRecords),
        )
    }

    private fun runModel(
        benchmark: ValidatedBenchmark,
        model: ResolvedModelConfig,
        entries: List<DatasetEntry>,
        enrollmentEntries: List<DatasetEntry>,
        evaluationEntries: List<DatasetEntry>,
        audio: Map<DatasetEntry, com.example.pepper_person_id_poc.speakerbenchmark.audio.WavAudio>,
    ): ModelRun {
        SpeakerModelAdapterFactory.create(model).use { adapter ->
            val embeddings = linkedMapOf<DatasetEntry, FloatArray>()
            val averageInferenceMillis = linkedMapOf<DatasetEntry, Double>()
            val timingNanoseconds = mutableListOf<Long>()
            val initialMemory = MeasurementCollector.captureMemory()
            var peakObservedHeapBytes = initialMemory.heapUsedBytes
            var peakObservedProcessMemoryBytes = initialMemory.processCommittedVirtualMemoryBytes

            entries.forEach { entry ->
                val wav = checkNotNull(audio[entry])
                repeat(benchmark.config.warmupRuns) {
                    adapter.extractEmbedding(wav.samples, wav.sampleRate)
                }
                val perEntryTimings = mutableListOf<Long>()
                var embedding: FloatArray? = null
                repeat(benchmark.config.measuredRuns) {
                    val measured = MeasurementCollector.measure {
                        adapter.extractEmbedding(wav.samples, wav.sampleRate)
                    }
                    embedding = measured.value
                    timingNanoseconds += measured.elapsedNanoseconds
                    perEntryTimings += measured.elapsedNanoseconds
                    peakObservedHeapBytes = maxOf(
                        peakObservedHeapBytes,
                        measured.memoryBefore.heapUsedBytes,
                        measured.memoryAfter.heapUsedBytes,
                    )
                    peakObservedProcessMemoryBytes = listOfNotNull(
                        peakObservedProcessMemoryBytes,
                        measured.memoryBefore.processCommittedVirtualMemoryBytes,
                        measured.memoryAfter.processCommittedVirtualMemoryBytes,
                    ).maxOrNull()
                }
                embeddings[entry] = checkNotNull(embedding)
                averageInferenceMillis[entry] =
                    perEntryTimings.average() / NANOSECONDS_PER_MILLISECOND
            }

            val centroids = enrollmentEntries
                .groupBy(DatasetEntry::speakerId)
                .map { (speakerId, samples) ->
                    SpeakerCentroid(
                        speakerId = speakerId,
                        embedding = EmbeddingMath.centroid(samples.map { checkNotNull(embeddings[it]) }),
                    )
                }
                .sortedBy(SpeakerCentroid::speakerId)
            require(centroids.isNotEmpty()) { "No valid enrollment centroids for ${model.name}" }

            val selectedThreshold = when (benchmark.config.thresholdSelection) {
                ThresholdSelectionStrategy.FIXED -> SelectedThreshold(
                    threshold = model.threshold.toFloat(),
                    margin = model.margin.toFloat(),
                    selectedOnSplit = "fixed-config",
                    selectionObjective = "fixed configuration; no development selection performed",
                )
                ThresholdSelectionStrategy.DEVELOPMENT -> {
                    val development = entries
                        .filter { it.split == DatasetSplit.DEVELOPMENT }
                        .map { entry ->
                            DevelopmentEmbedding(
                                trialId = entry.utteranceId,
                                expectedSpeakerId = entry.speakerId.takeIf { speakerId ->
                                    centroids.any { it.speakerId == speakerId }
                                },
                                embedding = checkNotNull(embeddings[entry]),
                            )
                        }
                    val tuned = ThresholdTuner.select(development, centroids)
                    SelectedThreshold(
                        threshold = tuned.threshold,
                        margin = tuned.minimumMargin,
                        selectedOnSplit = "development",
                        selectionObjective = DEVELOPMENT_SELECTION_OBJECTIVE,
                        selectionSampleCount = development.size,
                        registeredSelectionSampleCount = development.count { it.expectedSpeakerId != null },
                        unknownSelectionSampleCount = development.count { it.expectedSpeakerId == null },
                        selectionMetrics = tuned.metrics,
                    )
                }
            }
            val scorer = SpeakerScorer(selectedThreshold.threshold, selectedThreshold.margin)
            val scoredEntries = evaluationEntries.map { entry ->
                val scoring = scorer.score(checkNotNull(embeddings[entry]), centroids)
                ScoredEntry(
                    entry = entry,
                    expectedSpeakerId = entry.speakerId.takeIf {
                        entry.split != DatasetSplit.UNKNOWN && centroids.any { centroid -> centroid.speakerId == it }
                    },
                    scoring = scoring,
                    inferenceMilliseconds = checkNotNull(averageInferenceMillis[entry]),
                    embeddingSha256 = embeddingSha256(checkNotNull(embeddings[entry])),
                )
            }
            val headlineEntries = selectHeadlineEvaluationEntries(
                evaluationEntries,
                benchmark.config.durationsSeconds,
            ).toSet()
            val metricScoredEntries = scoredEntries.filter { it.entry in headlineEntries }
            val evaluationTrials = metricScoredEntries.map { scored ->
                SpeakerEvaluationTrial(
                    trialId = scored.entry.utteranceId,
                    expectedSpeakerId = scored.expectedSpeakerId,
                    durationSeconds = checkNotNull(
                        matchConfiguredDuration(
                            scored.entry.durationSeconds,
                            benchmark.config.durationsSeconds,
                        ),
                    ),
                    scoringResult = scored.scoring,
                )
            }
            val evaluated = SpeakerEvaluator.evaluate(evaluationTrials)
            val timing = MeasurementCollector.summarizeNanoseconds(timingNanoseconds)
            val byDuration = SpeakerEvaluator.evaluateByDuration(
                evaluationTrials,
                benchmark.config.durationsSeconds.toSet(),
            )
            val predictions = scoredEntries.map { scored ->
                PredictionRecord(
                    modelName = model.name,
                    utteranceId = scored.entry.utteranceId,
                    actualSpeakerId = scored.entry.speakerId,
                    predictedSpeakerId = scored.scoring.identifiedSpeakerId,
                    split = scored.entry.split.csvValue,
                    durationSeconds = scored.entry.durationSeconds,
                    highestScore = checkNotNull(scored.scoring.top1).score.toDouble(),
                    secondHighestScore = scored.scoring.top2?.score?.toDouble(),
                    scoreMargin = scored.scoring.margin?.toDouble(),
                    accepted = scored.scoring.decision == SpeakerDecision.IDENTIFIED,
                    inferenceMilliseconds = scored.inferenceMilliseconds,
                    embeddingSha256 = scored.embeddingSha256,
                )
            }
            val modelSha256 = Sha256.digest(model.modelPath)
            val parityRecords = scoredEntries.map { scored ->
                val embedding = checkNotNull(embeddings[scored.entry])
                val wav = checkNotNull(audio[scored.entry])
                ParityRecord(
                    utteranceId = scored.entry.utteranceId,
                    modelName = model.name,
                    modelSha256 = modelSha256,
                    sampleRate = wav.sampleRate,
                    numSamples = wav.samples.size,
                    durationSec = wav.durationSeconds,
                    embeddingDim = embedding.size,
                    embeddingNorm = sqrt(
                        embedding.sumOf { value -> value.toDouble() * value.toDouble() },
                    ),
                    embeddingSha256 = scored.embeddingSha256,
                    embedding = embedding.toList(),
                    speakerScores = scored.scoring.scores.associate { it.speakerId to it.score.toDouble() },
                    threshold = selectedThreshold.threshold.toDouble(),
                    margin = selectedThreshold.margin.toDouble(),
                    predictedSpeakerId = scored.scoring.identifiedSpeakerId,
                    isUnknown = scored.scoring.decision == SpeakerDecision.UNKNOWN,
                )
            }
            val scoreRecords = scoredEntries.flatMap { scored ->
                scored.scoring.scores.map { score ->
                    ScoreRecord(
                        modelName = model.name,
                        utteranceId = scored.entry.utteranceId,
                        referenceSpeakerId = score.speakerId,
                        querySpeakerId = scored.entry.speakerId,
                        score = score.score.toDouble(),
                        durationSeconds = scored.entry.durationSeconds,
                    ) to (scored.expectedSpeakerId == score.speakerId)
                }
            }
            val embeddingDimensions = embeddings.values.map(FloatArray::size).distinct()
            require(embeddingDimensions == listOf(adapter.embeddingDimension)) {
                "Inconsistent embedding dimensions for ${model.name}: $embeddingDimensions"
            }

            return ModelRun(
                metrics = ModelMetrics(
                    modelName = model.name,
                    top1Accuracy = evaluated.top1Accuracy,
                    trueAcceptRate = evaluated.correctlyIdentified.rateOf(evaluated.registeredTrials),
                    falseAcceptRate = evaluated.falseAcceptanceRate,
                    falseRejectRate = evaluated.falseRejectionRate,
                    equalErrorRate = evaluated.equalErrorRate?.rate,
                    unknownAccuracy = evaluated.unknownDetectionRate,
                    unknownFalseAcceptRate = evaluated.unknownFalseAcceptanceRate,
                    identityConfusions = evaluated.identityConfusions,
                    durationMetrics = benchmark.config.durationsSeconds.map { duration ->
                        val metric = checkNotNull(byDuration[duration])
                        DurationMetric(
                            durationSeconds = duration,
                            sampleCount = metric.totalTrials,
                            finalDecisionAccuracy = metric.finalDecisionAccuracy,
                        )
                    },
                    accuracy2Seconds = byDuration[2]?.finalDecisionAccuracy,
                    accuracy3Seconds = byDuration[3]?.finalDecisionAccuracy,
                    accuracy5Seconds = byDuration[5]?.finalDecisionAccuracy,
                    modelLoadMilliseconds = adapter.loadMilliseconds,
                    embeddingP50Milliseconds = timing.p50Milliseconds,
                    embeddingP95Milliseconds = timing.p95Milliseconds,
                    modelFileSizeBytes = Files.size(model.modelPath),
                    embeddingDimension = adapter.embeddingDimension,
                    peakObservedHeapBytes = peakObservedHeapBytes,
                    peakObservedProcessCommittedVirtualMemoryBytes = peakObservedProcessMemoryBytes,
                    evaluationSampleCount = evaluationTrials.size,
                ),
                predictions = predictions,
                parityRecords = parityRecords,
                threshold = ThresholdRecord(
                    modelName = model.name,
                    threshold = selectedThreshold.threshold.toDouble(),
                    margin = selectedThreshold.margin.toDouble(),
                    selectedOnSplit = selectedThreshold.selectedOnSplit,
                    selectionObjective = selectedThreshold.selectionObjective,
                    selectionSampleCount = selectedThreshold.selectionSampleCount,
                    registeredSelectionSampleCount = selectedThreshold.registeredSelectionSampleCount,
                    unknownSelectionSampleCount = selectedThreshold.unknownSelectionSampleCount,
                    selectedFalseAcceptRate = selectedThreshold.selectionMetrics?.falseAcceptanceRate,
                    selectedFalseRejectRate = selectedThreshold.selectionMetrics?.falseRejectionRate,
                    selectedIdentityConfusions = selectedThreshold.selectionMetrics?.identityConfusions,
                    selectedFinalDecisionAccuracy = selectedThreshold.selectionMetrics?.finalDecisionAccuracy,
                ),
                sameSpeakerScores = scoreRecords.filter { it.second }.map { it.first },
                differentSpeakerScores = scoreRecords.filterNot { it.second }.map { it.first },
                modelArtifact = ModelArtifactRecord(
                    modelName = model.name,
                    adapter = model.adapter,
                    fileName = model.modelPath.fileName.toString(),
                    sha256 = modelSha256,
                    fileSizeBytes = Files.size(model.modelPath),
                    sampleRate = model.sampleRate,
                    embeddingDimension = adapter.embeddingDimension,
                    loadMilliseconds = adapter.loadMilliseconds,
                    runtimeVersion = adapter.runtimeVersion,
                ),
            )
        }
    }

    private fun embeddingSha256(embedding: FloatArray): String {
        val bytes = ByteBuffer.allocate(embedding.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach(bytes::putFloat)
        return Sha256.digest(bytes.array())
    }

    internal fun benchmarkLimitations(
        entries: List<DatasetEntry>,
        configuredDurationsSeconds: List<Int>,
    ): List<String> {
        val evaluationEntries = entries.filter {
            it.split == DatasetSplit.TEST || it.split == DatasetSplit.UNKNOWN
        }
        val enrolledSpeakers = entries
            .filter { it.split == DatasetSplit.ENROLLMENT }
            .map(DatasetEntry::speakerId)
            .distinct()
            .size
        val unknownSpeakers = entries
            .filter { it.split == DatasetSplit.UNKNOWN }
            .map(DatasetEntry::speakerId)
            .distinct()
            .toSet()
        val enrolledSpeakerIds = entries
            .filter { it.split == DatasetSplit.ENROLLMENT }
            .map(DatasetEntry::speakerId)
            .toSet()
        val developmentEntries = entries.filter { it.split == DatasetSplit.DEVELOPMENT }
        val unregisteredDevelopmentSpeakers = developmentEntries
            .map(DatasetEntry::speakerId)
            .toSet() - enrolledSpeakerIds
        return buildList {
            if (enrolledSpeakers < 4 || unknownSpeakers.size < 4) {
                add(
                    "Statistically insufficient dataset: enrolled speakers=$enrolledSpeakers, " +
                        "unknown speakers=${unknownSpeakers.size}; at least 4 each are required for the PoC acceptance run.",
                )
            }
            val registeredCountFailures = enrolledSpeakerIds.mapNotNull { speakerId ->
                val enrollmentCount = entries.count {
                    it.speakerId == speakerId && it.split == DatasetSplit.ENROLLMENT
                }
                val developmentCount = entries.count {
                    it.speakerId == speakerId && it.split == DatasetSplit.DEVELOPMENT
                }
                val testCount = entries.count {
                    it.speakerId == speakerId && it.split == DatasetSplit.TEST
                }
                if (enrollmentCount >= 5 && developmentCount >= 5 && testCount >= 10) null else
                    "$speakerId(enrollment=$enrollmentCount,development=$developmentCount,test=$testCount)"
            }
            if (registeredCountFailures.isNotEmpty()) {
                add(
                    "Insufficient registered-speaker recordings; each speaker requires at least " +
                        "5 enrollment, 5 development, and 10 test clips: " +
                        registeredCountFailures.joinToString(),
                )
            }
            val finalUnknownCountFailures = unknownSpeakers.mapNotNull { speakerId ->
                val count = entries.count {
                    it.speakerId == speakerId && it.split == DatasetSplit.UNKNOWN
                }
                if (count >= 10) null else "$speakerId(unknown=$count)"
            }
            if (finalUnknownCountFailures.isNotEmpty()) {
                add(
                    "Insufficient final-unknown recordings; each speaker requires at least 10 clips: " +
                        finalUnknownCountFailures.joinToString(),
                )
            }
            if (developmentEntries.isNotEmpty()) {
                val developmentUnknownCountFailures = unregisteredDevelopmentSpeakers.mapNotNull { speakerId ->
                    val count = developmentEntries.count { it.speakerId == speakerId }
                    if (count >= 5) null else "$speakerId(development=$count)"
                }
                if (unregisteredDevelopmentSpeakers.size < 4 || developmentUnknownCountFailures.isNotEmpty()) {
                    add(
                        "Insufficient development-unknown data; at least 4 speakers and 5 clips per speaker " +
                            "are required (speakers=${unregisteredDevelopmentSpeakers.size}, failures=" +
                            developmentUnknownCountFailures.joinToString() + ").",
                    )
                }
            }
            if (evaluationEntries.isEmpty() || evaluationEntries.any {
                    !it.language.equals("ja", ignoreCase = true)
                }
            ) {
                add("All test and final-unknown evaluation speech must be Japanese for the acceptance run.")
            }
            val missingDurationGroups = buildList {
                configuredDurationsSeconds.forEach { duration ->
                    if (evaluationEntries.none {
                            it.split == DatasetSplit.TEST &&
                                matchConfiguredDuration(it.durationSeconds, listOf(duration)) == duration
                        }
                    ) add("test:${duration}s")
                    if (evaluationEntries.none {
                            it.split == DatasetSplit.UNKNOWN &&
                                matchConfiguredDuration(it.durationSeconds, listOf(duration)) == duration
                        }
                    ) add("unknown:${duration}s")
                }
            }
            if (missingDurationGroups.isNotEmpty()) {
                add(
                    "The evaluation splits do not contain configured duration groups: " +
                        missingDurationGroups.joinToString() +
                        " (required tolerance: +/-$DURATION_GROUP_TOLERANCE_SECONDS seconds).",
                )
            }
        }
    }

    private data class ScoredEntry(
        val entry: DatasetEntry,
        val expectedSpeakerId: String?,
        val scoring: com.example.pepper_person_id_poc.speakercore.SpeakerScoringResult,
        val inferenceMilliseconds: Double,
        val embeddingSha256: String,
    )

    private data class ModelRun(
        val metrics: ModelMetrics,
        val predictions: List<PredictionRecord>,
        val threshold: ThresholdRecord,
        val sameSpeakerScores: List<ScoreRecord>,
        val differentSpeakerScores: List<ScoreRecord>,
        val modelArtifact: ModelArtifactRecord,
        val parityRecords: List<ParityRecord>,
    )

    private data class SelectedThreshold(
        val threshold: Float,
        val margin: Float,
        val selectedOnSplit: String,
        val selectionObjective: String,
        val selectionSampleCount: Int? = null,
        val registeredSelectionSampleCount: Int? = null,
        val unknownSelectionSampleCount: Int? = null,
        val selectionMetrics: com.example.pepper_person_id_poc.speakercore.SpeakerEvaluationMetrics? = null,
    )

    private fun Int.rateOf(total: Int): Double? = if (total == 0) null else toDouble() / total

    private companion object {
        const val NANOSECONDS_PER_MILLISECOND = 1_000_000.0
        const val DEVELOPMENT_SELECTION_OBJECTIVE =
            "maximize final-decision accuracy, then minimize FAR, identity confusions, and FRR; " +
                "tie-break by higher threshold and lower margin"
    }
}

internal const val DURATION_GROUP_TOLERANCE_SECONDS = 0.05

/** Returns a configured bucket only when the measured duration is within the strict tolerance. */
internal fun matchConfiguredDuration(durationSeconds: Double, configuredDurationsSeconds: List<Int>): Int? =
    configuredDurationsSeconds.singleOrNull { configured ->
        kotlin.math.abs(durationSeconds - configured.toDouble()) <= DURATION_GROUP_TOLERANCE_SECONDS
    }

/** Only configured-duration test/final-unknown rows contribute to headline accuracy/FAR/FRR/EER. */
internal fun selectHeadlineEvaluationEntries(
    entries: List<DatasetEntry>,
    configuredDurationsSeconds: List<Int>,
): List<DatasetEntry> = entries.filter { entry ->
    (entry.split == DatasetSplit.TEST || entry.split == DatasetSplit.UNKNOWN) &&
        matchConfiguredDuration(entry.durationSeconds, configuredDurationsSeconds) != null
}
