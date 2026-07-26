package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.domain.metrics.FacePipelineMetrics
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FacePipelineMetricsTest {
    @Test
    fun measuredStageTotal_excludesUnmeasuredStages() {
        val metrics = FacePipelineMetrics(
            captureMillis = 2.0,
            preprocessingMillis = 3.0,
            detectionMillis = 5.0,
            qualityMillis = null,
            alignmentMillis = 7.0,
            embeddingMillis = 11.0,
            identificationMillis = 13.0,
            repositoryMillis = 17.0,
        )

        assertThat(metrics.measuredStageTotalMillis()).isEqualTo(58.0)
    }

    @Test
    fun explicitTotal_isKeptSeparateFromSumOfMeasuredStages() {
        val metrics = FacePipelineMetrics(
            detectionMillis = 10.0,
            embeddingMillis = 20.0,
            totalMillis = 35.0,
        )

        assertThat(metrics.totalMillis).isEqualTo(35.0)
        assertThat(metrics.measuredStageTotalMillis()).isEqualTo(30.0)
    }

    @Test
    fun detailedCoordinatorStages_replaceLegacyIdentificationAggregate() {
        val metrics = FacePipelineMetrics(
            embeddingMillis = 5.0,
            scoringMillis = 2.0,
            policyMillis = 1.0,
            identificationMillis = 99.0,
            repositoryMillis = 3.0,
            uiMillis = 4.0,
        )

        assertThat(metrics.measuredStageTotalMillis()).isEqualTo(15.0)
    }
}
