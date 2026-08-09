package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.AudioRecordingState
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.face.FaceIdentityCoordinator
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityCoordinator
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityUiState
import com.example.pepper_person_id_poc.application.speaker.SpeakerStageTimings
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingArtifactResolver
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController
import com.example.pepper_person_id_poc.infrastructure.audio.AndroidPcmAudioRecorder
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngineFactory
import com.example.pepper_person_id_poc.infrastructure.face.FaceDetectorFactory
import com.example.pepper_person_id_poc.infrastructure.face.FaceDetectorPipeline
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaPyannoteSegmentationEngine
import com.example.pepper_person_id_poc.infrastructure.device.AndroidDeviceLoadMonitor
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrackLinker
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrackState
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityPolicy
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityThresholds
import com.example.pepper_person_id_poc.ui.component.DeviceLoadPanel
import com.example.pepper_person_id_poc.ui.component.DataTableHeaderGroup
import com.example.pepper_person_id_poc.ui.component.IdentificationHistoryTable
import com.example.pepper_person_id_poc.ui.component.IdentificationCandidateEntry
import com.example.pepper_person_id_poc.ui.component.IdentificationTableEntry
import com.example.pepper_person_id_poc.ui.component.StageMetricUiState
import com.example.pepper_person_id_poc.ui.component.ScrollableDataTable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.absoluteValue

private const val DASHBOARD_HISTORY_LIMIT = 30

private val HtmlCanvas = Color(0xFFF6F7F9)
private val HtmlSurface = Color.White
private val HtmlMutedSurface = Color(0xFFF7F8FA)
private val HtmlLine = Color(0xFFDFE4E8)
private val HtmlLineSoft = Color(0xFFEDF0F2)
private val HtmlInk = Color(0xFF20252B)
private val HtmlMutedInk = Color(0xFF68727D)
private val HtmlBlue = Color(0xFF2563EB)
private val HtmlPurple = Color(0xFF7658B5)
private val HtmlRed = Color(0xFFC62828)
private val HtmlOrange = Color(0xFFA15C00)
private val HtmlGreen = Color(0xFF16803C)

private data class FaceDashboardRow(
    val elapsedMillis: Long,
    val label: String,
    val id: String,
    val similarity: Float?,
    val processingMillis: Double?,
    val isNew: Boolean,
)

private data class SpeakerDashboardRow(
    val elapsedMillis: Long,
    val id: String,
    val similarity: Float?,
    val processingMillis: Double?,
    val utteranceSeconds: Float?,
    val isNew: Boolean,
)

private data class DashboardEvent(val elapsedMillis: Long, val text: String)

private fun displayIdentityId(value: String?, prefix: String): String = value
    ?.substringAfterLast('-')
    ?.toIntOrNull()
    ?.let { "$prefix${it.toString().padStart(3, '0')}" }
    ?: value?.takeUnless { it.isBlank() || it == "unknown" } ?: "—"

private fun displayFaceLabel(value: String?): String = value
    ?.substringAfterLast('-')
    ?.toIntOrNull()
    ?.let { "FL${it.toString().padStart(3, '0')}" }
    ?: "—"

@Composable
private fun HtmlToolbar(elapsed: String, onOpenSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(HtmlSurface)
            .border(1.dp, HtmlLine, RectangleShape)
            .padding(horizontal = 18.dp),
    ) {
        Text("顔・話者識別", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HtmlInk)
        Spacer(Modifier.width(10.dp))
        Text("経過 $elapsed", fontSize = 11.sp, color = HtmlMutedInk, modifier = Modifier.border(1.dp, HtmlLine, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 4.dp))
        Spacer(Modifier.weight(1f))
        HtmlButton("設定", onClick = onOpenSettings, modifier = Modifier.width(58.dp))
    }
}

@Composable
private fun HtmlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color? = null,
) {
    val active = activeColor != null
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, activeColor ?: HtmlLine),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = activeColor ?: HtmlSurface,
            contentColor = if (active) Color.White else HtmlInk,
        ),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HtmlControlBar(
    faceRunning: Boolean,
    speakerRunning: Boolean,
    onToggleFace: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(HtmlSurface)
            .border(1.dp, HtmlLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
    ) {
        HtmlButton("顔識別", onToggleFace, activeColor = if (faceRunning) HtmlBlue else null)
        HtmlButton("話者識別", onToggleSpeaker, activeColor = if (speakerRunning) HtmlPurple else null)
        Spacer(Modifier.weight(1f))
        HtmlButton("停止", onStop, activeColor = if (!faceRunning && !speakerRunning) HtmlRed else null)
        HtmlButton("リセット", onReset)
    }
}

