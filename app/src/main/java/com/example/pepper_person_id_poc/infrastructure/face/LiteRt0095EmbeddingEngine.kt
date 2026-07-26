package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtFloatTensorSpec
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtModelContract
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import kotlin.math.sqrt
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class LiteRt0095EmbeddingEngine(
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
            "0095 LiteRT requires the exact converted artifact and tensor contract"
        }
        require(contract.artifactId !=
            FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32.artifactId
        ) {
            "LiteRT must use the converted 0095 artifactId, not the source ONNX artifactId"
        }
        require(contract.inputs.singleOrNull()?.dimensions ==
            listOf(1, 3, INPUT_SIZE, INPUT_SIZE)
        ) {
            "0095 LiteRT input contract must be [1,3,128,128]"
        }
        require(contract.outputs.singleOrNull()?.elementCount == EMBEDDING_SIZE) {
            "0095 LiteRT output contract must contain exactly 256 values"
        }
    }

    override val modelName: String =
        "face-reidentification-retail-0095 FP32 / ${contract.artifactId} / ${contract.runtimeId}"

    override fun prepare() {
        getOrCreateRuntime()
    }

    override fun extract(imageBgr: Mat, detectedFace: Mat): FloatArray {
        val input = prepare0095LiteRtInput(imageBgr, detectedFace)
        val raw = getOrCreateRuntime().infer(listOf(input)).single()
        return normalize0095Embedding(raw)
    }

    override fun close() {
        runtime?.close()
        runtime = null
    }

    private fun getOrCreateRuntime(): LiteRtRuntime {
        runtime?.let { return it }
        initializationFailure?.let {
            throw IllegalStateException("0095 LiteRT initialization failed", it)
        }
        return runCatching {
            runtimeProvider(appContext, contract, cpuThreadCount)
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { runtime = it }
    }

    companion object {
        const val ARTIFACT_ID = "face-0095-litert-fp32"
        const val ASSET_PATH = "models/face-reidentification-retail-0095.tflite"
        const val INPUT_SIZE = 128
        const val EMBEDDING_SIZE = 256

        val CONTRACT = LiteRtModelContract(
            artifactId = ARTIFACT_ID,
            assetPath = ASSET_PATH,
            inputs = listOf(
                LiteRtFloatTensorSpec("0", listOf(1, 3, INPUT_SIZE, INPUT_SIZE)),
            ),
            outputs = listOf(
                LiteRtFloatTensorSpec("Identity", listOf(1, 1, 1, EMBEDDING_SIZE)),
            ),
        )
    }
}

internal fun prepare0095LiteRtInput(
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
    try {
        transform.put(0, 0, *FaceLandmarkAlignment.similarityTransform(faceValues))
        Imgproc.warpAffine(
            imageBgr,
            aligned,
            transform,
            Size(
                LiteRt0095EmbeddingEngine.INPUT_SIZE.toDouble(),
                LiteRt0095EmbeddingEngine.INPUT_SIZE.toDouble(),
            ),
        )
        require(aligned.type() == CvType.CV_8UC3) {
            "0095 LiteRT requires an aligned CV_8UC3 BGR image"
        }
        val interleaved = ByteArray(
            LiteRt0095EmbeddingEngine.INPUT_SIZE *
                LiteRt0095EmbeddingEngine.INPUT_SIZE *
                3,
        )
        val copied = aligned.get(0, 0, interleaved)
        require(copied == interleaved.size) {
            "0095 aligned image copied $copied bytes instead of ${interleaved.size}"
        }
        return pack0095BgrNchw(interleaved)
    } finally {
        aligned.release()
        transform.release()
    }
}

internal fun pack0095BgrNchw(interleavedBgr: ByteArray): FloatArray {
    val plane = LiteRt0095EmbeddingEngine.INPUT_SIZE * LiteRt0095EmbeddingEngine.INPUT_SIZE
    require(interleavedBgr.size == plane * 3) {
        "0095 requires exactly ${plane * 3} interleaved BGR bytes"
    }
    val nchw = FloatArray(interleavedBgr.size)
    repeat(plane) { pixel ->
        val source = pixel * 3
        nchw[pixel] = interleavedBgr[source].toUByte().toFloat()
        nchw[plane + pixel] = interleavedBgr[source + 1].toUByte().toFloat()
        nchw[plane * 2 + pixel] = interleavedBgr[source + 2].toUByte().toFloat()
    }
    return nchw
}

internal fun normalize0095Embedding(raw: FloatArray): FloatArray {
    require(raw.size == LiteRt0095EmbeddingEngine.EMBEDDING_SIZE) {
        "0095 produced ${raw.size} values instead of ${LiteRt0095EmbeddingEngine.EMBEDDING_SIZE}"
    }
    require(raw.all(Float::isFinite)) { "0095 produced non-finite values" }
    var squaredNorm = 0.0
    raw.forEach { value -> squaredNorm += value.toDouble() * value.toDouble() }
    require(squaredNorm.isFinite() && squaredNorm > 0.0) {
        "0095 produced a zero or non-finite embedding norm"
    }
    val norm = sqrt(squaredNorm).toFloat()
    return FloatArray(raw.size) { index -> raw[index] / norm }.also { normalized ->
        require(normalized.all(Float::isFinite)) {
            "0095 L2 normalization produced non-finite values"
        }
    }
}
