package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onFaceMaxUpdatesChanged: (Int) -> Unit,
    onSpeakerThresholdChanged: (Float) -> Unit,
    onSpeakerMaxUpdatesChanged: (Int) -> Unit,
    onOpenModelSelection: () -> Unit,
    onSave: () -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
    onExit: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("pepper-person-id-poc") }) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("未登録人物識別PoC")
            Button(onClick = onOpenModelSelection, modifier = Modifier.fillMaxWidth()) { Text("モデル選択") }
            Button(onClick = { onOpenScreen(AppScreen.DeviceDiagnostics) }, modifier = Modifier.fillMaxWidth()) {
                Text("端末診断")
            }
            Button(onClick = { onOpenScreen(AppScreen.Benchmark) }, modifier = Modifier.fillMaxWidth()) {
                Text("ベンチマーク構成")
            }
            Button(onClick = { onOpenScreen(AppScreen.AnonymousFaceIdentification) }, modifier = Modifier.fillMaxWidth()) {
                Text("未登録顔識別")
            }
            Button(onClick = { onOpenScreen(AppScreen.AnonymousSpeakerIdentification) }, modifier = Modifier.fillMaxWidth()) {
                Text("未登録話者識別")
            }
            ClusterSettings("顔", settings.faceClusterJoinThreshold, settings.faceClusterMaxUpdateCount, onFaceThresholdChanged, onFaceMaxUpdatesChanged)
            ClusterSettings("声", settings.speakerClusterJoinThreshold, settings.speakerClusterMaxUpdateCount, onSpeakerThresholdChanged, onSpeakerMaxUpdatesChanged)
            Text("集約方式: L2_NORMALIZED_MEAN（固定）")
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("設定を保存") }
            if (settingsSaved) Text("保存しました")
            Button(onClick = { confirmExit = true }, modifier = Modifier.fillMaxWidth()) { Text("アプリを終了") }
        }
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("アプリを終了しますか？") },
            text = { Text("今回の顔・声クラスタを削除して終了します。") },
            confirmButton = { TextButton(onClick = onExit) { Text("終了") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun ClusterSettings(
    label: String,
    threshold: Float,
    maxUpdates: Int,
    onThreshold: (Float) -> Unit,
    onMaxUpdates: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("$label クラスタ参加閾値: ${String.format(Locale.US, "%.2f", threshold)}")
            Slider(value = threshold, onValueChange = onThreshold, valueRange = 0f..1f)
            Text("$label クラスタ最大更新件数: $maxUpdates")
            Slider(value = maxUpdates.toFloat(), onValueChange = { onMaxUpdates(it.toInt()) }, valueRange = 1f..100f, steps = 98)
        }
    }
}