@Composable
private fun HtmlPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.border(1.dp, HtmlLine, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HtmlSurface, contentColor = HtmlInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

@Composable
private fun HtmlCameraPreviewPanel(
    permissionGranted: Boolean,
    previewFrame: android.graphics.Bitmap?,
    detection: com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot,
    identity: com.example.pepper_person_id_poc.application.face.FaceIdentityUiState,
    settings: PocSettings,
    onRequestPermission: () -> Unit,
) {
    HtmlPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(36.dp).background(HtmlMutedSurface).padding(horizontal = 12.dp),
        ) {
            Text("カメラプレビュー", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HtmlInk)
            Spacer(Modifier.weight(1f))
            Text("640×480（4:3）", fontSize = 10.sp, color = HtmlMutedInk)
            Spacer(Modifier.width(8.dp))
            Text(
                "顔識別 FPS（平均） ${detection.analysisFramesPerSecond.takeIf { it > 0f }?.let { String.format(Locale.US, "%.1f", it) } ?: "—"}",
                fontSize = 9.sp,
                color = HtmlMutedInk,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFFF0F2F4)),
        ) {
            if (permissionGranted) {
                previewFrame?.let { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "カメラ映像",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val labelPaint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 10.dp.toPx()
                        isAntiAlias = true
                    }
                    detection.faces.forEachIndexed { index, face ->
                        val box = face.boundingBox
                        val sourceWidth = detection.orientedFrameWidth.takeIf { it > 0 }?.toFloat() ?: size.width
                        val sourceHeight = detection.orientedFrameHeight.takeIf { it > 0 }?.toFloat() ?: size.height
                        val scale = minOf(size.width / sourceWidth, size.height / sourceHeight)
                        val offsetX = (size.width - sourceWidth * scale) / 2f
                        val offsetY = (size.height - sourceHeight * scale) / 2f
                        val left = offsetX + (1f - box.right) * sourceWidth * scale
                        val right = offsetX + (1f - box.left) * sourceWidth * scale
                        val top = offsetY + box.top * sourceHeight * scale
                        val bottom = offsetY + box.bottom * sourceHeight * scale
                        val faceLabelNumber = face.trackId.substringAfterLast('-').toIntOrNull()?.toString()?.padStart(3, '0') ?: "---"
                        val faceIdNumber = identity.results[face.trackId]?.anonymousId?.substringAfterLast('-')?.toIntOrNull()?.toString()?.padStart(3, '0') ?: "---"
                        val similarity = identity.results[face.trackId]?.bestExistingScore?.score() ?: "---"
                        val accent = if (index % 2 == 0) HtmlBlue else HtmlRed
                        val label = "顔ラベル FL$faceLabelNumber / 顔ID FP$faceIdNumber / 類似度 $similarity"
                        val labelWidth = labelPaint.measureText(label) + 8.dp.toPx()
                        val labelHeight = labelPaint.textSize + 6.dp.toPx()
                        val labelLeft = left.coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
                        val labelTop = (top - labelHeight).coerceIn(0f, (size.height - labelHeight).coerceAtLeast(0f))
                        drawRect(accent, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2f))
                        drawIntoCanvas { canvas ->
                            val background = Paint().apply { color = accent.toArgb() }
                            canvas.nativeCanvas.drawRect(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight, background)
                            canvas.nativeCanvas.drawText(label, labelLeft + 4.dp.toPx(), labelTop + labelPaint.textSize + 1.dp.toPx(), labelPaint)
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("camera preview", color = Color(0xFF8A939C), fontSize = 11.sp)
                    Text("権限を許可すると映像を表示します", color = Color(0xFF8A939C), fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    HtmlButton("カメラ権限を許可", onRequestPermission, activeColor = HtmlBlue)
                }
            }
        }
    }
}

private fun elapsedLabel(elapsedMillis: Long): String {
    val tenths = (elapsedMillis.coerceAtLeast(0L) / 100L) % 10L
    val seconds = elapsedMillis.coerceAtLeast(0L) / 1_000L
    return String.format(Locale.US, "%02d:%02d.%d", seconds / 60L, seconds % 60L, tenths)
}

