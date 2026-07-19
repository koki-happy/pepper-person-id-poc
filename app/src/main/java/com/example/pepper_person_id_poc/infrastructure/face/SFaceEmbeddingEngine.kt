package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceModelOption
import java.io.File
import org.opencv.core.Mat
import org.opencv.objdetect.FaceRecognizerSF

class SFaceEmbeddingEngine(
    context: Context,
    private val model: FaceModelOption = FaceModelOption.SFACE_2021DEC,
) : FaceEmbeddingEngine {
    init {
        require(model.isSFace)
    }
    private val appContext = context.applicationContext
    private var recognizer: FaceRecognizerSF? = null
    private var initializationFailure: Throwable? = null

    override val modelName: String = model.displayName

    override fun prepare() {
        getOrCreateRecognizer()
    }

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray {
        val activeRecognizer = getOrCreateRecognizer()
        val alignedFace = Mat()
        val feature = Mat()
        try {
            activeRecognizer.alignCrop(imageBgr, detectedFace, alignedFace)
            activeRecognizer.feature(alignedFace, feature)
            val embedding = FloatArray((feature.total() * feature.channels()).toInt())
            feature.get(0, 0, embedding)
            require(embedding.isNotEmpty()) { "SFace produced an empty embedding" }
            return embedding
        } finally {
            feature.release()
            alignedFace.release()
        }
    }

    override fun close() {
        recognizer = null
    }

    private fun getOrCreateRecognizer(): FaceRecognizerSF {
        recognizer?.let { return it }
        initializationFailure?.let { throw IllegalStateException("SFace initialization failed", it) }
        return runCatching {
            val model = copyModelToInternalStorage()
            FaceRecognizerSF.create(model.absolutePath, "")
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { recognizer = it }
    }

    private fun copyModelToInternalStorage(): File {
        val output = File(appContext.filesDir, "models/${model.modelFileName}")
        if (output.exists() && output.length() > 0) return output
        output.parentFile?.mkdirs()
        appContext.assets.open("models/${model.modelFileName}").use { input ->
            output.outputStream().use(input::copyTo)
        }
        return output
    }
}
