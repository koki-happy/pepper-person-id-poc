package com.example.pepper_person_id_poc.domain.speaker

import com.example.pepper_person_id_poc.domain.person.PersonId

data class SpeakerIdentityResult(
    val utteranceId: String,
    val status: SpeakerIdentityStatus,
    val personId: PersonId?,
    val displayName: String?,
    val score: Float?,
    val bestCandidatePersonId: PersonId?,
    val threshold: Float,
    val processingTimeMillis: Long,
)

enum class SpeakerIdentityStatus {
    NO_SPEECH,
    INSUFFICIENT_AUDIO,
    EXCESSIVE_NOISE,
    IDENTIFIED,
    UNKNOWN,
}
