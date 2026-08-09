package com.example.pepper_person_id_poc.domain.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalSpeakerTrackLinkerTest {
    private val linker = LocalSpeakerTrackLinker()

    @Test
    fun everyObservationGetsAFreshLocalIdWithoutSimilarityMatching() {
        val first = linker.link(
            windowId = "window-001",
            observations = listOf(observation(windowSpeakerIndex = 0)),
        )
        val second = linker.link(
            windowId = "window-002",
            observations = listOf(observation(windowSpeakerIndex = 0)),
        )

        assertThat(first.assignments.single().localSpeakerId).isEqualTo("local-speaker-001")
        assertThat(second.assignments.single().localSpeakerId).isEqualTo("local-speaker-002")
        assertThat(second.assignments.single().similarity).isNull()
        assertThat(second.tracks.first().state).isEqualTo(LocalSpeakerTrackState.CLOSED)
        assertThat(second.tracks.last().state).isEqualTo(LocalSpeakerTrackState.ACTIVE)
    }

    @Test
    fun observationsInOneWindowAlsoGetSeparateLocalIds() {
        val result = linker.link(
            windowId = "window-001",
            observations = listOf(
                observation(windowSpeakerIndex = 0, startSample = 0L, endSample = 8_000L),
                observation(windowSpeakerIndex = 1, startSample = 8_000L, endSample = 16_000L),
            ),
        )

        assertThat(result.assignments.map { it.localSpeakerId })
            .containsExactly("local-speaker-001", "local-speaker-002")
            .inOrder()
        assertThat(result.assignments.map { it.similarity }).containsExactly(null, null).inOrder()
    }

    @Test
    fun freshLabelIsCopiedToEachIntervalSegment() {
        val result = linker.link(
            windowId = "window-001",
            observations = listOf(
                observation(
                    windowSpeakerIndex = 0,
                    segments = listOf(
                        DiarizedSpeakerSegment(
                            localSpeakerId = "window-segment-0",
                            startSample = 1_000L,
                            endSample = 2_000L,
                            activityState = SpeakerActivityState.SINGLE_SPEAKER,
                            confidence = 0.9f,
                            isSolo = true,
                        ),
                    ),
                ),
            ),
        )

        assertThat(result.tracks.single().soloSegments.single().localSpeakerId)
            .isEqualTo("local-speaker-001")
    }

    @Test
    fun overlappedObservationReturnsHoldWithoutNewAssignment() {
        linker.link(
            windowId = "window-001",
            observations = listOf(observation(windowSpeakerIndex = 0)),
        )

        val result = linker.link(
            windowId = "window-002",
            observations = listOf(
                observation(
                    windowSpeakerIndex = 0,
                    activityState = SpeakerActivityState.OVERLAPPED_SPEECH,
                ),
            ),
        )

        assertThat(result.assignments).isEmpty()
        assertThat(result.holdReasons).containsExactly("OVERLAPPED_SPEECH")
        assertThat(result.tracks).hasSize(1)
        assertThat(result.tracks.single().state).isEqualTo(LocalSpeakerTrackState.CLOSED)
    }

    private fun observation(
        windowSpeakerIndex: Int,
        startSample: Long = 0L,
        endSample: Long = 16_000L,
        activityState: SpeakerActivityState = SpeakerActivityState.SINGLE_SPEAKER,
        segments: List<DiarizedSpeakerSegment> = emptyList(),
    ) = WindowSpeakerObservation(
        windowSpeakerIndex = windowSpeakerIndex,
        startSample = startSample,
        endSample = endSample,
        activityState = activityState,
        segments = segments,
    )
}
