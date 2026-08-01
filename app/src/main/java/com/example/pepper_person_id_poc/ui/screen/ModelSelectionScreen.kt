package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator
import com.example.pepper_person_id_poc.domain.config.ModelRuntimeRole
import com.example.pepper_person_id_poc.domain.config.ModelRuntimeSetOption
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.selectableModelRuntimeSets
import com.example.pepper_person_id_poc.domain.config.selectedSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    settings: PocSettings,
    selectionCoordinator: ModelSelectionCoordinator,
    settingsSaved: Boolean,
    onModelRuntimeSetChanged: (ModelRuntimeSetOption) -> Unit,
    onSave: () -> Unit,
    onBackToSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("モデル設定") },
                navigationIcon = { Button(onClick = onBackToSettings) { Text("← 戻る") } },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("model-selection-screen"),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ModelSetDropdown(
                    label = "顔検出",
                    role = ModelRuntimeRole.FACE_DETECTOR,
                    settings = settings,
                    coordinator = selectionCoordinator,
                    onSelected = onModelRuntimeSetChanged,
                    modifier = Modifier.weight(1f),
                )
                ModelSetDropdown(
                    label = "顔特徴量",
                    role = ModelRuntimeRole.FACE_EMBEDDING,
                    settings = settings,
                    coordinator = selectionCoordinator,
                    onSelected = onModelRuntimeSetChanged,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ModelSetDropdown(
                    label = "話者特徴量",
                    role = ModelRuntimeRole.SPEAKER_EMBEDDING,
                    settings = settings,
                    coordinator = selectionCoordinator,
                    onSelected = onModelRuntimeSetChanged,
                    modifier = Modifier.weight(1f),
                )
                ModelSetDropdown(
                    label = "音声区間検出",
                    role = ModelRuntimeRole.VAD,
                    settings = settings,
                    coordinator = selectionCoordinator,
                    onSelected = onModelRuntimeSetChanged,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onSave,
                enabled = selectionCoordinator.resolve(settings).selectable,
                modifier = Modifier.fillMaxWidth().testTag("save-model-selection"),
            ) { Text(if (settingsSaved) "保存しました" else "モデルセットを保存") }
        }
    }
}

@Composable
private fun ModelSetDropdown(
    label: String,
    role: ModelRuntimeRole,
    settings: PocSettings,
    coordinator: ModelSelectionCoordinator,
    onSelected: (ModelRuntimeSetOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember(role, coordinator) { selectableModelRuntimeSets(role, coordinator) }
    val selectedKey = settings.selectedSet(role)
    val selected = options.firstOrNull { it.artifactId to it.runtimeId == selectedKey }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected?.displayName ?: if (options.isEmpty()) "利用可能なセットなし" else "選択してください")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
        if (options.isEmpty()) {
            Text("この端末・APKで実行できる組み合わせがありません", color = MaterialTheme.colorScheme.error)
        }
    }
}
