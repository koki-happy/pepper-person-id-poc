package com.example.pepper_person_id_poc.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.metrics.MetricSample
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceLoadMonitor
import java.util.Locale

data class StageMetricUiState(
    val id: String,
    val label: String,
    val currentMillis: Double?,
    val samples: List<MetricSample>,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(currentMillis == null || currentMillis >= 0.0 && currentMillis.isFinite())
    }
}

@Composable
fun DeviceLoadPanel(
    stageMetrics: List<StageMetricUiState> = emptyList(),
    rateLabel: String? = null,
    rateValue: Float? = null,
) {
    val context = LocalContext.current
    val monitor = remember { AndroidDeviceLoadMonitor(context) }
    val state by monitor.state.collectAsState()
    val totalMillis = stageMetrics
        .lastOrNull { it.label == "Total" || it.label == "Pipeline" }
        ?.currentMillis
        ?: stageMetrics.lastOrNull()?.currentMillis

    LaunchedEffect(monitor) { monitor.start() }
    DisposableEffect(monitor) {
        onDispose { monitor.close() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device-load-panel"),
    ) {
        Text("Pepper負荷")
        Text(
            "CPU: " + if (state.measuringCpu) {
                "計測中"
            } else {
                state.appCpuAllCoresPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "未測定"
            },
        )
        Text("メモリ: ${state.appPssBytes.asMegabytes()}")
        Text(
            "処理時間: " +
                (totalMillis?.let { String.format(Locale.US, "%.0f ms/回", it) } ?: "未測定"),
        )
        if (rateLabel != null) {
            Text(
                "$rateLabel: " +
                    (rateValue?.let { String.format(Locale.US, "%.2f", it) } ?: "未測定"),
            )
        }
    }
}

private fun Long?.asMegabytes(): String =
    this?.let { String.format(Locale.US, "%.1f MB", it / (1024.0 * 1024.0)) } ?: "未測定"
