package com.example.pepper_person_id_poc.infrastructure.face

data class FaceFeatureObservation(
    val trackId: String,
    val embedding: FloatArray,
    val embeddingTimeMillis: Long,
)
