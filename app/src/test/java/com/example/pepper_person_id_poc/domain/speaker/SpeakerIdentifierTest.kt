package com.example.pepper_person_id_poc.domain.speaker

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SpeakerIdentifierTest {
    private val identifier = SpeakerIdentifier()
    private val personA = profile("person1", "人物A", floatArrayOf(1f, 0f), floatArrayOf(0.9f, 0.1f))
    private val personB = profile("person2", "人物B", floatArrayOf(0f, 1f))

    @Test
    fun scoreAtThreshold_identifiesBestPersonAcrossAllSamples() {
        val result = identifier.identify(
            utteranceId = "utterance-001",
            embedding = floatArrayOf(0.9f, 0.1f),
            profiles = listOf(personB, personA),
            threshold = 1f,
            processingTimeMillis = 42L,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.IDENTIFIED)
        assertThat(result.personId).isEqualTo(PersonId("person1"))
        assertThat(result.displayName).isEqualTo("人物A")
        assertThat(result.bestCandidatePersonId).isEqualTo(PersonId("person1"))
        assertThat(result.processingTimeMillis).isEqualTo(42L)
    }

    @Test
    fun scoreBelowThreshold_returnsUnknownAndRetainsOnlyDebugCandidate() {
        val result = identifier.identify(
            utteranceId = "utterance-002",
            embedding = floatArrayOf(1f, 1f),
            profiles = listOf(personA, personB),
            threshold = 0.9f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.UNKNOWN)
        assertThat(result.personId).isNull()
        assertThat(result.displayName).isNull()
        assertThat(result.bestCandidatePersonId).isNotNull()
        assertThat(result.score).isLessThan(0.9f)
    }

    @Test
    fun profilesWithoutCompatibleSpeakerSamples_returnUnknownWithoutCandidate() {
        val incompatible = profile("person3", "人物C", floatArrayOf(1f, 0f, 0f))
        val noSamples = profile("person4", "人物D")

        val result = identifier.identify(
            "utterance-003",
            floatArrayOf(1f, 0f),
            listOf(incompatible, noSamples),
            0.5f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.UNKNOWN)
        assertThat(result.bestCandidatePersonId).isNull()
        assertThat(result.score).isNull()
    }

    @Test
    fun zeroAndNonFiniteRegisteredSamples_areIgnored() {
        val invalid = profile(
            "person3",
            "人物C",
            floatArrayOf(0f, 0f),
            floatArrayOf(Float.NaN, 1f),
        )

        val result = identifier.identify("utterance-004", floatArrayOf(1f, 0f), listOf(invalid), 0.5f)

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.UNKNOWN)
        assertThat(result.score).isNull()
    }

    @Test
    fun invalidArguments_areRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify("", floatArrayOf(1f), emptyList(), 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify("utterance", floatArrayOf(), emptyList(), 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify("utterance", floatArrayOf(0f), emptyList(), 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify("utterance", floatArrayOf(Float.NaN), emptyList(), 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify("utterance", floatArrayOf(1f), emptyList(), 1.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify("utterance", floatArrayOf(1f), emptyList(), 0.5f, -1L)
        }
    }

    private fun profile(
        id: String,
        name: String,
        vararg speakerEmbeddings: FloatArray,
    ) = PersonProfile(
        personId = PersonId(id),
        displayName = name,
        faceEmbeddings = emptyList(),
        speakerEmbeddings = speakerEmbeddings.toList(),
        faceModelName = null,
        speakerModelName = "test",
        registeredAtMillis = 1L,
    )
}
