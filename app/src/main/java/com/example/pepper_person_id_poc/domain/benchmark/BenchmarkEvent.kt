package com.example.pepper_person_id_poc.domain.benchmark

data class BenchmarkEvent(
    val event: String,
    val timestampMillis: Long,
    val durationMillis: Long? = null,
    val status: String,
    val attributes: Map<String, String> = emptyMap(),
    val error: String? = null,
)
