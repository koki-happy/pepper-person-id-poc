package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.domain.metrics.SpeakerPipelineMetrics
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeakerPipelineMetricsTest {
    @Test
    fun realTimeFactor_usesProcessingTimeOverAudioDuration() {
        val metrics = SpeakerPipelineMetrics(
            vadMillis = 20.0,
            segmentationMillis = 30.0,
            embeddingMillis = 50.0,
            processingMillis = 250.0,
            audioDurationMillis = 1_000.0,
        )

        assertThat(metrics.realTimeFactor()).isWithin(0.0001).of(0.25)
    }

    @Test
    fun realTimeFactor_isNullWhenDurationOrProcessingIsUnmeasured() {
        assertThat(
            SpeakerPipelineMetrics(
                processingMillis = null,
                audioDurationMillis = 1_000.0,
            ).realTimeFactor(),
        ).isNull()
        assertThat(
            SpeakerPipelineMetrics(
                processingMillis = 100.0,
                audioDurationMillis = null,
            ).realTimeFactor(),
        ).isNull()
    }
}
