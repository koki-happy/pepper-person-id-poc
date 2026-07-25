package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
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
                val result = repository.identify(
                    modelId = modelId,
                    embedding = observation.embedding,
                    threshold = threshold,
                    maximumUpdateCount = maximumUpdateCount,
                    reservedAnonymousIds = reserved,
                )
                reserved += result.anonymousId
                trackIdToAnonymousId[observation.trackId] = result.anonymousId
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
