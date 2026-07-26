package com.example.pepper_person_id_poc.domain.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetricSeriesTest {
    @Test
    fun append_boundsSamplesByDurationAndCapacity() {
        val series = MetricSeries(windowMillis = 60_000L, maximumSamples = 3)

        series.append(0L, 1.0)
        series.append(10_000L, 2.0)
        series.append(20_000L, 3.0)
        series.append(70_000L, 4.0)

        assertThat(series.samples()).containsExactly(
            MetricSample(10_000L, 2.0),
            MetricSample(20_000L, 3.0),
            MetricSample(70_000L, 4.0),
        ).inOrder()
    }

    @Test
    fun append_preservesMissingGapButStatisticsIgnoreIt() {
        val series = MetricSeries(windowMillis = 300_000L)
        series.append(1_000L, 1.0)
        series.append(2_000L, null)
        series.append(3_000L, 3.0)

        assertThat(series.samples()[1]).isEqualTo(MetricSample(2_000L, null))
        assertThat(series.statistics()).isEqualTo(
            MetricStatistics(
                measuredCount = 2,
                missingCount = 1,
                p50 = 1.0,
                p95 = 3.0,
                maximum = 3.0,
            ),
        )
    }

    @Test
    fun statistics_usesNearestRankForP50P95AndMaximum() {
        val series = MetricSeries(windowMillis = 300_000L)
        (1..20).forEach { value -> series.append(value.toLong(), value.toDouble()) }

        assertThat(series.statistics()).isEqualTo(
            MetricStatistics(
                measuredCount = 20,
                missingCount = 0,
                p50 = 10.0,
                p95 = 19.0,
                maximum = 20.0,
            ),
        )
    }
}
