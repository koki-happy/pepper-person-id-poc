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
import androidx.compose.runtime.mutableStateMapOf
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
    val unit: String = "ms",
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
    rateColumnLabel: String? = null,
    rateValue: Float? = null,
    rateAfterDeviceLoad: Boolean = false,
    headerGroups: List<DataTableHeaderGroup> = emptyList(),
) {
    val context = LocalContext.current
    val monitor = remember { AndroidDeviceLoadMonitor(context) }
    val state by monitor.state.collectAsState()
    val histories = remember { mutableStateMapOf<String, List<Double>>() }
    val rateColumn = rateLabel?.let {
        PerformanceColumn(
            id = "rate-$it",
            label = rateColumnLabel ?: it,
            current = rateValue?.toDouble(),
        )
    }
    val columns = buildList {
        add(PerformanceColumn("cpu", "CPU（%）", state.appCpuAllCoresPercent?.toDouble()))
        add(PerformanceColumn("memory", "メモリ（MB）", state.appPssBytes?.div(1024.0 * 1024.0)))
        if (rateAfterDeviceLoad) rateColumn?.let(::add)
        stageMetrics.forEach { add(PerformanceColumn(it.id, it.label, it.currentMillis)) }
        if (!rateAfterDeviceLoad) rateColumn?.let(::add)
    }
    LaunchedEffect(monitor) { monitor.start() }
    LaunchedEffect(columns.map { it.id to it.current }) {
        columns.forEach { column ->
            column.current?.takeIf { it.isFinite() }?.let { value ->
                histories[column.id] = (histories[column.id].orEmpty() + value).takeLast(300)
            }
        }
    }
    DisposableEffect(monitor) {
        onDispose { monitor.close() }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device-load-panel"),
    ) {
        Text("パフォーマンス", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        val headers = buildList {
            add("時点")
            columns.forEach { add(it.label) }
        }
        val rows = listOf(
            performanceRow("現在", columns) { column -> column.current },
            performanceRow("平均", columns) { column -> histories[column.id]?.average() },
            performanceRow("最大", columns) { column -> histories[column.id]?.maxOrNull() },
        )
        ScrollableDataTable(
            headers = headers,
            rows = rows,
            headerGroups = headerGroups,
            columnWidth = 76.dp,
            columnWidths = listOf(76.dp) + columns.map { column ->
                if (column.id.startsWith("rate-")) 116.dp else 76.dp
            },
        )
    }
}

private data class PerformanceColumn(
    val id: String,
    val label: String,
    val current: Double?,
)

private fun performanceRow(
    label: String,
    columns: List<PerformanceColumn>,
    value: (PerformanceColumn) -> Double?,
): List<String> = buildList {
    add(label)
    columns.forEach { column ->
        add(value(column)?.let { String.format(Locale.US, "%.1f", it) } ?: "未測定")
    }
}
