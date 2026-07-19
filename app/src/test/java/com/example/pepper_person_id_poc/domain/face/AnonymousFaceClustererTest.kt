package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnonymousFaceClustererTest {
    @Test
    fun similarFaceOnNewTrack_reusesSessionId() {
        val clusterer = AnonymousFaceClusterer(0.8f)
        val first = clusterer.identify("face-001", floatArrayOf(1f, 0f))
        val second = clusterer.identify("face-002", floatArrayOf(0.99f, 0.01f))
        assertThat(second.anonymousId).isEqualTo(first.anonymousId)
        assertThat(second.isNewCluster).isFalse()
    }

    @Test
    fun differentFace_createsAnotherSessionId() {
        val clusterer = AnonymousFaceClusterer(0.8f)
        clusterer.identify("face-001", floatArrayOf(1f, 0f))
        val second = clusterer.identify("face-002", floatArrayOf(0f, 1f))
        assertThat(second.anonymousId).isEqualTo("anonymous-002")
        assertThat(clusterer.clusterCount).isEqualTo(2)
    }

    @Test
    fun reset_discardsAllBiometricSessionState() {
        val clusterer = AnonymousFaceClusterer(0.8f)
        clusterer.identify("face-001", floatArrayOf(1f, 0f))
        clusterer.reset()
        val result = clusterer.identify("face-010", floatArrayOf(0f, 1f))
        assertThat(result.anonymousId).isEqualTo("anonymous-001")
        assertThat(clusterer.clusterCount).isEqualTo(1)
    }
}
