package com.example.pepper_person_id_poc.infrastructure.face

import org.opencv.core.Mat

class UnavailableFaceEmbeddingEngine(
    override val modelName: String,
    private val reason: String,
) : FaceEmbeddingEngine {
    override fun prepare(): Unit = error(reason)

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray = error(reason)
}
