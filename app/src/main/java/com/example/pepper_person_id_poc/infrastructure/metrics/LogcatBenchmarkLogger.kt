package com.example.pepper_person_id_poc.infrastructure.metrics

import android.content.Context
import android.util.Log
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkEventJson
import java.io.File

enum class BenchmarkDistribution {
    BENCHMARK,
    CANDIDATE,
}

fun benchmarkDistributionForPackage(packageName: String): BenchmarkDistribution =
    if (packageName.split('.').any { it.equals("candidate", ignoreCase = true) }) {
        BenchmarkDistribution.CANDIDATE
    } else {
        BenchmarkDistribution.BENCHMARK
    }

class BenchmarkLogGate(
    val distribution: BenchmarkDistribution,
) {
    @Volatile
    private var candidateExportEnabled = false

    fun isLogcatEnabled(): Boolean =
        distribution == BenchmarkDistribution.BENCHMARK || candidateExportEnabled

    fun enableExplicitCandidateExport() {
        check(distribution == BenchmarkDistribution.CANDIDATE) {
            "Explicit export is only required by the candidate distribution"
        }
        candidateExportEnabled = true
    }

    fun disableExplicitCandidateExport() {
        candidateExportEnabled = false
    }
}

class LogcatBenchmarkLogger(
    context: Context,
    val gate: BenchmarkLogGate,
) {
    init {
        File(context.applicationContext.filesDir, "benchmark/metrics-v1.jsonl").delete()
    }

    fun append(event: BenchmarkEvent): Boolean {
        if (!gate.isLogcatEnabled()) return false
        Log.i(TAG, BenchmarkEventJson.encode(event))
        return true
    }

    private companion object {
        const val TAG = "PepperIdentityBenchmark"
    }
}
