package com.example.pepper_person_id_poc.domain.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceLoadSnapshotTest {
    @Test
    fun snapshot_keepsBothClockDomainsAndDetailedProcessLoad() {
        val snapshot = DeviceLoadSnapshot(
            measuringCpu = false,
            appCpuAllCoresPercent = 12.5,
            appPssBytes = 3_000L,
            appJavaHeapBytes = 1_000L,
            appNativeHeapBytes = 2_000L,
            appThreadCount = 9,
            collectedAtElapsedRealtimeMillis = 123L,
            collectedAtEpochMillis = 456L,
            collectedAtMillis = 456L,
        )

        assertThat(snapshot.appJavaHeapBytes).isEqualTo(1_000L)
        assertThat(snapshot.appNativeHeapBytes).isEqualTo(2_000L)
        assertThat(snapshot.appThreadCount).isEqualTo(9)
        assertThat(snapshot.collectedAtElapsedRealtimeMillis).isEqualTo(123L)
        assertThat(snapshot.collectedAtEpochMillis).isEqualTo(456L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun snapshot_rejectsNegativeThreadCount() {
        DeviceLoadSnapshot(appThreadCount = -1)
    }
}
