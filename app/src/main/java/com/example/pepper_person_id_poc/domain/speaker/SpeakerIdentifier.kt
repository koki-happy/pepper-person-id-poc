package com.example.pepper_person_id_poc.domain.speaker

import com.example.pepper_person_id_poc.domain.person.PersonProfile
import kotlin.math.sqrt

/** Identifies one speaker embedding against all registered speaker samples. */
class SpeakerIdentifier {
    fun identify(
        utteranceId: String,
        embedding: FloatArray,
        profiles: List<PersonProfile>,
        threshold: Float,
        processingTimeMillis: Long = 0L,
    ): SpeakerIdentityResult {
        require(utteranceId.isNotBlank())
        require(embedding.isNotEmpty())
        require(embedding.all(Float::isFinite) && embedding.any { it != 0f })
        require(threshold in 0f..1f)
        require(processingTimeMillis >= 0L)

        val best = profiles
            .asSequence()
            .mapNotNull { profile ->
                profile.speakerEmbeddings
                    .asSequence()
                    .mapNotNull { registered -> cosineSimilarityOrNull(embedding, registered) }
                    .maxOrNull()
                    ?.let { score -> profile to score }
            }
            .maxByOrNull { (_, score) -> score }
        val matched = best?.takeIf { (_, score) -> score >= threshold }

        return SpeakerIdentityResult(
            utteranceId = utteranceId,
            status = if (matched == null) SpeakerIdentityStatus.UNKNOWN else SpeakerIdentityStatus.IDENTIFIED,
            personId = matched?.first?.personId,
            displayName = matched?.first?.displayName,
            score = best?.second,
            bestCandidatePersonId = best?.first?.personId,
            threshold = threshold,
            processingTimeMillis = processingTimeMillis,
        )
    }
}

internal fun cosineSimilarityOrNull(first: FloatArray, second: FloatArray): Float? {
    if (first.size != second.size || first.isEmpty()) return null
    var dotProduct = 0.0
    var firstMagnitude = 0.0
    var secondMagnitude = 0.0
    first.indices.forEach { index ->
        val firstValue = first[index].toDouble()
        val secondValue = second[index].toDouble()
        if (!firstValue.isFinite() || !secondValue.isFinite()) return null
        dotProduct += firstValue * secondValue
        firstMagnitude += firstValue * firstValue
        secondMagnitude += secondValue * secondValue
    }
    if (firstMagnitude == 0.0 || secondMagnitude == 0.0) return null
    return (dotProduct / (sqrt(firstMagnitude) * sqrt(secondMagnitude)))
        .toFloat()
        .coerceIn(-1f, 1f)
}
