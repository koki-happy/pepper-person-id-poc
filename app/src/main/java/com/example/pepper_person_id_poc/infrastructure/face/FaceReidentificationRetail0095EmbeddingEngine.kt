package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import java.io.File
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.dnn.Dnn
import org.opencv.dnn.Net
import org.opencv.imgproc.Imgproc

class FaceReidentificationRetail0095EmbeddingEngine(
    context: Context,
) : FaceEmbeddingEngine {
    private val appContext = context.applicationContext
    private var net: Net? = null
    private var initializationFailure: Throwable? = null

    override val modelName: String = MODEL_NAME

    override fun prepare() {
        getOrCreateNet()
    }

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray =
        extractMeasured(imageBgr, detectedFace).embedding

    override fun extractMeasured(imageBgr: Mat, detectedFace: Mat): FaceEmbeddingExtraction {
        val values = FloatArray(detectedFace.cols())
        detectedFace.get(0, 0, values)
        val transform = Mat(2, 3, CvType.CV_64F)
        val aligned = Mat()
        val blob: Mat
        val output: Mat
        try {
            val alignmentStarted = System.nanoTime()
            transform.put(0, 0, *FaceLandmarkAlignment.similarityTransform(values))
            Imgproc.warpAffine(imageBgr, aligned, transform, INPUT_SIZE)
            val alignmentMillis = (System.nanoTime() - alignmentStarted) / 1_000_000L
            val preprocessingStarted = System.nanoTime()
            // The converted ONNX preserves the IR's embedded BGR-to-RGB and /255 preprocessing.
            blob = Dnn.blobFromImage(aligned, 1.0, INPUT_SIZE, Scalar(0.0), false, false, CvType.CV_32F)
            val preprocessingMillis = (System.nanoTime() - preprocessingStarted) / 1_000_000L
            try {
                getOrCreateNet().setInput(blob)
                val embeddingStarted = System.nanoTime()
                output = getOrCreateNet().forward()
                try {
                    val embedding = FloatArray((output.total() * output.channels()).toInt())
                    val copied = output.get(IntArray(output.dims()), embedding)
                    require(embedding.size == EMBEDDING_DIMENSION) {
                        "0095 produced ${embedding.size} values instead of $EMBEDDING_DIMENSION"
                    }
                    require(copied == EMBEDDING_DIMENSION * Float.SIZE_BYTES) {
                        "0095 copied $copied bytes instead of ${EMBEDDING_DIMENSION * Float.SIZE_BYTES} from ${output.dims()}D output"
                    }
                    require(embedding.all(Float::isFinite)) { "0095 produced non-finite values" }
                    require(embedding.any { it != 0f }) { "0095 produced an all-zero embedding" }
                    return FaceEmbeddingExtraction(
                        embedding = embedding,
                        preprocessingMillis = preprocessingMillis,
                        alignmentMillis = alignmentMillis,
                        embeddingMillis = (System.nanoTime() - embeddingStarted) / 1_000_000L,
                    )
                } finally {
                    output.release()
                }
            } finally {
                blob.release()
            }
        } finally {
            aligned.release()
            transform.release()
        }
    }

    override fun close() {
        net = null
    }

    private fun getOrCreateNet(): Net {
        net?.let { return it }
        initializationFailure?.let { throw IllegalStateException("0095 initialization failed", it) }
        return runCatching {
            val model = copyModelToInternalStorage()
            Dnn.readNetFromONNX(model.absolutePath).also { require(!it.empty()) { "0095 network is empty" } }
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { net = it }
    }

    private fun copyModelToInternalStorage(): File {
        val output = File(appContext.filesDir, "models/$MODEL_FILE_NAME")
        if (output.exists() && output.length() > 0) return output
        output.parentFile?.mkdirs()
        appContext.assets.open(MODEL_ASSET_PATH).use { input ->
            output.outputStream().use(input::copyTo)
        }
        return output
    }

    private companion object {
        const val MODEL_NAME = "face-reidentification-retail-0095 onnx-v1"
        const val MODEL_FILE_NAME = "face-reidentification-retail-0095.onnx"
        const val MODEL_ASSET_PATH = "models/$MODEL_FILE_NAME"
        const val EMBEDDING_DIMENSION = 256
        val INPUT_SIZE = Size(128.0, 128.0)
    }
}
