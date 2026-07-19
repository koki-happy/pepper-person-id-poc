package com.example.pepper_person_id_poc.domain.speaker

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.example.pepper_person_id_poc.speakercore.UnknownReason
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
    fun scoreAboveThreshold_identifiesBestPersonUsingCentroid() {
        val result = identifier.identify(
            utteranceId = "utterance-001",
            embedding = floatArrayOf(0.9f, 0.1f),
            profiles = listOf(personB, personA),
            threshold = 0.99f,
            processingTimeMillis = 42L,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.IDENTIFIED)
        assertThat(result.personId).isEqualTo(PersonId("person1"))
        assertThat(result.displayName).isEqualTo("人物A")
        assertThat(result.bestCandidatePersonId).isEqualTo(PersonId("person1"))
        assertThat(result.secondBestCandidatePersonId).isEqualTo(PersonId("person2"))
        assertThat(result.secondBestScore).isNotNull()
        assertThat(result.margin).isNotNull()
        assertThat(result.unknownReasons).isEmpty()
        assertThat(result.processingTimeMillis).isEqualTo(42L)
    }

    @Test
    fun centroidScoreBelowThreshold_returnsUnknownEvenWhenOneSampleIsExactMatch() {
        val spreadSamples = profile(
            "person3",
            "人物C",
            floatArrayOf(1f, 0f),
            floatArrayOf(0f, 1f),
        )

        val result = identifier.identify(
            utteranceId = "utterance-centroid",
            embedding = floatArrayOf(1f, 0f),
            profiles = listOf(spreadSamples),
            threshold = 0.8f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.UNKNOWN)
        assertThat(result.score).isWithin(1e-6f).of(0.70710677f)
        assertThat(result.unknownReasons).containsExactly(UnknownReason.BELOW_THRESHOLD)
    }

    @Test
    fun scoreBelowThreshold_returnsUnknownAndRetainsRankedScores() {
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
        assertThat(result.secondBestCandidatePersonId).isNotNull()
        assertThat(result.secondBestScore).isNotNull()
        assertThat(result.margin).isNotNull()
        assertThat(result.unknownReasons).containsExactly(UnknownReason.BELOW_THRESHOLD)
    }

    @Test
    fun scoreAtMarginBoundary_isIdentified() {
        val result = identifier.identify(
            utteranceId = "utterance-margin-boundary",
            embedding = floatArrayOf(1f, 0f),
            profiles = listOf(
                profile("person1", "人物A", floatArrayOf(1f, 0f)),
                profile("person2", "人物B", floatArrayOf(0.8f, 0.6f)),
            ),
            threshold = 0.5f,
            minimumMargin = 0.2f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.IDENTIFIED)
        assertThat(result.personId).isEqualTo(PersonId("person1"))
        assertThat(result.margin).isWithin(1e-6f).of(0.2f)
        assertThat(result.minimumMargin).isEqualTo(0.2f)
        assertThat(result.unknownReasons).isEmpty()
    }

    @Test
    fun scoreBelowMinimumMargin_returnsUnknownWithReason() {
        val result = identifier.identify(
            utteranceId = "utterance-small-margin",
            embedding = floatArrayOf(1f, 0f),
            profiles = listOf(
                profile("person1", "人物A", floatArrayOf(1f, 0f)),
                profile("person2", "人物B", floatArrayOf(0.8f, 0.6f)),
            ),
            threshold = 0.5f,
            minimumMargin = 0.21f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.UNKNOWN)
        assertThat(result.personId).isNull()
        assertThat(result.score).isWithin(1e-6f).of(1f)
        assertThat(result.secondBestScore).isWithin(1e-6f).of(0.8f)
        assertThat(result.margin).isWithin(1e-6f).of(0.2f)
        assertThat(result.unknownReasons).containsExactly(UnknownReason.INSUFFICIENT_MARGIN)
    }

    @Test
    fun profilesWithoutValidCompatibleCentroids_returnUnknownWithoutCandidate() {
        val incompatible = profile("person3", "人物C", floatArrayOf(1f, 0f, 0f))
        val noSamples = profile("person4", "人物D")
        val inconsistentDimensions = profile(
            "person5",
            "人物E",
            floatArrayOf(1f, 0f),
            floatArrayOf(1f, 0f, 0f),
        )
        val cancelling = profile(
            "person6",
            "人物F",
            floatArrayOf(1f, 0f),
            floatArrayOf(-1f, 0f),
        )

        val result = identifier.identify(
            "utterance-003",
            floatArrayOf(1f, 0f),
            listOf(incompatible, noSamples, inconsistentDimensions, cancelling),
            0.5f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.UNKNOWN)
        assertThat(result.bestCandidatePersonId).isNull()
        assertThat(result.score).isNull()
        assertThat(result.unknownReasons).containsExactly(UnknownReason.NO_CANDIDATES)
    }

    @Test
    fun invalidProfile_isExcludedWhileValidProfileRemainsEligible() {
        val invalid = profile(
            "person3",
            "人物C",
            floatArrayOf(0f, 0f),
            floatArrayOf(Float.NaN, 1f),
        )

        val result = identifier.identify(
            "utterance-004",
            floatArrayOf(0f, 1f),
            listOf(invalid, personB),
            0.5f,
        )

        assertThat(result.status).isEqualTo(SpeakerIdentityStatus.IDENTIFIED)
        assertThat(result.personId).isEqualTo(PersonId("person2"))
        assertThat(result.score).isWithin(1e-6f).of(1f)
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
        assertThrows(IllegalArgumentException::class.java) {
            identifier.identify(
                "utterance",
                floatArrayOf(1f),
                emptyList(),
                0.5f,
                minimumMargin = 2.1f,
            )
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
