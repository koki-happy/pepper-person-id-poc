package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.face.FaceQualityAssessment

data class FaceFeatureObservation(
    val trackId: String,
    val embedding: FloatArray,
    val embeddingTimeMillis: Long,
    val qualityAssessment: FaceQualityAssessment?,
)
