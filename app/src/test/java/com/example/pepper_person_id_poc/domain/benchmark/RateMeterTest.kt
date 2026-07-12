package com.example.pepper_person_id_poc.domain.benchmark

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RateMeterTest {
    @Test
    fun record_reportsEventsPerSecondAfterWindow() {
        val meter = RateMeter(minimumWindowMillis = 500L)

        meter.record(1_000L)
        repeat(9) { index -> meter.record(1_050L + index * 50L) }
        val rate = meter.record(1_500L)

        assertThat(rate).isWithin(0.01f).of(20f)
    }

    @Test
    fun reset_clearsPreviousRate() {
        val meter = RateMeter(minimumWindowMillis = 500L)
        meter.record(0L)
        meter.record(500L)

        meter.reset()

        assertThat(meter.record(1_000L)).isEqualTo(0f)
    }
}
