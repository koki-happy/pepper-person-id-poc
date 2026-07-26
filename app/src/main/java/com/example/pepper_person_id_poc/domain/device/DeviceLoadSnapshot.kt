package com.example.pepper_person_id_poc.domain.device

data class DeviceLoadSnapshot(
    val measuringCpu: Boolean = true,
    val appCpuAllCoresPercent: Double? = null,
    val appPssBytes: Long? = null,
    val appJavaHeapBytes: Long? = null,
    val appNativeHeapBytes: Long? = null,
    val appThreadCount: Int? = null,
    val totalMemoryBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
    val lowMemory: Boolean? = null,
    val collectedAtElapsedRealtimeMillis: Long? = null,
    val collectedAtEpochMillis: Long? = null,
    val collectedAtMillis: Long? = null,
) {
    init {
        require(appCpuAllCoresPercent == null || appCpuAllCoresPercent >= 0.0)
        listOf(
            appPssBytes,
            appJavaHeapBytes,
            appNativeHeapBytes,
            totalMemoryBytes,
            availableMemoryBytes,
            collectedAtElapsedRealtimeMillis,
            collectedAtEpochMillis,
            collectedAtMillis,
        ).forEach { require(it == null || it >= 0L) }
        require(appThreadCount == null || appThreadCount >= 0)
    }
}
