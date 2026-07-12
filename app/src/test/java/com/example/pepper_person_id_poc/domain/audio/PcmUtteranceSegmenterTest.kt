package com.example.pepper_person_id_poc.domain.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PcmUtteranceSegmenterTest {
    private val sampleRate = 1_000
    private val chunk = ShortArray(100) { 1 }

    @Test
    fun process_ignoresSilenceBeforeSpeech() {
        val segmenter = PcmUtteranceSegmenter(sampleRate)

        val utterance = segmenter.process(chunk, chunkEndedAtMillis = 100L, speech = false)

        assertThat(utterance).isNull()
        assertThat(segmenter.speechActive).isFalse()
    }

    @Test
    fun process_completesAfterTrailingSilence() {
        val segmenter = PcmUtteranceSegmenter(sampleRate, endSilenceMillis = 200L)

        segmenter.process(chunk, chunkEndedAtMillis = 1_000L, speech = true)
        segmenter.process(chunk, chunkEndedAtMillis = 1_100L, speech = true)
        segmenter.process(chunk, chunkEndedAtMillis = 1_200L, speech = false)
        val utterance = segmenter.process(chunk, chunkEndedAtMillis = 1_300L, speech = false)

        assertThat(utterance).isNotNull()
        assertThat(utterance!!.startedAtMillis).isEqualTo(900L)
        assertThat(utterance.endedAtMillis).isEqualTo(1_300L)
        assertThat(utterance.voicedDurationMillis).isEqualTo(200L)
        assertThat(utterance.pcm16).hasLength(400)
        assertThat(utterance.sufficientForSpeakerIdentification).isFalse()
        assertThat(segmenter.speechActive).isFalse()
    }

    @Test
    fun flush_marksOneSecondOfVoiceAsSufficient() {
        val segmenter = PcmUtteranceSegmenter(sampleRate)
        repeat(10) { index ->
            segmenter.process(chunk, chunkEndedAtMillis = (index + 1) * 100L, speech = true)
        }

        val utterance = segmenter.flush(endedAtMillis = 1_000L)

        assertThat(utterance!!.voicedDurationMillis).isEqualTo(1_000L)
        assertThat(utterance.sufficientForSpeakerIdentification).isTrue()
    }
}
