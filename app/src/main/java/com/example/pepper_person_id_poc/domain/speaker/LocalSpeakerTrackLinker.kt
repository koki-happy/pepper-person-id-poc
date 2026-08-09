package com.example.pepper_person_id_poc.domain.speaker

data class WindowSpeakerObservation(
    val windowSpeakerIndex: Int,
    val startSample: Long,
    val endSample: Long,
    val activityState: SpeakerActivityState,
    val segments: List<DiarizedSpeakerSegment> = emptyList(),
)

data class LocalSpeakerAssignment(
    val windowSpeakerIndex: Int,
    val localSpeakerId: String,
    val similarity: Float?,
)

data class LocalSpeakerLinkResult(
    val assignments: List<LocalSpeakerAssignment>,
    val tracks: List<LocalSpeakerTrack>,
    val holdReasons: List<String>,
)

/**
 * Assigns a fresh local label to every speaker observation in every utterance window.
 * Local labels describe this interval only; anonymous speaker identification remains separate.
 */
class LocalSpeakerTrackLinker {
    private val tracks = mutableListOf<LocalSpeakerTrack>()
    private var nextTrackNumber = 1

    fun link(
        windowId: String,
        observations: List<WindowSpeakerObservation>,
    ): LocalSpeakerLinkResult {
        require(windowId.isNotBlank())
        require(observations.map { it.windowSpeakerIndex }.distinct().size == observations.size)

        closeActiveTracks()

        if (observations.any { it.activityState != SpeakerActivityState.SINGLE_SPEAKER }) {
            val reasons = observations.mapNotNull {
                when (it.activityState) {
                    SpeakerActivityState.OVERLAPPED_SPEECH -> "OVERLAPPED_SPEECH"
                    SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS -> "MULTIPLE_ACTIVE_SPEAKERS"
                    SpeakerActivityState.UNSUPPORTED -> "UNSUPPORTED_ACTIVITY_INFERENCE"
                    SpeakerActivityState.ERROR -> "ACTIVITY_INFERENCE_ERROR"
                    SpeakerActivityState.SILENCE -> "SILENCE"
                    SpeakerActivityState.SINGLE_SPEAKER -> null
                }
            }.distinct()
            return result(emptyList(), reasons)
        }

        val assignments = observations
            .sortedBy { it.windowSpeakerIndex }
            .map { observation ->
                require(observation.endSample > observation.startSample)
                require(observation.segments.all { segment ->
                    segment.startSample >= observation.startSample &&
                        segment.endSample <= observation.endSample &&
                        segment.endSample > segment.startSample
                })
                val localSpeakerId = nextLocalSpeakerId()
                tracks += LocalSpeakerTrack(
                    localSpeakerId = localSpeakerId,
                    firstSeenSample = observation.startSample,
                    lastSeenSample = observation.endSample,
                    windowIds = listOf(windowId),
                    soloSegments = observation.segments.map {
                        it.copy(localSpeakerId = localSpeakerId)
                    },
                    state = LocalSpeakerTrackState.ACTIVE,
                )
                LocalSpeakerAssignment(
                    windowSpeakerIndex = observation.windowSpeakerIndex,
                    localSpeakerId = localSpeakerId,
                    similarity = null,
                )
            }

        return result(assignments, emptyList())
    }

    private fun closeActiveTracks() {
        tracks.indices.forEach { index ->
            if (tracks[index].state == LocalSpeakerTrackState.ACTIVE) {
                tracks[index] = tracks[index].copy(state = LocalSpeakerTrackState.CLOSED)
            }
        }
    }

    private fun result(
        assignments: List<LocalSpeakerAssignment>,
        reasons: List<String>,
    ) = LocalSpeakerLinkResult(
        assignments = assignments,
        tracks = tracks.toList(),
        holdReasons = reasons,
    )

    private fun nextLocalSpeakerId(): String =
        "local-speaker-${nextTrackNumber++.toString().padStart(3, '0')}"
}
