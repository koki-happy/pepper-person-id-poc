package com.example.pepper_person_id_poc.domain.speaker

/**
 * Assigns session-only speaker IDs without requiring registration.
 *
 * Embeddings and IDs exist only in memory and are discarded by [reset]. There is intentionally
 * no fixed cluster limit; the device's available memory is the practical bound for a PoC session.
 */
class AnonymousSpeakerClusterer(
    private val threshold: Float,
) {
    private val clusters = mutableListOf<Cluster>()
    private var nextId = 1

    init {
        require(threshold in 0f..1f)
    }

    val clusterCount: Int
        @Synchronized get() = clusters.size

    @Synchronized
    fun identify(utteranceId: String, embedding: FloatArray): AnonymousSpeakerResult {
        require(utteranceId.isNotBlank())
        require(embedding.isNotEmpty())
        require(embedding.all(Float::isFinite) && embedding.any { it != 0f })

        val best = clusters
            .asSequence()
            .mapNotNull { cluster ->
                cosineSimilarityOrNull(embedding, cluster.centroid)?.let { cluster to it }
            }
            .maxByOrNull { (_, score) -> score }
        val matched = best?.takeIf { (_, score) -> score >= threshold }
        val cluster = matched?.first ?: Cluster(
            anonymousSpeakerId = "anonymous-speaker-${nextId++.toString().padStart(3, '0')}",
            centroid = embedding.copyOf(),
            sampleCount = 0,
        ).also(clusters::add)
        val score = matched?.second ?: 1f
        val isNewCluster = matched == null

        cluster.add(embedding)
        return AnonymousSpeakerResult(
            utteranceId = utteranceId,
            anonymousSpeakerId = cluster.anonymousSpeakerId,
            score = score,
            threshold = threshold,
            isNewCluster = isNewCluster,
            clusterSampleCount = cluster.sampleCount,
        )
    }

    @Synchronized
    fun reset() {
        clusters.clear()
        nextId = 1
    }

    private data class Cluster(
        val anonymousSpeakerId: String,
        val centroid: FloatArray,
        var sampleCount: Int,
    ) {
        fun add(embedding: FloatArray) {
            require(centroid.size == embedding.size)
            val nextCount = sampleCount + 1
            centroid.indices.forEach { index ->
                centroid[index] += (embedding[index] - centroid[index]) / nextCount
            }
            sampleCount = nextCount
        }
    }
}

data class AnonymousSpeakerResult(
    val utteranceId: String,
    val anonymousSpeakerId: String,
    val score: Float,
    val threshold: Float,
    val isNewCluster: Boolean,
    val clusterSampleCount: Int,
)
