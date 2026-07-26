package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.face.FaceQualityAssessment

data class FaceFeatureObservation(
    val trackId: String,
    val embedding: FloatArray,
    val embeddingTimeMillis: Long,
    val qualityAssessment: FaceQualityAssessment?,
    val preprocessingTimeMillis: Long? = null,
    val detectionTimeMillis: Long? = null,
    val qualityTimeMillis: Long? = null,
    val alignmentTimeMillis: Long? = null,
)
