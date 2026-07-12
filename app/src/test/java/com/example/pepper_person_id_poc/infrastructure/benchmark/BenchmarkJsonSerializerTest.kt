package com.example.pepper_person_id_poc.infrastructure.benchmark

import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BenchmarkJsonSerializerTest {
    @Test
    fun serialize_ordersAttributesAndEscapesText() {
        val json = BenchmarkJsonSerializer.serialize(
            BenchmarkEvent(
                event = "face\"test",
                timestampMillis = 123L,
                durationMillis = 45L,
                status = "SUCCESS",
                attributes = mapOf("z" to "line\n2", "a" to "value"),
            ),
        )

        assertEquals(
            "{\"event\":\"face\\\"test\",\"timestampMillis\":123,\"durationMillis\":45," +
                "\"status\":\"SUCCESS\",\"attributes\":{\"a\":\"value\",\"z\":\"line\\n2\"}}",
            json,
        )
    }

    @Test
    fun serialize_doesNotContainBiometricPayloadFields() {
        val json = BenchmarkJsonSerializer.serialize(
            BenchmarkEvent(event = "test", timestampMillis = 1L, status = "SUCCESS"),
        )

        assertFalse(json.contains("pcm", ignoreCase = true))
        assertFalse(json.contains("embedding", ignoreCase = true))
        assertFalse(json.contains("image", ignoreCase = true))
    }
}
