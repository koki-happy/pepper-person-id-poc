package com.example.pepper_person_id_poc.application.contract

import java.io.Closeable

interface SpeakerEmbeddingEngine : Closeable {
    val modelName: String
    val embeddingDimension: Int?
    fun prepare()
    fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray
}
