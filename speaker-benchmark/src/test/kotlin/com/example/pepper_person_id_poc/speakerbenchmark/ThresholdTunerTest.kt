package com.example.pepper_person_id_poc.speakerbenchmark

import com.example.pepper_person_id_poc.speakercore.SpeakerCentroid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThresholdTunerTest {
    private val centroids = listOf(
        SpeakerCentroid("a", floatArrayOf(1f, 0f)),
        SpeakerCentroid("b", floatArrayOf(0f, 1f)),
    )

    @Test
    fun selectsPerfectDevelopmentOperatingPointWithoutUsingTestData() {
        val selected = ThresholdTuner.select(
            samples = listOf(
                DevelopmentEmbedding("known-a", "a", floatArrayOf(1f, 0f)),
                DevelopmentEmbedding("known-b", "b", floatArrayOf(0f, 1f)),
                DevelopmentEmbedding("unknown", null, floatArrayOf(-1f, -1f)),
            ),
            centroids = centroids,
        )

        assertEquals(1.0, selected.metrics.finalDecisionAccuracy)
        assertEquals(0.0, selected.metrics.falseAcceptanceRate)
        assertEquals(0.0, selected.metrics.falseRejectionRate)
        assertEquals(1f, selected.threshold)
        assertEquals(0f, selected.minimumMargin)
    }

    @Test
    fun rejectsDevelopmentSetWithoutUnknownSpeaker() {
        assertFailsWith<IllegalArgumentException> {
            ThresholdTuner.select(
                samples = listOf(DevelopmentEmbedding("known", "a", floatArrayOf(1f, 0f))),
                centroids = centroids,
            )
        }
    }
}
