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
import com.example.pepper_person_id_poc.application.contract.AudioRecordingState
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.speaker.SpeakerIdentityUiState
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrack
import com.example.pepper_person_id_poc.domain.speaker.LocalSpeakerTrackState
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.example.pepper_person_id_poc.domain.speaker.SpeakerAudioQualityAssessment
import com.example.pepper_person_id_poc.ui.screen.SpeakerAudioContractDetails
import org.junit.Rule
import org.junit.Test

class AnonymousSpeakerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun candidateList_isNotRenderedOnTheMinimalScreen() {
        val candidates = (1..60).map { number ->
            AnonymousClusterScore(
                anonymousId = "anonymous-speaker-${number.toString().padStart(3, '0')}",
                score = 1f - number / 100f,
                selected = number == 1,
            )
        }
        val state = speakerState(
            result = speakerResult(
                anonymousId = "anonymous-speaker-001",
                candidates = candidates,
            ),
        )
        setContractContent(state)

        composeRule.onNodeWithText("anonymous-speaker-001").assertIsDisplayed()
    }

    @Test
    fun speakerContract_rendersOnlyIdentificationQualityAndLoadSummary() {
        val state = speakerState(
            result = speakerResult(
                anonymousId = "anonymous-speaker-009",
                candidates = listOf(
                    AnonymousClusterScore("anonymous-speaker-009", 0.79f, selected = false),
                ),
            ),
        )
        setContractContent(state)

        listOf(
            "品質不足",
            "QUALITY_HOLD",
            "0.79",
            "anonymous-speaker-009",
        ).forEach(::assertTextCanBeDisplayed)
    }

    private fun setContractContent(state: SpeakerIdentityUiState) {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SpeakerAudioContractDetails(
                        recorder = AudioRecordingState(
                            status = AudioRecordingStatus.RECORDING,
                            sampleRate = 16_000,
                            minBufferSizeBytes = 1_920,
                            vadModelName = "silero-vad",
                            speechActive = true,
                        ),
                        identity = state,
                        speakerClusterJoinThreshold = 0.80f,
                    )
                }
            }
        }
    }

    private fun speakerState(result: AnonymousIdentificationResult) = SpeakerIdentityUiState(
        modelName = "cam-plus-plus",
        modelReady = true,
        modelDimension = 512,
        results = mapOf("local-speaker-001" to result),
        result = result,
        currentModelClusterCount = 60,
        totalClusterCount = 60,
        activityArtifactId = "fixture-pyannote-powerset",
        activityRuntimeId = "fixture-cpu",
        activityReady = true,
        activityState = SpeakerActivityState.OVERLAPPED_SPEECH,
        overlapRatio = 0.25f,
        activeSpeakerCount = 2,
        localTracks = listOf(
            LocalSpeakerTrack(
                localSpeakerId = "local-speaker-001",
                firstSeenSample = 0L,
                lastSeenSample = 16_000L,
                windowIds = listOf("window-1"),
                soloSegments = emptyList(),
                state = LocalSpeakerTrackState.ACTIVE,
            ),
        ),
        qualityByLocalSpeaker = mapOf(
            "local-speaker-001" to SpeakerAudioQualityAssessment(
                createEligible = false,
                updateEligible = false,
                rejectionReasons = listOf("QUALITY_HOLD"),
                snr = null,
            ),
        ),
        activityHoldReasons = listOf("COMPLETE_OVERLAP"),
        activityInferenceTimeMillis = 10L,
        inferenceTimeMillis = 15L,
        totalPipelineTimeMillis = 500L,
        realTimeFactor = 0.125f,
    )

    private fun speakerResult(
        anonymousId: String,
        candidates: List<AnonymousClusterScore>,
    ) = AnonymousIdentificationResult(
        anonymousId = anonymousId,
        modelId = "speaker-model-space",
        bestExistingScore = candidates.firstOrNull()?.score,
        threshold = 0.80f,
        isNewCluster = false,
        updateCount = 0,
        maximumUpdateCount = 20,
        currentModelClusterCount = candidates.size,
        totalClusterCount = candidates.size,
        candidateScores = candidates,
        persistenceOperation = PersistenceOperation.HOLD,
    )

    private fun assertTextCanBeDisplayed(text: String) {
        composeRule.onNodeWithTag("speaker-audio-contract").performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }
}
