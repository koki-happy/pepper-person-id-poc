package com.example.pepper_person_id_poc.domain.device

data class DeviceLoadSnapshot(
    val measuringCpu: Boolean = true,
    val appCpuAllCoresPercent: Double? = null,
    val appPssBytes: Long? = null,
    val totalMemoryBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
    val lowMemory: Boolean? = null,
    val collectedAtMillis: Long? = null,
)
