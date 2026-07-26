package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.opencv.core.CvType
import org.opencv.core.Mat

enum class YuNetNativeRuntime {
    NCNN,
    MNN,
}

class YuNetNativeFaceDetector(
    context: Context,
    private val runtime: YuNetNativeRuntime,
    private val scoreThreshold: Float = SCORE_THRESHOLD,
    private val nmsThreshold: Float = NMS_THRESHOLD,
    private val topK: Int = TOP_K,
) : YuNetDetectionBackend {
    private val appContext = context.applicationContext
    private var handle = 0L
    private var initializationFailure: Throwable? = null
    private var closed = false

    init {
        require(scoreThreshold in 0f..1f)
        require(nmsThreshold in 0f..1f)
        require(topK > 0)
    }

    @Synchronized
    override fun detect(imageBgr: Mat, outputFaces: Mat) {
        check(!closed) { "YuNet $runtime detector is closed" }
        require(!imageBgr.empty() && imageBgr.channels() == 3) {
            "YuNet $runtime input must be a nonempty three-channel BGR image"
        }
        val outputs = NativeBridge.run(getOrCreateHandle(), prepareYuNetLiteRtInput(imageBgr))
            .toList()
        validateOutputs(outputs)
        val detections = YuNetLiteRtPostprocessor.decode(
            outputs = outputs,
            imageWidth = imageBgr.cols(),
            imageHeight = imageBgr.rows(),
            scoreThreshold = scoreThreshold,
            nmsThreshold = nmsThreshold,
            topK = topK,
        )
        outputFaces.create(detections.size, FACE_RESULT_COLUMN_COUNT, CvType.CV_32F)
        detections.forEachIndexed { row, detection ->
            outputFaces.put(row, 0, detection.asOpenCvRow())
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        if (handle != 0L) {
            NativeBridge.close(handle)
            handle = 0L
        }
    }

    private fun getOrCreateHandle(): Long {
        if (handle != 0L) return handle
        initializationFailure?.let {
            throw IllegalStateException("YuNet $runtime initialization failed; no fallback", it)
        }
        return runCatching {
            System.loadLibrary("face_runtime_jni")
            when (runtime) {
                YuNetNativeRuntime.NCNN -> {
                    val param = copyAndVerify(NCNN_PARAM)
                    val bin = copyAndVerify(NCNN_BIN)
                    NativeBridge.createNcnn(param.absolutePath, bin.absolutePath)
                }
                YuNetNativeRuntime.MNN ->
                    NativeBridge.createMnn(copyAndVerify(MNN_MODEL).absolutePath)
            }.also { check(it != 0L) { "YuNet $runtime returned a null handle; no fallback" } }
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { handle = it }
    }

    private fun copyAndVerify(contract: YuNetNativeArtifact): File {
        val output = File(appContext.filesDir, contract.assetPath)
        if (
            output.isFile &&
            output.length() == contract.sizeBytes &&
            output.sha256() == contract.sha256
        ) {
            return output
        }
        output.parentFile?.mkdirs()
        appContext.assets.open(contract.assetPath).use { input ->
            output.outputStream().use(input::copyTo)
        }
        check(output.length() == contract.sizeBytes) {
            "Unexpected ${contract.assetPath} size=${output.length()}; " +
                "expected=${contract.sizeBytes}; no fallback"
        }
        check(output.sha256() == contract.sha256) {
            "Unexpected ${contract.assetPath} SHA-256; no fallback"
        }
        return output
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private object NativeBridge {
        external fun createNcnn(paramPath: String, binPath: String): Long
        external fun createMnn(modelPath: String): Long
        external fun run(handle: Long, input: FloatArray): Array<FloatArray>
        external fun close(handle: Long)
    }

    companion object {
        const val INPUT_SIZE = 320
        private const val SCORE_THRESHOLD = 0.80f
        private const val NMS_THRESHOLD = 0.30f
        private const val TOP_K = 5_000
        private const val FACE_RESULT_COLUMN_COUNT = 15

        val OUTPUTS = listOf(
            YuNetNativeOutput("cls_8", "out0", 1_600),
            YuNetNativeOutput("cls_16", "out1", 400),
            YuNetNativeOutput("cls_32", "out2", 100),
            YuNetNativeOutput("obj_8", "out3", 1_600),
            YuNetNativeOutput("obj_16", "out4", 400),
            YuNetNativeOutput("obj_32", "out5", 100),
            YuNetNativeOutput("bbox_8", "out6", 6_400),
            YuNetNativeOutput("bbox_16", "out7", 1_600),
            YuNetNativeOutput("bbox_32", "out8", 400),
            YuNetNativeOutput("kps_8", "out9", 16_000),
            YuNetNativeOutput("kps_16", "out10", 4_000),
            YuNetNativeOutput("kps_32", "out11", 1_000),
        )
        val NCNN_PARAM = YuNetNativeArtifact(
            assetPath = "models/face_detection_yunet_2026may_320.ncnn.param",
            sizeBytes = 9_089L,
            sha256 = "f3b6ec99c4773da6edc0950f0fb1d6d728982114114e87a673780eb243b72bbf",
        )
        val NCNN_BIN = YuNetNativeArtifact(
            assetPath = "models/face_detection_yunet_2026may_320.ncnn.bin",
            sizeBytes = 212_628L,
            sha256 = "8faa696d61ad6bf5c13ed5d6c8ae35e376c660968db53d723a329020856ca5c9",
        )
        val MNN_MODEL = YuNetNativeArtifact(
            assetPath = "models/face_detection_yunet_2026may_320.mnn",
            sizeBytes = 226_056L,
            sha256 = "31cb825bbff3cfe1535cc40dc27e72c3614c8efcc0f3ae00beb1b557f5f04b69",
        )

        internal fun validateOutputs(outputs: List<FloatArray>) {
            require(outputs.size == OUTPUTS.size) {
                "YuNet native returned ${outputs.size} outputs; expected=${OUTPUTS.size}"
            }
            outputs.zip(OUTPUTS).forEach { (values, contract) ->
                require(values.size == contract.elementCount) {
                    "YuNet native output=${contract.logicalName} has ${values.size} values; " +
                        "expected=${contract.elementCount}"
                }
                require(values.all(Float::isFinite)) {
                    "YuNet native output=${contract.logicalName} contains non-finite values"
                }
            }
        }
    }
}

data class YuNetNativeArtifact(
    val assetPath: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    init {
        require(assetPath.startsWith("models/"))
        require(sizeBytes > 0L)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

data class YuNetNativeOutput(
    val logicalName: String,
    val ncnnName: String,
    val elementCount: Int,
)
