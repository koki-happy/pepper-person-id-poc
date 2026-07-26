package com.example.pepper_person_id_poc.infrastructure.face

import org.opencv.core.Mat

data class FaceEmbeddingExtraction(
    val embedding: FloatArray,
    val preprocessingMillis: Long? = null,
    val alignmentMillis: Long? = null,
    val embeddingMillis: Long,
)

interface FaceEmbeddingEngine : AutoCloseable {
    val modelName: String
    fun prepare()
    fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray
    fun extractMeasured(imageBgr: Mat, detectedFace: Mat): FaceEmbeddingExtraction {
        val started = System.nanoTime()
        val embedding = extract(imageBgr, detectedFace)
        return FaceEmbeddingExtraction(
            embedding = embedding,
            embeddingMillis = (System.nanoTime() - started) / 1_000_000L,
        )
    }
    override fun close() = Unit
}
