package com.example.pepper_person_id_poc.infrastructure.benchmark

import android.content.Context
import android.util.Log
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.io.File

class LogcatBenchmarkLogger(
    context: Context,
    private val logcatEnabled: () -> Boolean = { true },
) : BenchmarkLogger {
    init {
        File(context.applicationContext.filesDir, "benchmark/events.jsonl").delete()
    }

    override fun append(event: BenchmarkEvent) {
        if (!logcatEnabled()) return
        Log.i(TAG, BenchmarkJsonSerializer.serialize(event))
    }

    private companion object {
        const val TAG = "PepperIdentityMetrics"
    }
}
