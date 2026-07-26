package com.example.pepper_person_id_poc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.pepper_person_id_poc.application.face.FaceIdentityUiState
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.ui.screen.FacePreviewContractDetails
import org.junit.Rule
import org.junit.Test

class AnonymousFaceUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidateList_isNotRenderedOnTheMinimalScreen() {
        val candidates = (1..60).map { number ->
            AnonymousClusterScore(
                anonymousId = "anonymous-face-${number.toString().padStart(3, '0')}",
                score = 1f - number / 100f,
                selected = number == 1,
            )
        }
        val state = faceState(
            result = faceResult(
                anonymousId = "anonymous-face-001",
                candidates = candidates,
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    FacePreviewContractDetails(
                        identity = state,
                        detectionProcessingTimeMillis = 12L,
                        faceClusterJoinThreshold = 0.8f,
                    )
                }
            }
        }

        composeRule.onNodeWithText("anonymous-face-001").assertIsDisplayed()
    }

    @Test
    fun faceContract_rendersOnlyIdentificationAndLoadSummary() {
        val state = faceState(
            result = faceResult(
                anonymousId = "anonymous-face-009",
                candidates = listOf(
                    AnonymousClusterScore("anonymous-face-009", 0.93f, selected = true),
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    FacePreviewContractDetails(
                        identity = state,
                        detectionProcessingTimeMillis = 12L,
                        faceClusterJoinThreshold = 0.8f,
                    )
                }
            }
        }

        listOf(
            "anonymous-face-009",
            "既存Feature",
            "0.93",
        ).forEach(::assertTextCanBeDisplayed)
    }

    private fun faceState(result: AnonymousIdentificationResult) = FaceIdentityUiState(
        modelId = "face-model-space",
        results = mapOf("track-17" to result),
        currentModelClusterCount = 60,
        totalClusterCount = 60,
        lastEmbeddingAverageTimeMillis = 34L,
        lastEmbeddingMaximumTimeMillis = 40L,
        lastEmbeddingFaceCount = 1,
    )

    private fun faceResult(
        anonymousId: String,
        candidates: List<AnonymousClusterScore>,
    ) = AnonymousIdentificationResult(
        anonymousId = anonymousId,
        modelId = "face-model-space",
        bestExistingScore = candidates.firstOrNull()?.score,
        threshold = 0.8f,
        isNewCluster = false,
        updateCount = 2,
        maximumUpdateCount = 20,
        currentModelClusterCount = candidates.size,
        totalClusterCount = candidates.size,
        candidateScores = candidates,
    )

    private fun assertTextCanBeDisplayed(text: String) {
        composeRule.onNodeWithTag("face-preview-contract").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }
}
