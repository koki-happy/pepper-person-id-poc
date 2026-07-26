package com.example.pepper_person_id_poc.domain.anonymous

import com.example.pepper_person_id_poc.domain.model.ModelSpaceId

object IdentificationEvaluator {
    fun evaluate(
        modelSpaceId: ModelSpaceId,
        embedding: FloatArray,
        clusters: List<AnonymousCluster>,
        threshold: Float,
        minimumLead: Float,
    ): IdentificationEvaluation {
        require(threshold in -1f..1f)
        require(minimumLead in 0f..2f)

        val normalized = runCatching { AnonymousClusterEngine.normalize(embedding) }
            .getOrElse {
                return IdentificationEvaluation(
                    candidates = emptyList(),
                    highestScore = null,
                    secondHighestScore = null,
                    highestCandidateLead = null,
                    threshold = threshold,
                    minimumLead = minimumLead,
                    decision = IdentificationDecision.UNKNOWN,
                )
            }
        val scored = clusters.asSequence()
            .filter {
                it.modelSpaceId == modelSpaceId &&
                    it.embeddingDimension == normalized.size
            }
            .map { cluster ->
                cluster to AnonymousClusterEngine.cosine(normalized, cluster.centroid)
            }
            .sortedWith(
                compareByDescending<Pair<AnonymousCluster, Float>> { it.second }
                    .thenBy { it.first.anonymousId },
            )
            .toList()

        val highest = scored.firstOrNull()?.second
        val second = scored.getOrNull(1)?.second
        val lead = if (highest != null && second != null) highest - second else null
        val decision = when {
            highest == null && clusters.isEmpty() -> IdentificationDecision.CREATED_NEW
            highest == null -> IdentificationDecision.UNKNOWN
            highest < threshold -> IdentificationDecision.CREATED_NEW
            second != null && checkNotNull(lead) < minimumLead -> IdentificationDecision.AMBIGUOUS
            else -> IdentificationDecision.MATCHED_EXISTING
        }
        val selectedId = if (decision == IdentificationDecision.MATCHED_EXISTING) {
            scored.first().first.anonymousId
        } else {
            null
        }
        val candidates = scored.mapIndexed { index, (cluster, score) ->
            IdentificationCandidateScore(
                rank = index + 1,
                anonymousId = cluster.anonymousId,
                score = score,
                modelSpaceId = cluster.modelSpaceId,
                embeddingDimension = cluster.embeddingDimension,
                updateCount = cluster.updateCount,
                selected = cluster.anonymousId == selectedId,
            )
        }
        return IdentificationEvaluation(
            candidates = candidates,
            highestScore = highest,
            secondHighestScore = second,
            highestCandidateLead = lead,
            threshold = threshold,
            minimumLead = minimumLead,
            decision = decision,
        )
    }
}
