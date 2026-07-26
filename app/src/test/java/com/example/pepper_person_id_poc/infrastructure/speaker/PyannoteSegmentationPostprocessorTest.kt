package com.example.pepper_person_id_poc.infrastructure.speaker

import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PyannoteSegmentationPostprocessorTest {
    private val postprocessor = PyannoteSegmentationPostprocessor()

    @Test
    fun classZeroDecodesToSilence() {
        val frame = postprocessor.decodeFrame(scores(winner = 0))

        assertThat(frame.activityState).isEqualTo(SpeakerActivityState.SILENCE)
        assertThat(frame.activeSpeakerIndices).isEmpty()
        assertThat(frame.activeSpeakerCount).isEqualTo(0)
        assertThat(frame.overlapProbability).isEqualTo(0f)
    }

    @Test
    fun singleSpeakerClassesDecodeToTheirLocalSpeaker() {
        assertThat(postprocessor.decodeFrame(scores(winner = 1)).activeSpeakerIndices)
            .containsExactly(0)
        assertThat(postprocessor.decodeFrame(scores(winner = 2)).activeSpeakerIndices)
            .containsExactly(1)
        assertThat(postprocessor.decodeFrame(scores(winner = 3)).activeSpeakerIndices)
            .containsExactly(2)
    }

    @Test
    fun overlapClassesDecodeToTheDocumentedSpeakerPairs() {
        assertThat(postprocessor.decodeFrame(scores(winner = 4)).activeSpeakerIndices)
            .containsExactly(0, 1).inOrder()
        assertThat(postprocessor.decodeFrame(scores(winner = 5)).activeSpeakerIndices)
            .containsExactly(0, 2).inOrder()
        assertThat(postprocessor.decodeFrame(scores(winner = 6)).activeSpeakerIndices)
            .containsExactly(1, 2).inOrder()
    }

    @Test
    fun overlapClassIsReportedFailClosed() {
        val frame = postprocessor.decodeFrame(
            FloatArray(7).also {
                it[0] = 0.25f
                it[4] = 0.75f
            },
        )

        assertThat(frame.activityState).isEqualTo(SpeakerActivityState.OVERLAPPED_SPEECH)
        assertThat(frame.activeSpeakerCount).isEqualTo(2)
        assertThat(frame.overlapProbability).isEqualTo(0.75f)
    }

    @Test
    fun lowerClassIndexWinsAnExactTieDeterministically() {
        val tiedScores = FloatArray(7).also {
            it[1] = 0.5f
            it[2] = 0.5f
        }

        val frame = postprocessor.decodeFrame(tiedScores)

        assertThat(frame.activeSpeakerIndices).containsExactly(0)
    }

    @Test
    fun frameWithWrongClassCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            postprocessor.decodeFrame(FloatArray(6))
        }
        assertThrows(IllegalArgumentException::class.java) {
            postprocessor.decodeFrame(FloatArray(8))
        }
    }

    @Test
    fun nonFiniteClassScoresAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            postprocessor.decodeFrame(scores(winner = 1).also { it[3] = Float.NaN })
        }
        assertThrows(IllegalArgumentException::class.java) {
            postprocessor.decodeFrame(
                scores(winner = 1).also { it[3] = Float.POSITIVE_INFINITY },
            )
        }
    }

    @Test
    fun allFramesAreDecodedWithoutDroppingOrder() {
        val frames = postprocessor.decodeFrames(
            listOf(
                scores(winner = 0),
                scores(winner = 2),
                scores(winner = 6),
            ),
        )

        assertThat(frames.map { it.activityState }).containsExactly(
            SpeakerActivityState.SILENCE,
            SpeakerActivityState.SINGLE_SPEAKER,
            SpeakerActivityState.OVERLAPPED_SPEECH,
        ).inOrder()
    }

    private fun scores(
        winner: Int,
        winningScore: Float = 1f,
    ): FloatArray = FloatArray(7).also {
        it[winner] = winningScore
    }
}
