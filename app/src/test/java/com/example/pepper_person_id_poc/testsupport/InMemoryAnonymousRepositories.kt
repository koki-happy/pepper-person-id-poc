package com.example.pepper_person_id_poc.testsupport

import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.model.BiometricModality
import com.example.pepper_person_id_poc.infrastructure.repository.InMemoryAnonymousClusterRepository
import java.io.File

open class InMemoryAnonymousRepository(prefix: String) :
    InMemoryAnonymousClusterRepository(
        modality = if (prefix.contains("speaker")) {
            BiometricModality.SPEAKER
        } else {
            BiometricModality.FACE
        },
        idPrefix = prefix,
    )

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
