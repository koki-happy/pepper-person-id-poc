package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousPersistencePolicy
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationDecision
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
import com.example.pepper_person_id_poc.domain.metrics.FacePipelineMetrics
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FaceIdentityCoordinator(
    private val repository: AnonymousFaceClusterRepository,
    private val modelId: String,
    private val threshold: Float,
    private val maximumUpdateCount: Int,
    private val nanoTime: () -> Long = System::nanoTime,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutoCloseable {
    private val trackIdToAnonymousId = mutableMapOf<String, String>()
    private val mutableState = MutableStateFlow(FaceIdentityUiState(modelId = modelId))
    val state: StateFlow<FaceIdentityUiState> = mutableState.asStateFlow()

    fun onFaceAnalysis(faceCount: Int, observations: List<FacePoseObservation>): Set<String> {
        val visible = observations.mapTo(mutableSetOf()) { it.trackId }
        trackIdToAnonymousId.keys.retainAll(visible)
        mutableState.value = mutableState.value.copy(
            visibleFaceCount = faceCount,
            results = mutableState.value.results.filterKeys(visible::contains),
        )
        return visible
    }

    fun onFeatureObservations(observations: List<FaceFeatureObservation>) {
        val pipelineStartedNanos = nanoTime()
        var scoringNanos = 0L
        var policyNanos = 0L
        var repositoryNanos = 0L
        var uiNanos = 0L
        val reserved = mutableSetOf<String>()
        val results = linkedMapOf<String, AnonymousIdentificationResult>()
        runCatching {
            observations.forEach { observation ->
                val modelSpaceId = ModelSpaceId(modelId)
                val scoringStartedNanos = nanoTime()
                val initialEvaluation = repository.evaluate(
                    modelSpaceId = modelSpaceId,
                    embedding = observation.embedding,
                    threshold = threshold,
                    minimumLead = 0f,
                )
                scoringNanos += nanoTime() - scoringStartedNanos
                val selectedId = initialEvaluation.candidates.firstOrNull { it.selected }?.anonymousId
                val evaluation = if (
                    initialEvaluation.decision == IdentificationDecision.MATCHED_EXISTING &&
                    selectedId in reserved
                ) {
                    initialEvaluation.copy(
                        candidates = initialEvaluation.candidates.map { it.copy(selected = false) },
                        decision = IdentificationDecision.AMBIGUOUS,
                    )
                } else {
                    initialEvaluation
                }
                val policyStartedNanos = nanoTime()
                val (policy, operation) = AnonymousPersistencePolicy.evaluate(
                    decision = evaluation.decision,
                    createEligible = true,
                    updateEligible = true,
                    reasons = emptyList(),
                )
                policyNanos += nanoTime() - policyStartedNanos
                val repositoryStartedNanos = nanoTime()
                val cluster = repository.apply(
                    operation = operation,
                    modelSpaceId = modelSpaceId,
                    embedding = observation.embedding,
                    selectedAnonymousId = selectedId,
                    maximumUpdateCount = maximumUpdateCount,
                    nowElapsedRealtime = elapsedRealtimeMillis(),
                )
                cluster?.anonymousId?.let {
                    reserved += it
                    trackIdToAnonymousId[observation.trackId] = it
                }
                val currentModelClusterCount = repository.getAll().count {
                    it.modelSpaceId == modelSpaceId &&
                        it.embeddingDimension == observation.embedding.size
                }
                val totalClusterCount = repository.count()
                repositoryNanos += nanoTime() - repositoryStartedNanos
                val uiStartedNanos = nanoTime()
                val result = AnonymousIdentificationResult(
                    anonymousId = cluster?.anonymousId ?: "unknown",
                    modelId = modelId,
                    bestExistingScore = evaluation.highestScore,
                    threshold = threshold,
                    isNewCluster = operation == PersistenceOperation.CREATE,
                    updateCount = cluster?.updateCount ?: 0,
                    maximumUpdateCount = maximumUpdateCount,
                    currentModelClusterCount = currentModelClusterCount,
                    totalClusterCount = totalClusterCount,
                    candidateScores = evaluation.candidates.map {
                        AnonymousClusterScore(
                            anonymousId = it.anonymousId,
                            score = it.score,
                            selected = it.anonymousId == cluster?.anonymousId &&
                                operation == PersistenceOperation.UPDATE,
                        )
                    },
                    evaluation = evaluation,
                    persistencePolicy = policy,
                    persistenceOperation = operation,
                )
                results[observation.trackId] = result
                uiNanos += nanoTime() - uiStartedNanos
            }
        }.onSuccess {
            val latest = results.values.lastOrNull()
            val stateUpdateStartedNanos = nanoTime()
            val partialMetrics = FacePipelineMetrics(
                preprocessingMillis = observations.mapNotNull {
                    it.preprocessingTimeMillis
                }.maxOrNull()?.toDouble(),
                detectionMillis = observations.mapNotNull {
                    it.detectionTimeMillis
                }.maxOrNull()?.toDouble(),
                alignmentMillis = observations.mapNotNull {
                    it.alignmentTimeMillis
                }.sum().toDouble().takeIf { observations.any { it.alignmentTimeMillis != null } },
                embeddingMillis = observations.sumOf { it.embeddingTimeMillis }.toDouble(),
                scoringMillis = scoringNanos.toMillis(),
                policyMillis = policyNanos.toMillis(),
                repositoryMillis = repositoryNanos.toMillis(),
            )
            val nextState = mutableState.value.copy(
                results = results,
                currentModelClusterCount = latest?.currentModelClusterCount ?: 0,
                totalClusterCount = latest?.totalClusterCount ?: repository.count(),
                lastEmbeddingAverageTimeMillis = observations.map { it.embeddingTimeMillis }.average()
                    .takeIf { !it.isNaN() }?.toLong(),
                lastEmbeddingMaximumTimeMillis = observations.maxOfOrNull { it.embeddingTimeMillis },
                lastEmbeddingFaceCount = observations.size,
                pipelineMetrics = partialMetrics,
                error = null,
            )
            uiNanos += nanoTime() - stateUpdateStartedNanos
            val completedMetrics = partialMetrics.copy(uiMillis = uiNanos.toMillis())
            mutableState.value = nextState.copy(
                pipelineMetrics = completedMetrics.copy(
                    totalMillis = maxOf(
                        completedMetrics.measuredStageTotalMillis(),
                        observations.sumOf { it.embeddingTimeMillis }.toDouble() +
                            (nanoTime() - pipelineStartedNanos).toMillis(),
                    ),
                ),
            )
        }.onFailure {
            val failedMetrics = FacePipelineMetrics(
                preprocessingMillis = observations.mapNotNull {
                    it.preprocessingTimeMillis
                }.maxOrNull()?.toDouble(),
                detectionMillis = observations.mapNotNull {
                    it.detectionTimeMillis
                }.maxOrNull()?.toDouble(),
                alignmentMillis = observations.mapNotNull {
                    it.alignmentTimeMillis
                }.sum().toDouble().takeIf { observations.any { it.alignmentTimeMillis != null } },
                embeddingMillis = observations.sumOf { observation ->
                    observation.embeddingTimeMillis
                }.toDouble(),
                scoringMillis = scoringNanos.toMillis(),
                policyMillis = policyNanos.toMillis(),
                repositoryMillis = repositoryNanos.toMillis(),
                uiMillis = uiNanos.toMillis(),
            )
            mutableState.value = mutableState.value.copy(
                pipelineMetrics = failedMetrics.copy(
                    totalMillis = maxOf(
                        failedMetrics.measuredStageTotalMillis(),
                        observations.sumOf { observation ->
                            observation.embeddingTimeMillis
                        }.toDouble() + (nanoTime() - pipelineStartedNanos).toMillis(),
                    ),
                ),
                error = it.message ?: it::class.java.simpleName,
            )
        }
    }

    fun reportEmbeddingReady() {
        mutableState.value = mutableState.value.copy(modelReady = true, error = null)
    }

    fun reportEmbeddingError(throwable: Throwable) {
        mutableState.value = mutableState.value.copy(error = throwable.message ?: throwable::class.java.simpleName)
    }

    override fun close() {
        trackIdToAnonymousId.clear()
        mutableState.value = mutableState.value.copy(results = emptyMap(), visibleFaceCount = 0)
    }

    private fun Long.toMillis(): Double = coerceAtLeast(0L) / 1_000_000.0
}

data class FaceIdentityUiState(
    val modelId: String,
    val modelReady: Boolean = false,
    val visibleFaceCount: Int = 0,
    val results: Map<String, AnonymousIdentificationResult> = emptyMap(),
    val currentModelClusterCount: Int = 0,
    val totalClusterCount: Int = 0,
    val lastEmbeddingAverageTimeMillis: Long? = null,
    val lastEmbeddingMaximumTimeMillis: Long? = null,
    val lastEmbeddingFaceCount: Int = 0,
    val pipelineMetrics: FacePipelineMetrics? = null,
    val error: String? = null,
)
