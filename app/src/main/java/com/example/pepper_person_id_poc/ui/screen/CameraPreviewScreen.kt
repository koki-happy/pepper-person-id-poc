package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.face.FaceIdentityCoordinator
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingArtifactResolver
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngineFactory
import com.example.pepper_person_id_poc.infrastructure.face.FaceDetectorFactory
import com.example.pepper_person_id_poc.infrastructure.face.FaceDetectorPipeline
import com.example.pepper_person_id_poc.ui.component.DeviceLoadPanel
import com.example.pepper_person_id_poc.ui.component.DataTableHeaderGroup
import com.example.pepper_person_id_poc.ui.component.SampleVideoPlayer
import com.example.pepper_person_id_poc.ui.component.IdentificationHistoryTable
import com.example.pepper_person_id_poc.ui.component.IdentificationCandidateEntry
import com.example.pepper_person_id_poc.ui.component.IdentificationTableEntry
import com.example.pepper_person_id_poc.ui.component.StageMetricUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    settings: PocSettings,
    repository: AnonymousFaceClusterRepository,
    benchmarkLogger: BenchmarkLogger,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSpeaker: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember(settings.faceEmbeddingModel, settings.faceEmbeddingRuntime) {
        FaceEmbeddingEngineFactory.create(context, settings.faceEmbeddingModel, settings.faceEmbeddingRuntime)
    }
    val coordinator = remember(repository, engine.modelName, settings.faceClusterJoinThreshold, settings.effectiveFaceMaximumUpdateCount) {
        FaceIdentityCoordinator(
            repository = repository,
            modelId = engine.modelName,
            threshold = settings.faceClusterJoinThreshold,
            maximumUpdateCount = settings.effectiveFaceMaximumUpdateCount,
        )
    }
    val detector: FaceDetectorPipeline = remember(
        coordinator,
        settings.faceDetectorModel,
        settings.faceDetectorRuntime,
        engine,
        settings.faceAnalysisIntervalMillis,
        settings.faceDetectionScoreThreshold,
        settings.faceNmsThreshold,
        settings.faceMaximumDetectionCandidates,
        settings.mlKitMinimumFaceSize,
        settings.faceLabelContinuationIou,
        settings.faceLabelMaximumMissingFrames,
    ) {
        FaceDetectorFactory.create(
            context = context,
            model = settings.faceDetectorModel,
            runtime = settings.faceDetectorRuntime,
            embeddingEngine = engine,
            analysisIntervalMillis = settings.faceAnalysisIntervalMillis,
            scoreThreshold = settings.faceDetectionScoreThreshold,
            nmsThreshold = settings.faceNmsThreshold,
            maximumDetectionCandidates = settings.faceMaximumDetectionCandidates,
            minimumFaceSize = settings.mlKitMinimumFaceSize,
            labelContinuationIou = settings.faceLabelContinuationIou,
            labelMaximumMissingFrames = settings.faceLabelMaximumMissingFrames,
            estimateHeadPose = false,
            onPoseObservations = coordinator::onFaceAnalysis,
            onFeatureObservations = coordinator::onFeatureObservations,
            onEmbeddingReady = coordinator::reportEmbeddingReady,
            onEmbeddingError = coordinator::reportEmbeddingError,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val controller = remember(detector) { CameraXPreviewController(context, detector) }
    val previewFrame by controller.previewFrame.collectAsState()
    val detection by detector.snapshot.collectAsState()
    val identity by coordinator.state.collectAsState()
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var running by remember { mutableStateOf(true) }
    var confirmReset by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LaunchedEffect(permissionGranted, lifecycleOwner, running) {
        if (permissionGranted && running) controller.bind(lifecycleOwner) else controller.unbind()
    }
    DisposableEffect(controller) {
        onDispose {
            coordinator.close()
            if (controller.closeAndAwaitAnalysis()) detector.close()
        }
    }

    Scaffold { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("顔識別", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = { running = !running }) { Text(if (running) "停止" else "開始") }
                Button(onClick = { confirmReset = true }) { Text("初期化") }
                Button(onClick = onOpenSettings) { Text("パラメータ") }
                Button(onClick = onOpenModels) { Text("モデル") }
                Button(onClick = onOpenSpeaker) { Text("音声識別") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1.15f).fillMaxSize().background(Color.Black),
                ) {
                previewFrame?.let { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "カメラ映像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val labelPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 10.dp.toPx()
                        isAntiAlias = true
                    }
                    val labelBackgroundPaint = Paint().apply {
                        color = android.graphics.Color.argb(190, 0, 0, 0)
                    }
                    detection.faces.forEach { face ->
                        val box = face.boundingBox
                        val sourceWidth = detection.orientedFrameWidth.takeIf { it > 0 }?.toFloat() ?: size.width
                        val sourceHeight = detection.orientedFrameHeight.takeIf { it > 0 }?.toFloat() ?: size.height
                        val scale = maxOf(size.width / sourceWidth, size.height / sourceHeight)
                        val offsetX = (size.width - sourceWidth * scale) / 2f
                        val offsetY = (size.height - sourceHeight * scale) / 2f
                        val left = offsetX + (1f - box.right) * sourceWidth * scale
                        val right = offsetX + (1f - box.left) * sourceWidth * scale
                        val top = offsetY + box.top * sourceHeight * scale
                        val bottom = offsetY + box.bottom * sourceHeight * scale
                        val detectionNumber = face.trackId.substringAfterLast('-')
                        val featureNumber = identity.results[face.trackId]
                            ?.anonymousId
                            ?.substringAfterLast('-')
                            ?: "---"
                        val similarity = identity.results[face.trackId]?.bestExistingScore?.score() ?: "---"
                        val labels = listOf("顔ラベル:$detectionNumber", "顔ID:$featureNumber  類似度:$similarity")
                        val lineHeight = labelPaint.textSize * 1.2f
                        val labelWidth = labels.maxOf(labelPaint::measureText) + 8.dp.toPx()
                        val labelHeight = lineHeight * labels.size + 4.dp.toPx()
                        val labelLeft = left.coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
                        val labelTop = top.coerceIn(0f, (size.height - labelHeight).coerceAtLeast(0f))
                        drawRect(
                            color = Color.Green,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(3f),
                        )
                        if (settings.showFaceLandmarks) face.landmarks.forEach { landmark ->
                            val landmarkCenter = Offset(
                                x = offsetX + (1f - landmark.x) * sourceWidth * scale,
                                y = offsetY + landmark.y * sourceHeight * scale,
                            )
                            drawCircle(
                                color = Color.Black,
                                radius = 6.dp.toPx(),
                                center = landmarkCenter,
                            )
                            drawCircle(
                                color = Color.Green,
                                radius = 4.dp.toPx(),
                                center = landmarkCenter,
                            )
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawRect(
                                labelLeft,
                                labelTop,
                                labelLeft + labelWidth,
                                labelTop + labelHeight,
                                labelBackgroundPaint,
                            )
                            labels.forEachIndexed { index, label ->
                                canvas.nativeCanvas.drawText(
                                    label,
                                    labelLeft + 4.dp.toPx(),
                                    labelTop + 2.dp.toPx() + labelPaint.textSize + index * lineHeight,
                                    labelPaint,
                                )
                            }
                        }
                    }
                }
                if (!permissionGranted) {
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("カメラ権限を許可") }
                }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(0.85f).fillMaxSize()) {
                    Card(Modifier.fillMaxWidth().weight(1f)) {
                        FacePreviewContractDetails(
                            identity = identity,
                            detectionProcessingTimeMillis = detection.processingTimeMillis,
                            analysisFramesPerSecond = detection.analysisFramesPerSecond,
                            faceClusterJoinThreshold = settings.faceClusterJoinThreshold,
                            modelSpaceId = identity.results.values.firstOrNull()
                                ?.evaluation?.candidates?.firstOrNull()?.modelSpaceId?.value ?: engine.modelName,
                            artifactId = FaceEmbeddingArtifactResolver.resolve(
                                settings.faceEmbeddingModel,
                                settings.faceEmbeddingRuntime,
                            )?.artifactId ?: "MISSING_EXACT_ARTIFACT",
                            runtimeId = settings.faceEmbeddingRuntime.runtimeId,
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
fun FacePreviewContractDetails(
    identity: com.example.pepper_person_id_poc.application.face.FaceIdentityUiState,
    detectionProcessingTimeMillis: Long?,
    analysisFramesPerSecond: Float = 0f,
    faceClusterJoinThreshold: Float,
    modelSpaceId: String = "N/A",
    artifactId: String = "N/A",
    runtimeId: String = "N/A",
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("face-preview-contract"),
    ) {
        item {
            Text(
                "識別結果表（直近50フレーム）",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            IdentificationHistoryTable(
                entries = identity.results.map { (trackId, result) ->
                    IdentificationTableEntry(
                        label = trackId,
                        id = result.anonymousId,
                        similarity = result.bestExistingScore?.score() ?: "―",
                        candidates = result.candidateScores
                            .sortedByDescending { it.score }
                            .take(3)
                            .map { IdentificationCandidateEntry(it.anonymousId, it.score.score()) },
                    )
                },
                observationSequence = identity.observationSequence,
                labelHeader = "時間／顔ラベル",
                similarityHeader = "顔IDの一致率",
                labelPrefix = "顔ラベル",
                idPrefix = "顔ID",
            )
        }
        identity.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        item {
            val metrics = identity.pipelineMetrics
            DeviceLoadPanel(
                stageMetrics = listOf(
                    metrics?.detectionMillis.fpsStage("face-detection-fps", "顔検出"),
                    metrics?.scoringMillis.fpsStage("face-scoring-fps", "顔照合"),
                    StageMetricUiState(
                        "face-total-fps",
                        "合計",
                        analysisFramesPerSecond.takeIf { it > 0f }?.toDouble(),
                        emptyList(),
                        "fps",
                    ),
                ),
                headerGroups = listOf(
                    DataTableHeaderGroup("", 1),
                    DataTableHeaderGroup("計算負荷", 2),
                    DataTableHeaderGroup("顔識別（fps）", 3),
                ),
            )
        }
    }
}

private fun Double?.stage(id: String, label: String): StageMetricUiState =
    StageMetricUiState(id = id, label = label, currentMillis = this, samples = emptyList())

private fun Double?.fpsStage(id: String, label: String): StageMetricUiState =
    StageMetricUiState(
        id = id,
        label = label,
        currentMillis = this?.takeIf { it > 0.0 }?.let { 1_000.0 / it },
        samples = emptyList(),
        unit = "fps",
    )

@Composable
private fun TableHeader(
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
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(0.38f))
        Text(value, textAlign = TextAlign.End, modifier = Modifier.weight(0.62f))
    }
}

private fun Float.score() = String.format(Locale.US, "%.2f", this)
