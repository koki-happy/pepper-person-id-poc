package com.example.pepper_person_id_poc.speakerbenchmark.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.pepper_person_id_poc.speakerbenchmark.config.ResolvedModelConfig
import com.example.pepper_person_id_poc.speakerbenchmark.features.FeatureNormalization
import com.example.pepper_person_id_poc.speakerbenchmark.features.LogMelFbank
import com.example.pepper_person_id_poc.speakerbenchmark.features.LogMelFbankConfig
import com.example.pepper_person_id_poc.speakerbenchmark.features.MelScale
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.time.TimeSource

interface SpeakerModelAdapter : AutoCloseable {
    val modelName: String
    val embeddingDimension: Int
    val loadMilliseconds: Double
    val metadata: Map<String, String>
    val runtimeVersion: String

    fun extractEmbedding(samples: FloatArray, sampleRate: Int): FloatArray
}

object SpeakerModelAdapterFactory {
    fun create(config: ResolvedModelConfig, numThreads: Int = 1): SpeakerModelAdapter =
        when (config.adapter.lowercase()) {
            "campplus" -> CampPlusAdapter(config, numThreads)
            "campplus-zh-en" -> CampPlusZhEnAdapter(config, numThreads)
            "eres2net" -> ERes2NetAdapter(config, numThreads)
            "speakernet" -> SpeakerNetAdapter(config, numThreads)
            "titanet" -> TitaNetAdapter(config, numThreads)
            else -> throw IllegalArgumentException("Unsupported speaker model adapter: ${config.adapter}")
        }
}

class CampPlusAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.THREE_D_SPEAKER)

class CampPlusZhEnAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.THREE_D_SPEAKER)

class ERes2NetAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.THREE_D_SPEAKER)

class SpeakerNetAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.NEMO)

class TitaNetAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.NEMO)

enum class ModelFamily {
    THREE_D_SPEAKER,
    NEMO,
}

