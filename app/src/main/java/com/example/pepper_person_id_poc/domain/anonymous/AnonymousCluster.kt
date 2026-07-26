package com.example.pepper_person_id_poc.domain.anonymous

import com.example.pepper_person_id_poc.domain.model.BiometricModality
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId

data class AnonymousCluster(
    val anonymousId: String,
    val modelId: String,
    val embeddingDimension: Int,
    val normalizedEmbeddingSum: FloatArray,
    val centroid: FloatArray,
    val updateCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val modality: BiometricModality = when {
        anonymousId.startsWith("anonymous-face-") -> BiometricModality.FACE
        anonymousId.startsWith("anonymous-speaker-") -> BiometricModality.SPEAKER
        else -> BiometricModality.UNSPECIFIED
    },
    val modelSpaceId: ModelSpaceId = ModelSpaceId(modelId),
    val createdAtElapsedRealtime: Long = createdAtMillis,
    val updatedAtElapsedRealtime: Long = updatedAtMillis,
) {
    fun deepCopy() = copy(
        normalizedEmbeddingSum = normalizedEmbeddingSum.copyOf(),
        centroid = centroid.copyOf(),
    )
}

data class AnonymousIdentificationResult(
    val anonymousId: String,
    val modelId: String,
    val bestExistingScore: Float?,
    val threshold: Float,
    val isNewCluster: Boolean,
    val updateCount: Int,
    val maximumUpdateCount: Int,
    val currentModelClusterCount: Int,
    val totalClusterCount: Int,
    val candidateScores: List<AnonymousClusterScore>,
    val evaluation: IdentificationEvaluation? = null,
    val persistencePolicy: PersistencePolicyResult? = null,
    val persistenceOperation: PersistenceOperation? = null,
)

data class AnonymousClusterScore(
    val anonymousId: String,
    val score: Float,
    val selected: Boolean,
)
