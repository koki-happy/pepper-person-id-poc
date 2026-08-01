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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrackLinker
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityPolicy
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityThresholds
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.speaker.VadGatedSingleSpeakerSegmentationEngine
import com.example.pepper_person_id_poc.ui.component.DeviceLoadPanel
import com.example.pepper_person_id_poc.ui.component.DataTableHeaderGroup
import com.example.pepper_person_id_poc.ui.component.AudioLevelTimeline
import com.example.pepper_person_id_poc.ui.component.audioLevelPercent
import com.example.pepper_person_id_poc.ui.component.SampleVideoPlayer
import com.example.pepper_person_id_poc.ui.component.IdentificationHistoryTable
import com.example.pepper_person_id_poc.ui.component.IdentificationCandidateEntry
import com.example.pepper_person_id_poc.ui.component.IdentificationTableEntry
import com.example.pepper_person_id_poc.ui.component.StageMetricUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecordingScreen(
    settings: PocSettings,
    repository: AnonymousSpeakerClusterRepository,
    benchmarkLogger: BenchmarkLogger,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenFace: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coordinator = remember(
        settings.speakerModel,
        settings.speakerRuntime,
        settings.speakerClusterJoinThreshold,
        settings.effectiveSpeakerMaximumUpdateCount,
        settings.speakerMinimumAudioMillis,
        settings.speakerMinimumVoicedRatio,
        settings.speakerMinimumRms,
        settings.clippingAmplitudeThreshold,
        settings.maximumClippingRatio,
        settings.speakerUpdateMinimumAudioMillis,
        settings.speakerUpdateMinimumVoicedRatio,
        settings.speakerLabelContinuationSimilarity,
        settings.speakerLabelMaximumMissingSegments,
        repository,
    ) {
        SpeakerIdentityCoordinator(
            repository = repository,
            embeddingEngine = SherpaOnnxSpeakerEmbeddingEngine(context, settings.speakerModel),
            threshold = settings.speakerClusterJoinThreshold,
            maximumUpdateCount = settings.effectiveSpeakerMaximumUpdateCount,
            benchmarkLogger = benchmarkLogger,
            embeddingModelSpaceId = ModelSpaceId(settings.speakerModel.modelSpaceId),
            embeddingArtifactId = settings.speakerModel.artifactId,
            embeddingRuntimeId = settings.speakerRuntime.runtimeId,
            segmentationEngine = VadGatedSingleSpeakerSegmentationEngine(),
            qualityPolicy = SpeakerAudioQualityPolicy(
                SpeakerAudioQualityThresholds(
                    requiredSampleRate = 16_000,
                    minimumDurationMillis = settings.speakerMinimumAudioMillis,
                    minimumVoicedRatio = settings.speakerMinimumVoicedRatio,
                    minimumRms = settings.speakerMinimumRms,
                    maximumClippingRatio = settings.maximumClippingRatio,
                    maximumOverlapRatio = 0f,
                    maximumActiveSpeakerCount = 1,
                    updateMinimumDurationMillis = settings.speakerUpdateMinimumAudioMillis,
                    updateMinimumVoicedRatio = settings.speakerUpdateMinimumVoicedRatio,
                ),
            ),
            clippingAmplitudeThreshold = settings.clippingAmplitudeThreshold,
            trackLinker = LocalSpeakerTrackLinker(
                minimumSimilarity = settings.speakerLabelContinuationSimilarity,
                maximumMissingWindows = settings.speakerLabelMaximumMissingSegments,
            ),
        )
    }
    val recorder = remember(
        coordinator,
        settings.vadThreshold,
        settings.vadMinimumSilenceMillis,
        settings.vadMinimumSpeechMillis,
        settings.vadMaximumSpeechMillis,
        settings.utteranceEndSilenceMillis,
        settings.maximumUtteranceMillis,
    ) {
        AndroidPcmAudioRecorder(
            context = context,
            vadThreshold = settings.vadThreshold,
            vadMinimumSilenceMillis = settings.vadMinimumSilenceMillis,
            vadMinimumSpeechMillis = settings.vadMinimumSpeechMillis,
            vadMaximumSpeechMillis = settings.vadMaximumSpeechMillis,
            utteranceEndSilenceMillis = settings.utteranceEndSilenceMillis,
            maximumUtteranceMillis = settings.maximumUtteranceMillis,
            onUtterance = coordinator::onUtterance,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val recorderState by recorder.state.collectAsState()
    val identity by coordinator.state.collectAsState()
    var confirmReset by remember { androidx.compose.runtime.mutableStateOf(false) }
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

    Scaffold { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("音声識別", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = ::start, enabled = recorderState.status != AudioRecordingStatus.RECORDING) {
                    Text("開始")
                }
                Button(onClick = recorder::stop, enabled = recorderState.status == AudioRecordingStatus.RECORDING) {
                    Text("停止")
                }
                Button(onClick = { confirmReset = true }) { Text("初期化") }
                Button(onClick = onOpenSettings) { Text("パラメータ") }
                Button(onClick = onOpenModels) { Text("モデル") }
                Button(onClick = onOpenFace) { Text("顔識別") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                Card(Modifier.weight(1.15f).fillMaxSize()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("録音: ${recorderState.status}")
                            Text("発話: ${if (recorderState.speechActive) "発話中" else "待機"}")
                            Text(String.format(Locale.US, "現在 %.0f%%", audioLevelPercent(recorderState.levelDbFs)))
                        }
                        AudioLevelTimeline(
                            levelDbFs = recorderState.levelDbFs,
                            sampling = recorderState.status == AudioRecordingStatus.RECORDING,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Text("音声は保存しません")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(0.85f).fillMaxSize()) {
                    Card(Modifier.fillMaxWidth().weight(1f)) {
                        SpeakerAudioContractDetails(
                            recorder = recorderState,
                            identity = identity,
                            speakerClusterJoinThreshold = settings.speakerClusterJoinThreshold,
                        )
                    }
                    SampleVideoPlayer(
                        selection = settings.loadTestVideo,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 40.dp),
                    )
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("識別結果を初期化しますか？") },
            text = { Text("顔・音声の匿名IDと識別結果を削除します。保存済み設定は残ります。") },
            confirmButton = { TextButton(onClick = { confirmReset = false; onReset() }) { Text("初期化") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("キャンセル") } },
        )
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("speaker-audio-contract"),
    ) {
        item {
            SpeakerCurrentIdentificationSummary(identity)
            Text("識別結果表（直近50発話）", fontWeight = FontWeight.Bold)
            IdentificationHistoryTable(
                entries = identity.results.map { (localSpeakerId, result) ->
                    IdentificationTableEntry(
                        label = localSpeakerId,
                        id = result.anonymousId,
                        similarity = result.bestExistingScore?.score() ?: "―",
                        candidates = result.candidateScores
                            .sortedByDescending { it.score }
                            .take(3)
                            .map { IdentificationCandidateEntry(it.anonymousId, it.score.score()) },
                    )
                },
                observationSequence = identity.observationSequence,
                labelHeader = "時間／話者ラベル",
                similarityHeader = "話者IDの一致率",
                labelPrefix = "話者ラベル",
                idPrefix = "話者ID",
            )
        }
        identity.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        item {
            DeviceLoadPanel(
                stageMetrics = listOf(
                    StageMetricUiState(
                        "speaker-activity",
                        "発話区間検出",
                        identity.stageTimings?.activityMillis?.toDouble(),
                        emptyList(),
                    ),
                    StageMetricUiState(
                        "speaker-embedding",
                        "特徴量抽出",
                        identity.stageTimings?.embeddingMillis?.toDouble(),
                        emptyList(),
                    ),
                    StageMetricUiState(
                        "speaker-scoring",
                        "話者照合",
                        identity.stageTimings?.scoringMillis?.toDouble(),
                        emptyList(),
                    ),
                    StageMetricUiState(
                        "speaker-pipeline",
                        "合計",
                        identity.stageTimings?.totalMillis?.toDouble(),
                        emptyList(),
                    ),
                    StageMetricUiState(
                        "speaker-input-audio-length",
                        "―",
                        recorder.inputDurationMillis?.toDouble(),
                        emptyList(),
                    ),
                    StageMetricUiState(
                        "speaker-analysis-audio-length",
                        "―",
                        identity.stageTimings?.audioDurationMillis?.toDouble(),
                        emptyList(),
                    ),
                ),
                rateLabel = "RTF",
                rateColumnLabel = "話者識別合計／\n入力音声長",
                rateValue = identity.realTimeFactor,
                rateAfterDeviceLoad = true,
                headerGroups = listOf(
                    DataTableHeaderGroup("", 1),
                    DataTableHeaderGroup("計算負荷", 2),
                    DataTableHeaderGroup("RTF", 1),
                    DataTableHeaderGroup("話者識別（ms）", 4),
                    DataTableHeaderGroup("入力録音長（ms）", 1),
                    DataTableHeaderGroup("入力音声長（ms）", 1),
                ),
            )
        }
    }
}

@Composable
private fun SpeakerCurrentIdentificationSummary(identity: SpeakerIdentityUiState) {
    if (identity.results.isEmpty()) {
        val separated = when (identity.activityState) {
            com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState.SINGLE_SPEAKER ->
                "発話区間001（単一話者）"
            com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState.OVERLAPPED_SPEECH,
            com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS,
            -> "複数の発話区間"
            else -> "未分離"
        }
        val identification = if ("INSUFFICIENT_AUDIO" in identity.activityHoldReasons) {
            "未照合（音声不足）"
        } else {
            "未照合"
        }
        Text("現在: $separated／話者ID: $identification")
        return
    }
    identity.results.forEach { (localSpeakerId, result) ->
        val segmentNumber = localSpeakerId.substringAfterLast('-').padStart(3, '0')
        val id = result.anonymousId
            .takeUnless { it == "unknown" }
            ?.substringAfterLast('-')
            ?.padStart(3, '0')
        val status = when (result.persistenceOperation) {
            PersistenceOperation.CREATE -> "新規ID割当"
            PersistenceOperation.UPDATE -> "既存ID一致"
            PersistenceOperation.HOLD, null -> "未照合"
        }
        Text("現在: 話者ラベル$segmentNumber／話者ID ${id ?: "未確定"}（$status）")
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
