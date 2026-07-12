package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.infrastructure.camera.CameraStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.infrastructure.face.YuNetFaceDetector
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    benchmarkLogger: BenchmarkLogger,
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val faceDetector = remember {
        YuNetFaceDetector(context, onBenchmarkEvent = benchmarkLogger::append)
    }
    val controller = remember { CameraXPreviewController(context, frameProcessor = faceDetector) }
    val state by controller.state.collectAsState()
    val surfaceRequest by controller.surfaceRequest.collectAsState()
    val faceSnapshot by faceDetector.snapshot.collectAsState()
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
                title = { Text("顔識別 - カメラ") },
                navigationIcon = {
                    Button(onClick = onBackToSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("← 設定")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
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
                    FaceDetectionOverlay(faceSnapshot)
                } else {
                    Text(
                        if (permissionGranted) "カメラを開始しています" else "カメラ権限が必要です",
                        color = Color.White,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("状態: ${state.status}")
                        Text("解析フレーム: ${state.frameCount}")
                        Text("解像度: ${state.resolution ?: "取得中"}")
                    }
                    state.error?.let { Text("カメラエラー: $it", color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("顔状態: ${faceSnapshot.status}")
                        Text("顔数: ${faceSnapshot.faces.size}")
                        Text("検出: ${faceSnapshot.processingTimeMillis ?: "-"} ms")
                        Text("モデル: ${faceSnapshot.modelName}")
                    }
                    faceSnapshot.error?.let {
                        Text("顔検出エラー: $it", color = MaterialTheme.colorScheme.error)
                    }
                    if (!permissionGranted) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("カメラ権限を許可")
                        }
                    } else if (state.status == CameraStatus.Stopped || state.status == CameraStatus.Error) {
                        Button(onClick = { controller.bind(lifecycleOwner) }) { Text("カメラを再開") }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceDetectionOverlay(snapshot: FaceDetectionSnapshot) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        snapshot.faces.forEach { face ->
            val mirroredLeft = 1f - face.boundingBox.right
            val left = mirroredLeft * size.width
            val top = face.boundingBox.top * size.height
            val width = face.boundingBox.width * size.width
            val height = face.boundingBox.height * size.height
            drawRect(
                color = Color.Yellow,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4.dp.toPx()),
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${face.trackId}  Face ${String.format(Locale.US, "%.2f", face.detectionScore)}",
                left,
                (top - 8.dp.toPx()).coerceAtLeast(24.dp.toPx()),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.YELLOW
                    textSize = 18.dp.toPx()
                    isAntiAlias = true
                },
            )
        }
    }
}
