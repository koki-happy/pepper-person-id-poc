package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousPersistencePolicy
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationDecision
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
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
        val reserved = mutableSetOf<String>()
        val results = linkedMapOf<String, AnonymousIdentificationResult>()
        runCatching {
            observations.forEach { observation ->
                val modelSpaceId = ModelSpaceId(modelId)
                val initialEvaluation = repository.evaluate(
                    modelSpaceId = modelSpaceId,
                    embedding = observation.embedding,
                    threshold = threshold,
                    minimumLead = 0f,
                )
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
                val (policy, operation) = AnonymousPersistencePolicy.evaluate(
                    decision = evaluation.decision,
                    createEligible = observation.qualityAssessment?.createEligible == true,
                    updateEligible = observation.qualityAssessment?.updateEligible == true,
                    reasons = observation.qualityAssessment?.rejectionReasons
                        ?: listOf("QUALITY_UNAVAILABLE"),
                )
                val cluster = repository.apply(
                    operation = operation,
                    modelSpaceId = modelSpaceId,
                    embedding = observation.embedding,
                    selectedAnonymousId = selectedId,
                    maximumUpdateCount = maximumUpdateCount,
                    nowElapsedRealtime = System.currentTimeMillis(),
                )
                cluster?.anonymousId?.let {
                    reserved += it
                    trackIdToAnonymousId[observation.trackId] = it
                }
                val result = AnonymousIdentificationResult(
                    anonymousId = cluster?.anonymousId ?: "unknown",
                    modelId = modelId,
                    bestExistingScore = evaluation.highestScore,
                    threshold = threshold,
                    isNewCluster = operation == PersistenceOperation.CREATE,
                    updateCount = cluster?.updateCount ?: 0,
                    maximumUpdateCount = maximumUpdateCount,
                    currentModelClusterCount = repository.getAll().count {
                        it.modelSpaceId == modelSpaceId &&
                            it.embeddingDimension == observation.embedding.size
                    },
                    totalClusterCount = repository.count(),
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
            }
        }.onSuccess {
            val latest = results.values.lastOrNull()
            mutableState.value = mutableState.value.copy(
                results = results,
                currentModelClusterCount = latest?.currentModelClusterCount ?: 0,
                totalClusterCount = latest?.totalClusterCount ?: repository.count(),
                lastEmbeddingAverageTimeMillis = observations.map { it.embeddingTimeMillis }.average()
                    .takeIf { !it.isNaN() }?.toLong(),
                lastEmbeddingMaximumTimeMillis = observations.maxOfOrNull { it.embeddingTimeMillis },
                lastEmbeddingFaceCount = observations.size,
                error = null,
            )
        }.onFailure {
            mutableState.value = mutableState.value.copy(error = it.message ?: it::class.java.simpleName)
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
    val error: String? = null,
)
