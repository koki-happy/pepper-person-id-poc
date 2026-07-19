package com.example.pepper_person_id_poc.speakerbenchmark.measurement

import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory
import java.time.Instant
import kotlin.math.ceil

@Serializable
data class MemorySnapshot(
    val heapUsedBytes: Long,
    val heapCommittedBytes: Long,
    val heapMaxBytes: Long,
    val processCommittedVirtualMemoryBytes: Long? = null,
)

@Serializable
data class EnvironmentSnapshot(
    val capturedAtUtc: String,
    val operatingSystemName: String,
    val operatingSystemVersion: String,
    val operatingSystemArchitecture: String,
    val availableProcessors: Int,
    val javaVersion: String,
    val javaVendor: String,
    val javaVmName: String,
    val kotlinVersion: String,
    val processId: Long,
    val processCpuLoad: Double? = null,
    val memory: MemorySnapshot,
)

@Serializable
data class TimingSummary(
    val sampleCount: Int,
    val minimumMilliseconds: Double,
    val maximumMilliseconds: Double,
    val meanMilliseconds: Double,
    val p50Milliseconds: Double,
    val p95Milliseconds: Double,
)

data class TimedResult<T>(
    val value: T,
    val elapsedNanoseconds: Long,
    val memoryBefore: MemorySnapshot,
    val memoryAfter: MemorySnapshot,
) {
    val elapsedMilliseconds: Double = elapsedNanoseconds / NANOSECONDS_PER_MILLISECOND
    val heapDeltaBytes: Long = memoryAfter.heapUsedBytes - memoryBefore.heapUsedBytes

    private companion object {
        const val NANOSECONDS_PER_MILLISECOND = 1_000_000.0
    }
}

object MeasurementCollector {
    fun captureEnvironment(): EnvironmentSnapshot {
        val operatingSystem = ManagementFactory.getOperatingSystemMXBean()
        val extendedOperatingSystem = operatingSystem as? com.sun.management.OperatingSystemMXBean
        return EnvironmentSnapshot(
            capturedAtUtc = Instant.now().toString(),
            operatingSystemName = operatingSystem.name,
            operatingSystemVersion = operatingSystem.version,
            operatingSystemArchitecture = operatingSystem.arch,
            availableProcessors = operatingSystem.availableProcessors,
            javaVersion = System.getProperty("java.version", "unknown"),
            javaVendor = System.getProperty("java.vendor", "unknown"),
            javaVmName = System.getProperty("java.vm.name", "unknown"),
            kotlinVersion = KotlinVersion.CURRENT.toString(),
            processId = ProcessHandle.current().pid(),
            processCpuLoad = extendedOperatingSystem
                ?.processCpuLoad
                ?.takeIf { it.isFinite() && it >= 0.0 },
            memory = captureMemory(),
        )
    }

    fun captureMemory(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val extendedOperatingSystem = ManagementFactory.getOperatingSystemMXBean()
            as? com.sun.management.OperatingSystemMXBean
        return MemorySnapshot(
            heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            heapCommittedBytes = runtime.totalMemory(),
            heapMaxBytes = runtime.maxMemory(),
            processCommittedVirtualMemoryBytes = extendedOperatingSystem
                ?.committedVirtualMemorySize
                ?.takeIf { it >= 0L },
        )
    }

    fun <T> measure(block: () -> T): TimedResult<T> {
        val memoryBefore = captureMemory()
        val start = System.nanoTime()
        val value = block()
        val elapsed = System.nanoTime() - start
        val memoryAfter = captureMemory()
        return TimedResult(value, elapsed, memoryBefore, memoryAfter)
    }

    fun summarizeNanoseconds(samples: Collection<Long>): TimingSummary {
        require(samples.isNotEmpty()) { "At least one timing sample is required" }
        require(samples.all { it >= 0L }) { "Timing samples must not be negative" }
        val sorted = samples.sorted()
        return TimingSummary(
            sampleCount = sorted.size,
            minimumMilliseconds = sorted.first().toMilliseconds(),
            maximumMilliseconds = sorted.last().toMilliseconds(),
            meanMilliseconds = sorted.average() / NANOSECONDS_PER_MILLISECOND,
            p50Milliseconds = nearestRank(sorted, 0.50).toMilliseconds(),
            p95Milliseconds = nearestRank(sorted, 0.95).toMilliseconds(),
        )
    }

    private fun nearestRank(sorted: List<Long>, percentile: Double): Long {
        val rank = ceil(percentile * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun Long.toMilliseconds(): Double = this / NANOSECONDS_PER_MILLISECOND

    private const val NANOSECONDS_PER_MILLISECOND = 1_000_000.0
}
