package com.example.pepper_person_id_poc.domain.face

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceIdentifierTest {
    private val identifier = FaceIdentifier()
    private val personA = profile("person1", "人物A", floatArrayOf(1f, 0f), floatArrayOf(0.9f, 0.1f))
    private val personB = profile("person2", "人物B", floatArrayOf(0f, 1f))

    @Test
    fun scoreAtOrAboveThreshold_identifiesBestPersonAcrossSamples() {
        val result = identifier.identify(
            trackId = "face-001",
            embedding = floatArrayOf(0.95f, 0.05f),
            profiles = listOf(personA, personB),
            threshold = 0.8f,
        )

        assertThat(result.status).isEqualTo(FaceIdentityStatus.IDENTIFIED)
        assertThat(result.personId).isEqualTo(PersonId("person1"))
        assertThat(result.displayName).isEqualTo("人物A")
    }

    @Test
    fun scoreBelowThreshold_returnsUnknownButKeepsBestCandidateInternally() {
        val result = identifier.identify(
            trackId = "face-001",
            embedding = floatArrayOf(1f, 1f),
            profiles = listOf(personA, personB),
            threshold = 0.95f,
        )

        assertThat(result.status).isEqualTo(FaceIdentityStatus.UNKNOWN)
        assertThat(result.personId).isNull()
        assertThat(result.displayName).isNull()
        assertThat(result.bestCandidatePersonId).isNotNull()
        assertThat(result.score).isLessThan(0.95f)
    }

    @Test
    fun noRegisteredSamples_returnsUnknownWithoutCandidate() {
        val result = identifier.identify("face-001", floatArrayOf(1f, 0f), emptyList(), 0.5f, 0.1f)

        assertThat(result.status).isEqualTo(FaceIdentityStatus.UNKNOWN)
        assertThat(result.bestCandidatePersonId).isNull()
        assertThat(result.score).isNull()
    }

    @Test
    fun bestCandidateBelowMinimumMargin_returnsUnknown() {
        val closePersonB = profile("person2", "人物B", floatArrayOf(0.98f, 0.02f))

        val result = identifier.identify(
            trackId = "face-001",
            embedding = floatArrayOf(1f, 0f),
            profiles = listOf(personA, closePersonB),
            threshold = 0.8f,
            minimumMargin = 0.05f,
        )

        assertThat(result.status).isEqualTo(FaceIdentityStatus.UNKNOWN)
        assertThat(result.secondScore).isNotNull()
        assertThat(result.margin).isLessThan(0.05f)
    }

    @Test
    fun singleCandidate_needsThresholdButHasNoAmbiguousSecondCandidate() {
        val result = identifier.identify(
            trackId = "face-001",
            embedding = floatArrayOf(1f, 0f),
            profiles = listOf(personA),
            threshold = 0.8f,
            minimumMargin = 0.5f,
        )

        assertThat(result.status).isEqualTo(FaceIdentityStatus.IDENTIFIED)
        assertThat(result.secondScore).isNull()
        assertThat(result.margin).isNull()
    }

    private fun profile(
        id: String,
        name: String,
        vararg embeddings: FloatArray,
    ) = PersonProfile(
        personId = PersonId(id),
        displayName = name,
        faceEmbeddings = embeddings.toList(),
        speakerEmbeddings = emptyList(),
        faceModelName = "test",
        speakerModelName = null,
        registeredAtMillis = 1L,
    )
}
