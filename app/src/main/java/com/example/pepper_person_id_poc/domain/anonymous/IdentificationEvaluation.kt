package com.example.pepper_person_id_poc.domain.anonymous

import com.example.pepper_person_id_poc.domain.model.ModelSpaceId

data class IdentificationCandidateScore(
    val rank: Int,
    val anonymousId: String,
    val score: Float,
    val modelSpaceId: ModelSpaceId,
    val embeddingDimension: Int,
    val updateCount: Int,
    val selected: Boolean,
) {
    init {
        require(rank >= 1)
        require(anonymousId.isNotBlank())
        require(score.isFinite())
        require(embeddingDimension > 0)
        require(updateCount >= 1)
    }
}

enum class IdentificationDecision {
    MATCHED_EXISTING,
    CREATED_NEW,
    AMBIGUOUS,
    UNKNOWN,
}

data class IdentificationEvaluation(
    val candidates: List<IdentificationCandidateScore>,
    val highestScore: Float?,
    val secondHighestScore: Float?,
    val highestCandidateLead: Float?,
    val threshold: Float,
    val minimumLead: Float,
    val decision: IdentificationDecision,
) {
    init {
        require(threshold in -1f..1f)
        require(minimumLead in 0f..2f)
        require(highestScore == null || highestScore.isFinite())
        require(secondHighestScore == null || secondHighestScore.isFinite())
        require(highestCandidateLead == null || highestCandidateLead.isFinite())
        require(candidates.map { it.rank } == (1..candidates.size).toList())
        require(candidates.zipWithNext().all { (left, right) ->
            left.score > right.score ||
                (left.score == right.score && left.anonymousId <= right.anonymousId)
        })
        require(candidates.count { it.selected } <= 1)
        require(highestScore == candidates.firstOrNull()?.score)
        require(secondHighestScore == candidates.getOrNull(1)?.score)
        if (candidates.size < 2) {
            require(highestCandidateLead == null)
        } else {
            require(highestCandidateLead != null)
        }
    }
}

data class IdentificationClusterCandidate(
    val anonymousId: String,
    val modelSpaceId: ModelSpaceId,
    val embeddingDimension: Int,
    val centroid: FloatArray,
    val updateCount: Int,
) {
    init {
        require(anonymousId.isNotBlank())
        require(embeddingDimension > 0)
        require(centroid.size == embeddingDimension)
        require(centroid.all(Float::isFinite))
        require(updateCount >= 1)
    }
}
