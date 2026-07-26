package com.example.pepper_person_id_poc.domain.metrics

import java.util.ArrayDeque
import kotlin.math.ceil

data class MetricSample(
    val timestampElapsedRealtimeMillis: Long,
    val value: Double?,
) {
    init {
        require(timestampElapsedRealtimeMillis >= 0L)
        require(value == null || value.isFinite())
    }
}

data class MetricStatistics(
    val measuredCount: Int,
    val missingCount: Int,
    val p50: Double?,
    val p95: Double?,
    val maximum: Double?,
)

class MetricSeries(
    private val windowMillis: Long,
    private val maximumSamples: Int = 1_024,
) {
    private val values = ArrayDeque<MetricSample>()

    init {
        require(windowMillis > 0L)
        require(maximumSamples > 0)
    }

    @Synchronized
    fun append(timestampElapsedRealtimeMillis: Long, value: Double?) {
        val latest = values.peekLast()
        require(latest == null || timestampElapsedRealtimeMillis >= latest.timestampElapsedRealtimeMillis) {
            "Metric samples must use nondecreasing monotonic timestamps"
        }
        values.addLast(MetricSample(timestampElapsedRealtimeMillis, value))
        val cutoff = timestampElapsedRealtimeMillis - windowMillis
        while (values.isNotEmpty() && values.peekFirst().timestampElapsedRealtimeMillis < cutoff) {
            values.removeFirst()
        }
        while (values.size > maximumSamples) {
            values.removeFirst()
        }
    }

    @Synchronized
    fun samples(): List<MetricSample> = values.toList()

    @Synchronized
    fun statistics(): MetricStatistics {
        val measured = values.mapNotNull(MetricSample::value).sorted()
        val missing = values.size - measured.size
        return MetricStatistics(
            measuredCount = measured.size,
            missingCount = missing,
            p50 = measured.nearestRank(0.50),
            p95 = measured.nearestRank(0.95),
            maximum = measured.lastOrNull(),
        )
    }

    @Synchronized
    fun clear() {
        values.clear()
    }
}

private fun List<Double>.nearestRank(percentile: Double): Double? {
    if (isEmpty()) return null
    val rank = ceil(percentile * size).toInt().coerceIn(1, size)
    return this[rank - 1]
}
