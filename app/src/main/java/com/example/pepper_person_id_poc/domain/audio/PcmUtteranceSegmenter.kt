package com.example.pepper_person_id_poc.domain.audio

class PcmUtteranceSegmenter(
    private val sampleRate: Int,
    private val endSilenceMillis: Long = 500L,
    private val maximumUtteranceMillis: Long = 10_000L,
) {
    private val chunks = mutableListOf<ShortArray>()
    private var startedAtMillis: Long? = null
    private var totalSamples = 0
    private var voicedSamples = 0
    private var trailingSilenceSamples = 0
    private var vadProcessingMillis: Long? = null

    val speechActive: Boolean get() = startedAtMillis != null

    fun process(
        samples: ShortArray,
        chunkEndedAtMillis: Long,
        speech: Boolean,
        vadProcessingMillis: Long? = null,
    ): PcmUtterance? {
        vadProcessingMillis?.let { measuredMillis ->
            this.vadProcessingMillis = (this.vadProcessingMillis ?: 0L) + measuredMillis.coerceAtLeast(0L)
        }
        if (!speechActive && !speech) return null
        if (!speechActive) {
            startedAtMillis = chunkEndedAtMillis - samples.size.toDurationMillis()
        }
        chunks += samples.copyOf()
        totalSamples += samples.size
        if (speech) {
            voicedSamples += samples.size
            trailingSilenceSamples = 0
        } else {
            trailingSilenceSamples += samples.size
        }

        val shouldComplete = trailingSilenceSamples.toDurationMillis() >= endSilenceMillis ||
            totalSamples.toDurationMillis() >= maximumUtteranceMillis
        return if (shouldComplete) complete(chunkEndedAtMillis) else null
    }

    fun flush(endedAtMillis: Long): PcmUtterance? =
        if (speechActive) complete(endedAtMillis) else null

    private fun complete(endedAtMillis: Long): PcmUtterance {
        val combined = ShortArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(combined, destinationOffset = offset)
            offset += chunk.size
        }
        val utterance = PcmUtterance(
            pcm16 = combined,
            sampleRate = sampleRate,
            startedAtMillis = checkNotNull(startedAtMillis),
            endedAtMillis = endedAtMillis,
            voicedDurationMillis = voicedSamples.toDurationMillis(),
            vadProcessingMillis = vadProcessingMillis,
        )
        chunks.clear()
        startedAtMillis = null
        totalSamples = 0
        voicedSamples = 0
        trailingSilenceSamples = 0
        vadProcessingMillis = null
        return utterance
    }

    private fun Int.toDurationMillis(): Long = this * 1_000L / sampleRate
}
