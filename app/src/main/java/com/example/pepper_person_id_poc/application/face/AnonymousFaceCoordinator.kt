package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.face.AnonymousFaceClusterer
import com.example.pepper_person_id_poc.domain.face.AnonymousFaceResult
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AnonymousFaceCoordinator(
    private val clusterer: AnonymousFaceClusterer,
    private val modelName: String,
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(AnonymousFaceUiState())
    val state: StateFlow<AnonymousFaceUiState> = mutableState.asStateFlow()

    fun onFeatureObservations(observations: List<FaceFeatureObservation>) {
        val results = observations.map { observation ->
            val startedAtNanos = System.nanoTime()
            clusterer.identify(observation.trackId, observation.embedding).also { result ->
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "anonymous_face_identification",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = observation.embeddingTimeMillis +
                            (System.nanoTime() - startedAtNanos) / 1_000_000L,
                        status = if (result.isNewCluster) "NEW_CLUSTER" else "MATCHED",
                        attributes = mapOf(
                            "model" to modelName,
                            "anonymousId" to result.anonymousId,
                            "score" to result.score.toString(),
                            "threshold" to result.threshold.toString(),
                            "clusterCount" to clusterer.clusterCount.toString(),
                        ),
                    ),
                )
            }
        }
        mutableState.value = mutableState.value.copy(
            results = results,
            clusterCount = clusterer.clusterCount,
            error = null,
        )
    }

    fun reportEmbeddingReady() {
        mutableState.value = mutableState.value.copy(embeddingModelReady = true, error = null)
    }

    fun reportEmbeddingError(throwable: Throwable) {
        mutableState.value = mutableState.value.copy(
            error = throwable.message ?: throwable::class.java.simpleName,
        )
    }

    fun resetSession() {
        clusterer.reset()
        mutableState.value = AnonymousFaceUiState(
            embeddingModelReady = mutableState.value.embeddingModelReady,
        )
    }
}

data class AnonymousFaceUiState(
    val results: List<AnonymousFaceResult> = emptyList(),
    val clusterCount: Int = 0,
    val embeddingModelReady: Boolean = false,
    val error: String? = null,
)
