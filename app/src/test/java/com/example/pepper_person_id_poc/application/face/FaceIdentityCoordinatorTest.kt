package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousFaceRepository
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceIdentityCoordinatorTest {
    @Test
    fun close_doesNotDeleteRepository() {
        val repository = InMemoryAnonymousFaceRepository()
        val coordinator = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)
        coordinator.onFeatureObservations(listOf(FaceFeatureObservation("track-1", floatArrayOf(1f, 0f), 2L)))

        coordinator.close()

        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun reappearingFace_reusesPersistentAnonymousId() {
        val repository = InMemoryAnonymousFaceRepository()
        val first = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)
        first.onFeatureObservations(listOf(FaceFeatureObservation("track-1", floatArrayOf(1f, 0f), 1L)))
        val id = first.state.value.results.getValue("track-1").anonymousId
        first.close()

        val recreated = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)
        recreated.onFeatureObservations(listOf(FaceFeatureObservation("track-9", floatArrayOf(0.99f, 0.01f), 1L)))

        assertThat(recreated.state.value.results.getValue("track-9").anonymousId).isEqualTo(id)
    }
}
