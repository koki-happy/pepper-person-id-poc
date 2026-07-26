package com.example.pepper_person_id_poc.infrastructure.speaker

import android.content.Context
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

class SherpaOnnxSpeakerEmbeddingEngine(
    context: Context,
    private val model: SpeakerModelOption,
    private val numThreads: Int = 1,
) : SpeakerEmbeddingEngine {
    private val assets = context.applicationContext.assets
    private var extractor: SpeakerEmbeddingExtractor? = null

    // This value is persisted with enrolled embeddings. Keep it independent from UI wording.
    override val modelName: String = model.configModelId
    override val embeddingDimension: Int? get() = extractor?.dim()

    @Synchronized
    override fun prepare() {
        if (extractor != null) return
        require(numThreads > 0)
        val created = SpeakerEmbeddingExtractor(
            assetManager = assets,
            config = SpeakerEmbeddingExtractorConfig(
                model = "models/${model.modelFileName}",
                numThreads = numThreads,
                debug = false,
                provider = "cpu",
            ),
        )
        val actualDimension = created.dim()
        if (actualDimension != model.embeddingSize) {
            created.release()
            error(
                "Speaker model ${model.configModelId} produced metadata dimension=$actualDimension; " +
                    "expected=${model.embeddingSize}; no fallback",
            )
        }
        extractor = created
    }

    @Synchronized
    override fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray {
        require(sampleRate == REQUIRED_SAMPLE_RATE) {
            "$modelName requires $REQUIRED_SAMPLE_RATE Hz PCM, actual=$sampleRate"
        }
        require(pcm16.isNotEmpty())
        prepare()
        val activeExtractor = checkNotNull(extractor)
        val stream = activeExtractor.createStream()
        return try {
            pcm16.asFloatSamples().asList().chunked(INPUT_CHUNK_SAMPLES).forEach { chunk ->
                stream.acceptWaveform(chunk.toFloatArray(), sampleRate)
            }
            stream.inputFinished()
            require(activeExtractor.isReady(stream)) { "INSUFFICIENT_AUDIO: speaker model input is not ready" }
            activeExtractor.compute(stream).also { embedding ->
                require(embedding.size == model.embeddingSize) {
                    "Speaker model returned dimension=${embedding.size}; expected=${model.embeddingSize}; no fallback"
                }
                require(embedding.isNotEmpty() && embedding.all(Float::isFinite)) {
                    "Speaker model returned an invalid embedding"
                }
            }
        } finally {
            stream.release()
        }
    }

    @Synchronized
    override fun close() {
        extractor?.release()
        extractor = null
    }

    private fun ShortArray.asFloatSamples(): FloatArray = FloatArray(size) { index ->
        this[index] / PCM_NORMALIZATION
    }

    private companion object {
        const val REQUIRED_SAMPLE_RATE = 16_000
        const val INPUT_CHUNK_SAMPLES = 1_600
        const val PCM_NORMALIZATION = 32_768f
    }
}
