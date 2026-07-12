package com.example.pepper_person_id_poc.ui.screen

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PocSettings,
    settingsSaved: Boolean,
    onFaceThresholdChanged: (Float) -> Unit,
    onSpeakerThresholdChanged: (Float) -> Unit,
    onCombinedThresholdChanged: (Float) -> Unit,
    onObservationWindowChanged: (Long) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onOpenModelSelection: () -> Unit,
    onSave: () -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pepper Multimodal Identity PoC") }) },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("設定", style = MaterialTheme.typography.headlineMedium)
            Text("起動時はこの画面を表示します。各機能画面の戻る操作でもここへ戻ります。")

            SettingsCard(title = "使用モデル") {
                Text("顔: ${settings.faceModel.displayName}")
                Text("話者: ${settings.speakerModel.displayName}")
                Button(onClick = onOpenModelSelection, modifier = Modifier.fillMaxWidth()) {
                    Text("顔・話者モデルを選択")
                }
            }

            SettingsCard(title = "識別閾値") {
                ThresholdSlider("Face Identification", settings.faceThreshold, onFaceThresholdChanged)
                ThresholdSlider("Speaker Identification", settings.speakerThreshold, onSpeakerThresholdChanged)
                ThresholdSlider("Combined", settings.combinedThreshold, onCombinedThresholdChanged)
                Text("顔観測時間: ${settings.observationWindowMillis} ms")
                Slider(
                    value = settings.observationWindowMillis.toFloat(),
                    onValueChange = { onObservationWindowChanged(it.toLong()) },
                    valueRange = PocSettings.OBSERVATION_WINDOW_RANGE.first.toFloat()..
                        PocSettings.OBSERVATION_WINDOW_RANGE.last.toFloat(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("デバッグ表示")
                        Text(
                            "Unknown時の最上位候補とスコアを表示します",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.debugMode,
                        onCheckedChange = onDebugModeChanged,
                    )
                }
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settingsSaved) "保存しました" else "設定を保存")
                }
            }

            Text("PoC機能", style = MaterialTheme.typography.titleLarge)
            AppScreen.entries
                .filterNot { it == AppScreen.Settings || it == AppScreen.ModelSelection }
                .forEach { screen ->
                    Card(onClick = { onOpenScreen(screen) }, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(screen.title, style = MaterialTheme.typography.titleMedium)
                            Text(screen.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Text("$label: ${String.format(Locale.US, "%.2f", value)}")
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
}
