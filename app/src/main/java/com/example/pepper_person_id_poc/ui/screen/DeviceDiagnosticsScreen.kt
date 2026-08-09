package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.device.DeviceDiagnostics
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val DiagnosticsCanvas = Color(0xFFF4F6F8)
private val DiagnosticsSurface = Color.White
private val DiagnosticsLine = Color(0xFFCBD3DA)
private val DiagnosticsInk = Color(0xFF1D2329)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDiagnosticsScreen(
    diagnostics: DeviceDiagnostics?,
    isLoading: Boolean,
    error: String?,
    onBackToSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit,
) {
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        Scaffold(
        containerColor = DiagnosticsCanvas,
        topBar = {
            TopAppBar(
                title = { Text("端末診断", color = DiagnosticsInk) },
                actions = {
                    TextButton(onClick = onBackToSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("← 設定", color = DiagnosticsInk)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DiagnosticsSurface),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, DiagnosticsLine, RoundedCornerShape(12.dp)),
            )
        },
        ) { innerPadding ->
            Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRequestPermissions) { Text("権限を確認") }
                Button(onClick = onRefresh, enabled = !isLoading) { Text("再診断") }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            error?.let { Text("診断エラー: $it", color = MaterialTheme.colorScheme.error) }
            diagnostics?.let { DiagnosticContent(it) }
            }
        }
    }
}

@Composable
private fun DiagnosticContent(diagnostics: DeviceDiagnostics) {
    DiagnosticCard("端末") {
        DiagnosticRow("メーカー", diagnostics.manufacturer)
        DiagnosticRow("モデル / デバイス", "${diagnostics.model} / ${diagnostics.device}")
        DiagnosticRow("Android", "${diagnostics.androidVersion} (API ${diagnostics.apiLevel})")
        DiagnosticRow("ビルドID", diagnostics.buildId)
        DiagnosticRow("ABI", diagnostics.supportedAbis.joinToString())
        DiagnosticRow("利用可能CPU", "${diagnostics.availableProcessors} core")
    }
    DiagnosticCard("メモリ・画面") {
        DiagnosticRow("RAM", diagnostics.totalMemoryBytes.asMiB())
        DiagnosticRow("利用可能RAM", diagnostics.availableMemoryBytes.asMiB())
        DiagnosticRow("アプリ最大Heap", diagnostics.maxHeapBytes.asMiB())
        DiagnosticRow("Low memory", diagnostics.lowMemory?.toString() ?: "取得不可")
        DiagnosticRow(
            "画面",
            "${diagnostics.screenWidthPixels}x${diagnostics.screenHeightPixels} / ${diagnostics.densityDpi} dpi",
        )
    }
    DiagnosticCard("権限・接続") {
        DiagnosticRow("CAMERA", diagnostics.cameraPermissionGranted.asStatus())
        DiagnosticRow("RECORD_AUDIO", diagnostics.recordAudioPermissionGranted.asStatus())
        DiagnosticRow("ネットワーク", diagnostics.networkConnected.asStatus())
    }
    DiagnosticCard("前面カメラ") {
        if (diagnostics.frontCameras.isEmpty()) Text("前面カメラ件数: 0")
        diagnostics.frontCameras.forEach { camera ->
            DiagnosticRow("Camera ${camera.cameraId}", "Orientation ${camera.orientationDegrees ?: "不明"}°")
            Text(
                camera.previewSizes.joinToString().ifBlank { "プレビュー解像度: 未取得" },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    DiagnosticCard("PCM候補") {
        diagnostics.audioConfigurations.forEach { audio ->
            DiagnosticRow(
                "${audio.sampleRate} Hz / mono / PCM16",
                if (audio.supported) "候補 (${audio.minBufferSizeBytes} bytes)" else "非対応",
            )
        }
        Text(
            "診断API: AudioRecord.getMinBufferSize / 録音初期化テスト: PCM録音画面",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Text(
        "診断日時: ${DateFormat.getDateTimeInstance().format(Date(diagnostics.collectedAtMillis))}",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun DiagnosticCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DiagnosticsLine),
        colors = CardDefaults.cardColors(containerColor = DiagnosticsSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, modifier = Modifier.weight(2f))
    }
}

private fun Long?.asMiB(): String = this?.let {
    String.format(Locale.US, "%.1f MiB", it / 1024.0 / 1024.0)
} ?: "取得不可"

private fun Boolean.asStatus(): String = if (this) "利用可能" else "利用不可"
