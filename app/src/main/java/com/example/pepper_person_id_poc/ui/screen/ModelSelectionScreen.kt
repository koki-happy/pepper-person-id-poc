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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.application.config.ModelPairAvailability
import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator
import com.example.pepper_person_id_poc.domain.config.FaceDetectorArtifactResolver
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingArtifactResolver
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    settings: PocSettings,
    selectionCoordinator: ModelSelectionCoordinator,
    settingsSaved: Boolean,
    onFaceDetectorModelChanged: (FaceDetectorModelOption) -> Unit,
    onFaceDetectorRuntimeChanged: (FaceDetectorRuntime) -> Unit,
    onFaceEmbeddingModelChanged: (FaceEmbeddingModelOption) -> Unit,
    onFaceEmbeddingRuntimeChanged: (FaceEmbeddingRuntime) -> Unit,
    onSpeakerModelChanged: (SpeakerModelOption) -> Unit,
    onSave: () -> Unit,
    onBackToSettings: () -> Unit,
) {
    val settingsAvailability = selectionCoordinator.resolve(settings)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("モデル・runtime選択") },
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
                .padding(20.dp)
                .testTag("model-selection-screen"),
        ) {
            ModelCard(title = "顔検出モデル資産") {
                FaceDetectorArtifactResolver.logicalModels.forEach { option ->
                    val availability = FaceDetectorRuntime.entries
                        .map { runtime ->
                            FaceDetectorArtifactResolver.resolve(option, runtime)?.let { artifact ->
                                selectionCoordinator.resolve(artifact.artifactId, runtime.runtimeId)
                            } ?: ModelPairAvailability(
                                artifactId = option.artifactId,
                                runtimeId = runtime.runtimeId,
                                status = com.example.pepper_person_id_poc.domain.model.CompatibilityStatus.UNSUPPORTED,
                                selectable = false,
                                reason = "Exact runtime artifact is not prepared",
                            )
                        }
                        .bestAvailability()
                    ModelChoice(
                        title = option.displayName,
                        subtitle = option.details(availability),
                        selected = FaceDetectorArtifactResolver.logicalModel(settings.faceDetectorModel) == option,
                        enabled = availability.selectable,
                        onClick = { onFaceDetectorModelChanged(option) },
                    )
                }
            }

            ModelCard(title = "顔検出runtime") {
                FaceDetectorRuntime.entries.forEach { option ->
                    val artifact = FaceDetectorArtifactResolver.resolve(settings.faceDetectorModel, option)
                    val availability = artifact?.let {
                        selectionCoordinator.resolve(it.artifactId, option.runtimeId)
                    } ?: ModelPairAvailability(
                        artifactId = settings.faceDetectorModel.artifactId,
                        runtimeId = option.runtimeId,
                        status = com.example.pepper_person_id_poc.domain.model.CompatibilityStatus.UNSUPPORTED,
                        selectable = false,
                        reason = "Exact runtime artifact is not prepared",
                    )
                    ModelChoice(
                        title = option.displayName,
                        subtitle = option.description +
                            "\nartifact=${artifact?.artifactId ?: "MISSING"}" +
                            availability.details(),
                        selected = settings.faceDetectorRuntime == option,
                        enabled = availability.selectable,
                        onClick = { onFaceDetectorRuntimeChanged(option) },
                    )
                }
            }

            ModelCard(title = "顔特徴量モデル資産") {
                FaceEmbeddingArtifactResolver.logicalModels.forEach { option ->
                    val availability = FaceEmbeddingRuntime.entries
                        .map { runtime ->
                            FaceEmbeddingArtifactResolver.resolve(option, runtime)?.let { artifact ->
                                selectionCoordinator.resolve(artifact.artifactId, runtime.runtimeId)
                            } ?: ModelPairAvailability(
                                artifactId = option.artifactId,
                                runtimeId = runtime.runtimeId,
                                status = com.example.pepper_person_id_poc.domain.model.CompatibilityStatus.UNSUPPORTED,
                                selectable = false,
                                reason = "Exact runtime artifact is not prepared",
                            )
                        }
                        .bestAvailability()
                    ModelChoice(
                        title = option.displayName,
                        subtitle = "logicalModel=${option.artifactId}" + availability.details(),
                        selected = FaceEmbeddingArtifactResolver.logicalModel(settings.faceEmbeddingModel) == option,
                        enabled = availability.selectable,
                        onClick = { onFaceEmbeddingModelChanged(option) },
                    )
                }
            }

            ModelCard(title = "顔特徴量runtime") {
                FaceEmbeddingRuntime.entries.forEach { option ->
                    val artifact = FaceEmbeddingArtifactResolver.resolve(settings.faceEmbeddingModel, option)
                    val availability = artifact?.let {
                        selectionCoordinator.resolve(it.artifactId, option.runtimeId)
                    } ?: ModelPairAvailability(
                        artifactId = settings.faceEmbeddingModel.artifactId,
                        runtimeId = option.runtimeId,
                        status = com.example.pepper_person_id_poc.domain.model.CompatibilityStatus.UNSUPPORTED,
                        selectable = false,
                        reason = "Exact runtime artifact is not prepared",
                    )
                    ModelChoice(
                        title = option.displayName,
                        subtitle = option.description +
                            "\nartifact=${artifact?.artifactId ?: "MISSING"}" +
                            availability.details(),
                        selected = settings.faceEmbeddingRuntime == option,
                        enabled = availability.selectable,
                        onClick = { onFaceEmbeddingRuntimeChanged(option) },
                    )
                }
            }

            ModelCard(title = "話者特徴量モデル") {
                SpeakerModelOption.entries.forEach { option ->
                    val availability = selectionCoordinator.resolve(
                        option.artifactId,
                        settings.speakerRuntime.runtimeId,
                    )
                    ModelChoice(
                        title = option.displayName,
                        subtitle = "${option.modelFileName}\nartifact=${option.artifactId}" + availability.details(),
                        selected = settings.speakerModel == option,
                        enabled = availability.selectable,
                        onClick = { onSpeakerModelChanged(option) },
                    )
                }
            }

            val vadAvailability = settingsAvailability.vad
            Text(
                "VAD: ${settings.vadModel.displayName} / ${settings.speakerRuntime.displayName}" +
                    vadAvailability.details(),
                color = if (vadAvailability.selectable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.testTag("vad-compatibility"),
            )
            if (!settingsAvailability.selectable) {
                Text(
                    "保存不可: BLOCKEDまたはUNSUPPORTEDの組合せがあります",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("selection-blocked"),
                )
            }
            Button(
                onClick = onSave,
                enabled = settingsAvailability.selectable,
                modifier = Modifier.fillMaxWidth().testTag("save-model-selection"),
            ) {
                Text(if (settingsSaved) "モデル選択を保存しました" else "モデル選択を保存")
            }
        }
    }
}

private fun FaceDetectorModelOption.details(availability: ModelPairAvailability): String =
    "${description}\nartifact=$artifactId" + availability.details()

private fun ModelPairAvailability.details(): String = buildString {
    append("\nstatus=$status")
    reason?.let { append("\nreason=$it") }
}

private fun List<ModelPairAvailability>.bestAvailability(): ModelPairAvailability =
    firstOrNull { it.selectable } ?: first()

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
    enabled: Boolean,
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
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}
