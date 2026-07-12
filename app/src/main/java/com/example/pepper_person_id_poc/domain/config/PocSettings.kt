package com.example.pepper_person_id_poc.domain.config

data class PocSettings(
    val faceThreshold: Float = DEFAULT_FACE_THRESHOLD,
    val speakerThreshold: Float = DEFAULT_SPEAKER_THRESHOLD,
    val combinedThreshold: Float = DEFAULT_COMBINED_THRESHOLD,
    val observationWindowMillis: Long = DEFAULT_OBSERVATION_WINDOW_MILLIS,
    val debugMode: Boolean = true,
) {
    fun isValid(): Boolean =
        faceThreshold in SCORE_RANGE &&
            speakerThreshold in SCORE_RANGE &&
            combinedThreshold in SCORE_RANGE &&
            observationWindowMillis in OBSERVATION_WINDOW_RANGE

    companion object {
        const val DEFAULT_FACE_THRESHOLD = 0.60f
        const val DEFAULT_SPEAKER_THRESHOLD = 0.60f
        const val DEFAULT_COMBINED_THRESHOLD = 0.70f
        const val DEFAULT_OBSERVATION_WINDOW_MILLIS = 3_000L

        val SCORE_RANGE = 0f..1f
        val OBSERVATION_WINDOW_RANGE = 500L..10_000L
    }
}
