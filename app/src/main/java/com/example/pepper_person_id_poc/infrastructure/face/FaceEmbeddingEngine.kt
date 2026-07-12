package com.example.pepper_person_id_poc.infrastructure.face

import org.opencv.core.Mat

interface FaceEmbeddingEngine : AutoCloseable {
    val modelName: String
    fun prepare()
    fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray
    override fun close() = Unit
}
