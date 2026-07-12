package com.example.pepper_person_id_poc.application.contract

import java.io.Closeable

interface VoiceActivityDetector : Closeable {
    val modelName: String
    fun isSpeech(samples: ShortArray): Boolean
    fun reset()
}
