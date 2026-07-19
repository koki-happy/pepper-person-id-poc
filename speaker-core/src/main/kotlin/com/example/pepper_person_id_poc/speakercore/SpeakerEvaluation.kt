package com.example.pepper_person_id_poc.speakercore

/** One open-set identification observation. A null [expectedSpeakerId] means an unknown speaker. */
data class SpeakerEvaluationTrial(
    val trialId: String,
    val expectedSpeakerId: String?,
    val durationSeconds: Int,
    val scoringResult: SpeakerScoringResult,
) {
    init {
        require(trialId.isNotBlank()) { "trialId must not be blank" }
        require(expectedSpeakerId == null || expectedSpeakerId.isNotBlank()) {
            "expectedSpeakerId must be null or non-blank"
        }
        require(durationSeconds > 0) { "durationSeconds must be positive" }
    }
}

/** A score labelled for verification-threshold evaluation. */
data class VerificationTrial(
    val score: Float,
    val isGenuine: Boolean,
) {
    init {
        require(score in -1f..1f) { "score must be finite and in [-1, 1]" }
    }
}

data class VerificationMetrics(
    val threshold: Float,
    val genuineTrials: Int,
    val impostorTrials: Int,
    val falseAcceptances: Int,
    val falseRejections: Int,
) {
    val falseAcceptanceRate: Double?
        get() = falseAcceptances.rateOf(impostorTrials)

    val falseRejectionRate: Double?
        get() = falseRejections.rateOf(genuineTrials)
}

/** EER is linearly interpolated between adjacent discrete threshold operating points. */
data class EqualErrorRate(
    val rate: Double,
    val threshold: Float,
)

data class SpeakerEvaluationMetrics(
    val totalTrials: Int,
    val registeredTrials: Int,
    val unknownTrials: Int,
    val top1Correct: Int,
    val correctlyIdentified: Int,
    val correctlyRejectedUnknown: Int,
    val falseAcceptances: Int,
    val falseRejections: Int,
    val identityConfusions: Int,
    val equalErrorRate: EqualErrorRate?,
) {
    /** Raw Top-1 ranking accuracy for registered-speaker trials, before threshold rejection. */
    val top1Accuracy: Double?
        get() = top1Correct.rateOf(registeredTrials)

    /** Unknown speakers accepted as any registered identity, divided by all unknown trials. */
    val falseAcceptanceRate: Double?
        get() = falseAcceptances.rateOf(unknownTrials)

    val falseRejectionRate: Double?
        get() = falseRejections.rateOf(registeredTrials)

    /** Explicit alias used by open-set benchmark reports. */
    val unknownFalseAcceptanceRate: Double?
        get() = falseAcceptanceRate

    val unknownDetectionRate: Double?
        get() = correctlyRejectedUnknown.rateOf(unknownTrials)

    /** Exact final-decision accuracy, including correct Unknown decisions. */
    val finalDecisionAccuracy: Double?
        get() = (correctlyIdentified + correctlyRejectedUnknown).rateOf(totalTrials)
}

object SpeakerEvaluator {
    val defaultDurationSeconds: Set<Int> = linkedSetOf(2, 3, 5)

    /** Evaluates open-set identification decisions and derives verification scores for EER. */
    fun evaluate(trials: List<SpeakerEvaluationTrial>): SpeakerEvaluationMetrics {
        var registeredTrials = 0
        var unknownTrials = 0
        var top1Correct = 0
        var correctlyIdentified = 0
        var correctlyRejectedUnknown = 0
        var falseAcceptances = 0
        var falseRejections = 0
        var identityConfusions = 0

        trials.forEach { trial ->
            val expected = trial.expectedSpeakerId
            val identified = trial.scoringResult.identifiedSpeakerId
            if (expected == null) {
                unknownTrials++
                if (identified == null) correctlyRejectedUnknown++ else falseAcceptances++
            } else {
                registeredTrials++
                if (trial.scoringResult.top1?.speakerId == expected) top1Correct++
                when {
                    identified == null -> falseRejections++
                    identified == expected -> correctlyIdentified++
                    else -> identityConfusions++
                }
            }
        }

        return SpeakerEvaluationMetrics(
            totalTrials = trials.size,
            registeredTrials = registeredTrials,
            unknownTrials = unknownTrials,
            top1Correct = top1Correct,
            correctlyIdentified = correctlyIdentified,
            correctlyRejectedUnknown = correctlyRejectedUnknown,
            falseAcceptances = falseAcceptances,
            falseRejections = falseRejections,
            identityConfusions = identityConfusions,
            equalErrorRate = equalErrorRate(toVerificationTrials(trials)),
        )
    }

