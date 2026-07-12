package com.example.pepper_person_id_poc.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pepper_person_id_poc.infrastructure.camera.CameraStatus
import com.example.pepper_person_id_poc.infrastructure.camera.CameraXPreviewController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraPreviewScreen(
    onBackToSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraXPreviewController(context) }
    val state by controller.state.collectAsState()
    val surfaceRequest by controller.surfaceRequest.collectAsState()
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
        onDispose { controller.close() }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
            ) {
                val request = surfaceRequest
                if (request != null) {
                    CameraXViewfinder(
                        surfaceRequest = request,
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(4f / 3f),
                    )
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
                    if (!permissionGranted) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("カメラ権限を許可")
                        }
                    } else if (state.status == CameraStatus.Stopped || state.status == CameraStatus.Error) {
                        Button(onClick = { controller.bind(lifecycleOwner) }) { Text("カメラを再開") }
                    }
                    Text(
                        "顔検出は次の機能単位で追加します。現在は前面カメラのプレビューとフレーム取得を確認します。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