private fun speakerTimelineColor(label: String): Color {
    val number = label.removePrefix("SL").toIntOrNull() ?: label.hashCode().absoluteValue
    val hue = ((number * 137.508f) % 360f + 360f) % 360f
    return Color.hsl(hue, 0.65f, 0.55f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    settings: PocSettings,
    repository: AnonymousFaceClusterRepository,
    speakerRepository: AnonymousSpeakerClusterRepository,
    benchmarkLogger: BenchmarkLogger,
    onOpenSettings: () -> Unit,
    onOpenModels: () -> Unit,
    onExit: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val speakerCoordinator = remember(
        speakerRepository,
        settings.speakerModel,
        settings.speakerRuntime,
        settings.speakerClusterJoinThreshold,
        settings.effectiveSpeakerMaximumUpdateCount,
        settings.speakerOverlapDisplayThreshold,
    ) {
        SpeakerIdentityCoordinator(
            repository = speakerRepository,
            embeddingEngine = SherpaOnnxSpeakerEmbeddingEngine(context, settings.speakerModel),
            threshold = settings.speakerClusterJoinThreshold,
            maximumUpdateCount = settings.effectiveSpeakerMaximumUpdateCount,
            benchmarkLogger = benchmarkLogger,
            embeddingModelSpaceId = ModelSpaceId(settings.speakerModel.modelSpaceId),
            embeddingArtifactId = settings.speakerModel.artifactId,
            embeddingRuntimeId = settings.speakerRuntime.runtimeId,
            segmentationEngine = SherpaPyannoteSegmentationEngine(
                context = context,
                embeddingModelFilename = settings.speakerModel.modelFileName,
            ),
            overlapDisplayThreshold = settings.speakerOverlapDisplayThreshold,
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
        )
    }
    val speakerRecorder = remember(speakerCoordinator, settings.vadThreshold, settings.vadMinimumSilenceMillis, settings.vadMinimumSpeechMillis, settings.vadMaximumSpeechMillis, settings.utteranceEndSilenceMillis, settings.maximumUtteranceMillis) {
        AndroidPcmAudioRecorder(
            context = context,
            vadThreshold = settings.vadThreshold,
            vadMinimumSilenceMillis = settings.vadMinimumSilenceMillis,
            vadMinimumSpeechMillis = settings.vadMinimumSpeechMillis,
            vadMaximumSpeechMillis = settings.vadMaximumSpeechMillis,
            utteranceEndSilenceMillis = settings.utteranceEndSilenceMillis,
            maximumUtteranceMillis = settings.maximumUtteranceMillis.coerceAtMost(
                SherpaPyannoteSegmentationEngine.WINDOW_SAMPLES / 16L,
            ),
            onUtterance = speakerCoordinator::onUtterance,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val speakerRecorderState by speakerRecorder.state.collectAsState()
    val speakerIdentity by speakerCoordinator.state.collectAsState()
    // This clock belongs only to the identification screen. Navigating to Settings or
    // Benchmark disposes this screen, so time spent there is never included here.
    val identificationStartedAt = remember { SystemClock.elapsedRealtime() }
    val faceHistory = remember { mutableStateListOf<FaceDashboardRow>() }
    val speakerHistory = remember { mutableStateListOf<SpeakerDashboardRow>() }
    val dashboardEvents = remember { mutableStateListOf<DashboardEvent>() }
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
    var faceRunning by remember { mutableStateOf(true) }
    var speakerRunning by remember { mutableStateOf(true) }
    var confirmReset by remember { mutableStateOf(false) }
    var audioPermissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        audioPermissionGranted = it
        if (it && speakerRunning) speakerRecorder.start()
    }

    LaunchedEffect(identity.observationSequence, permissionGranted, faceRunning) {
        if (!permissionGranted || !faceRunning) return@LaunchedEffect
        val elapsed = SystemClock.elapsedRealtime() - identificationStartedAt
        identity.results.forEach { (trackId, result) ->
            faceHistory += FaceDashboardRow(
                elapsedMillis = elapsed,
                label = displayFaceLabel(trackId),
                id = displayIdentityId(result.anonymousId, "FP"),
                similarity = result.bestExistingScore,
                processingMillis = identity.pipelineMetrics?.totalMillis,
                isNew = result.isNewCluster,
            )
            dashboardEvents += DashboardEvent(
                elapsed,
                "[${elapsedLabel(elapsed)}] FACE sim=${result.bestExistingScore?.let { String.format(Locale.US, "%.3f", it) } ?: "—"} → 顔ID ${displayIdentityId(result.anonymousId, "FP")}",
            )
        }
        while (faceHistory.size > DASHBOARD_HISTORY_LIMIT) faceHistory.removeAt(0)
        while (dashboardEvents.size > DASHBOARD_HISTORY_LIMIT) dashboardEvents.removeAt(0)
    }

    LaunchedEffect(speakerIdentity.observationSequence, audioPermissionGranted, speakerRunning) {
        if (!audioPermissionGranted || !speakerRunning) return@LaunchedEffect
        val elapsed = SystemClock.elapsedRealtime() - identificationStartedAt
        val timing = speakerIdentity.stageTimings
        val resultEntries = speakerIdentity.results.values
        resultEntries.forEach { result ->
            val overlap = speakerIdentity.overlapRatio
            val relation = when {
                overlap != null && overlap >= settings.speakerOverlapDisplayThreshold -> "窓内重複あり（率 ${String.format(Locale.US, "%.2f", overlap)}）"
                overlap != null -> "窓内重複なし（率 ${String.format(Locale.US, "%.2f", overlap)}）"
                else -> "状態未検出"
            }
            if (result.anonymousId != "unknown") {
                speakerHistory += SpeakerDashboardRow(
                    elapsedMillis = elapsed,
                    id = displayIdentityId(result.anonymousId, "SP"),
                    similarity = result.bestExistingScore,
                    processingMillis = timing?.totalMillis?.toDouble(),
                    utteranceSeconds = speakerRecorderState.lastUtterance?.durationMillis?.div(1_000f),
                    isNew = result.isNewCluster,
                )
            }
            dashboardEvents += DashboardEvent(
                elapsed,
                "[${elapsedLabel(elapsed)}] VOICE sim=${result.bestExistingScore?.let { String.format(Locale.US, "%.3f", it) } ?: "—"} → 話者ID ${displayIdentityId(result.anonymousId, "SP")}（$relation）",
            )
        }
        if (resultEntries.isEmpty() &&
            (speakerIdentity.activityState.name.contains("MULTIPLE") ||
                speakerIdentity.activityState.name.contains("OVERLAPPED") ||
                speakerIdentity.error != null)
        ) {
            dashboardEvents += DashboardEvent(
                elapsed,
                "[${elapsedLabel(elapsed)}] VOICE → ${if (speakerIdentity.error != null) "識別失敗" else "複数話者（対応なし）"}",
            )
        }
        while (speakerHistory.size > DASHBOARD_HISTORY_LIMIT) speakerHistory.removeAt(0)
        while (dashboardEvents.size > DASHBOARD_HISTORY_LIMIT) dashboardEvents.removeAt(0)
    }

    LaunchedEffect(permissionGranted, lifecycleOwner, faceRunning) {
        if (permissionGranted && faceRunning) controller.bind(lifecycleOwner) else controller.unbind()
    }
    LaunchedEffect(audioPermissionGranted, speakerRunning) {
        if (audioPermissionGranted && speakerRunning) speakerRecorder.start() else speakerRecorder.stop()
    }
    DisposableEffect(controller, speakerRecorder, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) speakerRecorder.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speakerRecorder.close()
            speakerCoordinator.close()
            coordinator.close()
            if (controller.closeAndAwaitAnalysis()) detector.close()
        }
    }

    // Pepper is a fixed 1280x800px canvas at 213dpi; keep the HTML-sized layout in px.
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
    Scaffold(containerColor = HtmlCanvas) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(HtmlCanvas),
        ) {
            val viewportWidth = maxWidth
            val appWidth = if (viewportWidth < 960.dp) 960.dp else viewportWidth.coerceAtMost(1440.dp)
            Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                Spacer(Modifier.width(((viewportWidth - appWidth) / 2f).coerceAtLeast(0.dp)))
                Column(Modifier.width(appWidth).fillMaxHeight().background(HtmlCanvas)) {
                    HtmlToolbar(
                        elapsed = elapsedLabel(SystemClock.elapsedRealtime() - identificationStartedAt),
                        onOpenSettings = onOpenSettings,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).padding(8.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            DashboardSummary(
                                faceHistory = faceHistory,
                                speakerHistory = speakerHistory,
                                faceTotalMillis = identity.pipelineMetrics?.totalMillis,
                                speakerTotalMillis = speakerIdentity.stageTimings?.totalMillis?.toDouble(),
                                faceThreshold = settings.faceClusterJoinThreshold,
                                speakerThreshold = settings.speakerClusterJoinThreshold,
                                overlapRatio = speakerIdentity.overlapRatio,
                                overlapThreshold = settings.speakerOverlapDisplayThreshold,
                                multiSpeaker = speakerIdentity.activityState.name.contains("MULTIPLE") ||
                                    speakerIdentity.activityState.name.contains("OVERLAPPED"),
                                modifier = Modifier.fillMaxWidth().height(104.dp),
                            )
                            DashboardResultPanels(
                                faceHistory = faceHistory,
                                speakerHistory = speakerHistory,
                                faceThreshold = settings.faceClusterJoinThreshold,
                                speakerThreshold = settings.speakerClusterJoinThreshold,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                            HtmlControlBar(
                                faceRunning = faceRunning,
                                speakerRunning = speakerRunning,
                                onToggleFace = { faceRunning = !faceRunning },
                                onToggleSpeaker = {
                                    val nextSpeakerRunning = !speakerRunning
                                    speakerRunning = nextSpeakerRunning
                                    if (nextSpeakerRunning && !audioPermissionGranted) {
                                        audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                onStop = { faceRunning = false; speakerRunning = false },
                                onReset = { confirmReset = true },
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.width(368.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                        ) {
                            HtmlCameraPreviewPanel(
                                permissionGranted = permissionGranted,
                                previewFrame = previewFrame,
                                detection = detection,
                                identity = identity,
                                settings = settings,
                                onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
                            )
                            HtmlPanel(modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
                                AudioStatusPreview(
                                    running = speakerRunning,
                                    recorderState = speakerRecorderState,
                                    identity = speakerIdentity,
                                )
                            }
                            HtmlPanel(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    Text("端末負荷", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    UnifiedPerformancePreview()
                                }
                            }
                            // Keep the log readable on the compact phone layout while allowing
                            // the user to inspect more recent entries without leaving the screen.
                            DashboardEventLog(events = dashboardEvents, modifier = Modifier.fillMaxWidth().height(208.dp))
                        }
                    }
                }
                Spacer(Modifier.width(((viewportWidth - appWidth) / 2f).coerceAtLeast(0.dp)))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(48.dp)
                    .height(48.dp)
                    .clickable(onClick = onExit)
                    .testTag("hidden-exit-button"),
            )
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
private fun DashboardSummary(
    faceHistory: List<FaceDashboardRow>,
    speakerHistory: List<SpeakerDashboardRow>,
    faceTotalMillis: Double?,
    speakerTotalMillis: Double?,
    faceThreshold: Float,
    speakerThreshold: Float,
    overlapRatio: Float?,
    overlapThreshold: Float,
    multiSpeaker: Boolean,
    modifier: Modifier = Modifier,
) {
    val faceAverage = faceHistory.mapNotNull { it.similarity?.toDouble() }.averageOrNull()
    val speakerAverage = speakerHistory.mapNotNull { it.similarity?.toDouble() }.averageOrNull()
    val newFaceCount = faceHistory.filter { it.isNew }.map { it.id }.distinct().size
    val newSpeakerCount = speakerHistory.filter { it.isNew }.map { it.id }.distinct().size
    val overlapText = when {
        multiSpeaker -> "重複あり"
        overlapRatio == null -> "未検出"
        overlapRatio >= overlapThreshold -> "重複あり"
        else -> "重複なし"
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        SummaryCard("顔 平均類似度", faceAverage?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "閾値 ${String.format(Locale.US, "%.2f", faceThreshold)}", HtmlBlue, Modifier.weight(1f))
        SummaryCard("声 平均類似度", speakerAverage?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "閾値 ${String.format(Locale.US, "%.2f", speakerThreshold)}", HtmlPurple, Modifier.weight(1f))
        SummaryCard("話者窓内重複率", overlapRatio?.let { String.format(Locale.US, "%.2f", it) } ?: "—", "$overlapText ／ 閾値 ${String.format(Locale.US, "%.2f", overlapThreshold)}", HtmlPurple, Modifier.weight(1f))
        SummaryCard("新規ID件数", (newFaceCount + newSpeakerCount).takeIf { it > 0 }?.toString() ?: "—", "顔 ${newFaceCount.takeIf { it > 0 } ?: "—"} ／ 話者 ${newSpeakerCount.takeIf { it > 0 } ?: "—"}", HtmlGreen, Modifier.weight(1f))
        SummaryCard("顔 平均処理時間", faceTotalMillis?.let { String.format(Locale.US, "%.1f ms", it) } ?: "—", "顔識別", HtmlBlue, Modifier.weight(1f), valueFontSize = 19.sp)
        SummaryCard("声 平均処理時間", speakerTotalMillis?.let { String.format(Locale.US, "%.1f ms", it) } ?: "—", "話者識別", HtmlPurple, Modifier.weight(1f), valueFontSize = 19.sp)
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier,
    valueFontSize: TextUnit = 25.sp,
) {
    Card(
        modifier = modifier.border(1.dp, HtmlLine, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HtmlSurface, contentColor = HtmlInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
            Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 10.sp, color = HtmlMutedInk)
                Text(
                    value,
                    fontSize = valueFontSize,
                    lineHeight = valueFontSize,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold,
                    color = HtmlInk,
                )
                Text(detail, fontSize = 9.sp, color = HtmlMutedInk)
            }
        }
    }
}

@Composable
private fun DashboardResultPanels(
    faceHistory: List<FaceDashboardRow>,
    speakerHistory: List<SpeakerDashboardRow>,
    faceThreshold: Float,
    speakerThreshold: Float,
    modifier: Modifier = Modifier,
) {
    val faceAverage = faceHistory.mapNotNull { it.similarity?.toDouble() }.averageOrNull()
    val speakerAverage = speakerHistory.mapNotNull { it.similarity?.toDouble() }.averageOrNull()
    val faceMillis = faceHistory.mapNotNull { it.processingMillis }.averageOrNull()
    val speakerMillis = speakerHistory.mapNotNull { it.processingMillis }.averageOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        DashboardResultCard(
            title = "顔識別",
            marker = HtmlBlue,
            threshold = faceThreshold,
            headers = listOf("時刻", "ID", "類似度", "処理 ms"),
            rows = faceHistory.takeLast(DASHBOARD_HISTORY_LIMIT).asReversed().map {
                listOf(elapsedLabel(it.elapsedMillis), it.id, it.similarity?.let { value -> String.format(Locale.US, "%.2f", value) } ?: "—", it.processingMillis?.let { value -> String.format(Locale.US, "%.1f", value) } ?: "—")
            },
            similarities = faceHistory.takeLast(DASHBOARD_HISTORY_LIMIT).asReversed().map { it.similarity },
            summaryAverage = faceAverage,
            summaryMillis = faceMillis,
            modifier = Modifier.weight(1f),
        )
        DashboardResultCard(
            title = "話者識別",
            marker = HtmlPurple,
            threshold = speakerThreshold,
            headers = listOf("時刻", "ID", "既存ID類似度", "処理 ms", "発話 s"),
            rows = speakerHistory.takeLast(DASHBOARD_HISTORY_LIMIT).asReversed().map {
                listOf(
                    elapsedLabel(it.elapsedMillis),
                    it.id,
                    it.similarity?.let { value -> String.format(Locale.US, "%.2f", value) } ?: "—",
                    it.processingMillis?.let { value -> String.format(Locale.US, "%.1f", value) } ?: "—",
                    it.utteranceSeconds?.let { value -> String.format(Locale.US, "%.1f", value) } ?: "—",
                )
            },
            similarities = speakerHistory.takeLast(DASHBOARD_HISTORY_LIMIT).asReversed().map { it.similarity },
            summaryAverage = speakerAverage,
            summaryMillis = speakerMillis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DashboardResultCard(
    title: String,
    marker: Color,
    threshold: Float,
    headers: List<String>,
    rows: List<List<String>>,
    similarities: List<Float?>,
    summaryAverage: Double?,
    summaryMillis: Double?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.heightIn(min = 190.dp).border(1.dp, HtmlLine, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HtmlSurface, contentColor = HtmlInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(HtmlMutedSurface).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Box(Modifier.width(8.dp).height(8.dp).background(marker))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp).weight(1f), color = HtmlInk)
                Text(
                    "平均 ${summaryAverage?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} ／ ${summaryMillis?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} ms ／ 閾値 ${String.format(Locale.US, "%.2f", threshold)}",
                    fontSize = 10.sp,
                    color = HtmlMutedInk,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 12.dp)) {
                val nearUpperBound = (threshold + 0.09f).coerceAtMost(1f)
                val highConfidenceBound = (threshold + 0.10f).coerceAtMost(1f)
                Text("色分け", fontSize = 9.sp, color = HtmlMutedInk)
                Text("未達 <${String.format(Locale.US, "%.2f", threshold)}", color = HtmlRed, fontSize = 9.sp)
                Text(
                    "達成 ${String.format(Locale.US, "%.2f", threshold)}–${String.format(Locale.US, "%.2f", nearUpperBound)}",
                    color = HtmlOrange,
                    fontSize = 9.sp,
                )
                Text(
                    "高達成 ≥${String.format(Locale.US, "%.2f", highConfidenceBound)}",
                    color = HtmlGreen,
                    fontSize = 9.sp,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                headers.forEach { header -> Text(header, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = HtmlMutedInk, modifier = Modifier.weight(1f), textAlign = TextAlign.End) }
            }
            HorizontalDivider(color = HtmlLine)
            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                if (rows.isEmpty()) {
                    item { Text("未測定", modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center, color = HtmlMutedInk, fontSize = 12.sp) }
                } else {
                    itemsIndexed(rows) { index, row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { columnIndex, value ->
                                val color = if (columnIndex == 2) similarityColor(similarities.getOrNull(index), threshold) else HtmlInk
                                Text(value, color = color, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                        }
                        HorizontalDivider(color = HtmlLineSoft)
                    }
                }
            }
        }
    }
}

private fun similarityColor(value: Float?, threshold: Float): Color = when {
    value == null -> Color.Unspecified
    value < threshold -> Color(0xFFC62828)
    value < threshold + 0.10f -> Color(0xFFEF6C00)
    else -> Color(0xFF2E7D32)
}

@Composable
private fun DashboardEventLog(events: List<DashboardEvent>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.heightIn(min = 120.dp).border(1.dp, HtmlLine, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = HtmlSurface, contentColor = HtmlInk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("イベントログ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = HtmlInk)
            LazyColumn(modifier = Modifier.height(170.dp)) {
                if (events.isEmpty()) item { Text("識別ログ待機中", color = HtmlMutedInk, fontSize = 10.sp) }
                else itemsIndexed(events.asReversed()) { _, event -> Text(event.text, fontSize = 10.sp, color = HtmlMutedInk, modifier = Modifier.padding(vertical = 1.dp)) }
            }
        }
    }
}

private fun Iterable<Double>.averageOrNull(): Double? {
    val values = toList()
    return values.takeIf { it.isNotEmpty() }?.average()
}

@Composable
private fun UnifiedPerformancePreview() {
    data class Metric(val id: String, val group: String, val label: String, val value: Double?)
    val context = LocalContext.current
    val monitor = remember { AndroidDeviceLoadMonitor(context) }
    val deviceLoad by monitor.state.collectAsState()
    val histories = remember { mutableStateMapOf<String, List<Double>>() }
    val metrics = listOf(
        Metric("cpu", "端末負荷", "CPU（%）", deviceLoad.appCpuAllCoresPercent?.toDouble()),
        Metric("memory", "端末負荷", "メモリ（MB）", deviceLoad.appPssBytes?.div(1024.0 * 1024.0)),
    )
    LaunchedEffect(monitor) { monitor.start() }
    LaunchedEffect(metrics.map { it.id to it.value }) {
        metrics.forEach { metric ->
            metric.value?.takeIf { it.isFinite() }?.let { value ->
                histories[metric.id] = (histories[metric.id].orEmpty() + value).takeLast(300)
            }
        }
    }
    DisposableEffect(monitor) { onDispose { monitor.close() } }
    val averaged = metrics.map { metric ->
        metric to (histories[metric.id]?.average()?.let { String.format(Locale.US, "%.1f", it) } ?: "未測定")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HtmlLine, RectangleShape),
    ) {
        averaged.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { columnIndex, (metric, value) ->
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("${metric.group} ${metric.label}", fontSize = 9.sp, color = HtmlMutedInk)
                        Text(value, fontSize = 13.sp, color = HtmlInk, fontWeight = FontWeight.SemiBold)
                    }
                    if (columnIndex == 0 && row.size == 2) {
                        Box(Modifier.width(1.dp).height(42.dp).background(HtmlLineSoft))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex < averaged.chunked(2).lastIndex) HorizontalDivider(color = HtmlLineSoft)
        }
    }
}

