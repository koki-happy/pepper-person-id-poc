package com.example.pepper_person_id_poc.infrastructure.metrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BenchmarkLogGateTest {
    @Test
    fun benchmark_enablesCompleteFileLoggingByDefault() {
        val gate = BenchmarkLogGate(BenchmarkDistribution.BENCHMARK)

        assertThat(gate.isLogcatEnabled()).isTrue()
    }

    @Test
    fun candidate_requiresExplicitExportAndCanBeDisabledAgain() {
        val gate = BenchmarkLogGate(BenchmarkDistribution.CANDIDATE)

        assertThat(gate.isLogcatEnabled()).isFalse()
        gate.enableExplicitCandidateExport()
        assertThat(gate.isLogcatEnabled()).isTrue()
        gate.disableExplicitCandidateExport()
        assertThat(gate.isLogcatEnabled()).isFalse()
    }

    @Test(expected = IllegalStateException::class)
    fun benchmark_rejectsCandidateExportOverride() {
        BenchmarkLogGate(BenchmarkDistribution.BENCHMARK).enableExplicitCandidateExport()
    }

    @Test
    fun packageSuffix_selectsCandidateWhileCurrentAppDefaultsToBenchmark() {
        assertThat(benchmarkDistributionForPackage("com.example.app.candidate"))
            .isEqualTo(BenchmarkDistribution.CANDIDATE)
        assertThat(benchmarkDistributionForPackage("com.example.pepper_person_id_poc"))
            .isEqualTo(BenchmarkDistribution.BENCHMARK)
    }
}
