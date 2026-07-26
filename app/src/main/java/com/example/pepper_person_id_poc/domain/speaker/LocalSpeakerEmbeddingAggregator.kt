package com.example.pepper_person_id_poc.domain.speaker

import kotlin.math.sqrt

data class SpeakerSegmentEmbedding(
    val localSpeakerId: String,
    val embedding: FloatArray,
    val durationMillis: Long,
)

data class LocalSpeakerEmbeddingAggregate(
    val localSpeakerId: String,
    val embedding: FloatArray,
    val segmentCount: Int,
    val totalDurationMillis: Long,
)

class LocalSpeakerEmbeddingAggregator {
    fun aggregate(segments: List<SpeakerSegmentEmbedding>): List<LocalSpeakerEmbeddingAggregate> =
        segments.groupBy { it.localSpeakerId }
            .toSortedMap()
            .map { (localSpeakerId, speakerSegments) ->
                require(localSpeakerId.isNotBlank())
                val dimension = speakerSegments.first().embedding.size
                require(dimension > 0)
                require(speakerSegments.all {
                    it.durationMillis > 0L &&
                        it.embedding.size == dimension &&
                        it.embedding.all(Float::isFinite)
                })
                val totalDuration = speakerSegments.sumOf { it.durationMillis }
                val weighted = FloatArray(dimension)
                speakerSegments.forEach { segment ->
                    segment.embedding.indices.forEach { index ->
                        weighted[index] += segment.embedding[index] * segment.durationMillis
                    }
                }
                val norm = sqrt(weighted.sumOf { it.toDouble() * it.toDouble() }).toFloat()
                require(norm > 0f)
                LocalSpeakerEmbeddingAggregate(
                    localSpeakerId = localSpeakerId,
                    embedding = FloatArray(dimension) { weighted[it] / norm },
                    segmentCount = speakerSegments.size,
                    totalDurationMillis = totalDuration,
                )
            }
}
