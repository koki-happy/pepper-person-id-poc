package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import java.io.Closeable

interface SpeakerSegmentationEngine : Closeable {
    val artifactId: ArtifactId
    val runtimeId: RuntimeId
    val requiredWindowSamples: Int?
        get() = null

    fun prepare()

    fun segment(input: SpeakerSegmentationInput): DiarizationWindow
}

class SpeakerSegmentationInput(
    val windowId: String,
    val startSample: Long,
    val pcm16: ShortArray,
    val sampleRate: Int = REQUIRED_SAMPLE_RATE,
) {
    init {
        require(windowId.isNotBlank()) { "windowId must not be blank" }
        require(startSample >= 0L) { "startSample must not be negative" }
        require(pcm16.isNotEmpty()) { "pcm16 must not be empty" }
        require(sampleRate == REQUIRED_SAMPLE_RATE) {
            "Speaker segmentation requires 16 kHz mono PCM16 input"
        }
    }

    val endSample: Long
        get() = Math.addExact(startSample, pcm16.size.toLong())

    companion object {
        const val REQUIRED_SAMPLE_RATE = 16_000
    }
}
