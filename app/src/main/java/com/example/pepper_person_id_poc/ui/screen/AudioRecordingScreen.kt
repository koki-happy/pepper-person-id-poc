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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityCoordinator
import com.example.pepper_person_id_poc.application.speaker.SpeakerScreenMode
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentifier
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentityStatus
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingScreen(
    mode: SpeakerScreenMode,
    settings: PocSettings,
    personRepository: PersonRepository,
    benchmarkLogger: BenchmarkLogger,
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coordinator = remember(
        mode,
        settings.speakerModel,
        settings.speakerThreshold,
        settings.speakerMargin,
        personRepository,
    ) {
        SpeakerIdentityCoordinator(
            mode = mode,
            personRepository = personRepository,
            embeddingEngine = SherpaOnnxSpeakerEmbeddingEngine(context, settings.speakerModel),
            speakerIdentifier = SpeakerIdentifier(),
            speakerThreshold = settings.speakerThreshold,
            speakerMargin = settings.speakerMargin,
            benchmarkLogger = benchmarkLogger,
        )
    }
    val recorder = remember(coordinator, benchmarkLogger) {
        AndroidPcmAudioRecorder(
            context = context,
            onUtterance = coordinator::onUtterance,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val state by recorder.state.collectAsState()
    val identityState by coordinator.state.collectAsState()
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
            coordinator.close()
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
                title = {
                    Text(
                        when (mode) {
                            SpeakerScreenMode.REGISTRATION -> "声登録"
                            SpeakerScreenMode.IDENTIFICATION -> "話者識別"
                            SpeakerScreenMode.ANONYMOUS_IDENTIFICATION -> "未登録リアルタイム話者識別"
                        },
                    )
                },
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
            Text(
                "話者モデル: ${identityState.modelName} / ${
                    if (identityState.modelReady) "準備完了 (${identityState.modelDimension ?: "?"}次元)" else "初期化中"
                }",
            )
            Text("形式: ${state.sampleRate?.let { "$it Hz" } ?: "未初期化"} / mono / PCM 16-bit")
            Text("最小バッファ: ${state.minBufferSizeBytes?.let { "$it bytes" } ?: "-"}")
            Text("VAD: ${state.vadModelName ?: "未初期化"}")

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
            identityState.error?.let { Text("話者モデルエラー: $it", color = MaterialTheme.colorScheme.error) }

            when (mode) {
                SpeakerScreenMode.REGISTRATION -> SpeakerRegistrationPanel(coordinator, identityState)
                SpeakerScreenMode.IDENTIFICATION -> RegisteredSpeakerResultPanel(
                    state = identityState,
                    debugMode = settings.debugMode,
                )
                SpeakerScreenMode.ANONYMOUS_IDENTIFICATION -> AnonymousSpeakerResultPanel(
                    state = identityState,
                    onReset = coordinator::resetAnonymousSession,
                )
            }

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
                "16 kHz録音ではSilero VADを使用します。発話終了後、選択した話者モデルをバックグラウンドで実行します。",
                color = MaterialTheme.colorScheme.primary,
            )
            Text("PCM/WAV永続化: 無効 / 測定ログ: 処理時間・メタデータ")
        }
    }
}

@Composable
private fun SpeakerRegistrationPanel(
    coordinator: SpeakerIdentityCoordinator,
    state: com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityUiState,
) {
    var personId by rememberSaveable { mutableStateOf("person1") }
    var displayName by rememberSaveable { mutableStateOf("人物A") }
    Text("声サンプル登録", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = personId,
        onValueChange = { personId = it },
        label = { Text("personId") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it },
        label = { Text("表示名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { coordinator.requestRegistration(personId, displayName) },
        enabled = state.modelReady && !state.processing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("次の1秒以上の発話を声サンプルとして登録")
    }
    state.registrationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    Text("登録人物: ${state.profiles.size}人")
    state.profiles.forEach { profile ->
        Text("${profile.displayName} / ${profile.personId.value} / 声 ${profile.speakerSampleCount}")
    }
}

@Composable
private fun RegisteredSpeakerResultPanel(
    state: com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityUiState,
    debugMode: Boolean,
) {
    Text("現在の話者", style = MaterialTheme.typography.titleMedium)
    if (state.processing) Text("話者特徴量を計算中…")
    val result = state.result
    if (result == null) {
        Text("発話を待っています")
    } else {
        val title = if (result.status == SpeakerIdentityStatus.IDENTIFIED) {
            "${result.displayName} / ${result.personId?.value}"
        } else {
            "Unknown speaker"
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text("状態: ${result.status}")
        Text("Speaker: ${result.score.asScore()} / threshold ${result.threshold.asScore()}")
        Text("処理時間: ${result.processingTimeMillis} ms")
        if (debugMode && result.status == SpeakerIdentityStatus.UNKNOWN) {
            Text("Best candidate: ${result.bestCandidatePersonId?.value ?: "なし"}")
        }
    }
}

@Composable
private fun AnonymousSpeakerResultPanel(
    state: com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityUiState,
    onReset: () -> Unit,
) {
    Text("セッション内の一時話者", style = MaterialTheme.typography.titleMedium)
    Text("識別済みクラスタ: ${state.anonymousClusterCount}人")
    Text("保存先: セッションメモリ / 破棄: 画面終了またはリセット")
    if (state.processing) Text("話者特徴量を計算中…")
    state.anonymousResult?.let { result ->
        Text(result.anonymousSpeakerId, style = MaterialTheme.typography.titleSmall)
        Text("Similarity: ${result.score.asScore()} / threshold ${result.threshold.asScore()}")
        Text("samples: ${result.clusterSampleCount}")
        if (result.isNewCluster) Text("新しい一時話者として追加")
    } ?: Text("発話を待っています")
    Button(
        onClick = onReset,
        enabled = state.anonymousClusterCount > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("一時話者IDをリセット")
    }
}

private fun formatTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date(timestampMillis))

private fun Float?.asScore(): String = this?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
