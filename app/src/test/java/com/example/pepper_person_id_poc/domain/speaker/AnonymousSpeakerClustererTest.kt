package com.example.pepper_person_id_poc.domain.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AnonymousSpeakerClustererTest {
    @Test
    fun similarUtterancesReuseSessionSpeakerIdAndUpdateCentroid() {
        val clusterer = AnonymousSpeakerClusterer(threshold = 0.8f)
        val first = clusterer.identify("utterance-001", floatArrayOf(1f, 0f))

        val second = clusterer.identify("utterance-002", floatArrayOf(0.99f, 0.01f))

        assertThat(second.anonymousSpeakerId).isEqualTo(first.anonymousSpeakerId)
        assertThat(second.isNewCluster).isFalse()
        assertThat(second.clusterSampleCount).isEqualTo(2)
        assertThat(second.score).isAtLeast(0.8f)
        assertThat(clusterer.clusterCount).isEqualTo(1)
    }

    @Test
    fun dissimilarUtteranceCreatesNewSessionSpeaker() {
        val clusterer = AnonymousSpeakerClusterer(threshold = 0.8f)
        clusterer.identify("utterance-001", floatArrayOf(1f, 0f))

        val second = clusterer.identify("utterance-002", floatArrayOf(0f, 1f))

        assertThat(second.anonymousSpeakerId).isEqualTo("anonymous-speaker-002")
        assertThat(second.isNewCluster).isTrue()
        assertThat(second.score).isEqualTo(1f)
        assertThat(clusterer.clusterCount).isEqualTo(2)
    }

    @Test
    fun dimensionMismatchDoesNotMatchExistingCluster() {
        val clusterer = AnonymousSpeakerClusterer(threshold = 0.1f)
        clusterer.identify("utterance-001", floatArrayOf(1f, 0f))

        val result = clusterer.identify("utterance-002", floatArrayOf(1f, 0f, 0f))

        assertThat(result.isNewCluster).isTrue()
        assertThat(clusterer.clusterCount).isEqualTo(2)
    }

    @Test
    fun clusteringHasNoFixedPersonLimit() {
        val clusterer = AnonymousSpeakerClusterer(threshold = 0.99f)

        repeat(40) { index ->
            val embedding = FloatArray(40).also { it[index] = 1f }
            clusterer.identify("utterance-$index", embedding)
        }

        assertThat(clusterer.clusterCount).isEqualTo(40)
    }

    @Test
    fun resetDiscardsEmbeddingsAndRestartsSessionIds() {
        val clusterer = AnonymousSpeakerClusterer(threshold = 0.8f)
        clusterer.identify("utterance-001", floatArrayOf(1f, 0f))

        clusterer.reset()
        val result = clusterer.identify("utterance-010", floatArrayOf(0f, 1f))

        assertThat(clusterer.clusterCount).isEqualTo(1)
        assertThat(result.anonymousSpeakerId).isEqualTo("anonymous-speaker-001")
        assertThat(result.clusterSampleCount).isEqualTo(1)
    }

    @Test
    fun invalidInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AnonymousSpeakerClusterer(-0.1f)
        }
        val clusterer = AnonymousSpeakerClusterer(0.8f)
        assertThrows(IllegalArgumentException::class.java) {
            clusterer.identify("", floatArrayOf(1f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            clusterer.identify("utterance", floatArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            clusterer.identify("utterance", floatArrayOf(0f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            clusterer.identify("utterance", floatArrayOf(Float.POSITIVE_INFINITY))
        }
    }
}
