package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.io.File

interface BenchmarkLogger {
    fun append(event: BenchmarkEvent)
    fun outputFile(): File
    fun readRecent(limit: Int): List<String>
    fun deleteAll()
}
