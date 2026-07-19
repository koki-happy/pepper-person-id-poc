package com.example.pepper_person_id_poc.domain.face

import kotlin.math.sqrt

/** Session-only re-identification for people who have no enrolled profile. */
class AnonymousFaceClusterer(
    private val threshold: Float,
) {
    private val clusters = mutableListOf<Cluster>()
    private val anonymousIdByTrackId = mutableMapOf<String, String>()
    private var nextId = 1

    val clusterCount: Int get() = clusters.size

    @Synchronized
    fun identify(
        trackId: String,
        embedding: FloatArray,
        reservedAnonymousIds: Set<String> = emptySet(),
    ): AnonymousFaceResult {
        require(embedding.isNotEmpty())
        val mappedCluster = anonymousIdByTrackId[trackId]
            ?.let { mappedId -> clusters.firstOrNull { it.anonymousId == mappedId } }
            ?.takeUnless { it.anonymousId in reservedAnonymousIds }
        val best = mappedCluster?.let { it to cosineSimilarity(embedding, it.centroid) }
            ?: clusters.asSequence()
                .filterNot { it.anonymousId in reservedAnonymousIds }
                .filter { it.centroid.size == embedding.size }
                .map { it to cosineSimilarity(embedding, it.centroid) }
                .maxByOrNull { it.second }
        val cluster = if (mappedCluster != null || (best != null && best.second >= threshold)) {
            checkNotNull(best).first
        } else {
            Cluster(
                anonymousId = "anonymous-${nextId++.toString().padStart(3, '0')}",
                centroid = embedding.copyOf(),
                sampleCount = 0,
            ).also(clusters::add)
        }
        val score = if (cluster.sampleCount == 0) 1f else cosineSimilarity(embedding, cluster.centroid)
        val isNewCluster = cluster.sampleCount == 0
        cluster.add(embedding)
        anonymousIdByTrackId[trackId] = cluster.anonymousId
        return AnonymousFaceResult(
            trackId = trackId,
            anonymousId = cluster.anonymousId,
            score = score,
            threshold = threshold,
            isNewCluster = isNewCluster,
            clusterSampleCount = cluster.sampleCount,
            maximumSampleCount = MAX_SAMPLES_PER_CLUSTER,
        )
    }

    @Synchronized
    fun reset() {
        clusters.clear()
        anonymousIdByTrackId.clear()
        nextId = 1
    }

    private data class Cluster(
        val anonymousId: String,
        val centroid: FloatArray,
        var sampleCount: Int,
    ) {
        fun add(embedding: FloatArray) {
            require(centroid.size == embedding.size)
            if (sampleCount >= MAX_SAMPLES_PER_CLUSTER) return
            val nextCount = sampleCount + 1
            centroid.indices.forEach { index ->
                centroid[index] += (embedding[index] - centroid[index]) / nextCount
            }
            sampleCount = nextCount
        }
    }

    companion object {
        const val MAX_SAMPLES_PER_CLUSTER = 20
    }
}

data class AnonymousFaceResult(
    val trackId: String,
    val anonymousId: String,
    val score: Float,
    val threshold: Float,
    val isNewCluster: Boolean,
    val clusterSampleCount: Int,
    val maximumSampleCount: Int,
) {
    val isAtSampleLimit: Boolean get() = clusterSampleCount >= maximumSampleCount
}

private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
    if (left.size != right.size || left.isEmpty()) return -1f
    var dot = 0.0
    var leftNorm = 0.0
    var rightNorm = 0.0
    left.indices.forEach { index ->
        val a = left[index].toDouble()
        val b = right[index].toDouble()
        dot += a * b
        leftNorm += a * a
        rightNorm += b * b
    }
    if (leftNorm == 0.0 || rightNorm == 0.0) return -1f
    return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat().coerceIn(-1f, 1f)
}
