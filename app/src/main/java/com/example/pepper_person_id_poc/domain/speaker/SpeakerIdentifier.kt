package com.example.pepper_person_id_poc.domain.speaker

import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.example.pepper_person_id_poc.speakercore.EmbeddingMath
import com.example.pepper_person_id_poc.speakercore.SpeakerCentroid
import com.example.pepper_person_id_poc.speakercore.SpeakerDecision
import com.example.pepper_person_id_poc.speakercore.SpeakerScorer

/** Identifies one speaker embedding against one centroid per registered person. */
class SpeakerIdentifier {
    fun identify(
        utteranceId: String,
        embedding: FloatArray,
        profiles: List<PersonProfile>,
        threshold: Float,
        processingTimeMillis: Long = 0L,
        minimumMargin: Float = 0f,
    ): SpeakerIdentityResult {
        require(utteranceId.isNotBlank())
        EmbeddingMath.requireValidEmbedding(embedding)
        require(threshold in 0f..1f)
        require(processingTimeMillis >= 0L)
        require(minimumMargin in 0f..2f)

        val candidates = profiles
            .asSequence()
            .mapNotNull { profile -> profile.toCandidateOrNull(embedding.size) }
            .distinctBy { candidate -> candidate.profile.personId }
            .toList()
        val scoringResult = SpeakerScorer(threshold, minimumMargin).score(
            embedding = embedding,
            centroids = candidates.map(Candidate::centroid),
        )
        val candidatesById = candidates.associateBy { candidate -> candidate.centroid.speakerId }
        val top1Profile = scoringResult.top1?.speakerId?.let(candidatesById::get)?.profile
        val top2Profile = scoringResult.top2?.speakerId?.let(candidatesById::get)?.profile
        val identifiedProfile = scoringResult.identifiedSpeakerId?.let(candidatesById::get)?.profile

        return SpeakerIdentityResult(
            utteranceId = utteranceId,
            status = if (scoringResult.decision == SpeakerDecision.IDENTIFIED) {
                SpeakerIdentityStatus.IDENTIFIED
            } else {
                SpeakerIdentityStatus.UNKNOWN
            },
            personId = identifiedProfile?.personId,
            displayName = identifiedProfile?.displayName,
            score = scoringResult.top1?.score,
            bestCandidatePersonId = top1Profile?.personId,
            threshold = threshold,
            processingTimeMillis = processingTimeMillis,
            secondBestCandidatePersonId = top2Profile?.personId,
            secondBestScore = scoringResult.top2?.score,
            margin = scoringResult.margin,
            minimumMargin = minimumMargin,
            unknownReasons = scoringResult.unknownReasons,
        )
    }
}

private fun PersonProfile.toCandidateOrNull(expectedDimension: Int): Candidate? {
    val centroid = try {
        EmbeddingMath.centroid(speakerEmbeddings)
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (centroid.size != expectedDimension) return null
    return Candidate(
        profile = this,
        centroid = SpeakerCentroid(personId.value, centroid),
    )
}

private data class Candidate(
    val profile: PersonProfile,
    val centroid: SpeakerCentroid,
)

internal fun cosineSimilarityOrNull(first: FloatArray, second: FloatArray): Float? = try {
    EmbeddingMath.cosineSimilarity(first, second)
} catch (_: IllegalArgumentException) {
    null
}
