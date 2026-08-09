package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousPersistencePolicy
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.example.pepper_person_id_poc.domain.speaker.DiarizedSpeakerSegment
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerEmbeddingAggregator
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrack
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrackLinker
import com.example.pepper_person_id_poc.domain.speaker.SoloSpeakerSegmentSelector
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityAssessment
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityInput
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityPolicy
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityThresholds
import com.example.pepper_person_id_poc.domain.speaker.SpeakerSegmentEmbedding
import com.example.pepper_person_id_poc.domain.speaker.WindowSpeakerObservation
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpeakerIdentityCoordinator(
    private val repository: AnonymousSpeakerClusterRepository,
    private val embeddingEngine: SpeakerEmbeddingEngine,
    private val threshold: Float,
    private val maximumUpdateCount: Int,
    private val benchmarkLogger: BenchmarkLogger,
    private val segmentationEngine: SpeakerSegmentationEngine? = null,
    private val embeddingModelSpaceId: ModelSpaceId = ModelSpaceId(embeddingEngine.modelName),
    private val embeddingArtifactId: String? = null,
    private val embeddingRuntimeId: String? = null,
    private val qualityPolicy: SpeakerAudioQualityPolicy = defaultQualityPolicy(),
    private val overlapDisplayThreshold: Float = DEFAULT_OVERLAP_DISPLAY_THRESHOLD,
    private val clippingAmplitudeThreshold: Float = CLIPPING_THRESHOLD,
    private val trackLinker: LocalSpeakerTrackLinker = LocalSpeakerTrackLinker(),
    private val segmentSelector: SoloSpeakerSegmentSelector = SoloSpeakerSegmentSelector(),
    private val embeddingAggregator: LocalSpeakerEmbeddingAggregator = LocalSpeakerEmbeddingAggregator(),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(
        SpeakerIdentityUiState(
            modelName = embeddingEngine.modelName,
            modelSpaceId = embeddingModelSpaceId.value,
            embeddingArtifactId = embeddingArtifactId,
            embeddingRuntimeId = embeddingRuntimeId,
            activityArtifactId = segmentationEngine?.artifactId?.value,
            activityRuntimeId = segmentationEngine?.runtimeId?.value,
            activityHoldReasons = if (segmentationEngine == null) {
                listOf(UNSUPPORTED_ACTIVITY_INFERENCE)
            } else {
                emptyList()
            },
        ),
    )
    val state: StateFlow<SpeakerIdentityUiState> = mutableState.asStateFlow()
    private var nextWindowNumber = 1L

    init {
        require(overlapDisplayThreshold in 0f..1f)
        scope.launch {
            val embeddingPreparation = runCatching { embeddingEngine.prepare() }
            val activityPreparation = segmentationEngine?.let { engine ->
                runCatching { engine.prepare() }
            }
            mutableState.value = mutableState.value.copy(
                modelReady = embeddingPreparation.isSuccess,
                modelDimension = embeddingEngine.embeddingDimension,
                activityReady = activityPreparation?.isSuccess == true,
                activityHoldReasons = when {
                    segmentationEngine == null -> listOf(UNSUPPORTED_ACTIVITY_INFERENCE)
                    activityPreparation?.isFailure == true -> listOf(ACTIVITY_INFERENCE_ERROR)
                    else -> emptyList()
                },
                error = embeddingPreparation.exceptionOrNull()?.message
                    ?: activityPreparation?.exceptionOrNull()?.message,
            )
        }
    }

    fun onUtterance(utterance: PcmUtterance) {
        if (utterance.sampleRate != REQUIRED_SAMPLE_RATE) {
            holdWithoutMutation(utterance, listOf(UNSUPPORTED_SAMPLE_RATE))
            return
        }
        if (!utterance.sufficientForSpeakerIdentification) {
            holdWithoutMutation(utterance, listOf(INSUFFICIENT_AUDIO))
            return
        }
        val activityEngine = segmentationEngine
        if (activityEngine == null) {
            holdWithoutMutation(utterance, listOf(UNSUPPORTED_ACTIVITY_INFERENCE))
            return
        }
        val requiredWindowSamples = activityEngine.requiredWindowSamples
        if (requiredWindowSamples != null && utterance.pcm16.size > requiredWindowSamples) {
            holdWithoutMutation(utterance, listOf(ACTIVITY_WINDOW_TOO_LONG))
            return
        }
        mutableState.value = mutableState.value.copy(
            processing = true,
            localTracks = emptyList(),
            error = null,
        )
        scope.launch {
            mutex.withLock {
                processUtterance(utterance, activityEngine)
            }
        }
    }

    private fun processUtterance(
        utterance: PcmUtterance,
        activityEngine: SpeakerSegmentationEngine,
    ) {
        val pipelineStarted = System.nanoTime()
        runCatching {
            val windowId = "speaker-window-${nextWindowNumber++.toString().padStart(6, '0')}"
            val activityStarted = System.nanoTime()
            val segmentationPcm = activityEngine.requiredWindowSamples?.let { requiredSamples ->
                utterance.pcm16.copyOf(requiredSamples)
            } ?: utterance.pcm16
            val window = activityEngine.segment(
                SpeakerSegmentationInput(
                    windowId = windowId,
                    startSample = 0L,
                    pcm16 = segmentationPcm,
                    sampleRate = utterance.sampleRate,
                ),
            )
            val activityTimeMillis = elapsedMillis(activityStarted)
            require(window.sampleRate == REQUIRED_SAMPLE_RATE)
            require(window.startSample == 0L && window.endSample == segmentationPcm.size.toLong())

            val segments = window.toSegments(utterance.pcm16.size.toLong())
            val selection = segmentSelector.select(segments)
            if (!selection.persistenceEligible) {
                return@runCatching PipelineOutcome.hold(
                    window = window,
                    tracks = emptyList(),
                    reasons = selection.holdReasons,
                    activityTimeMillis = activityTimeMillis,
                    totalTimeMillis = elapsedMillis(pipelineStarted),
                        utteranceDurationMillis = utterance.voicedDurationMillis,
                )
            }

            val embeddingStarted = System.nanoTime()
            val rawSegmentEmbeddings = selection.soloSegments.mapIndexed { segmentIndex, segment ->
                val pcm = utterance.pcm16.slice(segment.startSample, segment.endSample)
                SpeakerSegmentEmbedding(
                    localSpeakerId = "window-segment-$segmentIndex",
                    embedding = embeddingEngine.extract(pcm, utterance.sampleRate),
                    durationMillis = sampleDurationMillis(pcm.size, utterance.sampleRate),
                )
            }
            val embeddingTimeMillis = elapsedMillis(embeddingStarted)
            var aggregationTimeMillis = 0L
            val firstAggregationStarted = System.nanoTime()
            val windowSpeakerAggregates = embeddingAggregator.aggregate(rawSegmentEmbeddings)
            aggregationTimeMillis += elapsedMillis(firstAggregationStarted)
            val segmentsByWindowSegment = selection.soloSegments.mapIndexed { segmentIndex, segment ->
                "window-segment-$segmentIndex" to segment
            }.toMap()
            val observations = windowSpeakerAggregates.map { aggregate ->
                val speakerSegment = segmentsByWindowSegment.getValue(aggregate.localSpeakerId)
                WindowSpeakerObservation(
                    windowSpeakerIndex = aggregate.localSpeakerId.substringAfterLast('-').toInt(),
                    startSample = speakerSegment.startSample,
                    endSample = speakerSegment.endSample,
                    activityState = SpeakerActivityState.SINGLE_SPEAKER,
                    segments = listOf(speakerSegment.copy(localSpeakerId = aggregate.localSpeakerId)),
                )
            }
            val trackingStarted = System.nanoTime()
            val linkResult = trackLinker.link(window.windowId, observations)
            val trackingTimeMillis = elapsedMillis(trackingStarted)
            if (linkResult.holdReasons.isNotEmpty()) {
                return@runCatching PipelineOutcome.hold(
                    window = window,
                    tracks = linkResult.tracks,
                    reasons = linkResult.holdReasons,
                    activityTimeMillis = activityTimeMillis,
                    trackingTimeMillis = trackingTimeMillis,
                    embeddingTimeMillis = embeddingTimeMillis,
                    aggregationTimeMillis = aggregationTimeMillis,
                    totalTimeMillis = elapsedMillis(pipelineStarted),
                    utteranceDurationMillis = utterance.voicedDurationMillis,
                )
            }

            val localIdByWindowSegment = linkResult.assignments.associate {
                "window-segment-${it.windowSpeakerIndex}" to it.localSpeakerId
            }
            val localSegmentEmbeddings = rawSegmentEmbeddings.map { segment ->
                segment.copy(localSpeakerId = localIdByWindowSegment.getValue(segment.localSpeakerId))
            }
            val localSegments = selection.soloSegments.mapIndexed { segmentIndex, segment ->
                segment.copy(localSpeakerId = localIdByWindowSegment.getValue("window-segment-$segmentIndex"))
            }
            val secondAggregationStarted = System.nanoTime()
            val aggregates = embeddingAggregator.aggregate(localSegmentEmbeddings)
            aggregationTimeMillis += elapsedMillis(secondAggregationStarted)
            val modelSpaceId = embeddingModelSpaceId
            val selectedAnonymousIds = mutableSetOf<String>()
            val results = linkedMapOf<String, AnonymousIdentificationResult>()
            val qualities = linkedMapOf<String, SpeakerAudioQualityAssessment>()
            val holdReasons = linkedSetOf<String>()
            var qualityTimeMillis = 0L
            var scoringTimeMillis = 0L
            var policyTimeMillis = 0L
            var repositoryTimeMillis = 0L

            aggregates.forEach { aggregate ->
                val speakerSegments = localSegments.filter { it.localSpeakerId == aggregate.localSpeakerId }
                val qualityStarted = System.nanoTime()
                val quality = qualityPolicy.assess(
                    qualityInput(
                        utterance = utterance,
                        segments = speakerSegments,
                    ),
                )
                qualityTimeMillis += elapsedMillis(qualityStarted)
                val scoringStarted = System.nanoTime()
                val evaluation = repository.evaluate(
                    modelSpaceId = modelSpaceId,
                    embedding = aggregate.embedding,
                    threshold = threshold,
                    minimumLead = 0f,
                )
                scoringTimeMillis += elapsedMillis(scoringStarted)
                val selectedId = evaluation.candidates.firstOrNull { it.selected }?.anonymousId
                val duplicateSelection = selectedId != null && selectedId in selectedAnonymousIds
                val policyStarted = System.nanoTime()
                val (policy, operation) = AnonymousPersistencePolicy.evaluate(
                    decision = evaluation.decision,
                    createEligible = quality.createEligible && !duplicateSelection,
                    updateEligible = quality.updateEligible && !duplicateSelection,
                )
                policyTimeMillis += elapsedMillis(policyStarted)
                if (duplicateSelection) holdReasons += ANONYMOUS_ID_ALREADY_SELECTED
                holdReasons += quality.rejectionReasons
                val repositoryStarted = System.nanoTime()
                val cluster = repository.apply(
                    operation = operation,
                    modelSpaceId = modelSpaceId,
                    embedding = aggregate.embedding,
                    selectedAnonymousId = selectedId,
                    maximumUpdateCount = maximumUpdateCount,
                    nowElapsedRealtime = System.currentTimeMillis(),
                )
                repositoryTimeMillis += elapsedMillis(repositoryStarted)
                if (operation != PersistenceOperation.HOLD && cluster != null) {
                    selectedAnonymousIds += cluster.anonymousId
                }
                val result = AnonymousIdentificationResult(
                    anonymousId = cluster?.anonymousId ?: selectedId ?: "unknown",
                    modelId = modelSpaceId.value,
                    bestExistingScore = evaluation.highestScore,
                    threshold = threshold,
                    isNewCluster = operation == PersistenceOperation.CREATE,
                    updateCount = cluster?.updateCount ?: 0,
                    maximumUpdateCount = maximumUpdateCount,
                    currentModelClusterCount = repository.getAll().count {
                        it.modelSpaceId == modelSpaceId &&
                            it.embeddingDimension == aggregate.embedding.size
                    },
                    totalClusterCount = repository.count(),
                    candidateScores = evaluation.candidates.map {
                        AnonymousClusterScore(
                            anonymousId = it.anonymousId,
                            score = it.score,
                            selected = it.anonymousId == selectedId,
                        )
                    },
                    evaluation = evaluation,
                    persistencePolicy = policy,
                    persistenceOperation = operation,
                )
                results[aggregate.localSpeakerId] = result
                qualities[aggregate.localSpeakerId] = quality
            }

            PipelineOutcome(
                window = window,
                tracks = linkResult.tracks,
                results = results,
                qualities = qualities,
                holdReasons = holdReasons.toList(),
                activityTimeMillis = activityTimeMillis,
                vadTimeMillis = utterance.vadProcessingMillis,
                trackingTimeMillis = trackingTimeMillis,
                qualityTimeMillis = qualityTimeMillis,
                embeddingTimeMillis = embeddingTimeMillis,
                aggregationTimeMillis = aggregationTimeMillis,
                scoringTimeMillis = scoringTimeMillis,
                policyTimeMillis = policyTimeMillis,
                repositoryTimeMillis = repositoryTimeMillis,
                totalTimeMillis = elapsedMillis(pipelineStarted),
                utteranceDurationMillis = utterance.voicedDurationMillis,
            )
        }.onSuccess { outcome ->
            mutableState.value = mutableState.value.copy(
                observationSequence = mutableState.value.observationSequence + 1,
                processing = false,
                result = outcome.results.values.firstOrNull(),
                results = outcome.results,
                currentModelClusterCount = outcome.results.values.maxOfOrNull {
                    it.currentModelClusterCount
                } ?: mutableState.value.currentModelClusterCount,
                totalClusterCount = repository.count(),
                inferenceTimeMillis = outcome.embeddingTimeMillis,
                lastUtteranceStartedAtMillis = utterance.startedAtMillis,
                lastUtteranceEndedAtMillis = utterance.endedAtMillis,
                activityReady = true,
                activityState = outcome.window.dominantState(),
                overlapRatio = outcome.window.overlapRatio,
                activeSpeakerCount = outcome.window.frames.maxOfOrNull { it.activeSpeakerCount } ?: 0,
                localTracks = outcome.tracks,
                qualityByLocalSpeaker = outcome.qualities,
                activityHoldReasons = outcome.holdReasons,
                activityInferenceTimeMillis = outcome.activityTimeMillis,
                totalPipelineTimeMillis = outcome.totalTimeMillis,
                realTimeFactor = outcome.realTimeFactor,
                stageTimings = outcome.stageTimings,
                error = null,
            )
            appendBenchmarkEvent(outcome)
        }.onFailure {
            mutableState.value = mutableState.value.copy(
                processing = false,
                result = null,
                results = emptyMap(),
                localTracks = emptyList(),
                activityState = SpeakerActivityState.ERROR,
                activityHoldReasons = listOf(ACTIVITY_INFERENCE_ERROR),
                error = it.message ?: it::class.java.simpleName,
            )
            appendFailureBenchmarkEvent(utterance, it)
        }
    }

    private fun appendBenchmarkEvent(outcome: PipelineOutcome) {
        runCatching {
            benchmarkLogger.append(
                BenchmarkEvent(
                    event = "speaker_pipeline",
                    timestampMillis = System.currentTimeMillis(),
                    durationMillis = outcome.totalTimeMillis,
                    status = if (outcome.holdReasons.isEmpty()) "SUCCESS" else "HOLD",
                    attributes = outcome.stageTimings.toAttributes() + mapOf(
                        "modelSpaceId" to embeddingModelSpaceId.value,
                        "embeddingArtifactId" to (embeddingArtifactId ?: "N/A"),
                        "embeddingRuntimeId" to (embeddingRuntimeId ?: "N/A"),
                        "activityArtifactId" to (segmentationEngine?.artifactId?.value ?: "N/A"),
                        "activityRuntimeId" to (segmentationEngine?.runtimeId?.value ?: "N/A"),
                        "localSpeakerCount" to outcome.results.size.toString(),
                        "candidateCount" to outcome.results.values
                            .sumOf { it.candidateScores.size }
                            .toString(),
                        "holdReasons" to outcome.holdReasons.joinToString(),
                    ),
                ),
            )
        }
    }

    private fun appendFailureBenchmarkEvent(
        utterance: PcmUtterance,
        throwable: Throwable,
    ) {
        runCatching {
            benchmarkLogger.append(
                BenchmarkEvent(
                    event = "speaker_pipeline",
                    timestampMillis = System.currentTimeMillis(),
                    status = "FAILURE",
                    attributes = mapOf(
                        "audioDurationMillis" to utterance.durationMillis.toString(),
                        "modelSpaceId" to embeddingModelSpaceId.value,
                    ),
                    error = throwable.message ?: throwable::class.java.simpleName,
                ),
            )
        }
    }

    private fun holdWithoutMutation(
        utterance: PcmUtterance,
        reasons: List<String>,
    ) {
        mutableState.value = mutableState.value.copy(
            processing = false,
            result = null,
            results = emptyMap(),
            localTracks = emptyList(),
            lastUtteranceStartedAtMillis = utterance.startedAtMillis,
            lastUtteranceEndedAtMillis = utterance.endedAtMillis,
            activityState = when {
                UNSUPPORTED_ACTIVITY_INFERENCE in reasons -> SpeakerActivityState.UNSUPPORTED
                else -> mutableState.value.activityState
            },
            activityHoldReasons = reasons,
            error = reasons.joinToString(),
        )
        val timings = SpeakerStageTimings(
            recorderMillis = null,
            vadMillis = null,
            activityMillis = null,
            trackingMillis = null,
            qualityMillis = null,
            embeddingMillis = null,
            aggregationMillis = null,
            scoringMillis = null,
            policyMillis = null,
            repositoryMillis = null,
            totalMillis = null,
            audioDurationMillis = utterance.voicedDurationMillis,
        )
        runCatching {
            benchmarkLogger.append(
                BenchmarkEvent(
                    event = "speaker_pipeline",
                    timestampMillis = System.currentTimeMillis(),
                    status = "HOLD",
                    attributes = timings.toAttributes() + mapOf(
                        "modelSpaceId" to embeddingModelSpaceId.value,
                        "holdReasons" to reasons.joinToString(),
                    ),
                ),
            )
        }
    }

    override fun close() {
        scope.cancel()
        segmentationEngine?.close()
        embeddingEngine.close()
    }

    private fun DiarizationWindow.toSegments(actualEndSample: Long): List<DiarizedSpeakerSegment> {
        if (frames.isEmpty()) {
            return listOf(
                DiarizedSpeakerSegment(
                    localSpeakerId = "unsupported",
                    startSample = startSample,
                    endSample = minOf(endSample, actualEndSample),
                    activityState = SpeakerActivityState.UNSUPPORTED,
                    confidence = 0f,
                    isSolo = false,
                ),
            )
        }
        val totalSamples = endSample - startSample
        return frames.flatMapIndexed { index, frame ->
            val frameStart = startSample + totalSamples * index / frames.size
            val frameEnd = minOf(
                startSample + totalSamples * (index + 1) / frames.size,
                actualEndSample,
            )
            if (frameEnd <= frameStart) return@flatMapIndexed emptyList()
            when {
                frame.activityState == SpeakerActivityState.SINGLE_SPEAKER ->
                    frame.activeSpeakerIndices.map { speakerIndex ->
                        DiarizedSpeakerSegment(
                            localSpeakerId = "window-speaker-$speakerIndex",
                            startSample = frameStart,
                            endSample = frameEnd,
                            activityState = frame.activityState,
                            confidence = frame.winningScore,
                            isSolo = true,
                        )
                    }

                else -> listOf(
                    DiarizedSpeakerSegment(
                        localSpeakerId = "activity-${frame.activityState.name.lowercase()}",
                        startSample = frameStart,
                        endSample = frameEnd,
                        activityState = frame.activityState,
                        confidence = frame.winningScore,
                        isSolo = false,
                    ),
                )
            }
        }.mergeAdjacentSegments()
    }

    private fun List<DiarizedSpeakerSegment>.mergeAdjacentSegments(): List<DiarizedSpeakerSegment> =
        fold(emptyList()) { merged, segment ->
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.localSpeakerId == segment.localSpeakerId &&
                previous.activityState == segment.activityState &&
                previous.endSample == segment.startSample
            ) {
                merged.dropLast(1) + previous.copy(
                    endSample = segment.endSample,
                    confidence = minOf(previous.confidence, segment.confidence),
                )
            } else {
                merged + segment
            }
        }

    private fun qualityInput(
        utterance: PcmUtterance,
        segments: List<DiarizedSpeakerSegment>,
    ): SpeakerAudioQualityInput {
        val samples = segments.flatMap { segment ->
            utterance.pcm16.slice(segment.startSample, segment.endSample).asList()
        }
        val normalized = samples.map { it / Short.MAX_VALUE.toFloat() }
        val peak = normalized.maxOfOrNull(::abs) ?: 0f
        val rms = if (normalized.isEmpty()) {
            0f
        } else {
            sqrt(normalized.sumOf { (it * it).toDouble() } / normalized.size).toFloat()
        }
        val clippingRatio = if (normalized.isEmpty()) {
            0f
        } else {
            normalized.count { abs(it) >= clippingAmplitudeThreshold }.toFloat() / normalized.size
        }
        val durationMillis = sampleDurationMillis(samples.size, utterance.sampleRate)
        return SpeakerAudioQualityInput(
            sampleRate = utterance.sampleRate,
            sampleCount = samples.size,
            durationMillis = durationMillis,
            voicedRatio = if (samples.isEmpty()) 0f else 1f,
            peak = peak,
            rms = rms,
            clippingRatio = clippingRatio,
            snr = null,
            overlapRatio = 0f,
            activeSpeakerCount = if (samples.isEmpty()) 0 else 1,
        )
    }

    private fun ShortArray.slice(startSample: Long, endSample: Long): ShortArray {
        val start = startSample.toInt().coerceIn(0, size)
        val end = endSample.toInt().coerceIn(start, size)
        return copyOfRange(start, end)
    }

    private fun DiarizationWindow.dominantState(): SpeakerActivityState {
        val states = frames.map { it.activityState }.toSet()
        val overlapFrameRatio = frames.count { it.activeSpeakerCount > 1 }
            .toFloat() / frames.size.coerceAtLeast(1)
        return when {
            SpeakerActivityState.ERROR in states -> SpeakerActivityState.ERROR
            SpeakerActivityState.UNSUPPORTED in states -> SpeakerActivityState.UNSUPPORTED
            overlapFrameRatio >= overlapDisplayThreshold -> SpeakerActivityState.OVERLAPPED_SPEECH
            SpeakerActivityState.SINGLE_SPEAKER in states -> SpeakerActivityState.SINGLE_SPEAKER
            else -> SpeakerActivityState.SILENCE
        }
    }

    private fun elapsedMillis(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L

    private fun sampleDurationMillis(sampleCount: Int, sampleRate: Int): Long =
        sampleCount * 1_000L / sampleRate

    private data class PipelineOutcome(
        val window: DiarizationWindow,
        val tracks: List<LocalSpeakerTrack>,
        val results: Map<String, AnonymousIdentificationResult>,
        val qualities: Map<String, SpeakerAudioQualityAssessment>,
        val holdReasons: List<String>,
        val activityTimeMillis: Long,
        val vadTimeMillis: Long? = null,
        val trackingTimeMillis: Long?,
        val qualityTimeMillis: Long?,
        val embeddingTimeMillis: Long?,
        val aggregationTimeMillis: Long?,
        val scoringTimeMillis: Long?,
        val policyTimeMillis: Long?,
        val repositoryTimeMillis: Long?,
        val totalTimeMillis: Long,
        val utteranceDurationMillis: Long,
    ) {
        val realTimeFactor: Float?
            get() = utteranceDurationMillis.takeIf { it > 0L }?.let {
                totalTimeMillis.toFloat() / it
            }
        val stageTimings: SpeakerStageTimings
            get() = SpeakerStageTimings(
                recorderMillis = null,
                vadMillis = vadTimeMillis,
                activityMillis = activityTimeMillis,
                trackingMillis = trackingTimeMillis,
                qualityMillis = qualityTimeMillis,
                embeddingMillis = embeddingTimeMillis,
                aggregationMillis = aggregationTimeMillis,
                scoringMillis = scoringTimeMillis,
                policyMillis = policyTimeMillis,
                repositoryMillis = repositoryTimeMillis,
                totalMillis = totalTimeMillis,
                audioDurationMillis = utteranceDurationMillis,
            )

        companion object {
            fun hold(
                window: DiarizationWindow,
                tracks: List<LocalSpeakerTrack>,
                reasons: List<String>,
                activityTimeMillis: Long,
                totalTimeMillis: Long,
                utteranceDurationMillis: Long,
                trackingTimeMillis: Long? = null,
                qualityTimeMillis: Long? = null,
                embeddingTimeMillis: Long? = null,
                aggregationTimeMillis: Long? = null,
                scoringTimeMillis: Long? = null,
                policyTimeMillis: Long? = null,
                repositoryTimeMillis: Long? = null,
            ) = PipelineOutcome(
                window = window,
                tracks = tracks,
                results = emptyMap(),
                qualities = emptyMap(),
                holdReasons = reasons,
                activityTimeMillis = activityTimeMillis,
                trackingTimeMillis = trackingTimeMillis,
                qualityTimeMillis = qualityTimeMillis,
                embeddingTimeMillis = embeddingTimeMillis,
                aggregationTimeMillis = aggregationTimeMillis,
                scoringTimeMillis = scoringTimeMillis,
                policyTimeMillis = policyTimeMillis,
                repositoryTimeMillis = repositoryTimeMillis,
                totalTimeMillis = totalTimeMillis,
                utteranceDurationMillis = utteranceDurationMillis,
            )
        }
    }

    private companion object {
        const val REQUIRED_SAMPLE_RATE = 16_000
        const val DEFAULT_OVERLAP_DISPLAY_THRESHOLD = 0.50f
        const val CLIPPING_THRESHOLD = 0.999f
        const val UNSUPPORTED_SAMPLE_RATE = "UNSUPPORTED_SAMPLE_RATE"
        const val INSUFFICIENT_AUDIO = "INSUFFICIENT_AUDIO"
        const val UNSUPPORTED_ACTIVITY_INFERENCE = "UNSUPPORTED_ACTIVITY_INFERENCE"
        const val ACTIVITY_INFERENCE_ERROR = "ACTIVITY_INFERENCE_ERROR"
        const val ACTIVITY_WINDOW_TOO_LONG = "ACTIVITY_WINDOW_TOO_LONG"
        const val ANONYMOUS_ID_ALREADY_SELECTED = "ANONYMOUS_ID_ALREADY_SELECTED"

        fun defaultQualityPolicy() = SpeakerAudioQualityPolicy(
            SpeakerAudioQualityThresholds(
                requiredSampleRate = REQUIRED_SAMPLE_RATE,
                minimumDurationMillis = 1_000L,
                minimumVoicedRatio = 0.50f,
                minimumRms = 0f,
                maximumClippingRatio = 0.05f,
                maximumOverlapRatio = 0f,
                maximumActiveSpeakerCount = 1,
                updateMinimumDurationMillis = 1_000L,
                updateMinimumVoicedRatio = 0.50f,
            ),
        )
    }
}