    /** Returns a metric row for each requested duration, including empty rows. */
    fun evaluateByDuration(
        trials: List<SpeakerEvaluationTrial>,
        durationsSeconds: Set<Int> = defaultDurationSeconds,
    ): Map<Int, SpeakerEvaluationMetrics> {
        require(durationsSeconds.isNotEmpty()) { "durationsSeconds must not be empty" }
        require(durationsSeconds.all { it > 0 }) { "durationsSeconds must contain only positive values" }
        return durationsSeconds
            .sorted()
            .associateWith { duration -> evaluate(trials.filter { it.durationSeconds == duration }) }
    }

    /**
     * Converts every centroid score to a verification trial. The expected speaker's score is
     * genuine; all other registered-speaker scores (including all scores for Unknown) are impostor.
     */
    fun toVerificationTrials(trials: List<SpeakerEvaluationTrial>): List<VerificationTrial> =
        trials.flatMap { trial ->
            trial.scoringResult.scores.map { score ->
                VerificationTrial(
                    score = score.score,
                    isGenuine = trial.expectedSpeakerId != null &&
                        score.speakerId == trial.expectedSpeakerId,
                )
            }
        }

    /** Calculates FAR and FRR with the same inclusive threshold boundary as [SpeakerScorer]. */
    fun verificationMetrics(
        trials: List<VerificationTrial>,
        threshold: Float,
    ): VerificationMetrics {
        require(threshold in -1f..1f) { "threshold must be in [-1, 1]" }
        val genuine = trials.filter(VerificationTrial::isGenuine)
        val impostor = trials.filterNot(VerificationTrial::isGenuine)
        return VerificationMetrics(
            threshold = threshold,
            genuineTrials = genuine.size,
            impostorTrials = impostor.size,
            falseAcceptances = impostor.count { meetsSpeakerThreshold(it.score, threshold) },
            falseRejections = genuine.count { !meetsSpeakerThreshold(it.score, threshold) },
        )
    }

    /** Returns null unless both genuine and impostor trials are available. */
    fun equalErrorRate(trials: List<VerificationTrial>): EqualErrorRate? {
        if (trials.none(VerificationTrial::isGenuine) || trials.all(VerificationTrial::isGenuine)) {
            return null
        }

        val thresholds = trials
            .map(VerificationTrial::score)
            .distinct()
            .sortedDescending()
        var previous = OperatingPoint(
            threshold = Math.nextUp(thresholds.first()),
            falseAcceptanceRate = 0.0,
            falseRejectionRate = 1.0,
        )

        thresholds.forEach { threshold ->
            val metrics = verificationMetrics(trials, threshold)
            val current = OperatingPoint(
                threshold = threshold,
                falseAcceptanceRate = checkNotNull(metrics.falseAcceptanceRate),
                falseRejectionRate = checkNotNull(metrics.falseRejectionRate),
            )
            val previousDifference = previous.falseAcceptanceRate - previous.falseRejectionRate
            val currentDifference = current.falseAcceptanceRate - current.falseRejectionRate

            if (previousDifference == 0.0) return previous.toEqualErrorRate()
            if (currentDifference == 0.0) return current.toEqualErrorRate()
            if (previousDifference < 0.0 && currentDifference > 0.0) {
                val interpolation =
                    -previousDifference / (currentDifference - previousDifference)
                val rate = previous.falseAcceptanceRate +
                    interpolation * (current.falseAcceptanceRate - previous.falseAcceptanceRate)
                val thresholdAtCrossing = previous.threshold.toDouble() +
                    interpolation * (current.threshold.toDouble() - previous.threshold.toDouble())
                return EqualErrorRate(rate = rate, threshold = thresholdAtCrossing.toFloat())
            }
            previous = current
        }

        return previous.toEqualErrorRate()
    }

    private data class OperatingPoint(
        val threshold: Float,
        val falseAcceptanceRate: Double,
        val falseRejectionRate: Double,
    ) {
        fun toEqualErrorRate() = EqualErrorRate(
            rate = (falseAcceptanceRate + falseRejectionRate) / 2.0,
            threshold = threshold,
        )
    }
}

private fun Int.rateOf(denominator: Int): Double? =
    if (denominator == 0) null else toDouble() / denominator.toDouble()
