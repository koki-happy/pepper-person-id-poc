package com.example.pepper_person_id_poc.domain.anonymous

import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousFaceRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnonymousClusteringTest {
    @Test
    fun normalizedMean_treatsDifferentVectorLengthsAsEqualVotes() {
        val repository = InMemoryAnonymousFaceRepository()
        repository.identify("model", floatArrayOf(10f, 0f), 0f, 20)
        repository.identify("model", floatArrayOf(0f, 1f), 0f, 20)

        val centroid = repository.getAll().single().centroid

        assertThat(centroid[0]).isWithin(0.0001f).of(0.70710677f)
        assertThat(centroid[1]).isWithin(0.0001f).of(0.70710677f)
    }

    @Test
    fun updateLimit_freezesCentroidButReusesId() {
        val repository = InMemoryAnonymousFaceRepository()
        val first = repository.identify("model", floatArrayOf(1f, 0f), 0f, 1)
        val second = repository.identify("model", floatArrayOf(0f, 1f), 0f, 1)

        assertThat(second.anonymousId).isEqualTo(first.anonymousId)
        assertThat(repository.getAll().single().centroid.asList()).containsExactly(1f, 0f).inOrder()
        assertThat(second.updateCount).isEqualTo(1)
    }

    @Test
    fun differentModelsDoNotMix() {
        val repository = InMemoryAnonymousFaceRepository()
        repository.identify("model-a", floatArrayOf(1f), 0f, 20)
        val result = repository.identify("model-b", floatArrayOf(1f), 0f, 20)
        assertThat(repository.count()).isEqualTo(2)
        assertThat(result.currentModelClusterCount).isEqualTo(1)
        assertThat(result.totalClusterCount).isEqualTo(2)
    }

    @Test
    fun resultListsSameModelClusterSimilaritiesInDescendingOrder() {
        val repository = InMemoryAnonymousFaceRepository()
        repository.identify("model", floatArrayOf(1f, 0f), 0.95f, 20)
        repository.identify("model", floatArrayOf(0f, 1f), 0.95f, 20)

        val result = repository.identify("model", floatArrayOf(0.8f, 0.2f), 0.5f, 20)

        assertThat(result.candidateScores.map { it.anonymousId })
            .containsExactly("anonymous-face-001", "anonymous-face-002").inOrder()
        assertThat(result.candidateScores.single { it.selected }.anonymousId)
            .isEqualTo(result.anonymousId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroVector_isRejectedWithoutMutation() {
        InMemoryAnonymousFaceRepository().identify("model", floatArrayOf(0f, 0f), 0.6f, 20)
    }

    @Test
    fun newCluster_reportsExistingBestScoreWithoutAddingSelfCandidate() {
        val repository = InMemoryAnonymousFaceRepository()
        val first = repository.identify("model", floatArrayOf(1f, 0f), 0.9f, 20)
        val second = repository.identify("model", floatArrayOf(0f, 1f), 0.9f, 20)

        assertThat(first.bestExistingScore).isNull()
        assertThat(first.candidateScores).isEmpty()
        assertThat(second.bestExistingScore).isWithin(0.0001f).of(0f)
        assertThat(second.candidateScores.map { it.anonymousId })
            .containsExactly("anonymous-face-001")
        assertThat(second.candidateScores.none { it.selected }).isTrue()
    }
}
