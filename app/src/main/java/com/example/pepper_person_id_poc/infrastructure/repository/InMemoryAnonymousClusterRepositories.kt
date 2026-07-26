package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.application.contract.AnonymousClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterEngine
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationDecision
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationEvaluation
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationEvaluator
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.BiometricModality
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId

abstract class InMemoryAnonymousClusterRepository(
    private val modality: BiometricModality,
    private val idPrefix: String,
) : AnonymousClusterRepository {
    private val clusters = mutableListOf<AnonymousCluster>()
    private var nextId = 1

    @Synchronized
    override fun getAll(): List<AnonymousCluster> = clusters.map(AnonymousCluster::deepCopy)

    @Synchronized
    override fun count(): Int = clusters.size

    @Synchronized
    override fun evaluate(
        modelSpaceId: ModelSpaceId,
        embedding: FloatArray,
        threshold: Float,
        minimumLead: Float,
    ): IdentificationEvaluation {
        val compatibleClusters = clusters.filter {
            it.modelSpaceId == modelSpaceId && it.embeddingDimension == embedding.size
        }
        return IdentificationEvaluator.evaluate(
            modelSpaceId = modelSpaceId,
            embedding = embedding,
            clusters = compatibleClusters,
            threshold = threshold,
            minimumLead = minimumLead,
        )
    }

    @Synchronized
    override fun apply(
        operation: PersistenceOperation,
        modelSpaceId: ModelSpaceId,
        embedding: FloatArray,
        selectedAnonymousId: String?,
        maximumUpdateCount: Int,
        nowElapsedRealtime: Long,
    ): AnonymousCluster? {
        require(maximumUpdateCount in 1..100)
        if (operation == PersistenceOperation.HOLD) return null

        val normalized = AnonymousClusterEngine.normalize(embedding)
        return when (operation) {
            PersistenceOperation.CREATE -> {
                require(selectedAnonymousId == null)
                val anonymousId = "$idPrefix-${nextId.toString().padStart(3, '0')}"
                nextId += 1
                AnonymousCluster(
                    anonymousId = anonymousId,
                    modelId = modelSpaceId.value,
                    embeddingDimension = normalized.size,
                    normalizedEmbeddingSum = normalized.copyOf(),
                    centroid = normalized.copyOf(),
                    updateCount = 1,
                    createdAtMillis = nowElapsedRealtime,
                    updatedAtMillis = nowElapsedRealtime,
                    modality = modality,
                    modelSpaceId = modelSpaceId,
                    createdAtElapsedRealtime = nowElapsedRealtime,
                    updatedAtElapsedRealtime = nowElapsedRealtime,
                ).also(clusters::add)
            }
            PersistenceOperation.UPDATE -> {
                val anonymousId = requireNotNull(selectedAnonymousId)
                val index = clusters.indexOfFirst {
                    it.anonymousId == anonymousId &&
                        it.modelSpaceId == modelSpaceId &&
                        it.embeddingDimension == normalized.size
                }
                require(index >= 0) { "Selected cluster is not compatible: $anonymousId" }
                val current = clusters[index]
                val updated = AnonymousClusterEngine.addEmbedding(
                    cluster = current,
                    normalizedEmbedding = normalized,
                    maximumUpdateCount = maximumUpdateCount,
                    nowMillis = nowElapsedRealtime,
                ).copy(
                    updatedAtElapsedRealtime = if (current.updateCount < maximumUpdateCount) {
                        nowElapsedRealtime
                    } else {
                        current.updatedAtElapsedRealtime
                    },
                )
                clusters[index] = updated
                updated.deepCopy()
            }
            PersistenceOperation.HOLD -> error("Handled before normalization")
        }?.deepCopy()
    }

    @Synchronized
    override fun identify(
        modelId: String,
        embedding: FloatArray,
        threshold: Float,
        maximumUpdateCount: Int,
        nowMillis: Long,
        reservedAnonymousIds: Set<String>,
    ): AnonymousIdentificationResult {
        val modelSpaceId = ModelSpaceId(modelId)
        val evaluation = evaluate(
            modelSpaceId = modelSpaceId,
            embedding = embedding,
            threshold = threshold,
            minimumLead = 0f,
        )
        val selected = evaluation.candidates.firstOrNull { it.selected }
        val operation = if (evaluation.decision == IdentificationDecision.MATCHED_EXISTING) {
            PersistenceOperation.UPDATE
        } else {
            PersistenceOperation.CREATE
        }
        val updated = requireNotNull(
            apply(
                operation = operation,
                modelSpaceId = modelSpaceId,
                embedding = embedding,
                selectedAnonymousId = selected?.anonymousId,
                maximumUpdateCount = maximumUpdateCount,
                nowElapsedRealtime = nowMillis,
            ),
        )
        return AnonymousIdentificationResult(
            anonymousId = updated.anonymousId,
            modelId = modelId,
            bestExistingScore = evaluation.highestScore,
            threshold = threshold,
            isNewCluster = operation == PersistenceOperation.CREATE,
            updateCount = updated.updateCount,
            maximumUpdateCount = maximumUpdateCount,
            currentModelClusterCount = clusters.count {
                it.modelSpaceId == modelSpaceId && it.embeddingDimension == embedding.size
            },
            totalClusterCount = clusters.size,
            candidateScores = evaluation.candidates.map {
                AnonymousClusterScore(
                    anonymousId = it.anonymousId,
                    score = it.score,
                    selected = it.anonymousId == updated.anonymousId &&
                        operation == PersistenceOperation.UPDATE,
                )
            },
        )
    }

    @Synchronized
    override fun deleteAll() {
        clusters.clear()
        nextId = 1
    }
}

class InMemoryAnonymousFaceClusterRepository :
    InMemoryAnonymousClusterRepository(BiometricModality.FACE, "anonymous-face"),
    AnonymousFaceClusterRepository

class InMemoryAnonymousSpeakerClusterRepository :
    InMemoryAnonymousClusterRepository(BiometricModality.SPEAKER, "anonymous-speaker"),
    AnonymousSpeakerClusterRepository
