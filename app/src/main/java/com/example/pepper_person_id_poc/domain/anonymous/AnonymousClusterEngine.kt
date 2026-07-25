package com.example.pepper_person_id_poc.domain.anonymous

import kotlin.math.abs
import kotlin.math.sqrt

data class ClusterMatch(
    val candidates: List<AnonymousClusterScore>,
    val bestCluster: AnonymousCluster?,
    val bestScore: Float?,
)

object AnonymousClusterEngine {
    fun normalize(embedding: FloatArray): FloatArray {
        require(embedding.isNotEmpty())
        require(embedding.all(Float::isFinite))
        val norm = sqrt(embedding.sumOf { it.toDouble() * it.toDouble() })
        require(norm > 0.0)
        return FloatArray(embedding.size) { (embedding[it] / norm).toFloat() }
    }

    fun findBestMatch(
        normalizedEmbedding: FloatArray,
        clusters: List<AnonymousCluster>,
        modelId: String,
        reservedAnonymousIds: Set<String> = emptySet(),
    ): ClusterMatch {
        val scored = clusters.asSequence()
            .filter {
                it.modelId == modelId &&
                    it.embeddingDimension == normalizedEmbedding.size &&
                    it.anonymousId !in reservedAnonymousIds
            }
            .map { it to cosine(normalizedEmbedding, it.centroid) }
            .sortedByDescending { it.second }
            .toList()
        return ClusterMatch(
            candidates = scored.map { (cluster, score) ->
                AnonymousClusterScore(cluster.anonymousId, score, selected = false)
            },
            bestCluster = scored.firstOrNull()?.first,
            bestScore = scored.firstOrNull()?.second,
        )
    }

    fun addEmbedding(
        cluster: AnonymousCluster,
        normalizedEmbedding: FloatArray,
        maximumUpdateCount: Int,
        nowMillis: Long,
    ): AnonymousCluster {
        require(normalizedEmbedding.size == cluster.embeddingDimension)
        require(maximumUpdateCount in 1..100)
        if (cluster.updateCount >= maximumUpdateCount) return cluster
        val sum = cluster.normalizedEmbeddingSum.copyOf()
        sum.indices.forEach { sum[it] += normalizedEmbedding[it] }
        return cluster.copy(
            normalizedEmbeddingSum = sum,
            centroid = normalize(sum),
            updateCount = cluster.updateCount + 1,
            updatedAtMillis = nowMillis,
        )
    }

    fun validate(cluster: AnonymousCluster) {
        require(cluster.anonymousId.isNotBlank())
        require(cluster.modelId.isNotBlank())
        require(cluster.embeddingDimension > 0)
        require(cluster.normalizedEmbeddingSum.size == cluster.embeddingDimension)
        require(cluster.centroid.size == cluster.embeddingDimension)
        require(cluster.normalizedEmbeddingSum.all(Float::isFinite))
        require(cluster.centroid.all(Float::isFinite))
        require(cluster.updateCount >= 1)
        require(cluster.createdAtMillis <= cluster.updatedAtMillis)
        val expected = normalize(cluster.normalizedEmbeddingSum)
        val centroidNorm = sqrt(cluster.centroid.sumOf { it.toDouble() * it.toDouble() })
        require(abs(centroidNorm - 1.0) <= 1e-4)
        require(cosine(expected, cluster.centroid) >= 0.9999f)
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size)
        require(left.all(Float::isFinite) && right.all(Float::isFinite))
        return left.indices.sumOf { left[it].toDouble() * right[it].toDouble() }
            .toFloat()
            .coerceIn(-1f, 1f)
    }
}
