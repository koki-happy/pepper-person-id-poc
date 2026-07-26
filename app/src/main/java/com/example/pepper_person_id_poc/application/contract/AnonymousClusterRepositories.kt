package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationEvaluation
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId

interface AnonymousClusterRepository {
    fun getAll(): List<AnonymousCluster>
    fun evaluate(
        modelSpaceId: ModelSpaceId,
        embedding: FloatArray,
        threshold: Float,
        minimumLead: Float,
    ): IdentificationEvaluation = error("This repository does not support non-mutating evaluation")
    fun apply(
        operation: PersistenceOperation,
        modelSpaceId: ModelSpaceId,
        embedding: FloatArray,
        selectedAnonymousId: String?,
        maximumUpdateCount: Int,
        nowElapsedRealtime: Long,
    ): AnonymousCluster? = error("This repository does not support explicit persistence operations")
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
