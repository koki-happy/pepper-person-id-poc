package com.example.pepper_person_id_poc.testsupport

import com.example.pepper_person_id_poc.application.contract.AnonymousClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.io.File
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousCluster
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterEngine
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult

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
        val normalized = AnonymousClusterEngine.normalize(embedding)
        val match = AnonymousClusterEngine.findBestMatch(
            normalized,
            clusters,
            modelId,
            reservedAnonymousIds,
        )
        val best = match.bestCluster
        val isNew = best == null || checkNotNull(match.bestScore) < threshold
        val target = if (isNew) {
            AnonymousCluster("$prefix-${nextId++.toString().padStart(3, '0')}", modelId, embedding.size, FloatArray(embedding.size), normalized, 0, nowMillis, nowMillis)
        } else best!!
        val updated = AnonymousClusterEngine.addEmbedding(target, normalized, maximumUpdateCount, nowMillis)
        clusters.removeAll { it.anonymousId == updated.anonymousId }
        clusters += updated
        val currentModelCount = clusters.count {
            it.modelId == modelId && it.embeddingDimension == embedding.size
        }
        return AnonymousIdentificationResult(
            updated.anonymousId,
            modelId,
            match.bestScore,
            threshold,
            isNew,
            updated.updateCount,
            maximumUpdateCount,
            currentModelCount,
            clusters.size,
            match.candidates.map {
                it.copy(selected = !isNew && it.anonymousId == updated.anonymousId)
            },
        )
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
