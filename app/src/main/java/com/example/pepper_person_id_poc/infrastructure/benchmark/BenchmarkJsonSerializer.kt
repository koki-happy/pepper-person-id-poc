package com.example.pepper_person_id_poc.infrastructure.benchmark

import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent

object BenchmarkJsonSerializer {
    fun serialize(event: BenchmarkEvent): String = buildString {
        append('{')
        field("event", event.event)
        append(',')
        field("timestampMillis", event.timestampMillis)
        event.durationMillis?.let {
            append(',')
            field("durationMillis", it)
        }
        append(',')
        field("status", event.status)
        append(",\"attributes\":{")
        event.attributes.toSortedMap().entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            field(key, value)
        }
        append('}')
        event.error?.let {
            append(',')
            field("error", it)
        }
        append('}')
    }

    private fun StringBuilder.field(name: String, value: String) {
        append('"').append(escape(name)).append("\":\"").append(escape(value)).append('"')
    }

    private fun StringBuilder.field(name: String, value: Long) {
        append('"').append(escape(name)).append("\":").append(value)
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
    }
}
