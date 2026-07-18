package com.example.pepper_person_id_poc.domain.face

import com.example.pepper_person_id_poc.domain.person.PersonId

data class FaceIdentityResult(
    val trackId: String,
    val status: FaceIdentityStatus,
    val personId: PersonId?,
    val displayName: String?,
    val score: Float?,
    val secondScore: Float?,
    val margin: Float?,
    val bestCandidatePersonId: PersonId?,
    val threshold: Float,
    val minimumMargin: Float,
    val processingTimeMillis: Long,
)

enum class FaceIdentityStatus {
    IDENTIFIED,
    UNKNOWN,
}
