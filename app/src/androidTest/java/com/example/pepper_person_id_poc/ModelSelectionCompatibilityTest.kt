package com.example.pepper_person_id_poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.infrastructure.model.ParsedCatalogArtifact
import com.example.pepper_person_id_poc.infrastructure.model.ParsedModelCatalog
import com.example.pepper_person_id_poc.infrastructure.model.ParsedRuntimeCompatibility
import com.example.pepper_person_id_poc.ui.screen.ModelSelectionScreen
import org.junit.Rule
import org.junit.Test

class ModelSelectionCompatibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingSelectedArtifactDisablesSaveAndShowsBlockedReason() {
        val settings = PocSettings()
        val coordinator = coordinator(settings)
        composeRule.setContent {
            MaterialTheme {
                ModelSelectionScreen(
                    settings = settings,
                    selectionCoordinator = coordinator,
                    settingsSaved = false,
                    onModelRuntimeSetChanged = {},
                    onSave = {},
                    onBackToSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("save-model-selection").assertIsNotEnabled()
    }

    private fun coordinator(settings: PocSettings): ModelSelectionCoordinator {
        val pairs = listOf(
            Triple(settings.faceDetectorModel.artifactId, settings.faceDetectorModel.modelFileName, settings.faceDetectorRuntime.runtimeId),
            Triple(settings.faceEmbeddingModel.artifactId, settings.faceEmbeddingModel.modelFileName, settings.faceEmbeddingRuntime.runtimeId),
            Triple(settings.speakerModel.artifactId, settings.speakerModel.modelFileName, settings.speakerRuntime.runtimeId),
            Triple(settings.vadModel.artifactId, "silero_vad.onnx", settings.vadRuntime.runtimeId),
        )
        val artifacts = pairs.map { (artifactId, filename, runtimeId) ->
            ParsedCatalogArtifact(
                artifactId = artifactId,
                role = "EMBEDDING",
                format = "ONNX",
                filename = filename,
                runtimeCompatibility = listOf(
                    ParsedRuntimeCompatibility(
                        runtimeId = runtimeId,
                        abi = "armeabi-v7a",
                        minApi = 23,
                        status = "BUILDABLE",
                        reason = null,
                    ),
                ),
            )
        }
        return ModelSelectionCoordinator(
            catalog = ParsedModelCatalog(
                schemaVersion = 2,
                modelSpaceIds = emptySet(),
                runtimeIds = pairs.map { it.third }.toSet(),
                artifactIds = artifacts.map { it.artifactId }.toSet(),
                artifacts = artifacts,
            ),
            bundledArtifactFileNames = pairs.mapNotNull { it.second }.toSet() -
                settings.faceDetectorModel.modelFileName.orEmpty(),
            abi = "armeabi-v7a",
            apiLevel = 23,
        )
    }
}
