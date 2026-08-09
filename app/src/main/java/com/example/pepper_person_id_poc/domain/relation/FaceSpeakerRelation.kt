package com.example.pepper_person_id_poc.domain.relation

import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState

enum class FaceSpeakerRelationStatus {
    MATCHED,
    NO_MATCH,
    MULTIPLE_SPEAKERS,
    UNAVAILABLE,
}

data class FaceTrackInterval(
    val trackId: String,
    val faceId: String?,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
    val similarity: Float?,
) {
    init {
        require(trackId.isNotBlank()) { "trackId must not be blank" }
        require(lastSeenAtMillis >= firstSeenAtMillis) {
            "lastSeenAtMillis must not be earlier than firstSeenAtMillis"
        }
        require(similarity == null || similarity.isFinite()) { "similarity must be finite" }
    }
}

data class FaceSpeakerRelation(
    val faceId: String?,
    val speakerId: String?,
    val overlapRatio: Float?,
    val overlapMillis: Long?,
    val status: FaceSpeakerRelationStatus,
)

object FaceSpeakerRelationCalculator {
    fun calculate(
        speakerId: String?,
        speakerStartAtMillis: Long?,
        speakerEndAtMillis: Long?,
        activityState: SpeakerActivityState,
        faceTracks: Collection<FaceTrackInterval>,
        threshold: Float,
    ): FaceSpeakerRelation {
        require(threshold in 0f..1f) { "threshold must be between 0 and 1" }

        if (
            activityState == SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS ||
            activityState == SpeakerActivityState.OVERLAPPED_SPEECH
        ) {
            return FaceSpeakerRelation(
                faceId = null,
                speakerId = null,
                overlapRatio = null,
                overlapMillis = null,
                status = FaceSpeakerRelationStatus.MULTIPLE_SPEAKERS,
            )
        }

        val normalizedSpeakerId = speakerId?.takeUnless { it.isBlank() || it == "unknown" }
        val start = speakerStartAtMillis
        val end = speakerEndAtMillis
        if (
            activityState != SpeakerActivityState.SINGLE_SPEAKER ||
            normalizedSpeakerId == null ||
            start == null ||
            end == null ||
            end <= start
        ) {
            return FaceSpeakerRelation(
                faceId = null,
                speakerId = null,
                overlapRatio = null,
                overlapMillis = null,
                status = FaceSpeakerRelationStatus.UNAVAILABLE,
            )
        }

        val speakerDurationMillis = end - start
        val candidate = faceTracks.mapNotNull { track ->
            val faceId = track.faceId?.takeUnless { it.isBlank() || it == "unknown" || it == "—" }
                ?: return@mapNotNull null
            val overlapMillis = (
                minOf(track.lastSeenAtMillis, end) - maxOf(track.firstSeenAtMillis, start)
                ).coerceAtLeast(0L)
            if (overlapMillis <= 0L) {
                null
            } else {
                Candidate(track, faceId, overlapMillis)
            }
        }.maxWithOrNull(
            compareBy<Candidate> { it.overlapMillis }
                .thenBy { it.track.similarity ?: Float.NEGATIVE_INFINITY }
                .thenBy { it.track.trackId },
        )

        if (candidate == null) {
            return FaceSpeakerRelation(
                faceId = null,
                speakerId = null,
                overlapRatio = 0f,
                overlapMillis = 0L,
                status = FaceSpeakerRelationStatus.NO_MATCH,
            )
        }

        val overlapRatio = candidate.overlapMillis.toFloat() / speakerDurationMillis.toFloat()
        return if (overlapRatio >= threshold) {
            FaceSpeakerRelation(
                faceId = candidate.faceId,
                speakerId = normalizedSpeakerId,
                overlapRatio = overlapRatio,
                overlapMillis = candidate.overlapMillis,
                status = FaceSpeakerRelationStatus.MATCHED,
            )
        } else {
            FaceSpeakerRelation(
                faceId = null,
                speakerId = null,
                overlapRatio = overlapRatio,
                overlapMillis = candidate.overlapMillis,
                status = FaceSpeakerRelationStatus.NO_MATCH,
            )
        }
    }

    private data class Candidate(
        val track: FaceTrackInterval,
        val faceId: String,
        val overlapMillis: Long,
    )
}