open class OrtSpeakerModelAdapter internal constructor(
    private val config: ResolvedModelConfig,
    numThreads: Int,
    private val family: ModelFamily,
) : SpeakerModelAdapter {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions()
    private val session: OrtSession
    private val featureExtractor: LogMelFbank
    private val generalInputName: String?
    private val nemoSignalInputName: String?
    private val nemoLengthInputName: String?
    private val embeddingOutputIndex: Int

    final override val modelName: String = config.name
    final override val loadMilliseconds: Double
    final override val metadata: Map<String, String>
    final override val runtimeVersion: String
    final override val embeddingDimension: Int

    init {
        require(numThreads > 0) { "numThreads must be positive" }
        require(Sha256.matches(config.modelPath, config.expectedSha256)) {
            "Model SHA-256 mismatch: ${config.modelPath}"
        }
        sessionOptions.setIntraOpNumThreads(numThreads)
        sessionOptions.setInterOpNumThreads(1)
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)

        val loadStarted = TimeSource.Monotonic.markNow()
        session = environment.createSession(config.modelPath.toString(), sessionOptions)
        loadMilliseconds = loadStarted.elapsedNow().inWholeNanoseconds / 1_000_000.0
        runtimeVersion = environment.version
        metadata = session.metadata.customMetadata.toSortedMap()

        val framework = metadata.required("framework")
        val expectedFramework = when (family) {
            ModelFamily.THREE_D_SPEAKER -> "3d-speaker"
            ModelFamily.NEMO -> "nemo"
        }
        require(framework == expectedFramework) {
            "${config.name} uses framework '$framework', expected '$expectedFramework'"
        }
        val modelSampleRate = metadata.requiredInt("sample_rate")
        require(modelSampleRate == config.sampleRate) {
            "${config.name} metadata sample_rate=$modelSampleRate, config=${config.sampleRate}"
        }
        embeddingDimension = metadata.requiredInt("output_dim")
        require(config.embeddingDimension == null || config.embeddingDimension == embeddingDimension) {
            "${config.name} metadata output_dim=$embeddingDimension, config=${config.embeddingDimension}"
        }

        when (family) {
            ModelFamily.THREE_D_SPEAKER -> {
                require(session.inputNames.size == 1) {
                    "${config.name} must expose one 3D-Speaker feature input: ${session.inputNames}"
                }
                generalInputName = session.inputNames.single()
                nemoSignalInputName = null
                nemoLengthInputName = null
                embeddingOutputIndex = 0
                val featureNormalization = when (metadata["feature_normalize_type"].orEmpty()) {
                    "" -> FeatureNormalization.NONE
                    "global-mean" -> FeatureNormalization.GLOBAL_MEAN
                    else -> error(
                        "Unsupported 3D-Speaker feature_normalize_type=" +
                            metadata["feature_normalize_type"],
                    )
                }
                featureExtractor = LogMelFbank(
                    LogMelFbankConfig(
                        sampleRate = modelSampleRate,
                        numBins = 80,
                        frameLengthMillis = 25,
                        frameShiftMillis = 10,
                        lowFrequency = 20f,
                        highFrequency = modelSampleRate / 2f - 400f,
                        snipEdges = false,
                        removeDcOffset = true,
                        preemphasisCoefficient = 0.97f,
                        windowType = "povey",
                        melScale = MelScale.KALDI,
                        slaneyNormalization = false,
                        normalizeInputSamples = metadata.requiredBoolean("normalize_samples"),
                        featureNormalization = featureNormalization,
                    ),
                )
            }

            ModelFamily.NEMO -> {
                generalInputName = null
                nemoSignalInputName = session.inputNames.firstOrNull { it == "audio_signal" }
                    ?: error("${config.name} has no audio_signal input: ${session.inputNames}")
                nemoLengthInputName = session.inputNames.firstOrNull { it == "length" }
                    ?: error("${config.name} has no length input: ${session.inputNames}")
                val outputNames = session.outputNames.toList()
                embeddingOutputIndex = outputNames.indexOf("embs").takeIf { it >= 0 }
                    ?: requireNotNull(outputNames.indices.lastOrNull()) {
                        "${config.name} has no outputs"
                    }
                val normalizeType = metadata["feature_normalize_type"].orEmpty()
                require(normalizeType == "per_feature") {
                    "Unsupported NeMo feature_normalize_type=$normalizeType"
                }
                featureExtractor = LogMelFbank(
                    LogMelFbankConfig(
                        sampleRate = modelSampleRate,
                        numBins = metadata.requiredInt("feat_dim"),
                        frameLengthMillis = metadata.requiredInt("window_size_ms"),
                        frameShiftMillis = metadata.requiredInt("window_stride_ms"),
                        lowFrequency = 0f,
                        highFrequency = modelSampleRate / 2f - 400f,
                        snipEdges = true,
                        removeDcOffset = false,
                        preemphasisCoefficient = 0.97f,
                        windowType = metadata["window_type"].orEmpty().ifBlank { "povey" },
                        melScale = MelScale.SLANEY,
                        slaneyNormalization = true,
                        normalizeInputSamples = true,
                        featureNormalization = FeatureNormalization.PER_FEATURE,
                    ),
                )
            }
        }
    }

    override fun extractEmbedding(samples: FloatArray, sampleRate: Int): FloatArray {
        require(sampleRate == config.sampleRate) {
            "${config.name} requires ${config.sampleRate} Hz input, actual=$sampleRate"
        }
        val features = featureExtractor.compute(samples)
        val embedding = when (family) {
            ModelFamily.THREE_D_SPEAKER -> runThreeDSpeaker(features.values, features.numFrames, features.numBins)
            ModelFamily.NEMO -> runNemo(features.values, features.numFrames, features.numBins)
        }
        require(embedding.size == embeddingDimension) {
            "${config.name} returned ${embedding.size} values, expected $embeddingDimension"
        }
        require(embedding.all(Float::isFinite) && embedding.any { it != 0f }) {
            "${config.name} returned an invalid embedding"
        }
        return embedding
    }

    private fun runThreeDSpeaker(features: FloatArray, numFrames: Int, numBins: Int): FloatArray {
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(features),
            longArrayOf(1, numFrames.toLong(), numBins.toLong()),
        ).use { input ->
            session.run(mapOf(checkNotNull(generalInputName) to input)).use { result ->
                return result.embeddingAt(embeddingOutputIndex)
            }
        }
    }

    private fun runNemo(features: FloatArray, numFrames: Int, numBins: Int): FloatArray {
        val transposed = FloatArray(features.size)
        repeat(numFrames) { frame ->
            repeat(numBins) { bin ->
                transposed[bin * numFrames + frame] = features[frame * numBins + bin]
            }
        }
        OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(transposed),
            longArrayOf(1, numBins.toLong(), numFrames.toLong()),
        ).use { signal ->
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(longArrayOf(numFrames.toLong())),
                longArrayOf(1),
            ).use { length ->
                session.run(
                    mapOf(
                        checkNotNull(nemoSignalInputName) to signal,
                        checkNotNull(nemoLengthInputName) to length,
                    ),
                ).use { result ->
                    return result.embeddingAt(embeddingOutputIndex)
                }
            }
        }
    }

    override fun close() {
        session.close()
        sessionOptions.close()
    }

    private fun OrtSession.Result.embeddingAt(index: Int): FloatArray {
        val value = get(index).value
        val rows = value as? Array<*>
            ?: error("${config.name} embedding output is not a rank-2 float tensor")
        val row = rows.singleOrNull() as? FloatArray
            ?: error("${config.name} embedding output is not [1, D]")
        return row.copyOf()
    }
}

private fun Map<String, String>.required(name: String): String =
    get(name)?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("ONNX metadata '$name' is missing")

private fun Map<String, String>.requiredInt(name: String): Int =
    required(name).toIntOrNull()
        ?: throw IllegalArgumentException("ONNX metadata '$name' is not an integer: ${get(name)}")

private fun Map<String, String>.requiredBoolean(name: String): Boolean = when (required(name).lowercase()) {
    "1", "true" -> true
    "0", "false" -> false
    else -> throw IllegalArgumentException("ONNX metadata '$name' is not a boolean: ${get(name)}")
}
