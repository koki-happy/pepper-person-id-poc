package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import com.example.pepper_person_id_poc.testsupport.FakePersonRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceIdentityCoordinatorTest {
    private val repository = FakePersonRepository()
    private val coordinator = FaceIdentityCoordinator(
        personRepository = repository,
        faceIdentifier = FaceIdentifier(),
        faceThreshold = 0.6f,
        faceModelName = "SFace 2021dec",
    )

    @Test
    fun registrationWithSingleFace_addsOnlyEmbeddingNotImage() {
        coordinator.requestFaceRegistration("person1", "人物A")
        coordinator.onFeatureObservations(
            listOf(FaceFeatureObservation("face-001", floatArrayOf(1f, 0f), 10L)),
        )

        val profile = repository.getAll().single()
        assertThat(profile.personId.value).isEqualTo("person1")
        assertThat(profile.faceSampleCount).isEqualTo(1)
        assertThat(profile.speakerSampleCount).isEqualTo(0)
    }

    @Test
    fun registrationWithMultipleFaces_waitsWithoutSaving() {
        coordinator.requestFaceRegistration("person1", "人物A")
        coordinator.onFeatureObservations(
            listOf(
                FaceFeatureObservation("face-001", floatArrayOf(1f, 0f), 10L),
                FaceFeatureObservation("face-002", floatArrayOf(0f, 1f), 10L),
            ),
        )

        assertThat(repository.getAll()).isEmpty()
        assertThat(coordinator.state.value.registrationMessage).contains("複数")
    }

    @Test
    fun deleteAll_removesFaceAndSpeakerProfiles() {
        repository.addFaceEmbedding(
            com.example.pepper_person_id_poc.domain.person.PersonId("person1"),
            "人物A",
            floatArrayOf(1f),
            "SFace",
            1L,
        )

        coordinator.deleteAllRegistrations()

        assertThat(repository.getAll()).isEmpty()
    }
}
