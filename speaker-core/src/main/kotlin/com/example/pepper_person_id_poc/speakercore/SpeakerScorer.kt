package com.example.pepper_person_id_poc.speakercore

/** One registered speaker represented by a normalized or unnormalized centroid vector. */
data class SpeakerCentroid(
    val speakerId: String,
    val embedding: FloatArray,
) {
    init {
        require(speakerId.isNotBlank()) { "speakerId must not be blank" }
        EmbeddingMath.requireValidEmbedding(embedding)
    }
}

data class SpeakerScoringConfig(
    val threshold: Float,
    val minimumMargin: Float,
) {
    init {
        require(threshold in -1f..1f) { "threshold must be in [-1, 1]" }
        require(minimumMargin in 0f..2f) { "minimumMargin must be in [0, 2]" }
    }
}

data class SpeakerScore(
    val speakerId: String,
    val score: Float,
) {
    init {
        require(speakerId.isNotBlank()) { "speakerId must not be blank" }
        require(score in -1f..1f) { "score must be finite and in [-1, 1]" }
    }
}

enum class SpeakerDecision {
    IDENTIFIED,
    UNKNOWN,
}

enum class UnknownReason {
    NO_CANDIDATES,
    BELOW_THRESHOLD,
    INSUFFICIENT_MARGIN,
}

/**
 * Scores are sorted from highest to lowest. Equal scores preserve centroid input order.
 */
data class SpeakerScoringResult(
    val decision: SpeakerDecision,
    val identifiedSpeakerId: String?,
    val scores: List<SpeakerScore>,
    val top1: SpeakerScore?,
    val top2: SpeakerScore?,
    val margin: Float?,
    val unknownReasons: List<UnknownReason>,
) {
    init {
        require((decision == SpeakerDecision.IDENTIFIED) == (identifiedSpeakerId != null)) {
            "identifiedSpeakerId must be present only for an IDENTIFIED decision"
        }
        require((decision == SpeakerDecision.UNKNOWN) == unknownReasons.isNotEmpty()) {
            "UNKNOWN decisions require a reason and IDENTIFIED decisions must not have one"
        }
    }
}

/** Applies open-set threshold and Top-1/Top-2 margin decisions to speaker centroids. */
class SpeakerScorer(
    val config: SpeakerScoringConfig,
) {
    constructor(threshold: Float, minimumMargin: Float) :
        this(SpeakerScoringConfig(threshold, minimumMargin))

    fun score(
        embedding: FloatArray,
        centroids: List<SpeakerCentroid>,
    ): SpeakerScoringResult {
        EmbeddingMath.requireValidEmbedding(embedding)
        require(centroids.map(SpeakerCentroid::speakerId).distinct().size == centroids.size) {
            "speakerId values must be unique"
        }

        if (centroids.isEmpty()) {
            return SpeakerScoringResult(
                decision = SpeakerDecision.UNKNOWN,
                identifiedSpeakerId = null,
                scores = emptyList(),
                top1 = null,
                top2 = null,
                margin = null,
                unknownReasons = listOf(UnknownReason.NO_CANDIDATES),
            )
        }

        val scores = centroids
            .mapIndexed { index, centroid ->
                EmbeddingMath.requireValidEmbedding(centroid.embedding, embedding.size)
                IndexedScore(
                    inputIndex = index,
                    score = SpeakerScore(
                        speakerId = centroid.speakerId,
                        score = EmbeddingMath.cosineSimilarity(embedding, centroid.embedding),
                    ),
                )
            }
            .sortedWith(
                compareByDescending<IndexedScore> { it.score.score }
                    .thenBy(IndexedScore::inputIndex),
            )
            .map(IndexedScore::score)

        val top1 = scores[0]
        val top2 = scores.getOrNull(1)
        val margin = top2?.let { top1.score - it.score }
        val unknownReasons = buildList {
            if (!meetsSpeakerThreshold(top1.score, config.threshold)) {
                add(UnknownReason.BELOW_THRESHOLD)
            }
            if (margin != null && margin + MARGIN_DECISION_EPSILON < config.minimumMargin) {
                add(UnknownReason.INSUFFICIENT_MARGIN)
            }
        }
        val identified = unknownReasons.isEmpty()

        return SpeakerScoringResult(
            decision = if (identified) SpeakerDecision.IDENTIFIED else SpeakerDecision.UNKNOWN,
            identifiedSpeakerId = top1.speakerId.takeIf { identified },
            scores = scores,
            top1 = top1,
            top2 = top2,
            margin = margin,
            unknownReasons = unknownReasons,
        )
    }

    private data class IndexedScore(
        val inputIndex: Int,
        val score: SpeakerScore,
    )

    private companion object {
        // The margin is derived by subtracting two Float scores, so tolerate its final rounding bits.
        const val MARGIN_DECISION_EPSILON = 1e-6f
    }
}

/** A score exactly equal to the threshold is accepted; every lower Float value is rejected. */
internal fun meetsSpeakerThreshold(score: Float, threshold: Float): Boolean = score >= threshold
