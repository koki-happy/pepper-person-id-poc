package com.example.pepper_person_id_poc.speakerbenchmark

import com.example.pepper_person_id_poc.speakercore.SpeakerCentroid
import com.example.pepper_person_id_poc.speakercore.SpeakerEvaluationMetrics
import com.example.pepper_person_id_poc.speakercore.SpeakerEvaluationTrial
import com.example.pepper_person_id_poc.speakercore.SpeakerEvaluator
import com.example.pepper_person_id_poc.speakercore.SpeakerScorer

data class DevelopmentEmbedding(
    val trialId: String,
    val expectedSpeakerId: String?,
    val embedding: FloatArray,
)

data class SelectedOperatingPoint(
    val threshold: Float,
    val minimumMargin: Float,
    val metrics: SpeakerEvaluationMetrics,
)

/** Selects threshold and margin exclusively from the development split. */
object ThresholdTuner {
    fun select(
        samples: List<DevelopmentEmbedding>,
        centroids: List<SpeakerCentroid>,
    ): SelectedOperatingPoint {
        require(samples.isNotEmpty()) { "Development threshold selection requires development samples" }
        require(samples.any { it.expectedSpeakerId != null }) {
            "Development threshold selection requires at least one enrolled-speaker sample"
        }
        require(samples.any { it.expectedSpeakerId == null }) {
            "Development threshold selection requires at least one unknown-speaker sample"
        }
        require(centroids.isNotEmpty()) { "Development threshold selection requires enrollment centroids" }

        val unfiltered = SpeakerScorer(-1f, 0f)
        val baseScores = samples.map { sample -> unfiltered.score(sample.embedding, centroids) }
        val thresholds = buildSet {
            add(-1f)
            add(1f)
            baseScores.mapNotNull { it.top1?.score }.forEach { score ->
                add(score)
                add(Math.nextUp(score).coerceAtMost(1f))
            }
        }.sorted()
        val margins = if (centroids.size < 2) {
            listOf(0f)
        } else {
            buildSet {
                add(0f)
                add(2f)
                baseScores.mapNotNull { it.margin }.forEach { margin ->
                    add(margin)
                    add(Math.nextUp(margin).coerceAtMost(2f))
                }
            }.sorted()
        }

        var best: SelectedOperatingPoint? = null
        thresholds.forEach { threshold ->
            margins.forEach { margin ->
                val scorer = SpeakerScorer(threshold, margin)
                val trials = samples.map { sample ->
                    SpeakerEvaluationTrial(
                        trialId = sample.trialId,
                        expectedSpeakerId = sample.expectedSpeakerId,
                        durationSeconds = 1,
                        scoringResult = scorer.score(sample.embedding, centroids),
                    )
                }
                val candidate = SelectedOperatingPoint(
                    threshold = threshold,
                    minimumMargin = margin,
                    metrics = SpeakerEvaluator.evaluate(trials),
                )
                if (best == null || candidate.isBetterThan(checkNotNull(best))) best = candidate
            }
        }
        return checkNotNull(best)
    }

    private fun SelectedOperatingPoint.isBetterThan(other: SelectedOperatingPoint): Boolean {
        val accuracy = metrics.finalDecisionAccuracy ?: -1.0
        val otherAccuracy = other.metrics.finalDecisionAccuracy ?: -1.0
        if (accuracy != otherAccuracy) return accuracy > otherAccuracy

        val far = metrics.falseAcceptanceRate ?: 1.0
        val otherFar = other.metrics.falseAcceptanceRate ?: 1.0
        if (far != otherFar) return far < otherFar
        if (metrics.identityConfusions != other.metrics.identityConfusions) {
            return metrics.identityConfusions < other.metrics.identityConfusions
        }
        val frr = metrics.falseRejectionRate ?: 1.0
        val otherFrr = other.metrics.falseRejectionRate ?: 1.0
        if (frr != otherFrr) return frr < otherFrr

        // Prefer a conservative threshold but the smallest margin that the development data proves
        // necessary, avoiding an over-fitted margin at a floating-point boundary.
        if (threshold != other.threshold) return threshold > other.threshold
        return minimumMargin < other.minimumMargin
    }
}
