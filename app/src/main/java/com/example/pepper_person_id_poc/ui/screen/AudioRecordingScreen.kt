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
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityCoordinator
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.ui.component.DeviceLoadPanel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingScreen(
    settings: PocSettings,
    repository: AnonymousSpeakerClusterRepository,
    benchmarkLogger: BenchmarkLogger,
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coordinator = remember(settings.speakerModel, settings.speakerClusterJoinThreshold, settings.speakerClusterMaxUpdateCount, repository) {
        SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = SherpaOnnxSpeakerEmbeddingEngine(context, settings.speakerModel),
            threshold = settings.speakerClusterJoinThreshold,
            maximumUpdateCount = settings.speakerClusterMaxUpdateCount,
            benchmarkLogger = benchmarkLogger,
        )
    }
    val recorder = remember(coordinator) {
        AndroidPcmAudioRecorder(context, coordinator::onUtterance, benchmarkLogger::append)
    }
    val recorderState by recorder.state.collectAsState()
    val identity by coordinator.state.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) recorder.start()
    }
    DisposableEffect(recorder, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) recorder.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recorder.close()
            coordinator.close()
        }
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            recorder.start()
        } else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("未登録話者識別") },
                navigationIcon = { Button(onClick = onBackToSettings) { Text("← 設定") } },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Text("録音状態: ${recorderState.status}", style = MaterialTheme.typography.headlineSmall)
            Text("VAD状態: ${if (recorderState.speechActive) "発話中" else "待機"}")
            Text("話者モデル: ${identity.modelName}")
            Text("形式: ${recorderState.sampleRate?.let { "$it Hz" } ?: "未初期化"} / mono / PCM16")
            LinearProgressIndicator(
                progress = { ((recorderState.levelDbFs + 90f) / 90f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::start, enabled = recorderState.status != AudioRecordingStatus.RECORDING) {
                    Text("録音開始")
                }
                Button(onClick = recorder::stop, enabled = recorderState.status == AudioRecordingStatus.RECORDING) {
                    Text("録音停止")
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
                    identity.result?.let {
                        Text(it.anonymousId, style = MaterialTheme.typography.titleLarge)
                        Text("類似度: ${String.format(Locale.US, "%.2f", it.score)}")
                        Text("参加閾値: ${String.format(Locale.US, "%.2f", it.threshold)}")
                        Text("更新 ${it.updateCount}/${it.maximumUpdateCount}")
                        Text("匿名話者クラスタ類似度一覧", style = MaterialTheme.typography.titleSmall)
                        it.candidateScores.forEach { candidate ->
                            Text(
                                "${if (candidate.selected) "✓ " else ""}${candidate.anonymousId}: " +
                                    String.format(Locale.US, "%.2f", candidate.score),
                            )
                        }
                    } ?: Text("発話を待っています")
                    Text("クラスタ数: ${identity.clusterCount}")
                    Text("特徴量抽出時間: ${identity.inferenceTimeMillis?.let { "$it ms" } ?: "未計測"}")
                    recorderState.lastUtterance?.let {
                        Text("発話区間: ${it.durationMillis} ms / 有声時間: ${it.voicedDurationMillis} ms")
                    }
                    identity.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    DeviceLoadPanel()
                }
            }
            Text("PCM/WAV永続化: 無効")
        }
    }
}
