package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtFloatTensorSpec
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtModelContract
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class LiteRtSFaceEmbeddingEngine(
    context: Context,
    private val contract: LiteRtModelContract,
    private val cpuThreadCount: Int? = null,
    private val runtimeProvider: (Context, LiteRtModelContract, Int?) -> LiteRtRuntime =
        LiteRtRuntime::open,
) : FaceEmbeddingEngine {
    private val appContext = context.applicationContext
    private var runtime: LiteRtRuntime? = null
    private var initializationFailure: Throwable? = null

    init {
        require(contract == CONTRACT) {
            "SFace LiteRT requires the exact converted artifact and tensor contract"
        }
        require(contract.artifactId != FaceEmbeddingModelOption.SFACE_2021DEC_FP32.artifactId) {
            "LiteRT must use a converted artifactId, not the source ONNX artifactId"
        }
        require(contract.inputs.singleOrNull()?.dimensions == listOf(1, 3, INPUT_SIZE, INPUT_SIZE)) {
            "SFace LiteRT input contract must be [1,3,112,112]"
        }
        require(contract.outputs.singleOrNull()?.elementCount == EMBEDDING_SIZE) {
            "SFace LiteRT output contract must contain exactly 128 values"
        }
    }

    override val modelName: String = "SFace 2021dec FP32 / ${contract.artifactId} / ${contract.runtimeId}"

    override fun prepare() {
        getOrCreateRuntime()
    }

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray {
        val input = prepareSFaceLiteRtInput(imageBgr, detectedFace)
        return getOrCreateRuntime().infer(listOf(input)).single()
    }

    override fun close() {
        runtime?.close()
        runtime = null
    }

    private fun getOrCreateRuntime(): LiteRtRuntime {
        runtime?.let { return it }
        initializationFailure?.let { throw IllegalStateException("SFace LiteRT initialization failed", it) }
        return runCatching {
            runtimeProvider(appContext, contract, cpuThreadCount)
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { runtime = it }
    }

    companion object {
        const val ARTIFACT_ID = "sface-2021dec-litert-fp32"
        const val ASSET_PATH = "models/face_recognition_sface_2021dec.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 128

        val CONTRACT = LiteRtModelContract(
            artifactId = ARTIFACT_ID,
            assetPath = ASSET_PATH,
            inputs = listOf(LiteRtFloatTensorSpec("data", listOf(1, 3, INPUT_SIZE, INPUT_SIZE))),
            outputs = listOf(LiteRtFloatTensorSpec("Identity", listOf(1, EMBEDDING_SIZE))),
        )
    }
}

internal fun prepareSFaceLiteRtInput(
    imageBgr: Mat,
    detectedFace: Mat,
): FloatArray {
    val faceValues = FloatArray(detectedFace.cols())
    detectedFace.get(0, 0, faceValues)
    require(faceValues.size >= 14) {
        "Face detector did not provide the required five landmarks"
    }
    val transform = Mat(2, 3, CvType.CV_64F)
    val aligned = Mat()
    val floatImage = Mat()
    try {
        transform.put(0, 0, *FaceLandmarkAlignment.similarityTransform(faceValues))
        Imgproc.warpAffine(imageBgr, aligned, transform, Size(112.0, 112.0))
        aligned.convertTo(floatImage, CvType.CV_32FC3, 1.0 / 128.0, -127.5 / 128.0)
        val interleaved = FloatArray((floatImage.total() * floatImage.channels()).toInt())
        floatImage.get(0, 0, interleaved)
        val plane = 112 * 112
        val nchw = FloatArray(interleaved.size)
        repeat(112) { y ->
            repeat(112) { x ->
                val source = (y * 112 + x) * 3
                val target = y * 112 + x
                nchw[target] = interleaved[source]
                nchw[plane + target] = interleaved[source + 1]
                nchw[plane * 2 + target] = interleaved[source + 2]
            }
        }
        return nchw
    } finally {
        floatImage.release()
        aligned.release()
        transform.release()
    }
}
