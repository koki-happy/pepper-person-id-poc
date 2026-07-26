package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.pepper_person_id_poc.ui.component.StageMetricUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    settings: PocSettings,
    repository: AnonymousFaceClusterRepository,
    benchmarkLogger: BenchmarkLogger,
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember(settings.faceEmbeddingModel, settings.faceEmbeddingRuntime) {
        FaceEmbeddingEngineFactory.create(context, settings.faceEmbeddingModel, settings.faceEmbeddingRuntime)
    }
    val coordinator = remember(repository, engine.modelName, settings.faceClusterJoinThreshold, settings.faceClusterMaxUpdateCount) {
        FaceIdentityCoordinator(
            repository = repository,
            modelId = engine.modelName,
            threshold = settings.faceClusterJoinThreshold,
            maximumUpdateCount = settings.faceClusterMaxUpdateCount,
        )
    }
    val detector: FaceDetectorPipeline = remember(
        coordinator,
        settings.faceDetectorModel,
        settings.faceDetectorRuntime,
        engine,
    ) {
        FaceDetectorFactory.create(
            context = context,
            model = settings.faceDetectorModel,
            runtime = settings.faceDetectorRuntime,
            embeddingEngine = engine,
            analysisIntervalMillis = 1_000L,
            estimateHeadPose = false,
            onPoseObservations = coordinator::onFaceAnalysis,
            onFeatureObservations = coordinator::onFeatureObservations,
            onEmbeddingReady = coordinator::reportEmbeddingReady,
            onEmbeddingError = coordinator::reportEmbeddingError,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val controller = remember(detector) { CameraXPreviewController(context, detector) }
    val surfaceRequest by controller.surfaceRequest.collectAsState()
    val detection by detector.snapshot.collectAsState()
    val identity by coordinator.state.collectAsState()
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LaunchedEffect(permissionGranted, lifecycleOwner) {
        if (permissionGranted) controller.bind(lifecycleOwner) else controller.unbind()
    }
    DisposableEffect(controller) {
        onDispose {
            coordinator.close()
            if (controller.closeAndAwaitAnalysis()) detector.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("未登録顔識別") },
                navigationIcon = { Button(onClick = onBackToSettings) { Text("← 設定") } },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color.Black),
            ) {
                surfaceRequest?.let {
                    CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize())
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
                        val labels = listOf(
                            "Detection: $detectionNumber",
                            "Feature: $featureNumber",
                        )
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
                        face.landmarks.forEach { landmark ->
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
            Card(Modifier.fillMaxWidth().weight(1f)) {
                FacePreviewContractDetails(
                    identity = identity,
                    detectionProcessingTimeMillis = detection.processingTimeMillis,
                    analysisFramesPerSecond = detection.analysisFramesPerSecond,
                    faceClusterJoinThreshold = settings.faceClusterJoinThreshold,
                    modelSpaceId = identity.results.values.firstOrNull()
                        ?.evaluation?.candidates?.firstOrNull()?.modelSpaceId?.value
                        ?: engine.modelName,
                    artifactId = FaceEmbeddingArtifactResolver.resolve(
                        settings.faceEmbeddingModel,
                        settings.faceEmbeddingRuntime,
                    )?.artifactId ?: "MISSING_EXACT_ARTIFACT",
                    runtimeId = settings.faceEmbeddingRuntime.runtimeId,
                    qualityByTrackId = detection.faces.mapNotNull { face ->
                        face.qualityAssessment?.let { quality ->
                            val input = quality.input
                            face.trackId to FaceQualityUiState(
                                summary = if (input == null) {
                                    "N/A"
                                } else {
                                    "blur=${input.blurScore.score()}, brightness=${input.brightnessMean.score()}, " +
                                        "clipped=${input.clippedRatio.score()}, landmarks=${input.landmarkCount}, " +
                                        "track=${input.trackDurationMillis}ms, confidence=" +
                                        (input.detectionConfidence?.score() ?: "N/A")
                                },
                                createEligible = quality.createEligible,
                                updateEligible = quality.updateEligible,
                                rejectionReasons = quality.rejectionReasons,
                            )
                        }
                    }.toMap(),
                )
            }
        }
    }
}

data class FaceQualityUiState(
    val summary: String,
    val createEligible: Boolean,
    val updateEligible: Boolean,
    val rejectionReasons: List<String>,
)

@Composable
fun FacePreviewContractDetails(
    identity: com.example.pepper_person_id_poc.application.face.FaceIdentityUiState,
    detectionProcessingTimeMillis: Long?,
    analysisFramesPerSecond: Float = 0f,
    faceClusterJoinThreshold: Float,
    qualityByTrackId: Map<String, FaceQualityUiState> = emptyMap(),
    modelSpaceId: String = "N/A",
    artifactId: String = "N/A",
    runtimeId: String = "N/A",
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("face-preview-contract"),
    ) {
        identity.results.forEach { (trackId, result) ->
            item(key = "identity-$trackId") {
                val quality = qualityByTrackId[trackId]
                DetailRow("Feature ID", result.anonymousId)
                DetailRow(
                    "判定",
                    when {
                        quality != null && !quality.createEligible && !quality.updateEligible -> "品質不足"
                        result.isNewCluster -> "新規Feature"
                        else -> "既存Feature"
                    },
                )
                DetailRow(
                    "類似度",
                    result.bestExistingScore?.score() ?: "比較対象なし",
                )
                quality?.rejectionReasons?.takeIf(List<String>::isNotEmpty)?.let { reasons ->
                    DetailRow(
                        "理由",
                        reasons.joinToString(),
                    )
                }
            }
        }
        identity.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        item {
            val metrics = identity.pipelineMetrics
            DeviceLoadPanel(
                stageMetrics = listOf(
                    metrics?.preprocessingMillis.stage("face-preprocessing", "Preprocessing"),
                    metrics?.detectionMillis.stage("face-detection", "Detection"),
                    metrics?.qualityMillis.stage("face-quality", "Quality"),
                    metrics?.alignmentMillis.stage("face-alignment", "Alignment"),
                    metrics?.embeddingMillis.stage("face-embedding", "Embedding"),
                    metrics?.scoringMillis.stage("face-scoring", "Scoring"),
                    metrics?.policyMillis.stage("face-policy", "Policy"),
                    metrics?.repositoryMillis.stage("face-repository", "Repository"),
                    metrics?.uiMillis.stage("face-ui", "UI"),
                    metrics?.totalMillis.stage("face-total", "Total"),
                ),
                rateLabel = "解析FPS",
                rateValue = analysisFramesPerSecond,
            )
        }
    }
}

private fun Double?.stage(id: String, label: String): StageMetricUiState =
    StageMetricUiState(id = id, label = label, currentMillis = this, samples = emptyList())

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
