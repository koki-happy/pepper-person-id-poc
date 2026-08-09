package com.example.pepper_person_id_poc.speakerbenchmark.model

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.pepper_person_id_poc.speakerbenchmark.config.ResolvedModelConfig
import com.example.pepper_person_id_poc.speakerbenchmark.features.FeatureNormalization
import com.example.pepper_person_id_poc.speakerbenchmark.features.LogMelFbank
import com.example.pepper_person_id_poc.speakerbenchmark.features.LogMelFbankConfig
import com.example.pepper_person_id_poc.speakerbenchmark.util.Sha256
import java.nio.FloatBuffer
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
            "campplus-zh-en" -> CampPlusZhEnAdapter(config, numThreads)
            "wespeaker" -> WeSpeakerAdapter(config, numThreads)
            else -> throw IllegalArgumentException("Unsupported speaker model adapter: ${config.adapter}")
        }
}

class CampPlusZhEnAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.THREE_D_SPEAKER)

class WeSpeakerAdapter(config: ResolvedModelConfig, numThreads: Int = 1) :
    OrtSpeakerModelAdapter(config, numThreads, ModelFamily.WESPEAKER)

enum class ModelFamily {
    THREE_D_SPEAKER,
    WESPEAKER,
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
            ModelFamily.WESPEAKER -> "wespeaker"
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
            ModelFamily.THREE_D_SPEAKER,
            ModelFamily.WESPEAKER,
            -> {
                require(session.inputNames.size == 1) {
                    "${config.name} must expose one 3D-Speaker feature input: ${session.inputNames}"
                }
                generalInputName = session.inputNames.single()
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
                        removeDcOffset = true,
                        preemphasisCoefficient = 0.97f,
                        normalizeInputSamples = metadata.requiredBoolean("normalize_samples"),
                        featureNormalization = featureNormalization,
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
            ModelFamily.WESPEAKER -> runThreeDSpeaker(features.values, features.numFrames, features.numBins)
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
