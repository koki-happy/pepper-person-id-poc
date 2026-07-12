package com.example.pepper_person_id_poc.domain.benchmark

class RateMeter(
    private val minimumWindowMillis: Long = 500L,
) {
    private var windowStartedAtMillis: Long? = null
    private var eventCount = 0L
    private var latestRate = 0f

    fun record(nowMillis: Long): Float {
        val startedAt = windowStartedAtMillis
        if (startedAt == null || nowMillis < startedAt) {
            windowStartedAtMillis = nowMillis
            eventCount = 1L
            latestRate = 0f
            return latestRate
        }
        eventCount += 1L
        val elapsed = nowMillis - startedAt
        if (elapsed >= minimumWindowMillis) {
            latestRate = ((eventCount - 1L) * 1_000.0 / elapsed).toFloat()
            windowStartedAtMillis = nowMillis
            eventCount = 1L
        }
        return latestRate
    }

    fun reset() {
        windowStartedAtMillis = null
        eventCount = 0L
        latestRate = 0f
    }
}