data class SpeakerStageTimings(
    val recorderMillis: Long?,
    val vadMillis: Long?,
    val activityMillis: Long?,
    val trackingMillis: Long?,
    val qualityMillis: Long?,
    val embeddingMillis: Long?,
    val aggregationMillis: Long?,
    val scoringMillis: Long?,
    val policyMillis: Long?,
    val repositoryMillis: Long?,
    val totalMillis: Long?,
    val audioDurationMillis: Long?,
) {
    init {
        listOf(
            recorderMillis,
            vadMillis,
            activityMillis,
            trackingMillis,
            qualityMillis,
            embeddingMillis,
            aggregationMillis,
            scoringMillis,
            policyMillis,
            repositoryMillis,
            totalMillis,
            audioDurationMillis,
        ).forEach { require(it == null || it >= 0L) }
    }

    val realTimeFactor: Float?
        get() {
            val processing = totalMillis ?: return null
            val audioDuration = audioDurationMillis?.takeIf { it > 0L } ?: return null
            return processing.toFloat() / audioDuration
        }

    fun toAttributes(): Map<String, String> = linkedMapOf(
        "recorderMillis" to recorderMillis.measurement(),
        "vadMillis" to vadMillis.measurement(),
        "activityMillis" to activityMillis.measurement(),
        "trackingMillis" to trackingMillis.measurement(),
        "qualityMillis" to qualityMillis.measurement(),
        "embeddingMillis" to embeddingMillis.measurement(),
        "aggregationMillis" to aggregationMillis.measurement(),
        "scoringMillis" to scoringMillis.measurement(),
        "policyMillis" to policyMillis.measurement(),
        "repositoryMillis" to repositoryMillis.measurement(),
        "totalMillis" to totalMillis.measurement(),
        "audioDurationMillis" to audioDurationMillis.measurement(),
        "realTimeFactor" to (realTimeFactor?.toString() ?: "null"),
    )

    private fun Long?.measurement() = this?.toString() ?: "null"
}

