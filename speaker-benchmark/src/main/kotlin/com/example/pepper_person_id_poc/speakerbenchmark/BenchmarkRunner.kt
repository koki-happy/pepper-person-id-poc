package com.example.pepper_person_id_poc.speakerbenchmark

import com.example.pepper_person_id_poc.speakerbenchmark.report.BenchmarkResults
import com.example.pepper_person_id_poc.speakerbenchmark.validation.ValidatedBenchmark

/**
 * Inference implementations plug into the CLI through this boundary. The CLI owns validation and report writing.
 */
fun interface BenchmarkRunner {
    fun run(benchmark: ValidatedBenchmark): BenchmarkResults
}
