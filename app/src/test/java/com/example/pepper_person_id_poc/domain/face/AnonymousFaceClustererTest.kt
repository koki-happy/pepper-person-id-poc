package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AnonymousFaceClustererTest {
    @Test
    fun identify_similarNewTrackReusesAnonymousId() {
        val clusterer = AnonymousFaceClusterer(threshold = 0.8f)
        val first = clusterer.identify("face-001", floatArrayOf(1f, 0f))

        val second = clusterer.identify("face-002", floatArrayOf(0.99f, 0.01f))

        assertThat(second.anonymousId).isEqualTo(first.anonymousId)
        assertThat(second.isNewCluster).isFalse()
        assertThat(second.clusterSampleCount).isEqualTo(2)
        assertThat(clusterer.clusterCount).isEqualTo(1)
    }

    @Test
    fun identify_dissimilarFaceCreatesNewAnonymousId() {
        val clusterer = AnonymousFaceClusterer(threshold = 0.8f)
        val first = clusterer.identify("face-001", floatArrayOf(1f, 0f))

        val second = clusterer.identify("face-002", floatArrayOf(0f, 1f))

        assertThat(second.anonymousId).isNotEqualTo(first.anonymousId)
        assertThat(second.anonymousId).isEqualTo("anonymous-002")
        assertThat(second.isNewCluster).isTrue()
        assertThat(clusterer.clusterCount).isEqualTo(2)
    }

    @Test
    fun identify_doesNotLimitNumberOfAnonymousPeople() {
        val clusterer = AnonymousFaceClusterer(threshold = 0.99f)

        repeat(25) { index ->
            val embedding = FloatArray(25).also { it[index] = 1f }
            clusterer.identify("face-$index", embedding)
        }

        assertThat(clusterer.clusterCount).isEqualTo(25)
    }

    @Test
    fun reset_discardsSessionIds() {
        val clusterer = AnonymousFaceClusterer(threshold = 0.8f)
        clusterer.identify("face-001", floatArrayOf(1f, 0f))

        clusterer.reset()
        val result = clusterer.identify("face-010", floatArrayOf(0f, 1f))

        assertThat(clusterer.clusterCount).isEqualTo(1)
        assertThat(result.anonymousId).isEqualTo("anonymous-001")
    }
}
