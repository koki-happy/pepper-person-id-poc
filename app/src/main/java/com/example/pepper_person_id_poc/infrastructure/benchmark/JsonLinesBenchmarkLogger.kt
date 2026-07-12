package com.example.pepper_person_id_poc.infrastructure.benchmark

import android.content.Context
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.io.File

class JsonLinesBenchmarkLogger(
    context: Context,
) : BenchmarkLogger {
    private val file = File(context.applicationContext.filesDir, "benchmark/events.jsonl")
    private val lock = Any()

    override fun append(event: BenchmarkEvent) {
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.appendText(BenchmarkJsonSerializer.serialize(event) + "\n")
        }
    }

    override fun outputFile(): File = file

    override fun readRecent(limit: Int): List<String> {
        require(limit > 0)
        return synchronized(lock) {
            if (!file.exists()) emptyList() else file.readLines().takeLast(limit)
        }
    }
}
