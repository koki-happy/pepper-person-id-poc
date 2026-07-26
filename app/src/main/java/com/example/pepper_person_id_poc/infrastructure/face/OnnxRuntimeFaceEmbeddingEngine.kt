package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import java.io.File
import java.nio.FloatBuffer
import java.util.Collections
import org.opencv.core.Mat

/** Uses reflection so normal builds remain possible until the API-23/ARMv7 custom AAR is generated. */
class OnnxRuntimeFaceEmbeddingEngine(
    context: Context,
    private val model: FaceEmbeddingModelOption,
) : FaceEmbeddingEngine {
    private val appContext = context.applicationContext
    private var environment: Any? = null
    private var sessionOptions: AutoCloseable? = null
    private var session: AutoCloseable? = null
    private var inputName: String? = null
    private var initializationFailure: Throwable? = null

    override val modelName: String = "${model.displayName} / ONNX Runtime 1.27.0"

    override fun prepare() {
        getOrCreateSession()
    }

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray {
        val activeSession = getOrCreateSession()
        val env = checkNotNull(environment)
        val input = prepareFaceModelInput(model, imageBgr, detectedFace)
        val tensorClass = Class.forName("ai.onnxruntime.OnnxTensor")
        val envClass = Class.forName("ai.onnxruntime.OrtEnvironment")
        val tensor = tensorClass
            .getMethod("createTensor", envClass, FloatBuffer::class.java, LongArray::class.java)
            .invoke(null, env, FloatBuffer.wrap(input.values), input.shape) as AutoCloseable
        try {
            val result = activeSession.javaClass
                .getMethod("run", Map::class.java)
                .invoke(activeSession, Collections.singletonMap(checkNotNull(inputName), tensor)) as AutoCloseable
            try {
                val value = result.javaClass.getMethod("get", Int::class.javaPrimitiveType).invoke(result, 0)
                val rawValue = value.javaClass.getMethod("getValue").invoke(value)
                return flattenFloats(rawValue).also(::validateEmbedding)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    override fun close() {
        session?.close()
        sessionOptions?.close()
        session = null
        sessionOptions = null
        environment = null
    }

    private fun getOrCreateSession(): AutoCloseable {
        session?.let { return it }
        initializationFailure?.let { throw IllegalStateException("ONNX Runtime initialization failed", it) }
        return runCatching {
            val environmentClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val sessionOptionsClass = Class.forName("ai.onnxruntime.OrtSession\$SessionOptions")
            val env = environmentClass.getMethod("getEnvironment").invoke(null)
            val options = sessionOptionsClass.getConstructor().newInstance() as AutoCloseable
            val modelFile = copyModelToInternalStorage(model.modelFileName)
            val created = environmentClass
                .getMethod("createSession", String::class.java, sessionOptionsClass)
                .invoke(env, modelFile.absolutePath, options) as AutoCloseable
            @Suppress("UNCHECKED_CAST")
            val names = created.javaClass.getMethod("getInputNames").invoke(created) as Set<String>
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
}
