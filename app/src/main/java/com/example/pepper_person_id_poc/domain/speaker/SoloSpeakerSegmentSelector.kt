package com.example.pepper_person_id_poc.domain.speaker

data class SoloSpeakerSegmentSelection(
    val soloSegments: List<DiarizedSpeakerSegment>,
    val persistenceEligible: Boolean,
    val holdReasons: List<String>,
)

class SoloSpeakerSegmentSelector {
    fun select(segments: List<DiarizedSpeakerSegment>): SoloSpeakerSegmentSelection {
        val terminalReasons = segments.mapNotNull { segment ->
            when (segment.activityState) {
                SpeakerActivityState.UNSUPPORTED -> "UNSUPPORTED_ACTIVITY_INFERENCE"
                SpeakerActivityState.ERROR -> "ACTIVITY_INFERENCE_ERROR"
                SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS -> "MULTIPLE_ACTIVE_SPEAKERS"
                else -> null
            }
        }.distinct()
        if (terminalReasons.isNotEmpty()) {
            return SoloSpeakerSegmentSelection(emptyList(), false, terminalReasons)
        }

        val solo = segments.filter {
            it.isSolo && it.activityState == SpeakerActivityState.SINGLE_SPEAKER
        }
        if (solo.isNotEmpty()) {
            return SoloSpeakerSegmentSelection(solo, true, emptyList())
        }
        val reasons = if (
            segments.isNotEmpty() &&
            segments.all { it.activityState == SpeakerActivityState.OVERLAPPED_SPEECH }
        ) {
            listOf("COMPLETE_OVERLAP")
        } else {
            listOf("NO_SOLO_SPEECH")
        }
        return SoloSpeakerSegmentSelection(emptyList(), false, reasons)
    }
}
