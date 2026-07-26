package com.example.pepper_person_id_poc.infrastructure.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.os.Build
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Exact YuNet detector path through ONNX Runtime Android.
 *
 * Each supported artifact runs at its exact fixed BGR NCHW input size. The twelve raw
 * YuNet heads are decoded by the same postprocessor used by the LiteRT path.
 */
class OnnxRuntimeYuNetFaceDetector(
    context: Context,
    model: FaceDetectorModelOption,
    private val scoreThreshold: Float = SCORE_THRESHOLD,
    private val nmsThreshold: Float = NMS_THRESHOLD,
    private val topK: Int = TOP_K,
    private val numThreads: Int = 1,
) : YuNetDetectionBackend {
    private val appContext = context.applicationContext
    private val contract = contractFor(model)
    private var environment: OrtEnvironment? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var session: OrtSession? = null
    private var initializationFailure: Throwable? = null
    private var closed = false

    init {
        requireSupportedAbi(Build.SUPPORTED_ABIS.toList())
        require(scoreThreshold in 0f..1f)
        require(nmsThreshold in 0f..1f)
        require(topK > 0)
        require(numThreads > 0)
    }

    @Synchronized
    override fun detect(imageBgr: Mat, outputFaces: Mat) {
        check(!closed) { "YuNet ONNX Runtime detector is closed" }
        require(!imageBgr.empty() && imageBgr.channels() == 3) {
            "YuNet ONNX Runtime input must be a nonempty three-channel BGR image"
        }
        val activeEnvironment = checkNotNull(getOrCreateSession().let { environment })
        val inputSize = contract.inputSize
        val input = prepareYuNetNchwInput(imageBgr, inputSize)
        OnnxTensor.createTensor(
            activeEnvironment,
            FloatBuffer.wrap(input),
            contract.input.dimensions.map(Int::toLong).toLongArray(),
        ).use { tensor ->
            checkNotNull(session).run(mapOf(INPUT_NAME to tensor)).use { result ->
                val outputs = contract.outputs.map { output ->
                    val value = result.get(output.name).orElseThrow {
                        IllegalStateException("Missing YuNet ONNX output=${output.name}")
                    }.value
                    validateRawOutput(output, value)
                }
                val detections = YuNetLiteRtPostprocessor.decode(
                    outputs = outputs,
                    imageWidth = imageBgr.cols(),
                    imageHeight = imageBgr.rows(),
                    scoreThreshold = scoreThreshold,
                    nmsThreshold = nmsThreshold,
                    topK = topK,
                    inputSize = inputSize,
                )
                outputFaces.create(detections.size, FACE_RESULT_COLUMN_COUNT, CvType.CV_32F)
                detections.forEachIndexed { row, detection ->
                    outputFaces.put(row, 0, detection.asOpenCvRow())
                }
            }
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        session?.close()
        sessionOptions?.close()
        session = null
        sessionOptions = null
        environment = null
    }

    private fun getOrCreateSession(): OrtSession {
        session?.let { return it }
        initializationFailure?.let {
            throw IllegalStateException("YuNet ONNX Runtime initialization failed", it)
        }
        return runCatching {
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setInterOpNumThreads(numThreads)
                setIntraOpNumThreads(numThreads)
            }
            val modelFile = copyAndVerifyExactModel()
            val created = env.createSession(modelFile.absolutePath, options)
            check(created.inputNames == setOf(INPUT_NAME)) {
                "Unexpected YuNet ONNX input names=${created.inputNames}; expected=$INPUT_NAME"
            }
            check(created.outputNames == contract.outputs.mapTo(linkedSetOf()) { it.name }) {
                "Unexpected YuNet ONNX output names=${created.outputNames}; " +
                    "expected=${contract.outputs.map { it.name }}"
            }
            validateSessionTensorMetadata(created)
            environment = env
            sessionOptions = options
            created
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { session = it }
    }

    private fun validateSessionTensorMetadata(activeSession: OrtSession) {
        val actualInput = activeSession.inputInfo.getValue(INPUT_NAME).info as? TensorInfo
            ?: error("YuNet ONNX input=$INPUT_NAME is not a tensor")
        check(contract.input.acceptsMetadataShape(actualInput.shape.toList())) {
            "Unexpected YuNet ONNX input shape=${actualInput.shape.toList()}; " +
                "expected=${contract.input.metadataLongDimensions}"
        }
        contract.outputs.forEach { expected ->
            val actual = activeSession.outputInfo.getValue(expected.name).info as? TensorInfo
                ?: error("YuNet ONNX output=${expected.name} is not a tensor")
            check(expected.acceptsMetadataShape(actual.shape.toList())) {
                "Unexpected YuNet ONNX output=${expected.name} shape=${actual.shape.toList()}; " +
                    "expected=${expected.metadataLongDimensions}"
            }
        }
    }

    private fun copyAndVerifyExactModel(): File {
        val output = File(appContext.filesDir, "models/${contract.modelFileName}")
        if (
            output.isFile &&
            output.length() == contract.fileSizeBytes &&
            output.sha256() == contract.sha256
        ) {
            return output
        }
        output.parentFile?.mkdirs()
        appContext.assets.open("models/${contract.modelFileName}").use { input ->
            output.outputStream().use(input::copyTo)
        }
        check(output.length() == contract.fileSizeBytes) {
            "Unexpected ${contract.artifactId} size=${output.length()}; " +
                "expected=${contract.fileSizeBytes}"
        }
        check(output.sha256() == contract.sha256) {
            "Unexpected ${contract.artifactId} SHA-256; no fallback"
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

    companion object {
        const val RUNTIME_ID = "onnxruntime-android-1.20.0-cpu"
        const val INPUT_NAME = "input"
        private const val SCORE_THRESHOLD = 0.80f
        private const val NMS_THRESHOLD = 0.30f
        private const val TOP_K = 5_000
        private const val FACE_RESULT_COLUMN_COUNT = 15
        private val SUPPORTED_ABIS = setOf("armeabi-v7a", "arm64-v8a")

        val FP32_2026_CONTRACT = YuNetOnnxContract(
            artifactId = "yunet-2026may-onnx-fp32",
            modelFileName = "face_detection_yunet_2026may.onnx",
            fileSizeBytes = 229_738L,
            sha256 = "ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0",
            input = YuNetOnnxTensorContract(
                INPUT_NAME,
                dimensions = listOf(1, 3, 320, 320),
                metadataDimensions = listOf(1, 3, -1, -1),
            ),
            outputs = yunetOnnxOutputs(inputSize = 320, dynamicAnchors = true),
        )

        fun contractFor(model: FaceDetectorModelOption): YuNetOnnxContract = when (model) {
            FaceDetectorModelOption.YUNET_2026MAY_FP32 -> FP32_2026_CONTRACT
            else -> throw IllegalArgumentException(
                "Unsupported YuNet ONNX Runtime artifact=${model.artifactId}; no fallback",
            )
        }

        fun requireSupportedAbi(abis: Collection<String>) {
            require(abis.any(SUPPORTED_ABIS::contains)) {
                "YuNet ONNX Runtime is BLOCKED for ABIs=${abis.joinToString()}; " +
                    "required=${SUPPORTED_ABIS.joinToString()}; no fallback"
            }
        }

        internal fun flattenFloatTensor(value: Any?): FloatArray {
            val output = ArrayList<Float>()
            fun visit(node: Any?) {
                when (node) {
                    is Float -> output += node
                    is FloatArray -> node.forEach { output += it }
                    is Array<*> -> node.forEach(::visit)
                    else -> error(
                        "Unsupported YuNet ONNX output type: ${node?.javaClass?.name}",
                    )
                }
            }
            visit(value)
            return output.toFloatArray()
        }

        internal fun validateRawOutput(
            contract: YuNetOnnxTensorContract,
            value: Any?,
        ): FloatArray = flattenFloatTensor(value).also { flattened ->
            require(flattened.size == contract.elementCount) {
                "YuNet ONNX output=${contract.name} has ${flattened.size} values; " +
                    "expected=${contract.elementCount}"
            }
            require(flattened.all(Float::isFinite)) {
                "YuNet ONNX output=${contract.name} contains non-finite values"
            }
        }
    }
}

data class YuNetOnnxTensorContract(
    val name: String,
    val dimensions: List<Int>,
    val metadataDimensions: List<Int> = dimensions,
) {
    val elementCount: Int = dimensions.fold(1, Int::times)
    val longDimensions: List<Long> = dimensions.map(Int::toLong)
    val metadataLongDimensions: List<Long> = metadataDimensions.map(Int::toLong)

    init {
        require(dimensions.isNotEmpty())
        require(metadataDimensions.size == dimensions.size)
        require(dimensions.all { it > 0 })
        require(metadataDimensions.all { it == -1 || it > 0 })
    }

    fun acceptsMetadataShape(actual: List<Long>): Boolean =
        actual.size == metadataDimensions.size &&
            metadataDimensions.indices.all { index ->
                val metadataDimension = metadataDimensions[index].toLong()
                actual[index] == metadataDimension ||
                    (metadataDimension == -1L && actual[index] == dimensions[index].toLong())
            }
}

data class YuNetOnnxContract(
    val artifactId: String,
    val modelFileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val input: YuNetOnnxTensorContract =
        YuNetOnnxTensorContract(OnnxRuntimeYuNetFaceDetector.INPUT_NAME, listOf(1, 3, 320, 320)),
    val outputs: List<YuNetOnnxTensorContract> = yunetOnnxOutputs(input.dimensions[2]),
) {
    val inputSize: Int = input.dimensions[2]

    init {
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(fileSizeBytes > 0L)
        require(input.name == "input")
        require(input.dimensions == listOf(1, 3, inputSize, inputSize))
        require(inputSize > 0 && inputSize % 32 == 0)
        val dynamicAnchors = input.metadataDimensions == listOf(1, 3, -1, -1)
        require(outputs == yunetOnnxOutputs(inputSize, dynamicAnchors))
    }
}

private fun yunetOnnxOutputs(
    inputSize: Int,
    dynamicAnchors: Boolean = false,
): List<YuNetOnnxTensorContract> {
    val anchors = listOf(8, 16, 32).map { stride ->
        val gridSize = inputSize / stride
        gridSize * gridSize
    }
    fun tensor(name: String, anchorsAtLevel: Int, components: Int) =
        YuNetOnnxTensorContract(
            name = name,
            dimensions = listOf(1, anchorsAtLevel, components),
            metadataDimensions = listOf(1, if (dynamicAnchors) -1 else anchorsAtLevel, components),
        )
    return listOf(
        tensor("cls_8", anchors[0], 1),
        tensor("cls_16", anchors[1], 1),
        tensor("cls_32", anchors[2], 1),
        tensor("obj_8", anchors[0], 1),
        tensor("obj_16", anchors[1], 1),
        tensor("obj_32", anchors[2], 1),
        tensor("bbox_8", anchors[0], 4),
        tensor("bbox_16", anchors[1], 4),
        tensor("bbox_32", anchors[2], 4),
        tensor("kps_8", anchors[0], 10),
        tensor("kps_16", anchors[1], 10),
        tensor("kps_32", anchors[2], 10),
    )
}
