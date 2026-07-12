package com.example.pepper_person_id_poc.infrastructure.face

import org.opencv.core.Mat

interface FaceEmbeddingEngine {
    val modelName: String
    fun prepare()
    fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray
}
