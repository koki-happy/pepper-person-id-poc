package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import java.io.File
import org.opencv.core.Mat

class NativeFaceEmbeddingEngine(
    context: Context,
    private val model: FaceEmbeddingModelOption,
    private val backend: FaceEmbeddingRuntime,
) : FaceEmbeddingEngine {
    init {
        require(backend == FaceEmbeddingRuntime.NCNN || backend == FaceEmbeddingRuntime.MNN)
    }

    private val appContext = context.applicationContext
    private var handle = 0L
    private var initializationFailure: Throwable? = null
    override val modelName: String = "${model.displayName} / ${backend.displayName}"

    @Synchronized
    override fun prepare() {
        getOrCreateHandle()
    }

    @Synchronized
    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray {
        val input = prepareFaceModelInput(model, imageBgr, detectedFace)
        return NativeBridge.run(getOrCreateHandle(), input.values, input.width, input.height, input.channels)
            .also { embedding ->
                val expected = model.embeddingSize
                require(embedding.size == expected) { "$modelName produced ${embedding.size} values instead of $expected" }
                require(embedding.all(Float::isFinite)) { "$modelName produced non-finite values" }
            }
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) NativeBridge.close(handle)
        handle = 0L
    }

    private fun getOrCreateHandle(): Long {
        if (handle != 0L) return handle
        initializationFailure?.let { throw IllegalStateException("$modelName initialization failed", it) }
        return runCatching {
            NativeBridge.ensureLoaded()
            val modelDirectory = File(appContext.filesDir, "models").apply { mkdirs() }
            when (backend) {
                FaceEmbeddingRuntime.NCNN -> {
                    val parameterName = model.modelFileName
                    val weightsName = parameterName.removeSuffix(".param") + ".bin"
                    copyAsset("models/$parameterName", File(modelDirectory, parameterName))
                    copyAsset("models/$weightsName", File(modelDirectory, weightsName))
                    NativeBridge.createNcnn(
                        File(modelDirectory, parameterName).absolutePath,
                        File(modelDirectory, weightsName).absolutePath,
                    )
                }
                FaceEmbeddingRuntime.MNN -> {
                    val file = File(modelDirectory, model.modelFileName)
                    copyAsset("models/${model.modelFileName}", file)
                    NativeBridge.createMnn(file.absolutePath)
                }
                else -> error("Unsupported native backend: $backend")
            }.also { require(it != 0L) { "$modelName native session creation failed" } }
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { handle = it }
    }

    private fun copyAsset(path: String, output: File) {
        if (output.isFile && output.length() > 0L) return
        appContext.assets.open(path).use { input -> output.outputStream().use(input::copyTo) }
    }

    private object NativeBridge {
        private val loadResult = runCatching { System.loadLibrary("face_runtime_jni") }

        fun ensureLoaded() {
            loadResult.getOrElse {
                throw IllegalStateException("face_runtime_jni is unavailable for this ABI", it)
            }
        }

        external fun createNcnn(paramPath: String, binPath: String): Long
        external fun createMnn(modelPath: String): Long
        external fun run(handle: Long, input: FloatArray, width: Int, height: Int, channels: Int): FloatArray
        external fun close(handle: Long)
    }
}
