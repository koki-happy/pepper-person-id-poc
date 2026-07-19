package com.example.pepper_person_id_poc.speakerbenchmark.measurement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementsTest {
    @Test
    fun `summarizes timings using nearest-rank percentiles`() {
        val summary = MeasurementCollector.summarizeNanoseconds(
            listOf(1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L),
        )

        assertEquals(1.0, summary.minimumMilliseconds)
        assertEquals(4.0, summary.maximumMilliseconds)
        assertEquals(2.5, summary.meanMilliseconds)
        assertEquals(2.0, summary.p50Milliseconds)
        assertEquals(4.0, summary.p95Milliseconds)
    }

    @Test
    fun `captures environment and before-after measurement`() {
        val environment = MeasurementCollector.captureEnvironment()
        val timed = MeasurementCollector.measure { "value" }

        assertTrue(environment.operatingSystemName.isNotBlank())
        assertTrue(environment.availableProcessors > 0)
        assertTrue(environment.memory.heapMaxBytes > 0)
        assertEquals("value", timed.value)
        assertTrue(timed.elapsedNanoseconds >= 0)
    }
}
