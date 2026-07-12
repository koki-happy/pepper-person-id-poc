package com.example.pepper_person_id_poc.domain.audio

import com.example.pepper_person_id_poc.application.contract.VoiceActivityDetector

class EnergyVoiceActivityDetector(
    private val speechThresholdDbFs: Float = -40f,
) : VoiceActivityDetector {
    override val modelName: String = "Energy VAD (fallback)"
    override fun isSpeech(samples: ShortArray): Boolean = AudioLevel.dbFs(samples) >= speechThresholdDbFs
    override fun reset() = Unit
    override fun close() = Unit
}
