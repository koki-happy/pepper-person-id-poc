package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorOption
import com.example.pepper_person_id_poc.domain.config.FaceInferenceBackend
import com.example.pepper_person_id_poc.domain.config.ModelImplementationStatus
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    settings: PocSettings,
    settingsSaved: Boolean,
    onFaceModelChanged: (FaceModelOption) -> Unit,
    onFaceDetectorChanged: (FaceDetectorOption) -> Unit,
    onFaceInferenceBackendChanged: (FaceInferenceBackend) -> Unit,
    onSpeakerModelChanged: (SpeakerModelOption) -> Unit,
    onSave: () -> Unit,
    onBackToSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("モデル選択") },
                navigationIcon = {
                    Button(onClick = onBackToSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("← 設定")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            ModelCard(title = "顔検出・追跡") {
                FaceDetectorOption.entries.forEach { option ->
                    ModelChoice(
                        title = option.displayName,
                        subtitle = option.description,
                        selected = settings.faceDetector == option,
                        onClick = { onFaceDetectorChanged(option) },
                    )
                }
            }

            ModelCard(title = "顔識別モデル") {
                FaceModelOption.entries.forEach { option ->
                    ModelChoice(
                        title = option.displayName,
                        subtitle = buildString {
                            append(option.modelFileName)
                            if (option == FaceModelOption.SFACE_2021DEC_INT8) {
                                append("\n対応推論基盤: OpenCV 5.0.0 DNN")
                            }
                            if (option.implementationStatus == ModelImplementationStatus.PENDING) {
                                append("\n実装状態: 未実装 / 推論起動: 無効")
                            } else if (option.implementationStatus == ModelImplementationStatus.UNAVAILABLE) {
                                append("\nPepper互換性: 非対応 / 欠落要素: OpenVINO IRランタイム")
                            }
                        },
                        selected = settings.faceModel == option,
                        onClick = { onFaceModelChanged(option) },
                    )
                }
            }

            ModelCard(title = "顔特徴量の推論基盤") {
                FaceInferenceBackend.entries.forEach { option ->
                    val supported = settings.faceModel.supports(option)
                    ModelChoice(
                        title = option.displayName,
                        subtitle = buildString {
                            append(option.description)
                            option.runtimeArtifact?.let { append("\nローカル成果物: $it") }
                            if (!supported) append("\n選択中の顔識別モデルでは未対応")
                        },
                        selected = settings.faceInferenceBackend == option,
                        enabled = supported,
                        onClick = { onFaceInferenceBackendChanged(option) },
                    )
                }
            }

            ModelCard(title = "話者識別モデル") {
                SpeakerModelOption.entries.forEach { option ->
                    ModelChoice(
                        title = option.displayName,
                        subtitle = buildString {
                            append("${option.modelFileName}\n16 kHz mono PCM / sherpa-onnx")
                            append("\nJVS候補: 閾値 %.3f / Top-2 margin %.3f".format(
                                java.util.Locale.US,
                                option.jvsCandidateThreshold,
                                option.jvsCandidateMargin,
                            ))
                            if (option == SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN) {
                                append("\nJVS評価 第1候補")
                            } else if (option == SpeakerModelOption.ERES2NET) {
                                append("\nJVS評価 第2候補 / Pepper既存スモークの既定")
                            }
                        },
                        selected = settings.speakerModel == option,
                        onClick = { onSpeakerModelChanged(option) },
                    )
                }
            }

            Text(
                "閾値プリセット: JVS studio評価値 / 調整入力: Pepperマイク収録音声\n" +
                    "モデル配布: Git管理外のローカルアセット / モデル欠落時: エラー / フォールバック: 無効",
                color = MaterialTheme.colorScheme.primary,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(if (settingsSaved) "モデル選択を保存しました" else "モデル選択を保存")
            }
        }
    }
}

@Composable
private fun ModelCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ModelChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
