package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
import com.example.pepper_person_id_poc.domain.face.HeadPose
import com.example.pepper_person_id_poc.domain.face.RegistrationPose
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import com.example.pepper_person_id_poc.testsupport.FakePersonRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceIdentityCoordinatorTest {
    private val repository = FakePersonRepository()
    private var nowMillis = 0L
    private val coordinator = FaceIdentityCoordinator(
        personRepository = repository,
        faceIdentifier = FaceIdentifier(),
        settings = PocSettings(faceSmoothingSampleCount = 1),
        faceModelName = "SFace 2021dec",
        clockMillis = { nowMillis },
    )

    @Test
    fun registrationWithFrontLeftRight_addsExactlyThreeEmbeddings() {
        coordinator.requestFaceRegistration("person1", "人物A")

        capturePose(HeadPose(0f, 0f, 0f), floatArrayOf(1f, 0f))
        assertThat(coordinator.state.value.registrationTarget).isEqualTo(RegistrationPose.LEFT)
        capturePose(HeadPose(-20f, 0f, 0f), floatArrayOf(0.9f, 0.1f))
        assertThat(coordinator.state.value.registrationTarget).isEqualTo(RegistrationPose.RIGHT)
        capturePose(HeadPose(20f, 0f, 0f), floatArrayOf(0.8f, 0.2f))

        val profile = repository.getAll().single()
        assertThat(profile.personId.value).isEqualTo("person1")
        assertThat(profile.faceSampleCount).isEqualTo(3)
        assertThat(profile.speakerSampleCount).isEqualTo(0)
        assertThat(coordinator.state.value.registrationTarget).isNull()
        assertThat(coordinator.state.value.registrationMessage).contains("完了")
    }

    @Test
    fun registrationWithMultipleFaces_waitsWithoutSaving() {
        coordinator.requestFaceRegistration("person1", "人物A")
        coordinator.onPoseObservations(
            listOf(
                FacePoseObservation("face-001", HeadPose(0f, 0f, 0f)),
                FacePoseObservation("face-002", HeadPose(0f, 0f, 0f)),
            ),
        )

        assertThat(repository.getAll()).isEmpty()
        assertThat(coordinator.state.value.registrationMessage).contains("複数")
    }

    @Test
    fun registrationLeavingPoseRange_resetsHoldWithoutExtracting() {
        coordinator.requestFaceRegistration("person1", "人物A")
        coordinator.onPoseObservations(listOf(pose(0f)))
        nowMillis = 900L
        coordinator.onPoseObservations(listOf(pose(20f)))
        nowMillis = 1_000L
        coordinator.onPoseObservations(listOf(pose(0f)))
        nowMillis = 1_900L

        val requestedTracks = coordinator.onPoseObservations(listOf(pose(0f)))

        assertThat(requestedTracks).isEmpty()
        assertThat(repository.getAll()).isEmpty()
        assertThat(coordinator.state.value.poseProgressMillis).isEqualTo(900L)
    }

    @Test
    fun reRegistration_replacesPriorFaceSamplesOnlyAfterAllThreePosesComplete() {
        repository.addFaceEmbedding(
            com.example.pepper_person_id_poc.domain.person.PersonId("person1"),
            "人物A",
            floatArrayOf(9f, 9f),
            "SFace 2021dec",
            1L,
        )
        coordinator.requestFaceRegistration("person1", "人物A")
        capturePose(HeadPose(0f, 0f, 0f), floatArrayOf(1f, 0f))
        coordinator.cancelFaceRegistration()
        assertThat(repository.getAll().single().faceSampleCount).isEqualTo(1)

        coordinator.requestFaceRegistration("person1", "人物A")
        capturePose(HeadPose(0f, 0f, 0f), floatArrayOf(1f, 0f))
        capturePose(HeadPose(-20f, 0f, 0f), floatArrayOf(0.9f, 0.1f))
        capturePose(HeadPose(20f, 0f, 0f), floatArrayOf(0.8f, 0.2f))

        assertThat(repository.getAll().single().faceSampleCount).isEqualTo(3)
    }

    @Test
    fun realTimeIdentification_updatesAllVisibleFacesEveryAnalysis() {
        repository.addFaceEmbedding(
            com.example.pepper_person_id_poc.domain.person.PersonId("person1"),
            "人物A",
            floatArrayOf(1f, 0f),
            "SFace 2021dec",
            1L,
        )

        val realTimeCoordinator = FaceIdentityCoordinator(
            personRepository = repository,
            faceIdentifier = FaceIdentifier(),
            settings = PocSettings(faceSmoothingSampleCount = 1),
            faceModelName = "SFace 2021dec",
            realTimeIdentificationEnabled = true,
            clockMillis = { nowMillis },
        )
        val faces = listOf(pose(0f), FacePoseObservation("face-002", HeadPose(0f, 0f, 0f)))
        assertThat(realTimeCoordinator.onFaceAnalysis(2, faces)).containsExactly("face-001", "face-002")
        realTimeCoordinator.onFeatureObservations(
            listOf(
                FaceFeatureObservation("face-001", floatArrayOf(1f, 0f), 1L),
                FaceFeatureObservation("face-002", floatArrayOf(0f, 1f), 1L),
            ),
        )
        assertThat(realTimeCoordinator.state.value.results).hasSize(2)
        assertThat(realTimeCoordinator.state.value.results.first { it.trackId == "face-001" }.personId?.value)
            .isEqualTo("person1")
        assertThat(realTimeCoordinator.state.value.results.first { it.trackId == "face-002" }.personId).isNull()

        assertThat(realTimeCoordinator.onPoseObservations(listOf(pose(0f)))).containsExactly("face-001")
        realTimeCoordinator.onFeatureObservations(
            listOf(FaceFeatureObservation("face-001", floatArrayOf(0f, 1f), 1L)),
        )
        assertThat(realTimeCoordinator.state.value.results.single().personId).isNull()

        assertThat(realTimeCoordinator.onFaceAnalysis(0, emptyList())).isEmpty()
        assertThat(realTimeCoordinator.state.value.results).isEmpty()
    }

    @Test
    fun realTimeIdentification_withoutEnrollment_tracksAndReidentifiesAnonymousFace() {
        val realTimeCoordinator = FaceIdentityCoordinator(
            personRepository = repository,
            faceIdentifier = FaceIdentifier(),
            settings = PocSettings(faceSmoothingSampleCount = 1, faceThreshold = 0.8f),
            faceModelName = "SFace 2021dec",
            realTimeIdentificationEnabled = true,
            clockMillis = { nowMillis },
        )

        assertThat(realTimeCoordinator.onPoseObservations(listOf(pose(0f)))).containsExactly("face-001")
        realTimeCoordinator.onFeatureObservations(
            listOf(FaceFeatureObservation("face-001", floatArrayOf(1f, 0f), 1L)),
        )
        val firstId = realTimeCoordinator.state.value.anonymousResults.single().anonymousId

        val reappeared = FacePoseObservation("face-010", HeadPose(0f, 0f, 0f))
        assertThat(realTimeCoordinator.onPoseObservations(listOf(reappeared))).containsExactly("face-010")
        realTimeCoordinator.onFeatureObservations(
            listOf(FaceFeatureObservation("face-010", floatArrayOf(0.99f, 0.01f), 1L)),
        )

        assertThat(realTimeCoordinator.state.value.anonymousResults.single().anonymousId).isEqualTo(firstId)
        assertThat(realTimeCoordinator.state.value.anonymousClusterCount).isEqualTo(1)
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

    private fun capturePose(headPose: HeadPose, embedding: FloatArray) {
        coordinator.onPoseObservations(listOf(FacePoseObservation("face-001", headPose)))
        nowMillis += 1_000L
        assertThat(coordinator.onPoseObservations(listOf(FacePoseObservation("face-001", headPose))))
            .containsExactly("face-001")
        coordinator.onFeatureObservations(listOf(FaceFeatureObservation("face-001", embedding, 10L)))
        nowMillis += 1L
    }

    private fun pose(yaw: Float) = FacePoseObservation("face-001", HeadPose(yaw, 0f, 0f))
}
