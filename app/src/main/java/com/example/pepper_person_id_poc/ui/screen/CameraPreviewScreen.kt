package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.application.face.FaceIdentityCoordinator
import com.example.pepper_person_id_poc.application.face.FaceIdentityUiState
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.FaceIdentityStatus
import com.example.pepper_person_id_poc.domain.face.FacePoseRanges
import com.example.pepper_person_id_poc.domain.face.RegistrationPose
import com.example.pepper_person_id_poc.infrastructure.camera.CameraStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.FaceReidentificationRetail0095EmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.SFaceEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.YuNetFaceDetector
import java.util.Locale

enum class FaceCameraMode {
    Registration,
    Identification,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    mode: FaceCameraMode,
    settings: PocSettings,
    personRepository: PersonRepository,
    benchmarkLogger: BenchmarkLogger,
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val embeddingEngine: FaceEmbeddingEngine = remember(settings.faceModel) {
        when (settings.faceModel) {
            FaceModelOption.SFACE_2021DEC -> SFaceEmbeddingEngine(context)
            FaceModelOption.FACE_REIDENTIFICATION_RETAIL_0095 ->
                FaceReidentificationRetail0095EmbeddingEngine(context)
        }
    }
    val coordinator = remember(mode, personRepository, settings, embeddingEngine.modelName) {
        FaceIdentityCoordinator(
            personRepository = personRepository,
            faceIdentifier = FaceIdentifier(),
            settings = settings,
            faceModelName = embeddingEngine.modelName,
            realTimeIdentificationEnabled = mode == FaceCameraMode.Identification,
            onBenchmarkEvent = benchmarkLogger::append,
            onDeleteAllBenchmarkEvents = benchmarkLogger::deleteAll,
        )
    }
    val faceDetector = remember(mode, coordinator, embeddingEngine.modelName) {
        YuNetFaceDetector(
            context = context,
            embeddingEngine = embeddingEngine,
            analysisIntervalMillis = if (mode == FaceCameraMode.Registration) {
                settings.faceRegistrationAnalysisIntervalMillis
            } else {
                settings.faceIdentificationAnalysisIntervalMillis
            },
            estimateHeadPose = mode == FaceCameraMode.Registration,
            onPoseObservations = coordinator::onFaceAnalysis,
            onFeatureObservations = coordinator::onFeatureObservations,
            onEmbeddingReady = coordinator::reportEmbeddingReady,
            onEmbeddingError = coordinator::reportEmbeddingError,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val controller = remember(faceDetector) { CameraXPreviewController(context, frameProcessor = faceDetector) }
    val cameraState by controller.state.collectAsState()
    val surfaceRequest by controller.surfaceRequest.collectAsState()
    val faceSnapshot by faceDetector.snapshot.collectAsState()
    val identityState by coordinator.state.collectAsState()
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(permissionGranted, lifecycleOwner) {
        if (permissionGranted) controller.bind(lifecycleOwner) else controller.unbind()
    }
    DisposableEffect(controller) {
        onDispose {
            coordinator.cancelFaceRegistration()
            controller.close()
            faceDetector.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (mode == FaceCameraMode.Registration) "人物登録 - 顔" else "顔識別") },
                navigationIcon = {
                    Button(onClick = onBackToSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("← 設定")
                    }
                },
            )
        },
    ) { innerPadding ->
        val previewPane: @Composable (Modifier) -> Unit = { paneModifier ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = paneModifier.background(Color.Black),
            ) {
                val request = surfaceRequest
                if (request != null) {
                    CameraXViewfinder(
                        surfaceRequest = request,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FaceDetectionOverlay(faceSnapshot, identityState, settings, settings.debugMode)
                } else {
                    Text(
                        if (permissionGranted) "カメラを開始しています" else "カメラ権限が必要です",
                        color = Color.White,
                    )
                }
            }
        }
        val controlPane: @Composable (Modifier) -> Unit = { paneModifier ->
            Card(modifier = paneModifier) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                ) {
                    CameraAndModelStatus(cameraState, faceSnapshot)
                    Text(
                        "顔特徴量: ${if (identityState.embeddingModelReady) {
                            "${embeddingEngine.modelName} 準備完了"
                        } else if (identityState.error != null) {
                            "${embeddingEngine.modelName} 初期化失敗"
                        } else {
                            "${embeddingEngine.modelName} 初期化中"
                        }}",
                    )
                    if (!permissionGranted) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("カメラ権限を許可")
                        }
                    } else if (cameraState.status == CameraStatus.Stopped || cameraState.status == CameraStatus.Error) {
                        Button(onClick = { controller.bind(lifecycleOwner) }) { Text("カメラを再開") }
                    }
                    if (mode == FaceCameraMode.Registration) {
                        RegistrationPanel(coordinator, identityState, settings)
                    } else {
                        IdentificationPanel(coordinator, identityState, settings.debugMode)
                    }
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(12.dp)) {
            if (maxWidth < 700.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    previewPane(Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    controlPane(Modifier.fillMaxWidth().weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    previewPane(Modifier.weight(1f).fillMaxHeight())
                    controlPane(Modifier.width(380.dp).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun CameraAndModelStatus(
    cameraState: com.example.pepper_person_id_poc.infrastructure.camera.CameraPreviewState,
    faceSnapshot: FaceDetectionSnapshot,
) {
    Text("カメラ: ${cameraState.status}")
    Text("カメラ入力: ${cameraState.frameCount} frames / ${cameraState.resolution ?: "取得中"}")
    Text("カメラ入力FPS: ${cameraState.inputFramesPerSecond.asFps()}")
    Text("顔解析FPS: ${faceSnapshot.analysisFramesPerSecond.asFps()}")
    Text("顔状態: ${faceSnapshot.status} / ${faceSnapshot.faces.size}人")
    Text("顔検出: ${faceSnapshot.processingTimeMillis ?: "-"} ms / ${faceSnapshot.modelName}")
    cameraState.error?.let { Text("カメラエラー: $it", color = MaterialTheme.colorScheme.error) }
    faceSnapshot.error?.let { Text("顔検出エラー: $it", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun RegistrationPanel(
    coordinator: FaceIdentityCoordinator,
    state: FaceIdentityUiState,
    settings: PocSettings,
) {
    var personId by rememberSaveable { mutableStateOf("person1") }
    var displayName by rememberSaveable { mutableStateOf("人物A") }
    var deleteConfirmationVisible by rememberSaveable { mutableStateOf(false) }

    Text("正面・左・右の3姿勢登録", style = MaterialTheme.typography.titleMedium)
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
        onClick = { coordinator.requestFaceRegistration(personId, displayName) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("3姿勢の顔登録を開始") }
    if (state.registrationTarget != null) {
        Button(onClick = coordinator::cancelFaceRegistration, modifier = Modifier.fillMaxWidth()) {
            Text("顔登録をキャンセル")
        }
    }
    state.registrationTarget?.let { target ->
        val progress = (state.poseProgressMillis * 100L / settings.facePoseStableDurationMillis).coerceIn(0L, 100L)
        val instruction = settings.facePoseRanges().guidance(target, state.currentHeadPose)
        Text(instruction, style = MaterialTheme.typography.headlineSmall)
        Text("現在の目標: ${target.displayName} / 維持 $progress%")
        Text("完了: ${state.completedRegistrationPoses.joinToString { it.displayName }.ifEmpty { "なし" }}")
    }
    state.currentHeadPose?.let { pose ->
        val direction = when {
            pose.yawDegrees <= -settings.faceSideMinimumYawDegrees -> "左"
            pose.yawDegrees >= settings.faceSideMinimumYawDegrees -> "右"
            else -> "正面"
        }
        Text("現在判定: $direction（左=Yaw負、右=Yaw正）")
        Text("現在: Yaw ${pose.yawDegrees.asAngle()} / Pitch ${pose.pitchDegrees.asAngle()} / Roll ${pose.rollDegrees.asAngle()}")
    }
    Text("検出顔数: ${state.visibleFaceCount}")
    state.registrationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Text("登録人物 (${state.profiles.size}人)", style = MaterialTheme.typography.titleMedium)
    state.profiles.forEach { profile ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${profile.displayName} / ${profile.personId.value} / 顔 ${profile.faceSampleCount} / 声 ${profile.speakerSampleCount}",
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { coordinator.deletePerson(profile.personId) }) { Text("人物削除") }
        }
    }
    if (!deleteConfirmationVisible) {
        Button(
            onClick = { deleteConfirmationVisible = true },
            enabled = state.profiles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("登録データをすべて削除") }
    } else {
        Text("顔・声の特徴量と評価ログをすべて削除します。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { deleteConfirmationVisible = false }) { Text("キャンセル") }
            Button(onClick = { coordinator.deleteAllRegistrations(); deleteConfirmationVisible = false }) {
                Text("削除を確定")
            }
        }
    }
}

@Composable
private fun IdentificationPanel(
    coordinator: FaceIdentityCoordinator,
    state: FaceIdentityUiState,
    debugMode: Boolean,
) {
    Text("リアルタイム複数人顔識別", style = MaterialTheme.typography.titleMedium)
    Text("登録人物: ${state.profiles.size}人 / 検出顔数: ${state.visibleFaceCount}")
    Text("短期trackIdで顔を追跡し、登録人物は名前、未登録人物はセッション内anonymousIdで再識別します")
    Text("未登録人物クラスタ: ${state.anonymousClusterCount}人（画面を閉じると破棄）")
    Button(
        onClick = coordinator::resetAnonymousSession,
        enabled = state.anonymousClusterCount > 0,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("未登録人物の一時IDをリセット") }
    state.identificationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (state.results.isEmpty()) Text("顔を検出すると自動で識別します")
    state.results.forEach { result ->
        val title = if (result.status == FaceIdentityStatus.IDENTIFIED) {
            "${result.displayName} / ${result.personId?.value}"
        } else {
            "Unknown"
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            "Best ${result.score.asScore()} / Second ${result.secondScore.asScore()} / " +
                "Margin ${result.margin.asScore()} (min ${result.minimumMargin.asScore()}) / " +
                "threshold ${result.threshold.asScore()} / ${result.processingTimeMillis} ms",
        )
        if (debugMode && result.status == FaceIdentityStatus.UNKNOWN) {
            Text("Best candidate: ${result.bestCandidatePersonId?.value ?: "なし"}")
        }
        state.anonymousResults.firstOrNull { it.trackId == result.trackId }?.let { anonymous ->
            Text(
                "${anonymous.anonymousId} / re-id ${anonymous.score.asScore()} / " +
                    "samples ${anonymous.clusterSampleCount}",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
    state.error?.let { Text("顔特徴量エラー: $it", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun FaceDetectionOverlay(
    detectionSnapshot: FaceDetectionSnapshot,
    identityState: FaceIdentityUiState,
    settings: PocSettings,
    debugMode: Boolean,
) {
    val identityByTrackId = identityState.results.associateBy { it.trackId }
    val anonymousByTrackId = identityState.anonymousResults.associateBy { it.trackId }
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            detectionSnapshot.faces.forEach { face ->
                val identity = identityByTrackId[face.trackId]
                val anonymous = anonymousByTrackId[face.trackId]
                val identified = identity?.status == FaceIdentityStatus.IDENTIFIED
                val color = when {
                    identified -> Color.Green
                    anonymous != null -> Color.Cyan
                    else -> Color.Yellow
                }
                val left = (1f - face.boundingBox.right) * size.width
                val top = face.boundingBox.top * size.height
                val width = face.boundingBox.width * size.width
                val height = face.boundingBox.height * size.height
                drawRect(color, Offset(left, top), Size(width, height), style = Stroke(width = 4.dp.toPx()))
                face.landmarks.forEachIndexed { index, landmark ->
                    val point = Offset((1f - landmark.x) * size.width, landmark.y * size.height)
                    drawCircle(
                        color = Color.Cyan,
                        radius = 5.dp.toPx(),
                        center = point,
                    )
                    val landmarkPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.CYAN
                        textSize = 12.dp.toPx()
                        isAntiAlias = true
                        setShadowLayer(3.dp.toPx(), 1.dp.toPx(), 1.dp.toPx(), android.graphics.Color.BLACK)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "${index + 1}:${landmark.type.displayName}",
                        point.x + 7.dp.toPx(),
                        point.y - 7.dp.toPx(),
                        landmarkPaint,
                    )
                }
                val identityLabel = when {
                    identified -> "${identity?.displayName}  ${identity?.personId?.value}"
                    anonymous != null -> "Unknown / ${anonymous.anonymousId}"
                    identity != null -> "Unknown"
                    else -> "Face"
                }
                val debugLabel = if (debugMode && identity?.bestCandidatePersonId != null) {
                    " / best=${identity.bestCandidatePersonId.value}"
                } else ""
                val lines = listOf(
                    identityLabel,
                    face.trackId,
                    "Detection ${face.detectionScore.asScore()}",
                    "Landmarks ${face.landmarks.size}/5",
                    "Identity ${identity?.score.asScore()} / margin ${identity?.margin.asScore()}$debugLabel",
                )
                val paint = android.graphics.Paint().apply {
                    this.color = if (identified) android.graphics.Color.GREEN else android.graphics.Color.YELLOW
                    textSize = 16.dp.toPx()
                    isAntiAlias = true
                    setShadowLayer(3.dp.toPx(), 1.dp.toPx(), 1.dp.toPx(), android.graphics.Color.BLACK)
                }
                val lineHeight = 18.dp.toPx()
                val firstBaseline = (top - (lines.size - 1) * lineHeight - 8.dp.toPx()).coerceAtLeast(lineHeight)
                lines.forEachIndexed { index, line ->
                    drawContext.canvas.nativeCanvas.drawText(line, left, firstBaseline + index * lineHeight, paint)
                }
            }
        }
        identityState.registrationTarget?.let { target ->
            Text(
                settings.facePoseRanges().guidance(target, identityState.currentHeadPose),
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private fun PocSettings.facePoseRanges() = FacePoseRanges(
    frontYawDegrees = faceFrontYawDegrees,
    frontPitchDegrees = faceFrontPitchDegrees,
    sideMinimumYawDegrees = faceSideMinimumYawDegrees,
    sideMaximumYawDegrees = faceSideMaximumYawDegrees,
)

private fun Float?.asScore(): String = this?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
private fun Float.asFps(): String = String.format(Locale.US, "%.2f fps", this)
private fun Float.asAngle(): String = String.format(Locale.US, "%.1f°", this)
