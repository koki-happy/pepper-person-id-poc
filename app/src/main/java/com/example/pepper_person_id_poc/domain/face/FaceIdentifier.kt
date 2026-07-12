package com.example.pepper_person_id_poc.domain.face

import com.example.pepper_person_id_poc.domain.person.PersonProfile
import kotlin.math.sqrt

class FaceIdentifier {
    fun identify(
        trackId: String,
        embedding: FloatArray,
        profiles: List<PersonProfile>,
        threshold: Float,
        processingTimeMillis: Long = 0L,
    ): FaceIdentityResult {
        require(threshold in 0f..1f)
        val best = profiles
            .mapNotNull { profile ->
                profile.faceEmbeddings
                    .mapNotNull { registered -> cosineSimilarityOrNull(embedding, registered) }
                    .maxOrNull()
                    ?.let { score -> profile to score }
            }
            .maxByOrNull { (_, score) -> score }
        val matched = best?.takeIf { (_, score) -> score >= threshold }
        return FaceIdentityResult(
            trackId = trackId,
            status = if (matched != null) FaceIdentityStatus.IDENTIFIED else FaceIdentityStatus.UNKNOWN,
            personId = matched?.first?.personId,
            displayName = matched?.first?.displayName,
            score = best?.second,
            bestCandidatePersonId = best?.first?.personId,
            threshold = threshold,
            processingTimeMillis = processingTimeMillis,
        )
    }

    private fun cosineSimilarityOrNull(first: FloatArray, second: FloatArray): Float? {
        if (first.size != second.size || first.isEmpty()) return null
        var dotProduct = 0.0
        var firstMagnitude = 0.0
        var secondMagnitude = 0.0
        first.indices.forEach { index ->
            val firstValue = first[index].toDouble()
            val secondValue = second[index].toDouble()
            dotProduct += firstValue * secondValue
            firstMagnitude += firstValue * firstValue
            secondMagnitude += secondValue * secondValue
        }
        if (firstMagnitude == 0.0 || secondMagnitude == 0.0) return null
        return (dotProduct / (sqrt(firstMagnitude) * sqrt(secondMagnitude))).toFloat().coerceIn(-1f, 1f)
    }
}
