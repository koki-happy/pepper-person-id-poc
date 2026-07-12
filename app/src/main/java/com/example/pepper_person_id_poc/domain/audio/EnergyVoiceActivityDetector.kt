package com.example.pepper_person_id_poc.domain.audio

class EnergyVoiceActivityDetector(
    private val speechThresholdDbFs: Float = -40f,
) {
    fun isSpeech(samples: ShortArray): Boolean = AudioLevel.dbFs(samples) >= speechThresholdDbFs
}
