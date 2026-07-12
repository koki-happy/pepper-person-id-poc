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
import com.example.pepper_person_id_poc.application.face.AnonymousFaceCoordinator
import com.example.pepper_person_id_poc.application.face.AnonymousFaceUiState
import com.example.pepper_person_id_poc.application.face.FaceIdentityCoordinator
import com.example.pepper_person_id_poc.application.face.FaceIdentityUiState
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.domain.face.AnonymousFaceClusterer
import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.FaceIdentityStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController
import com.example.pepper_person_id_poc.infrastructure.face.SFaceEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.FaceReidentificationRetail0095EmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.YuNetFaceDetector
import java.util.Locale

enum class FaceCameraMode {
    Registration,
    Identification,
    AnonymousIdentification,
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
    val coordinator = remember(personRepository, settings.faceThreshold, embeddingEngine.modelName) {
        FaceIdentityCoordinator(
            personRepository = personRepository,
            faceIdentifier = FaceIdentifier(),
            faceThreshold = settings.faceThreshold,
            faceModelName = embeddingEngine.modelName,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val anonymousCoordinator = remember(settings.faceThreshold, embeddingEngine.modelName) {
        AnonymousFaceCoordinator(
            clusterer = AnonymousFaceClusterer(settings.faceThreshold),
            modelName = embeddingEngine.modelName,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val faceDetector = remember(mode, embeddingEngine.modelName) {
        YuNetFaceDetector(
            context = context,
            embeddingEngine = embeddingEngine,
            onFeatureObservations = if (mode == FaceCameraMode.AnonymousIdentification) {
                anonymousCoordinator::onFeatureObservations
            } else {
                coordinator::onFeatureObservations
            },
            onEmbeddingReady = if (mode == FaceCameraMode.AnonymousIdentification) {
                anonymousCoordinator::reportEmbeddingReady
            } else {
                coordinator::reportEmbeddingReady
            },
            onEmbeddingError = if (mode == FaceCameraMode.AnonymousIdentification) {
                anonymousCoordinator::reportEmbeddingError
            } else {
                coordinator::reportEmbeddingError
            },
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val controller = remember { CameraXPreviewController(context, frameProcessor = faceDetector) }
    val cameraState by controller.state.collectAsState()
    val surfaceRequest by controller.surfaceRequest.collectAsState()
    val faceSnapshot by faceDetector.snapshot.collectAsState()
    val identityState by coordinator.state.collectAsState()
    val anonymousState by anonymousCoordinator.state.collectAsState()
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
            controller.close()
            faceDetector.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (mode) {
                            FaceCameraMode.Registration -> "人物登録 - 顔"
                            FaceCameraMode.Identification -> "顔識別"
                            FaceCameraMode.AnonymousIdentification -> "未登録リアルタイム顔識別"
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .aspectRatio(4f / 3f)
                    .background(Color.Black),
            ) {
                val request = surfaceRequest
                if (request != null) {
                    CameraXViewfinder(
                        surfaceRequest = request,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    FaceDetectionOverlay(
                        detectionSnapshot = faceSnapshot,
                        identityState = identityState,
                        anonymousState = anonymousState,
                        anonymousMode = mode == FaceCameraMode.AnonymousIdentification,
                        debugMode = settings.debugMode,
                    )
                } else {
                    Text(
                        if (permissionGranted) "カメラを開始しています" else "カメラ権限が必要です",
                        color = Color.White,
                    )
                }
            }

            Card(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    CameraAndModelStatus(cameraState, faceSnapshot)
                    val embeddingReady = if (mode == FaceCameraMode.AnonymousIdentification) {
                        anonymousState.embeddingModelReady
                    } else {
                        identityState.embeddingModelReady
                    }
                    val embeddingError = if (mode == FaceCameraMode.AnonymousIdentification) {
                        anonymousState.error
                    } else {
                        identityState.error
                    }
                    Text(
                        "顔特徴量: ${if (
                            embeddingReady
                        ) "${embeddingEngine.modelName} 準備完了" else if (embeddingError != null) {
                            "${embeddingEngine.modelName} 初期化失敗"
                        } else "${embeddingEngine.modelName} 初期化中"}",
                    )
                    if (!permissionGranted) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("カメラ権限を許可")
                        }
                    } else if (cameraState.status == CameraStatus.Stopped ||
                        cameraState.status == CameraStatus.Error
                    ) {
                        Button(onClick = { controller.bind(lifecycleOwner) }) { Text("カメラを再開") }
                    }
                    when (mode) {
                        FaceCameraMode.Registration -> RegistrationPanel(coordinator, identityState)
                        FaceCameraMode.Identification -> IdentificationPanel(identityState, settings.debugMode)
                        FaceCameraMode.AnonymousIdentification -> AnonymousIdentificationPanel(
                            state = anonymousState,
                            onReset = anonymousCoordinator::resetSession,
                        )
                    }
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
    identityState: FaceIdentityUiState,
) {
    var personId by rememberSaveable { mutableStateOf("person1") }
    var displayName by rememberSaveable { mutableStateOf("人物A") }
    var deleteConfirmationVisible by rememberSaveable { mutableStateOf(false) }

    Text("顔サンプル登録", style = MaterialTheme.typography.titleMedium)
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
    ) {
        Text("正面を向いて顔を1サンプル登録")
    }
    identityState.registrationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    identityState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Text("登録人物 (${identityState.profiles.size}人)", style = MaterialTheme.typography.titleMedium)
    identityState.profiles.forEach { profile ->
        Text(
            "${profile.displayName} / ${profile.personId.value} / 顔 ${profile.faceSampleCount} / 声 ${profile.speakerSampleCount}",
        )
    }
    if (!deleteConfirmationVisible) {
        Button(
            onClick = { deleteConfirmationVisible = true },
            enabled = identityState.profiles.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("登録データをすべて削除")
        }
    } else {
        Text("顔・声の特徴量をすべて削除します。")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { deleteConfirmationVisible = false }) { Text("キャンセル") }
            Button(
                onClick = {
                    coordinator.deleteAllRegistrations()
                    deleteConfirmationVisible = false
                },
            ) {
                Text("削除を確定")
            }
        }
    }
}

@Composable
private fun IdentificationPanel(
    identityState: FaceIdentityUiState,
    debugMode: Boolean,
) {
    Text("識別結果", style = MaterialTheme.typography.titleMedium)
    Text("登録人物: ${identityState.profiles.size}人")
    if (identityState.results.isEmpty()) {
        Text("識別対象の顔がありません")
    }
    identityState.results.forEach { result ->
        val title = if (result.status == FaceIdentityStatus.IDENTIFIED) {
            "${result.displayName} / ${result.personId?.value}"
        } else {
            "Unknown"
        }
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            "${result.trackId} / Face ${result.score.asScore()} / threshold ${result.threshold.asScore()} / " +
                "${result.processingTimeMillis} ms",
        )
        if (debugMode && result.status == FaceIdentityStatus.UNKNOWN) {
            Text("Best candidate: ${result.bestCandidatePersonId?.value ?: "なし"}")
        }
    }
    identityState.error?.let { Text("顔特徴量エラー: $it", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun AnonymousIdentificationPanel(
    state: AnonymousFaceUiState,
    onReset: () -> Unit,
) {
    Text("セッション内の一時人物", style = MaterialTheme.typography.titleMedium)
    Text("識別済みクラスタ: ${state.clusterCount}人")
    Text("登録・永続保存は行いません。アプリ終了またはリセットで一時IDは破棄されます。")
    if (state.results.isEmpty()) {
        Text("識別対象の顔がありません")
    }
    state.results.forEach { result ->
        Text(result.anonymousId, style = MaterialTheme.typography.titleSmall)
        Text(
            "${result.trackId} / Similarity ${result.score.asScore()} / " +
                "threshold ${result.threshold.asScore()} / samples ${result.clusterSampleCount}",
        )
        if (result.isNewCluster) Text("新しい一時人物として追加")
    }
    Button(onClick = onReset, enabled = state.clusterCount > 0, modifier = Modifier.fillMaxWidth()) {
        Text("一時IDをリセット")
    }
    state.error?.let { Text("顔特徴量エラー: $it", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun FaceDetectionOverlay(
    detectionSnapshot: FaceDetectionSnapshot,
    identityState: FaceIdentityUiState,
    anonymousState: AnonymousFaceUiState,
    anonymousMode: Boolean,
    debugMode: Boolean,
) {
    val identityByTrackId = identityState.results.associateBy { it.trackId }
    val anonymousByTrackId = anonymousState.results.associateBy { it.trackId }
    Canvas(modifier = Modifier.fillMaxSize()) {
        detectionSnapshot.faces.forEach { face ->
            val identity = identityByTrackId[face.trackId]
            val anonymous = anonymousByTrackId[face.trackId]
            val identified = if (anonymousMode) {
                anonymous != null
            } else {
                identity?.status == FaceIdentityStatus.IDENTIFIED
            }
            val color = if (identified) Color.Green else Color.Yellow
            val mirroredLeft = 1f - face.boundingBox.right
            val left = mirroredLeft * size.width
            val top = face.boundingBox.top * size.height
            val width = face.boundingBox.width * size.width
            val height = face.boundingBox.height * size.height
            drawRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4.dp.toPx()),
            )
            val identityLabel = if (anonymousMode) {
                anonymous?.anonymousId ?: "Analyzing"
            } else if (identified) {
                "${identity?.displayName}  ${identity?.personId?.value}"
            } else {
                "Unknown"
            }
            val debugLabel = if (debugMode && !identified && identity?.bestCandidatePersonId != null) {
                " / best=${identity.bestCandidatePersonId.value}"
            } else {
                ""
            }
            val score = if (anonymousMode) anonymous?.score else identity?.score
            val threshold = if (anonymousMode) anonymous?.threshold else identity?.threshold
            val lines = if (anonymousMode) {
                listOf(
                    identityLabel,
                    face.trackId,
                    "Detection ${face.detectionScore.asScore()}",
                    "Similarity ${score.asScore()} / threshold ${threshold.asScore()}",
                )
            } else {
                listOf(
                    identityLabel,
                    face.trackId,
                    "Detection ${face.detectionScore.asScore()}",
                    "Identity similarity ${score.asScore()} / threshold ${threshold.asScore()}$debugLabel",
                )
            }
            val paint = android.graphics.Paint().apply {
                this.color = if (identified) android.graphics.Color.GREEN else android.graphics.Color.YELLOW
                textSize = 16.dp.toPx()
                isAntiAlias = true
                setShadowLayer(3.dp.toPx(), 1.dp.toPx(), 1.dp.toPx(), android.graphics.Color.BLACK)
            }
            val lineHeight = 18.dp.toPx()
            val firstBaseline = (top - (lines.size - 1) * lineHeight - 8.dp.toPx())
                .coerceAtLeast(lineHeight)
            lines.forEachIndexed { index, line ->
                drawContext.canvas.nativeCanvas.drawText(
                    line,
                    left,
                    firstBaseline + index * lineHeight,
                    paint,
                )
            }
        }
    }
}

private fun Float?.asScore(): String = this?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
private fun Float.asFps(): String = String.format(Locale.US, "%.2f fps", this)
