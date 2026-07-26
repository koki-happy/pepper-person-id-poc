package com.example.pepper_person_id_poc.domain.speaker

enum class SpeakerActivityState {
    SILENCE,
    SINGLE_SPEAKER,
    OVERLAPPED_SPEECH,
    MULTIPLE_ACTIVE_SPEAKERS,
    UNSUPPORTED,
    ERROR,
}

data class SpeakerActivityFrame(
    val activityState: SpeakerActivityState,
    val activeSpeakerIndices: List<Int>,
    val winningClassIndex: Int,
    val winningScore: Float,
    val overlapProbability: Float,
) {
    val activeSpeakerCount: Int get() = activeSpeakerIndices.size
}

data class DiarizationWindow(
    val windowId: String,
    val startSample: Long,
    val endSample: Long,
    val sampleRate: Int,
    val frames: List<SpeakerActivityFrame>,
    val overlapRatio: Float,
    val runtimeId: String,
    val inferenceTimeMillis: Long,
)

data class DiarizedSpeakerSegment(
    val localSpeakerId: String,
    val startSample: Long,
    val endSample: Long,
    val activityState: SpeakerActivityState,
    val confidence: Float,
    val isSolo: Boolean,
)

enum class LocalSpeakerTrackState {
    ACTIVE,
    MISSING,
    CLOSED,
}

data class LocalSpeakerTrack(
    val localSpeakerId: String,
    val firstSeenSample: Long,
    val lastSeenSample: Long,
    val windowIds: List<String>,
    val soloSegments: List<DiarizedSpeakerSegment>,
    val state: LocalSpeakerTrackState,
)
