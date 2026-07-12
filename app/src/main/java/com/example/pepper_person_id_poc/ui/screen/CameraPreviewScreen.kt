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
import com.example.pepper_person_id_poc.application.face.FaceIdentityCoordinator
import com.example.pepper_person_id_poc.application.face.FaceIdentityUiState
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.FaceIdentityStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController
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
    val embeddingEngine = remember { SFaceEmbeddingEngine(context) }
    val coordinator = remember(personRepository, settings.faceThreshold) {
        FaceIdentityCoordinator(
            personRepository = personRepository,
            faceIdentifier = FaceIdentifier(),
            faceThreshold = settings.faceThreshold,
            faceModelName = embeddingEngine.modelName,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val faceDetector = remember {
        YuNetFaceDetector(
            context = context,
            embeddingEngine = embeddingEngine,
            onFeatureObservations = coordinator::onFeatureObservations,
            onEmbeddingReady = coordinator::reportEmbeddingReady,
            onEmbeddingError = coordinator::reportEmbeddingError,
            onBenchmarkEvent = benchmarkLogger::append,
        )
    }
    val controller = remember { CameraXPreviewController(context, frameProcessor = faceDetector) }
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
            controller.close()
            faceDetector.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (mode == FaceCameraMode.Registration) "人物登録 - 顔" else "顔識別")
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
                    Text(
                        "顔特徴量: ${if (identityState.embeddingModelReady) "SFace準備完了" else "SFace初期化中"}",
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
                    if (mode == FaceCameraMode.Registration) {
                        RegistrationPanel(coordinator, identityState)
                    } else {
                        IdentificationPanel(identityState, settings.debugMode)
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
    Text("解析フレーム: ${cameraState.frameCount} / ${cameraState.resolution ?: "取得中"}")
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
private fun FaceDetectionOverlay(
    detectionSnapshot: FaceDetectionSnapshot,
    identityState: FaceIdentityUiState,
    debugMode: Boolean,
) {
    val identityByTrackId = identityState.results.associateBy { it.trackId }
    Canvas(modifier = Modifier.fillMaxSize()) {
        detectionSnapshot.faces.forEach { face ->
            val identity = identityByTrackId[face.trackId]
            val identified = identity?.status == FaceIdentityStatus.IDENTIFIED
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
            val identityLabel = if (identified) {
                "${identity?.displayName}  ${identity?.personId?.value}"
            } else {
                "Unknown"
            }
            val debugLabel = if (debugMode && !identified && identity?.bestCandidatePersonId != null) {
                " best=${identity.bestCandidatePersonId.value}"
            } else {
                ""
            }
            drawContext.canvas.nativeCanvas.drawText(
                "$identityLabel  ${face.trackId}  Face ${identity?.score.asScore()}$debugLabel",
                left,
                (top - 8.dp.toPx()).coerceAtLeast(24.dp.toPx()),
                android.graphics.Paint().apply {
                    this.color = if (identified) android.graphics.Color.GREEN else android.graphics.Color.YELLOW
                    textSize = 18.dp.toPx()
                    isAntiAlias = true
                },
            )
        }
    }
}

private fun Float?.asScore(): String = this?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
