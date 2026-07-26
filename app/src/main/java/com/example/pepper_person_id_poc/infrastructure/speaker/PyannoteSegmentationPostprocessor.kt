package com.example.pepper_person_id_poc.infrastructure.speaker

import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityFrame
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState

class PyannoteSegmentationPostprocessor {
    fun decodeFrames(classScores: List<FloatArray>): List<SpeakerActivityFrame> =
        classScores.map(::decodeFrame)

    fun decodeFrame(classScores: FloatArray): SpeakerActivityFrame {
        require(classScores.size == CLASS_COUNT) {
            "Expected $CLASS_COUNT powerset class scores, got ${classScores.size}"
        }
        require(classScores.all(Float::isFinite)) {
            "Segmentation class scores must be finite"
        }
        val winningClassIndex = classScores.indices.maxWithOrNull(
            compareBy<Int> { classScores[it] }.thenBy { -it },
        ) ?: error("Segmentation class scores are empty")
        val activeSpeakerIndices = CLASS_TO_ACTIVE_SPEAKERS.getValue(winningClassIndex)
        val state = when (activeSpeakerIndices.size) {
            0 -> SpeakerActivityState.SILENCE
            1 -> SpeakerActivityState.SINGLE_SPEAKER
            2 -> SpeakerActivityState.OVERLAPPED_SPEECH
            else -> SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS
        }
        return SpeakerActivityFrame(
            activityState = state,
            activeSpeakerIndices = activeSpeakerIndices,
            winningClassIndex = winningClassIndex,
            winningScore = classScores[winningClassIndex],
            overlapProbability = if (activeSpeakerIndices.size > 1) {
                classScores[winningClassIndex]
            } else {
                0f
            },
        )
    }

    private companion object {
        const val CLASS_COUNT = 7
        val CLASS_TO_ACTIVE_SPEAKERS = mapOf(
            0 to emptyList(),
            1 to listOf(0),
            2 to listOf(1),
            3 to listOf(2),
            4 to listOf(0, 1),
            5 to listOf(0, 2),
            6 to listOf(1, 2),
        )
    }
}
