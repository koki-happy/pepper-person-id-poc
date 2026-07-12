package com.example.pepper_person_id_poc.domain.audio

data class PcmUtterance(
    val pcm16: ShortArray,
    val sampleRate: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val voicedDurationMillis: Long,
) {
    val durationMillis: Long get() = endedAtMillis - startedAtMillis
    val sufficientForSpeakerIdentification: Boolean get() = voicedDurationMillis >= 1_000L
}
