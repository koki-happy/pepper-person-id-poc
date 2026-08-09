package com.example.pepper_person_id_poc.domain.relation

import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceSpeakerRelationTest {
    @Test
    fun choosesFaceWithLongestTimeOverlap() {
        val relation = calculate(
            faces = listOf(
                face("track-1", "FP001", first = 0L, last = 700L, similarity = 0.70f),
                face("track-2", "FP002", first = 600L, last = 1_400L, similarity = 0.95f),
            ),
        )

        assertThat(relation.status).isEqualTo(FaceSpeakerRelationStatus.MATCHED)
        assertThat(relation.faceId).isEqualTo("FP001")
        assertThat(relation.speakerId).isEqualTo("SP001")
        assertThat(relation.overlapRatio).isWithin(0.001f).of(0.7f)
        assertThat(relation.overlapMillis).isEqualTo(700L)
    }

    @Test
    fun breaksEqualOverlapByFaceSimilarity() {
        val relation = calculate(
            faces = listOf(
                face("track-1", "FP001", first = 100L, last = 600L, similarity = 0.70f),
                face("track-2", "FP002", first = 100L, last = 600L, similarity = 0.90f),
            ),
        )

        assertThat(relation.status).isEqualTo(FaceSpeakerRelationStatus.MATCHED)
        assertThat(relation.faceId).isEqualTo("FP002")
    }

    @Test
    fun suppressesBelowThresholdRelationButKeepsMeasuredRatio() {
        val relation = calculate(
            faces = listOf(face("track-1", "FP001", first = 0L, last = 400L, similarity = 0.90f)),
            threshold = 0.60f,
        )

        assertThat(relation.status).isEqualTo(FaceSpeakerRelationStatus.NO_MATCH)
        assertThat(relation.faceId).isNull()
        assertThat(relation.speakerId).isNull()
        assertThat(relation.overlapRatio).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun suppressesMultipleSpeakersWithoutMapping() {
        val relation = FaceSpeakerRelationCalculator.calculate(
            speakerId = "SP001",
            speakerStartAtMillis = 0L,
            speakerEndAtMillis = 1_000L,
            activityState = SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS,
            faceTracks = listOf(face("track-1", "FP001", first = 0L, last = 1_000L, similarity = 0.90f)),
            threshold = 0.60f,
        )

        assertThat(relation.status).isEqualTo(FaceSpeakerRelationStatus.MULTIPLE_SPEAKERS)
        assertThat(relation.faceId).isNull()
        assertThat(relation.overlapRatio).isNull()
    }

    private fun calculate(
        faces: List<FaceTrackInterval>,
        threshold: Float = 0.50f,
    ) = FaceSpeakerRelationCalculator.calculate(
        speakerId = "SP001",
        speakerStartAtMillis = 0L,
        speakerEndAtMillis = 1_000L,
        activityState = SpeakerActivityState.SINGLE_SPEAKER,
        faceTracks = faces,
        threshold = threshold,
    )

    private fun face(
        trackId: String,
        faceId: String,
        first: Long,
        last: Long,
        similarity: Float,
    ) = FaceTrackInterval(
        trackId = trackId,
        faceId = faceId,
        firstSeenAtMillis = first,
        lastSeenAtMillis = last,
        similarity = similarity,
    )
}
