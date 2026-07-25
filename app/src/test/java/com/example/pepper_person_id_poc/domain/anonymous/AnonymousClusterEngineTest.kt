package com.example.pepper_person_id_poc.domain.anonymous

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnonymousClusterEngineTest {
    @Test
    fun normalizedMean_treatsEachEmbeddingAsOneVote() {
        val first = newCluster(AnonymousClusterEngine.normalize(floatArrayOf(10f, 0f)))
        val afterFirst = AnonymousClusterEngine.addEmbedding(
            first,
            AnonymousClusterEngine.normalize(floatArrayOf(10f, 0f)),
            20,
            1,
        )
        val afterSecond = AnonymousClusterEngine.addEmbedding(
            afterFirst,
            AnonymousClusterEngine.normalize(floatArrayOf(0f, 1f)),
            20,
            2,
        )

        assertThat(afterSecond.centroid[0]).isWithin(0.0001f).of(0.70710677f)
        assertThat(afterSecond.centroid[1]).isWithin(0.0001f).of(0.70710677f)
    }

    @Test
    fun findBestMatch_filtersModelDimensionAndReservedIds() {
        val clusters = listOf(
            persistedCluster("face-1", "model", floatArrayOf(1f, 0f)),
            persistedCluster("face-2", "other", floatArrayOf(1f, 0f)),
            persistedCluster("face-3", "model", floatArrayOf(1f, 0f, 0f)),
        )

        val result = AnonymousClusterEngine.findBestMatch(
            floatArrayOf(1f, 0f),
            clusters,
            "model",
            setOf("face-1"),
        )

        assertThat(result.bestCluster).isNull()
        assertThat(result.candidates).isEmpty()
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalize_rejectsNonFiniteValues() {
        AnonymousClusterEngine.normalize(floatArrayOf(Float.NaN))
    }

    private fun newCluster(centroid: FloatArray) = AnonymousCluster(
        "face-1", "model", centroid.size, FloatArray(centroid.size), centroid,
        0, 0, 0,
    )

    private fun persistedCluster(id: String, model: String, vector: FloatArray): AnonymousCluster {
        val normalized = AnonymousClusterEngine.normalize(vector)
        return AnonymousCluster(id, model, vector.size, normalized, normalized, 1, 0, 0)
    }
}
