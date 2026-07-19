package com.example.pepper_person_id_poc.speakercore

import kotlin.math.sqrt

/** Pure-Kotlin operations shared by JVM and Android speaker-embedding clients. */
object EmbeddingMath {
    /**
     * Validates the invariants required by all embedding operations.
     *
     * @throws IllegalArgumentException when the embedding is empty, has the wrong dimension,
     * contains a non-finite value, or has zero L2 norm.
     */
    fun requireValidEmbedding(
        embedding: FloatArray,
        expectedDimension: Int? = null,
    ) {
        require(expectedDimension == null || expectedDimension > 0) {
            "expectedDimension must be positive when specified"
        }
        require(embedding.isNotEmpty()) { "embedding must not be empty" }
        require(expectedDimension == null || embedding.size == expectedDimension) {
            "embedding dimension ${embedding.size} does not match expected dimension $expectedDimension"
        }

        var squaredNorm = 0.0
        embedding.forEachIndexed { index, value ->
            require(value.isFinite()) { "embedding[$index] must be finite" }
            val asDouble = value.toDouble()
            squaredNorm += asDouble * asDouble
        }
        require(squaredNorm > 0.0) { "embedding must have non-zero L2 norm" }
    }

    /** Returns a new unit-length vector without modifying [embedding]. */
    fun l2Normalize(embedding: FloatArray): FloatArray {
        requireValidEmbedding(embedding)
        val norm = sqrt(squaredNorm(embedding))
        return FloatArray(embedding.size) { index ->
            (embedding[index].toDouble() / norm).toFloat()
        }
    }

    /**
     * Builds a centroid by L2-normalizing every sample, taking their arithmetic mean, and
     * L2-normalizing that mean. This prevents high-magnitude input vectors from dominating.
     */
    fun centroid(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "embeddings must not be empty" }
        val dimension = embeddings.first().size
        require(dimension > 0) { "embeddings must not be empty" }

        val sum = DoubleArray(dimension)
        embeddings.forEachIndexed { sampleIndex, embedding ->
            require(embedding.size == dimension) {
                "embedding[$sampleIndex] dimension ${embedding.size} does not match expected dimension $dimension"
            }
            requireValidEmbedding(embedding, dimension)
            val norm = sqrt(squaredNorm(embedding))
            embedding.indices.forEach { dimensionIndex ->
                sum[dimensionIndex] += embedding[dimensionIndex].toDouble() / norm
            }
        }

        val mean = DoubleArray(dimension) { index -> sum[index] / embeddings.size.toDouble() }
        val meanSquaredNorm = mean.sumOf { value -> value * value }
        require(meanSquaredNorm > 0.0) {
            "normalized embeddings cancel out and do not define a centroid"
        }
        val meanNorm = sqrt(meanSquaredNorm)
        return FloatArray(dimension) { index -> (mean[index] / meanNorm).toFloat() }
    }

    /** Returns cosine similarity in the inclusive range `[-1, 1]`. */
    fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        requireValidEmbedding(first)
        requireValidEmbedding(second, first.size)

        var dotProduct = 0.0
        first.indices.forEach { index ->
            dotProduct += first[index].toDouble() * second[index].toDouble()
        }
        val denominator = sqrt(squaredNorm(first)) * sqrt(squaredNorm(second))
        return (dotProduct / denominator).coerceIn(-1.0, 1.0).toFloat()
    }

    private fun squaredNorm(embedding: FloatArray): Double =
        embedding.sumOf { value ->
            val asDouble = value.toDouble()
            asDouble * asDouble
        }
}
