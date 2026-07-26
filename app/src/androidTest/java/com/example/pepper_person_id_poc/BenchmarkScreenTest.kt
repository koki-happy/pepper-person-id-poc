package com.example.pepper_person_id_poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkModelSelection
import com.example.pepper_person_id_poc.infrastructure.metrics.BenchmarkDistribution
import com.example.pepper_person_id_poc.ui.screen.BenchmarkConfigurationUiState
import com.example.pepper_person_id_poc.ui.screen.BenchmarkScreen
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class BenchmarkScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidate_requiresExplicitExportAndValidConfigurationCanRun() {
        var exportRequested = false
        var runRequested = false
        composeRule.setContent {
            MaterialTheme {
                BenchmarkScreen(
                    configuration = BenchmarkConfigurationUiState(
                        scenarioId = "A-COMPARE-001",
                        inputDescriptor = "fixed-input",
                        inputSha256 = "a".repeat(64),
                        preprocessingId = "sface-v1",
                        datasetId = "local-fixed-v1",
                        repetitions = "10",
                    ),
                    modelSelections = listOf(
                        BenchmarkModelSelection(
                            role = "FACE_EMBEDDING",
                            modelSpaceId = "sface-v1",
                            artifactId = "sface-onnx",
                            runtimeId = "opencv",
                        ),
                    ),
                    distribution = BenchmarkDistribution.CANDIDATE,
                    candidateExportEnabled = false,
                    onConfigurationChange = {},
                    onRun = { runRequested = true },
                    onEnableCandidateExport = { exportRequested = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("benchmark-screen")
            .performScrollToNode(hasText("JSONL: 無効（candidate既定）"))
        composeRule.onNodeWithText("JSONL: 無効（candidate既定）").assertIsDisplayed()
        composeRule.onNodeWithTag("benchmark-screen")
            .performScrollToNode(hasTestTag("enable-candidate-export"))
        composeRule.onNodeWithTag("enable-candidate-export").performClick()
        composeRule.onNodeWithTag("benchmark-screen")
            .performScrollToNode(hasTestTag("run-benchmark"))
        composeRule.onNodeWithTag("run-benchmark").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertThat(exportRequested).isTrue()
            assertThat(runRequested).isTrue()
        }
    }
}
