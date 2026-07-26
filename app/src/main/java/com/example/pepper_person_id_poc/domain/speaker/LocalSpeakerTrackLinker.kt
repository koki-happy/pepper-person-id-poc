package com.example.pepper_person_id_poc.domain.speaker

import kotlin.math.sqrt

data class WindowSpeakerObservation(
    val windowSpeakerIndex: Int,
    val startSample: Long,
    val endSample: Long,
    val activityState: SpeakerActivityState,
    val embedding: FloatArray,
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

class LocalSpeakerTrackLinker(
    private val minimumSimilarity: Float,
    private val maximumMissingWindows: Int,
) {
    private val tracks = mutableListOf<TrackState>()
    private var nextTrackNumber = 1

    init {
        require(minimumSimilarity in -1f..1f)
        require(maximumMissingWindows >= 0)
    }

    fun link(
        windowId: String,
        observations: List<WindowSpeakerObservation>,
    ): LocalSpeakerLinkResult {
        require(windowId.isNotBlank())
        require(observations.map { it.windowSpeakerIndex }.distinct().size == observations.size)
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

        val normalized = observations.map { observation ->
            require(observation.endSample > observation.startSample)
            observation to normalize(observation.embedding)
        }
        val eligibleTracks = tracks.filter { it.state != LocalSpeakerTrackState.CLOSED }
        val proposed = normalized.map { (observation, embedding) ->
            val ranked = eligibleTracks
                .map { it to dot(embedding, it.centroid) }
                .sortedWith(
                    compareByDescending<Pair<TrackState, Float>> { it.second }
                        .thenBy { it.first.localSpeakerId },
                )
            val best = ranked.firstOrNull()
            val tied = best != null &&
                ranked.drop(1).any { (_, score) -> score == best.second }
            if (tied) {
                return result(emptyList(), listOf("AMBIGUOUS_LOCAL_TRACKING"))
            }
            ProposedAssignment(
                observation = observation,
                embedding = embedding,
                track = best?.first?.takeIf { best.second >= minimumSimilarity },
                similarity = best?.second,
            )
        }
        val duplicateTrack = proposed
            .mapNotNull { it.track?.localSpeakerId }
            .groupingBy(String::toString)
            .eachCount()
            .any { it.value > 1 }
        if (duplicateTrack) {
            return result(emptyList(), listOf("AMBIGUOUS_LOCAL_TRACKING"))
        }

        val assignedIds = mutableSetOf<String>()
        val assignments = proposed.map { proposal ->
            val track = proposal.track ?: TrackState(
                localSpeakerId = nextLocalSpeakerId(),
                centroid = proposal.embedding.copyOf(),
                embeddingCount = 0,
                firstSeenSample = proposal.observation.startSample,
                lastSeenSample = proposal.observation.endSample,
                windowIds = mutableListOf(),
                missingWindows = 0,
                state = LocalSpeakerTrackState.ACTIVE,
            ).also(tracks::add)
            track.centroid = normalize(
                FloatArray(track.centroid.size) { index ->
                    track.centroid[index] * track.embeddingCount + proposal.embedding[index]
                },
            )
            track.embeddingCount += 1
            track.lastSeenSample = proposal.observation.endSample
            if (windowId !in track.windowIds) track.windowIds += windowId
            track.missingWindows = 0
            track.state = LocalSpeakerTrackState.ACTIVE
            assignedIds += track.localSpeakerId
            LocalSpeakerAssignment(
                windowSpeakerIndex = proposal.observation.windowSpeakerIndex,
                localSpeakerId = track.localSpeakerId,
                similarity = proposal.similarity,
            )
        }.sortedBy { it.windowSpeakerIndex }

        tracks.filterNot { it.localSpeakerId in assignedIds }.forEach { track ->
            track.missingWindows += 1
            track.state = if (track.missingWindows > maximumMissingWindows) {
                LocalSpeakerTrackState.CLOSED
            } else {
                LocalSpeakerTrackState.MISSING
            }
        }
        return result(assignments, emptyList())
    }

    private fun result(
        assignments: List<LocalSpeakerAssignment>,
        reasons: List<String>,
    ) = LocalSpeakerLinkResult(
        assignments = assignments,
        tracks = tracks.map { track ->
            LocalSpeakerTrack(
                localSpeakerId = track.localSpeakerId,
                firstSeenSample = track.firstSeenSample,
                lastSeenSample = track.lastSeenSample,
                windowIds = track.windowIds.toList(),
                soloSegments = emptyList(),
                state = track.state,
            )
        },
        holdReasons = reasons,
    )

    private fun nextLocalSpeakerId(): String =
        "local-speaker-${nextTrackNumber++.toString().padStart(3, '0')}"

    private fun normalize(values: FloatArray): FloatArray {
        require(values.isNotEmpty() && values.all(Float::isFinite))
        val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        require(norm > 0f)
        return FloatArray(values.size) { values[it] / norm }
    }

    private fun dot(left: FloatArray, right: FloatArray): Float {
        require(left.size == right.size)
        return left.indices.sumOf { (left[it] * right[it]).toDouble() }.toFloat()
    }

    private data class TrackState(
        val localSpeakerId: String,
        var centroid: FloatArray,
        var embeddingCount: Int,
        val firstSeenSample: Long,
        var lastSeenSample: Long,
        val windowIds: MutableList<String>,
        var missingWindows: Int,
        var state: LocalSpeakerTrackState,
    )

    private data class ProposedAssignment(
        val observation: WindowSpeakerObservation,
        val embedding: FloatArray,
        val track: TrackState?,
        val similarity: Float?,
    )
}
