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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.AudioRecordingState
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityCoordinator
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityUiState
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import com.example.pepper_person_id_poc.infrastructure.speaker.PyannoteSegmentationOnnxEngine
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.ui.component.DeviceLoadPanel
import com.example.pepper_person_id_poc.ui.component.StageMetricUiState
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
    val coordinator = remember(
        settings.speakerModel,
        settings.speakerClusterJoinThreshold,
        settings.speakerClusterMaxUpdateCount,
        repository,
    ) {
        SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = SherpaOnnxSpeakerEmbeddingEngine(context, settings.speakerModel),
            threshold = settings.speakerClusterJoinThreshold,
            maximumUpdateCount = settings.speakerClusterMaxUpdateCount,
            benchmarkLogger = benchmarkLogger,
            embeddingModelSpaceId = ModelSpaceId(settings.speakerModel.modelSpaceId),
            embeddingArtifactId = settings.speakerModel.artifactId,
            embeddingRuntimeId = "sherpa-onnx-1.13.4-android-cpu",
            segmentationEngine = PyannoteSegmentationOnnxEngine(context),
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
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            recorder.start()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text("録音状態: ${recorderState.status}", style = MaterialTheme.typography.headlineSmall)
            Text("VAD状態: ${if (recorderState.speechActive) "発話中" else "待機"}")
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
            Card(Modifier.fillMaxWidth().weight(1f)) {
                SpeakerAudioContractDetails(
                    recorder = recorderState,
                    identity = identity,
                    speakerClusterJoinThreshold = settings.speakerClusterJoinThreshold,
                )
            }
            Text("音声は保存しません")
        }
    }
}

@Composable
fun SpeakerAudioContractDetails(
    recorder: AudioRecordingState,
    identity: SpeakerIdentityUiState,
    speakerClusterJoinThreshold: Float,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("speaker-audio-contract"),
    ) {
        identity.results.forEach { (localSpeakerId, result) ->
            item(key = "result-$localSpeakerId") {
                val quality = identity.qualityByLocalSpeaker[localSpeakerId]
                SpeakerDetailRow("Anonymous speaker ID", result.anonymousId)
                SpeakerDetailRow(
                    "判定",
                    when {
                        quality != null && !quality.createEligible && !quality.updateEligible -> "品質不足"
                        result.isNewCluster -> "新規話者"
                        else -> "既存話者"
                    },
                )
                SpeakerDetailRow(
                    "類似度",
                    result.bestExistingScore?.score() ?: "比較対象なし",
                )
                quality?.rejectionReasons?.takeIf { it.isNotEmpty() }?.let { reasons ->
                    SpeakerDetailRow("理由", reasons.joinToString())
                }
            }
        }
        identity.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        item {
            DeviceLoadPanel(
                stageMetrics = listOfNotNull(
                    identity.stageTimings?.activityMillis?.let {
                        StageMetricUiState("speaker-activity", "Activity", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.trackingMillis?.let {
                        StageMetricUiState("speaker-tracking", "Tracking", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.qualityMillis?.let {
                        StageMetricUiState("speaker-quality", "Quality", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.embeddingMillis?.let {
                        StageMetricUiState("speaker-embedding", "Embedding", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.aggregationMillis?.let {
                        StageMetricUiState("speaker-aggregation", "Aggregation", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.scoringMillis?.let {
                        StageMetricUiState("speaker-scoring", "Scoring", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.policyMillis?.let {
                        StageMetricUiState("speaker-policy", "Policy", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.repositoryMillis?.let {
                        StageMetricUiState("speaker-repository", "Repository", it.toDouble(), emptyList())
                    },
                    identity.stageTimings?.totalMillis?.let {
                        StageMetricUiState("speaker-pipeline", "Pipeline", it.toDouble(), emptyList())
                    },
                ),
                rateLabel = "RTF",
                rateValue = identity.realTimeFactor,
            )
        }
    }
}

@Composable
private fun SpeakerTableHeader(
    first: String,
    second: String,
    firstColumnWeight: Float = 0.38f,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(first, fontWeight = FontWeight.Bold, modifier = Modifier.weight(firstColumnWeight))
        Text(
            second,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f - firstColumnWeight),
        )
    }
    HorizontalDivider()
}

@Composable
private fun SpeakerDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(0.38f))
        Text(value, textAlign = TextAlign.End, modifier = Modifier.weight(0.62f))
    }
}

private fun Float.score() = String.format(Locale.US, "%.2f", this)

private fun Float.percent() = String.format(Locale.US, "%.1f%%", this * 100f)

private fun Long?.millis() = this?.let { "$it ms" } ?: "未計測"
