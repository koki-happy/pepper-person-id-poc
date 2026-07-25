package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult

interface AnonymousClusterRepository {
    fun getAll(): List<AnonymousCluster>
    fun identify(
        modelId: String,
        embedding: FloatArray,
        threshold: Float,
        maximumUpdateCount: Int,
        nowMillis: Long = System.currentTimeMillis(),
        reservedAnonymousIds: Set<String> = emptySet(),
    ): AnonymousIdentificationResult
    fun count(): Int
    fun deleteAll()
}

interface AnonymousFaceClusterRepository : AnonymousClusterRepository
interface AnonymousSpeakerClusterRepository : AnonymousClusterRepository
