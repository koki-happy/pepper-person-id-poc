package com.example.pepper_person_id_poc.testsupport

import com.example.pepper_person_id_poc.application.contract.AnonymousClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.io.File
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import kotlin.math.sqrt

open class InMemoryAnonymousRepository(private val prefix: String) : AnonymousClusterRepository {
    private val clusters = mutableListOf<AnonymousCluster>()
    private var nextId = 1

    override fun getAll() = clusters.map(AnonymousCluster::deepCopy)
    override fun count() = clusters.size
    override fun deleteAll() {
        clusters.clear()
        nextId = 1
    }

    override fun identify(
        modelId: String,
        embedding: FloatArray,
        threshold: Float,
        maximumUpdateCount: Int,
        nowMillis: Long,
        reservedAnonymousIds: Set<String>,
    ): AnonymousIdentificationResult {
        val normalized = normalize(embedding)
        val best = clusters.filter { it.modelId == modelId && it.embeddingDimension == embedding.size && it.anonymousId !in reservedAnonymousIds }
            .maxByOrNull { cluster -> cluster.centroid.indices.sumOf { cluster.centroid[it].toDouble() * normalized[it] } }
        val score = best?.centroid?.indices?.sumOf { best.centroid[it].toDouble() * normalized[it] }?.toFloat() ?: 1f
        val isNew = best == null || score < threshold
        val target = if (isNew) {
            AnonymousCluster("$prefix-${nextId++.toString().padStart(3, '0')}", modelId, embedding.size, FloatArray(embedding.size), normalized, 0, nowMillis, nowMillis)
        } else best!!
        val sum = target.normalizedEmbeddingSum.copyOf()
        if (target.updateCount < maximumUpdateCount) sum.indices.forEach { sum[it] += normalized[it] }
        val updated = if (target.updateCount < maximumUpdateCount) target.copy(
            normalizedEmbeddingSum = sum,
            centroid = normalize(sum),
            updateCount = target.updateCount + 1,
            updatedAtMillis = nowMillis,
        ) else target
        clusters.removeAll { it.anonymousId == updated.anonymousId }
        clusters += updated
        val candidateScores = clusters
            .filter { it.modelId == modelId && it.embeddingDimension == embedding.size }
            .map {
                AnonymousClusterScore(
                    it.anonymousId,
                    it.centroid.indices.sumOf { index -> it.centroid[index].toDouble() * normalized[index] }.toFloat(),
                    it.anonymousId == updated.anonymousId,
                )
            }
            .sortedByDescending(AnonymousClusterScore::score)
        return AnonymousIdentificationResult(
            updated.anonymousId, modelId, if (isNew) 1f else score, threshold, isNew,
            updated.updateCount, maximumUpdateCount, clusters.size, candidateScores,
        )
    }

    private fun normalize(input: FloatArray): FloatArray {
        val norm = sqrt(input.sumOf { it.toDouble() * it })
        require(norm > 0)
        return FloatArray(input.size) { (input[it] / norm).toFloat() }
    }
}

class InMemoryAnonymousFaceRepository : InMemoryAnonymousRepository("anonymous-face"), AnonymousFaceClusterRepository
class InMemoryAnonymousSpeakerRepository : InMemoryAnonymousRepository("anonymous-speaker"), AnonymousSpeakerClusterRepository

class FakeSpeakerEmbeddingEngine(private val embedding: FloatArray) : SpeakerEmbeddingEngine {
    override val modelName = "speaker-model"
    override val embeddingDimension: Int = embedding.size
    override fun prepare() = Unit
    override fun extract(pcm16: ShortArray, sampleRate: Int) = embedding.copyOf()
    override fun close() = Unit
}

class FakeBenchmarkLogger : BenchmarkLogger {
    val events = mutableListOf<BenchmarkEvent>()
    override fun append(event: BenchmarkEvent) { events += event }
    override fun outputFile() = File("benchmark.jsonl")
    override fun readRecent(limit: Int) = emptyList<String>()
    override fun deleteAll() { events.clear() }
}
