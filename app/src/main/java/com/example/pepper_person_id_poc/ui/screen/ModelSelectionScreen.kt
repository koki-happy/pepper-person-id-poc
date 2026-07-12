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
import com.example.pepper_person_id_poc.domain.config.ModelImplementationStatus
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    settings: PocSettings,
    settingsSaved: Boolean,
    onFaceModelChanged: (FaceModelOption) -> Unit,
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
            ModelCard(title = "顔識別モデル") {
                FaceModelOption.entries.forEach { option ->
                    ModelChoice(
                        title = option.displayName,
                        subtitle = buildString {
                            append(option.modelFileName)
                            if (option.implementationStatus == ModelImplementationStatus.PENDING) {
                                append("\n実装準備中: 選択時は推論を開始しません")
                            }
                        },
                        selected = settings.faceModel == option,
                        onClick = { onFaceModelChanged(option) },
                    )
                }
            }

            ModelCard(title = "話者識別モデル") {
                SpeakerModelOption.entries.forEach { option ->
                    ModelChoice(
                        title = option.displayName,
                        subtitle = "${option.modelFileName}\n16 kHz mono PCM / sherpa-onnx",
                        selected = settings.speakerModel == option,
                        onClick = { onSpeakerModelChanged(option) },
                    )
                }
            }

            Text(
                "モデル本体はGitへ含めません。選択したモデルファイルが端末にない場合はエラーを表示し、別モデルへ自動変更しません。",
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
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
