package com.example.pepper_person_id_poc.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceLoadMonitor
import java.util.Locale

@Composable
fun DeviceLoadPanel() {
    val context = LocalContext.current
    val monitor = remember { AndroidDeviceLoadMonitor(context) }
    val state by monitor.state.collectAsState()
    DisposableEffect(monitor) {
        monitor.start()
        onDispose { monitor.close() }
    }
    Text(
        "アプリCPU（全コア比）: " +
            if (state.measuringCpu) "計測中" else state.appCpuAllCoresPercent?.let {
                String.format(Locale.US, "%.1f%%", it)
            }.orUnmeasured(),
    )
    Text("アプリメモリ（PSS）: ${state.appPssBytes.asMegabytes()}")
    Text("端末空きメモリ: ${state.availableMemoryBytes.asMegabytes()} / ${state.totalMemoryBytes.asMegabytes()}")
    Text("低メモリ状態: ${state.lowMemory?.let { if (it) "はい" else "いいえ" } ?: "未計測"}")
}

private fun String?.orUnmeasured() = this ?: "未計測"
private fun Long?.asMegabytes(): String =
    this?.let { String.format(Locale.US, "%.1f MB", it / 1024.0 / 1024.0) } ?: "未計測"
