package com.example.pepper_person_id_poc.infrastructure.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntimeCompatibility
import java.io.File
import java.nio.FloatBuffer
import org.opencv.core.Mat

/** Exact ARM64 path for the packaged ONNX Runtime Android 1.20.0 dependency. */
class OnnxRuntimeFaceEmbeddingEngine(
    context: Context,
    private val model: FaceEmbeddingModelOption,
) : FaceEmbeddingEngine {
    init {
        require(
            FaceEmbeddingRuntimeCompatibility.supportsExactPair(
                model,
                FaceEmbeddingRuntime.ONNX_RUNTIME,
                FaceEmbeddingRuntimeCompatibility.ARM64_ABI,
            ),
        ) {
            "Unsupported ONNX Runtime face artifact=${model.artifactId}; no fallback"
        }
        requireSupportedAbi(Build.SUPPORTED_ABIS.toList())
    }
    private val appContext = context.applicationContext
    private var environment: OrtEnvironment? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var session: OrtSession? = null
    private var inputName: String? = null
    private var initializationFailure: Throwable? = null

    override val modelName: String = "${model.displayName} / ONNX Runtime Android 1.20.0"

    override fun prepare() {
        getOrCreateSession()
    }

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray {
        val activeSession = getOrCreateSession()
        val env = checkNotNull(environment)
        val input = prepareFaceModelInput(model, imageBgr, detectedFace)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input.values), input.shape).use { tensor ->
            activeSession.run(mapOf(checkNotNull(inputName) to tensor)).use { result ->
                val rawValue = result[0].value
                return flattenFloats(rawValue).also(::validateEmbedding)
            }
        }
    }

    override fun close() {
        session?.close()
        sessionOptions?.close()
        session = null
        sessionOptions = null
        environment = null
    }

    private fun getOrCreateSession(): OrtSession {
        session?.let { return it }
        initializationFailure?.let { throw IllegalStateException("ONNX Runtime initialization failed", it) }
        return runCatching {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions()
            val modelFile = copyModelToInternalStorage(model.modelFileName)
            val created = env.createSession(modelFile.absolutePath, options)
            val names = created.inputNames
            environment = env
            sessionOptions = options
            inputName = names.singleOrNull() ?: names.firstOrNull()
            requireNotNull(inputName) { "ONNX model has no input" }
            created
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { session = it }
    }

    private fun copyModelToInternalStorage(fileName: String): File {
        val output = File(appContext.filesDir, "models/$fileName")
        if (output.isFile && output.length() > 0L) return output
        output.parentFile?.mkdirs()
        appContext.assets.open("models/$fileName").use { input -> output.outputStream().use(input::copyTo) }
        return output
    }

    private fun flattenFloats(value: Any?): FloatArray {
        val output = ArrayList<Float>()
        fun visit(node: Any?) {
            when (node) {
                is Float -> output += node
                is FloatArray -> node.forEach { output += it }
                is Array<*> -> node.forEach(::visit)
                else -> error("Unsupported ONNX output type: ${node?.javaClass?.name}")
            }
        }
        visit(value)
        return output.toFloatArray()
    }

    private fun validateEmbedding(embedding: FloatArray) {
        val expected = model.embeddingSize
        require(embedding.size == expected) { "${model.displayName} produced ${embedding.size} values instead of $expected" }
        require(embedding.all(Float::isFinite)) { "${model.displayName} produced non-finite values" }
        require(embedding.any { it != 0f }) { "${model.displayName} produced an all-zero embedding" }
    }

    companion object {
        const val RUNTIME_ID = "onnxruntime-android-1.20.0-cpu"

        fun requireSupportedAbi(abis: Collection<String>) {
            val supportedAbis = setOf(
                FaceEmbeddingRuntimeCompatibility.ARMV7_ABI,
                FaceEmbeddingRuntimeCompatibility.ARM64_ABI,
            )
            require(abis.any(supportedAbis::contains)) {
                "ONNX Runtime face embedding is BLOCKED for ABIs=${abis.joinToString()}; " +
                    "required=${supportedAbis.joinToString()}; no fallback"
            }
        }
    }
}
