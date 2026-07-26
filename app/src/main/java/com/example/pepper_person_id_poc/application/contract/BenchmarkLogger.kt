package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
interface BenchmarkLogger {
    fun append(event: BenchmarkEvent)
}
