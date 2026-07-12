package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingScreen(
    benchmarkLogger: BenchmarkLogger,
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember(benchmarkLogger) {
        AndroidPcmAudioRecorder(onBenchmarkEvent = benchmarkLogger::append)
    }
    val state by recorder.state.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) recorder.start()
    }

    DisposableEffect(recorder, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) recorder.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.close()
        }
    }

    fun startWithPermission() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            recorder.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PCM録音・発話区間") },
                navigationIcon = {
                    Button(onClick = onBackToSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("← 設定")
                    }
                },
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
            Text("録音状態: ${state.status}", style = MaterialTheme.typography.headlineSmall)
            Text("形式: ${state.sampleRate?.let { "$it Hz" } ?: "未初期化"} / mono / PCM 16-bit")
            Text("最小バッファ: ${state.minBufferSizeBytes?.let { "$it bytes" } ?: "-"}")

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text("音声レベル: %.1f dBFS".format(Locale.US, state.levelDbFs))
                    LinearProgressIndicator(
                        progress = { ((state.levelDbFs + 90f) / 90f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("発話区間: ${if (state.speechActive) "検出中" else "未検出"}")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = ::startWithPermission,
                    enabled = state.status != AudioRecordingStatus.STARTING &&
                        state.status != AudioRecordingStatus.RECORDING,
                ) {
                    Text("録音開始")
                }
                Button(
                    onClick = recorder::stop,
                    enabled = state.status == AudioRecordingStatus.STARTING ||
                        state.status == AudioRecordingStatus.RECORDING,
                ) {
                    Text("録音停止")
                }
            }

            state.error?.let { Text("録音エラー: $it", color = MaterialTheme.colorScheme.error) }

            state.lastUtterance?.let { utterance ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text("直近の発話区間", style = MaterialTheme.typography.titleMedium)
                        Text("${formatTime(utterance.startedAtMillis)} - ${formatTime(utterance.endedAtMillis)}")
                        Text("区間長: ${utterance.durationMillis} ms")
                        Text("有声時間: ${utterance.voicedDurationMillis} ms")
                        Text(
                            if (utterance.sufficientForSpeakerIdentification) {
                                "話者識別入力: 十分"
                            } else {
                                "話者識別状態: INSUFFICIENT_AUDIO"
                            },
                        )
                    }
                }
            }

            Text(
                "現在の発話検出はPCM経路確認用の暫定エネルギーVADです。次の機能単位でSilero VADへ差し替えます。",
                color = MaterialTheme.colorScheme.primary,
            )
            Text("PCM・WAV本体は保存せず、処理時間とメタデータだけを測定ログへ記録します。")
        }
    }
}

private fun formatTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date(timestampMillis))