@Composable
private fun AudioStatusPreview(
    running: Boolean,
    recorderState: AudioRecordingState,
    identity: SpeakerIdentityUiState,
    modifier: Modifier = Modifier,
) {
    val recording = running && recorderState.status == AudioRecordingStatus.RECORDING
    fun shortSpeakerCode(value: String?, prefix: String): String = value
        ?.substringAfterLast('-')
        ?.toIntOrNull()
        ?.let { "$prefix${it.toString().padStart(3, '0')}" }
        ?: (value ?: "-")
    val currentTrack = identity.localTracks
        .filter { it.state == LocalSpeakerTrackState.ACTIVE }
        .maxByOrNull { it.lastSeenSample }
    val multipleSpeakerResult = (
        identity.activityState == com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS ||
            identity.activityState == com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState.OVERLAPPED_SPEECH
        ) && (!recorderState.speechActive || identity.processing)
    val currentSpeaker = when {
        !recording -> "停止"
        multipleSpeakerResult -> "複数話者"
        !recorderState.speechActive -> "無音"
        else -> shortSpeakerCode(currentTrack?.localSpeakerId, "SL")
            .takeIf { it != "-" }
            ?: "未検出"
    }
    val currentSpeakerColor = when {
        currentSpeaker == "無音" || currentSpeaker == "停止" -> Color(0xFF8A8A8A)
        currentSpeaker.startsWith("SL") -> speakerTimelineColor(currentSpeaker)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val timelineLevels = remember { mutableStateListOf<Float>() }
    val timelineLabels = remember { mutableStateListOf<String>() }
    val currentAmplitude = ((recorderState.levelDbFs + 90f) / 90f).coerceIn(0f, 1f)
    val timelineLabel = when {
        currentSpeaker == "複数話者" -> "MULTIPLE"
        currentSpeaker.startsWith("SL") -> currentSpeaker
        currentSpeaker == "未検出" -> "未検出"
        else -> "無音"
    }
    LaunchedEffect(recording, recorderState.levelDbFs, timelineLabel) {
        if (!recording) {
            timelineLevels.clear()
            timelineLabels.clear()
        } else {
            timelineLevels += currentAmplitude
            timelineLabels += timelineLabel
            while (timelineLevels.size > 120) timelineLevels.removeAt(0)
            while (timelineLabels.size > 120) timelineLabels.removeAt(0)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.fillMaxWidth().padding(8.dp),
    ) {
        Text("発話タイムライン", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("識別状態: ${if (recording) "識別中" else "待機中"}", style = MaterialTheme.typography.labelSmall)
            Text("発話状態: ${if (recorderState.speechActive) "発話検出" else "待機"}", style = MaterialTheme.typography.labelSmall)
        Text("マイク入力強度: ${if (recording) "%.1f dB".format(Locale.US, abs(recorderState.levelDbFs)) else "停止"}", style = MaterialTheme.typography.labelSmall)
        }
        Text("現在の話者", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (currentSpeaker == "複数話者") {
                Text(
                    "話者ラベル  複数話者",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Text(
                    "話者ラベル  $currentSpeaker",
                    color = currentSpeakerColor,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text("話者照合  ${if (identity.processing) "処理中" else if (recording) "待機" else "停止"}", style = MaterialTheme.typography.labelSmall)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            val hasSpeechTimeline = recording
            if (!hasSpeechTimeline) {
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.BottomStart),
                        color = HtmlBlue,
                    )
                    Text(
                        "停止",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barCount = (maxWidth.value / 5f).toInt().coerceIn(12, 120)
            val levels = if (timelineLevels.isEmpty()) List(barCount) { 0f } else {
                timelineLevels.takeLast(barCount).let { samples ->
                    List(barCount) { index -> samples.getOrNull(index - (barCount - samples.size)) ?: 0f }
                }
            }
            val labels = if (timelineLabels.isEmpty()) {
                List(barCount) { "無音" }
            } else {
                timelineLabels.takeLast(barCount).let { samples ->
                    List(barCount) { index -> samples.getOrNull(index - (barCount - samples.size)) ?: "無音" }
                }
            }
            val speakerLabels = labels.distinct()
            val colorByLabel = speakerLabels.associateWith { label ->
                when (label) {
                    "MULTIPLE" -> Color.Black
                    "無音" -> Color(0xFFB0B0B0)
                    "未検出" -> HtmlMutedInk
                    else -> speakerTimelineColor(label)
                }
            }.toMap()
            val neutralColor = Color(0xFFB0B0B0)
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = HtmlBlue,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                levels.forEachIndexed { index, level ->
                    val height = (2f + level * 32f).dp
                    val label = labels.getOrElse(index) { "無音" }
                    Box(Modifier.weight(1f).height(56.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 1.dp)
                                .height(height)
                                .align(Alignment.BottomCenter)
                                .background(colorByLabel[label] ?: neutralColor),
                        )
                    }
                }
                }
            }
            }
            }
        }
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
                    DataTableHeaderGroup("端末負荷", 2),
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
