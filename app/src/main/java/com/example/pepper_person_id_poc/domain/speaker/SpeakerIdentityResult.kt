package com.example.pepper_person_id_poc.domain.speaker

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.speakercore.UnknownReason

data class SpeakerIdentityResult(
    val utteranceId: String,
    val status: SpeakerIdentityStatus,
    val personId: PersonId?,
    val displayName: String?,
    val score: Float?,
    val bestCandidatePersonId: PersonId?,
    val threshold: Float,
    val processingTimeMillis: Long,
    val secondBestCandidatePersonId: PersonId? = null,
    val secondBestScore: Float? = null,
    val margin: Float? = null,
    val minimumMargin: Float = 0f,
    val unknownReasons: List<UnknownReason> = emptyList(),
)

enum class SpeakerIdentityStatus {
    NO_SPEECH,
    INSUFFICIENT_AUDIO,
    EXCESSIVE_NOISE,
    IDENTIFIED,
    UNKNOWN,
}