data class SpeakerIdentityUiState(
    val modelName: String,
    val observationSequence: Long = 0L,
    val modelSpaceId: String = modelName,
    val embeddingArtifactId: String? = null,
    val embeddingRuntimeId: String? = null,
    val modelReady: Boolean = false,
    val modelDimension: Int? = null,
    val processing: Boolean = false,
    val result: AnonymousIdentificationResult? = null,
    val results: Map<String, AnonymousIdentificationResult> = emptyMap(),
    val currentModelClusterCount: Int = 0,
    val totalClusterCount: Int = 0,
    val inferenceTimeMillis: Long? = null,
    val lastUtteranceStartedAtMillis: Long? = null,
    val lastUtteranceEndedAtMillis: Long? = null,
    val activityArtifactId: String? = null,
    val activityRuntimeId: String? = null,
    val activityReady: Boolean = false,
    val activityState: SpeakerActivityState = SpeakerActivityState.UNSUPPORTED,
    val overlapRatio: Float? = null,
    val activeSpeakerCount: Int? = null,
    val localTracks: List<LocalSpeakerTrack> = emptyList(),
    val qualityByLocalSpeaker: Map<String, SpeakerAudioQualityAssessment> = emptyMap(),
    val activityHoldReasons: List<String> = emptyList(),
    val activityInferenceTimeMillis: Long? = null,
    val totalPipelineTimeMillis: Long? = null,
    val realTimeFactor: Float? = null,
    val stageTimings: SpeakerStageTimings? = null,
    val error: String? = null,
)
