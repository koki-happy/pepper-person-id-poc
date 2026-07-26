package com.example.pepper_person_id_poc.domain.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SoloSpeakerSegmentSelectorTest {
    private val selector = SoloSpeakerSegmentSelector()

    @Test
    fun extractsOnlySoloSegmentsAndPreservesTheirOrder() {
        val firstSolo = segment(
            localSpeakerId = "local-speaker-001",
            startSample = 0L,
            endSample = 8_000L,
        )
        val overlap = segment(
            localSpeakerId = "overlap",
            startSample = 8_000L,
            endSample = 12_000L,
            activityState = SpeakerActivityState.OVERLAPPED_SPEECH,
            isSolo = false,
        )
        val secondSolo = segment(
            localSpeakerId = "local-speaker-002",
            startSample = 12_000L,
            endSample = 20_000L,
        )

        val selection = selector.select(listOf(firstSolo, overlap, secondSolo))

        assertThat(selection.soloSegments).containsExactly(firstSolo, secondSolo).inOrder()
        assertThat(selection.persistenceEligible).isTrue()
        assertThat(selection.holdReasons).isEmpty()
    }

    @Test
    fun completeOverlapFailsClosed() {
        val selection = selector.select(
            listOf(
                segment(
                    localSpeakerId = "overlap",
                    startSample = 0L,
                    endSample = 16_000L,
                    activityState = SpeakerActivityState.OVERLAPPED_SPEECH,
                    isSolo = false,
                ),
            ),
        )

        assertThat(selection.soloSegments).isEmpty()
        assertThat(selection.persistenceEligible).isFalse()
        assertThat(selection.holdReasons).contains("COMPLETE_OVERLAP")
    }

    @Test
    fun unsupportedActivityFailsClosedEvenWhenAnotherSegmentLooksSolo() {
        val selection = selector.select(
            listOf(
                segment(
                    localSpeakerId = "unsupported",
                    startSample = 0L,
                    endSample = 8_000L,
                    activityState = SpeakerActivityState.UNSUPPORTED,
                    isSolo = false,
                ),
                segment(
                    localSpeakerId = "local-speaker-001",
                    startSample = 8_000L,
                    endSample = 16_000L,
                ),
            ),
        )

        assertThat(selection.soloSegments).isEmpty()
        assertThat(selection.persistenceEligible).isFalse()
        assertThat(selection.holdReasons).contains("UNSUPPORTED_ACTIVITY_INFERENCE")
    }

    @Test
    fun activityErrorAndMultipleActiveSpeakersFailClosed() {
        val error = selector.select(
            listOf(
                segment(
                    localSpeakerId = "error",
                    startSample = 0L,
                    endSample = 8_000L,
                    activityState = SpeakerActivityState.ERROR,
                    isSolo = false,
                ),
            ),
        )
        val multiple = selector.select(
            listOf(
                segment(
                    localSpeakerId = "multiple",
                    startSample = 0L,
                    endSample = 8_000L,
                    activityState = SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS,
                    isSolo = false,
                ),
            ),
        )

        assertThat(error.persistenceEligible).isFalse()
        assertThat(error.holdReasons).contains("ACTIVITY_INFERENCE_ERROR")
        assertThat(multiple.persistenceEligible).isFalse()
        assertThat(multiple.holdReasons).contains("MULTIPLE_ACTIVE_SPEAKERS")
    }

    private fun segment(
        localSpeakerId: String,
        startSample: Long,
        endSample: Long,
        activityState: SpeakerActivityState = SpeakerActivityState.SINGLE_SPEAKER,
        isSolo: Boolean = true,
    ) = DiarizedSpeakerSegment(
        localSpeakerId = localSpeakerId,
        startSample = startSample,
        endSample = endSample,
        activityState = activityState,
        confidence = 0.90f,
        isSolo = isSolo,
    )
}
