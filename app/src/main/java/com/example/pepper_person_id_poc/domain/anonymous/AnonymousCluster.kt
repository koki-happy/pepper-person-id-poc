package com.example.pepper_person_id_poc.domain.anonymous

data class AnonymousCluster(
    val anonymousId: String,
    val modelId: String,
    val embeddingDimension: Int,
    val normalizedEmbeddingSum: FloatArray,
    val centroid: FloatArray,
    val updateCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun deepCopy() = copy(
        normalizedEmbeddingSum = normalizedEmbeddingSum.copyOf(),
        centroid = centroid.copyOf(),
    )
}

data class AnonymousIdentificationResult(
    val anonymousId: String,
    val modelId: String,
    val score: Float,
    val threshold: Float,
    val isNewCluster: Boolean,
    val updateCount: Int,
    val maximumUpdateCount: Int,
    val clusterCount: Int,
    val candidateScores: List<AnonymousClusterScore>,
)

data class AnonymousClusterScore(
    val anonymousId: String,
    val score: Float,
    val selected: Boolean,
)
