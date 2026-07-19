package com.example.pepper_person_id_poc.speakercore

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.sqrt

@RunWith(JUnit4::class)
class EmbeddingMathTest {
    @Test
    fun l2Normalize_returnsUnitVectorWithoutChangingInput() {
        val input = floatArrayOf(3f, 4f)

        val normalized = EmbeddingMath.l2Normalize(input)

        assertThat(normalized.toList()).containsExactly(0.6f, 0.8f).inOrder()
        assertThat(input.toList()).containsExactly(3f, 4f).inOrder()
    }

    @Test
    fun requireValidEmbedding_rejectsEmptyNonFiniteZeroAndWrongDimension() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.requireValidEmbedding(floatArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.requireValidEmbedding(floatArrayOf(Float.NaN, 1f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.requireValidEmbedding(floatArrayOf(Float.POSITIVE_INFINITY, 1f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.requireValidEmbedding(floatArrayOf(0f, 0f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.requireValidEmbedding(floatArrayOf(1f, 0f), expectedDimension = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.requireValidEmbedding(floatArrayOf(1f), expectedDimension = 0)
        }
    }

    @Test
    fun centroid_normalizesEachSampleThenMeanThenResult() {
        val centroid = EmbeddingMath.centroid(
            listOf(
                floatArrayOf(100f, 0f),
                floatArrayOf(0f, 1f),
            ),
        )

        val expected = (1.0 / sqrt(2.0)).toFloat()
        assertThat(centroid[0]).isWithin(1e-6f).of(expected)
        assertThat(centroid[1]).isWithin(1e-6f).of(expected)
        assertThat(EmbeddingMath.cosineSimilarity(centroid, centroid)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun centroid_rejectsEmptyMismatchedAndCancellingSamples() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.centroid(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.centroid(listOf(floatArrayOf(1f, 0f), floatArrayOf(1f)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.centroid(listOf(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)))
        }
    }

    @Test
    fun cosineSimilarity_handlesDirectionAndRejectsInvalidOperands() {
        assertThat(
            EmbeddingMath.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 4f)),
        ).isWithin(1e-6f).of(0f)
        assertThat(
            EmbeddingMath.cosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-3f, 0f)),
        ).isWithin(1e-6f).of(-1f)

        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.cosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 0f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingMath.cosineSimilarity(floatArrayOf(1f), floatArrayOf(0f))
        }
    }
}
