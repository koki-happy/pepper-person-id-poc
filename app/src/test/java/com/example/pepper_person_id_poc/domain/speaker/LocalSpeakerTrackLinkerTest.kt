package com.example.pepper_person_id_poc.domain.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalSpeakerTrackLinkerTest {
    private val linker = LocalSpeakerTrackLinker(
        minimumSimilarity = 0.50f,
        maximumMissingWindows = 2,
    )

    @Test
    fun swappedWindowSpeakerIndicesKeepStableLocalSpeakerIds() {
        val first = linker.link(
            windowId = "window-001",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(1f, 0f)),
                observation(windowSpeakerIndex = 1, embedding = floatArrayOf(0f, 1f)),
            ),
        )
        val originalIds = first.assignments.associate {
            it.windowSpeakerIndex to it.localSpeakerId
        }

        val swapped = linker.link(
            windowId = "window-002",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(0f, 1f)),
                observation(windowSpeakerIndex = 1, embedding = floatArrayOf(1f, 0f)),
            ),
        )

        assertThat(swapped.assignments.associate {
            it.windowSpeakerIndex to it.localSpeakerId
        }).containsExactly(
            0, originalIds.getValue(1),
            1, originalIds.getValue(0),
        )
        assertThat(swapped.holdReasons).isEmpty()
    }

    @Test
    fun disappearedTrackIsMissingThenReusedWhenSpeakerReappearsWithinWindowLimit() {
        val first = linker.link(
            windowId = "window-001",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(1f, 0f)),
                observation(windowSpeakerIndex = 1, embedding = floatArrayOf(0f, 1f)),
            ),
        )
        val returningId = first.assignments.single {
            it.windowSpeakerIndex == 1
        }.localSpeakerId

        val disappearance = linker.link(
            windowId = "window-002",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(1f, 0f)),
            ),
        )

        assertThat(disappearance.tracks.single {
            it.localSpeakerId == returningId
        }.state).isEqualTo(LocalSpeakerTrackState.MISSING)

        val reappearance = linker.link(
            windowId = "window-003",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(1f, 0f)),
                observation(windowSpeakerIndex = 1, embedding = floatArrayOf(0f, 1f)),
            ),
        )

        assertThat(reappearance.assignments.single {
            it.windowSpeakerIndex == 1
        }.localSpeakerId).isEqualTo(returningId)
        assertThat(reappearance.tracks.single {
            it.localSpeakerId == returningId
        }.state).isEqualTo(LocalSpeakerTrackState.ACTIVE)
    }

    @Test
    fun overlappedObservationFailsClosedWithoutAssignmentOrTrackMutation() {
        val initial = linker.link(
            windowId = "window-001",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(1f, 0f)),
            ),
        )

        val overlap = linker.link(
            windowId = "window-002",
            observations = listOf(
                observation(
                    windowSpeakerIndex = 0,
                    embedding = floatArrayOf(1f, 0f),
                    activityState = SpeakerActivityState.OVERLAPPED_SPEECH,
                ),
            ),
        )

        assertThat(overlap.assignments).isEmpty()
        assertThat(overlap.holdReasons).contains("OVERLAPPED_SPEECH")
        assertThat(overlap.tracks).containsExactlyElementsIn(initial.tracks)
    }

    @Test
    fun exactBestSimilarityTieFailsClosedInsteadOfUsingInputOrder() {
        linker.link(
            windowId = "window-001",
            observations = listOf(
                observation(windowSpeakerIndex = 0, embedding = floatArrayOf(1f, 0f)),
                observation(windowSpeakerIndex = 1, embedding = floatArrayOf(0f, 1f)),
            ),
        )

        val tie = linker.link(
            windowId = "window-002",
            observations = listOf(
                observation(
                    windowSpeakerIndex = 0,
                    embedding = floatArrayOf(0.70710677f, 0.70710677f),
                ),
            ),
        )

        assertThat(tie.assignments).isEmpty()
        assertThat(tie.holdReasons).contains("AMBIGUOUS_LOCAL_TRACKING")
        assertThat(tie.tracks).hasSize(2)
    }

    private fun observation(
        windowSpeakerIndex: Int,
        embedding: FloatArray,
        activityState: SpeakerActivityState = SpeakerActivityState.SINGLE_SPEAKER,
    ) = WindowSpeakerObservation(
        windowSpeakerIndex = windowSpeakerIndex,
        startSample = 0L,
        endSample = 16_000L,
        activityState = activityState,
        embedding = embedding,
    )
}
