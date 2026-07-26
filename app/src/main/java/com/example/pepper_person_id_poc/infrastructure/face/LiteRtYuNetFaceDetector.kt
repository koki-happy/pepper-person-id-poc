package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtFloatTensorSpec
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtModelContract
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

interface YuNetDetectionBackend : AutoCloseable {
    fun detect(imageBgr: Mat, outputFaces: Mat)
}

internal class OpenCvYuNetDetectionBackend(
    private val detector: org.opencv.objdetect.FaceDetectorYN,
) : YuNetDetectionBackend {
    private var inputSize: Size? = null

    override fun detect(imageBgr: Mat, outputFaces: Mat) {
        if (inputSize != imageBgr.size()) {
            detector.setInputSize(imageBgr.size())
            inputSize = imageBgr.size()
        }
        detector.detect(imageBgr, outputFaces)
    }

    override fun close() = Unit
}

class LiteRtYuNetFaceDetector(
    context: Context,
    private val contract: LiteRtModelContract = CONTRACT,
    private val scoreThreshold: Float = SCORE_THRESHOLD,
    private val nmsThreshold: Float = NMS_THRESHOLD,
    private val topK: Int = TOP_K,
    private val cpuThreadCount: Int? = null,
    private val runtimeProvider: (Context, LiteRtModelContract, Int?) -> LiteRtRuntime =
        LiteRtRuntime::open,
) : YuNetDetectionBackend {
    private val appContext = context.applicationContext
    private var runtime: LiteRtRuntime? = null
    private var initializationFailure: Throwable? = null

    init {
        require(contract == CONTRACT) {
            "YuNet LiteRT requires the exact converted artifact and tensor contract"
        }
        require(scoreThreshold in 0f..1f)
        require(nmsThreshold in 0f..1f)
        require(topK > 0)
    }

    override fun detect(imageBgr: Mat, outputFaces: Mat) {
        require(!imageBgr.empty() && imageBgr.channels() == 3) {
            "YuNet LiteRT input must be a nonempty three-channel BGR image"
        }
        val input = prepareYuNetLiteRtInput(imageBgr)
        val outputs = getOrCreateRuntime().infer(listOf(input)).toMutableList()
        outputs[KPS_32_OUTPUT_INDEX] = invertYuNetKps32ConversionPermutation(
            outputs[KPS_32_OUTPUT_INDEX],
        )
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

    override fun close() {
        runtime?.close()
        runtime = null
    }

    private fun getOrCreateRuntime(): LiteRtRuntime {
        runtime?.let { return it }
        initializationFailure?.let {
            throw IllegalStateException("YuNet LiteRT initialization failed", it)
        }
        return runCatching {
            runtimeProvider(appContext, contract, cpuThreadCount)
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { runtime = it }
    }

    companion object {
        const val ARTIFACT_ID = "yunet-2026may-litert-fp32-320"
        const val ASSET_PATH = "models/face_detection_yunet_2026may_320.tflite"
        private const val INPUT_SIZE = 320
        private const val SCORE_THRESHOLD = 0.80f
        private const val NMS_THRESHOLD = 0.30f
        private const val TOP_K = 5_000
        private const val FACE_RESULT_COLUMN_COUNT = 15
        private const val KPS_32_OUTPUT_INDEX = 11

        val CONTRACT = LiteRtModelContract(
            artifactId = ARTIFACT_ID,
            assetPath = ASSET_PATH,
            inputs = listOf(LiteRtFloatTensorSpec("input", listOf(1, 3, INPUT_SIZE, INPUT_SIZE))),
            outputs = listOf(
                LiteRtFloatTensorSpec("Identity", listOf(1, 1_600, 1)),
                LiteRtFloatTensorSpec("Identity_1", listOf(1, 400, 1)),
                LiteRtFloatTensorSpec("Identity_2", listOf(1, 100, 1)),
                LiteRtFloatTensorSpec("Identity_3", listOf(1, 1_600, 1)),
                LiteRtFloatTensorSpec("Identity_4", listOf(1, 400, 1)),
                LiteRtFloatTensorSpec("Identity_5", listOf(1, 100, 1)),
                LiteRtFloatTensorSpec("Identity_6", listOf(1, 1_600, 4)),
                LiteRtFloatTensorSpec("Identity_7", listOf(1, 400, 4)),
                LiteRtFloatTensorSpec("Identity_8", listOf(1, 100, 4)),
                LiteRtFloatTensorSpec("Identity_9", listOf(1, 1_600, 10)),
                LiteRtFloatTensorSpec("Identity_10", listOf(1, 400, 10)),
                LiteRtFloatTensorSpec("Identity_11", listOf(1, 100, 10)),
            ),
        )
    }
}

internal fun prepareYuNetLiteRtInput(imageBgr: Mat): FloatArray =
    prepareYuNetNchwInput(imageBgr, 320)

internal fun prepareYuNetNchwInput(imageBgr: Mat, inputSize: Int): FloatArray {
    require(inputSize > 0 && inputSize % 32 == 0)
    val resized = Mat()
    val floatImage = Mat()
    try {
        Imgproc.resize(imageBgr, resized, Size(inputSize.toDouble(), inputSize.toDouble()))
        resized.convertTo(floatImage, CvType.CV_32FC3)
        val interleaved = FloatArray(inputSize * inputSize * 3)
        floatImage.get(0, 0, interleaved)
        val plane = inputSize * inputSize
        return FloatArray(interleaved.size).also { nchw ->
            repeat(plane) { pixel ->
                val source = pixel * 3
                nchw[pixel] = interleaved[source]
                nchw[plane + pixel] = interleaved[source + 1]
                nchw[plane * 2 + pixel] = interleaved[source + 2]
            }
        }
    } finally {
        floatImage.release()
        resized.release()
    }
}

/**
 * onnx2tf emits Identity_11 as transpose(reshape(ONNX kps_32, 10x10x10), 2,0,1).
 * Decoding requires the inverse permutation (1,2,0), not the raw TFLite order.
 */
internal fun invertYuNetKps32ConversionPermutation(converted: FloatArray): FloatArray {
    require(converted.size == 10 * 10 * 10)
    val restored = FloatArray(converted.size)
    repeat(10) { row ->
        repeat(10) { column ->
            repeat(10) { component ->
                val onnxIndex = (row * 10 + column) * 10 + component
                val convertedIndex = (component * 10 + row) * 10 + column
                restored[onnxIndex] = converted[convertedIndex]
            }
        }
    }
    return restored
}

internal object YuNetLiteRtPostprocessor {
    private val strides = intArrayOf(8, 16, 32)

    fun decode(
        outputs: List<FloatArray>,
        imageWidth: Int,
        imageHeight: Int,
        scoreThreshold: Float,
        nmsThreshold: Float,
        topK: Int,
        inputSize: Int = 320,
    ): List<YuNetLiteRtDetection> {
        require(outputs.size == 12)
        require(imageWidth > 0 && imageHeight > 0)
        require(inputSize > 0 && inputSize % 32 == 0)
        val anchorCounts = strides.map { stride ->
            val gridSize = inputSize / stride
            gridSize * gridSize
        }
        val scaleX = imageWidth / inputSize.toFloat()
        val scaleY = imageHeight / inputSize.toFloat()
        val candidates = buildList {
            strides.indices.forEach { level ->
                val stride = strides[level]
                val gridWidth = inputSize / stride
                val cls = outputs[level]
                val obj = outputs[level + 3]
                val bbox = outputs[level + 6]
                val kps = outputs[level + 9]
                require(cls.size == anchorCounts[level])
                require(obj.size == anchorCounts[level])
                require(bbox.size == anchorCounts[level] * 4)
                require(kps.size == anchorCounts[level] * 10)
                repeat(anchorCounts[level]) { anchor ->
                    val score = sqrt(
                        cls[anchor].coerceIn(0f, 1f) * obj[anchor].coerceIn(0f, 1f),
                    )
                    if (score < scoreThreshold) return@repeat
                    val row = anchor / gridWidth
                    val column = anchor % gridWidth
                    val boxOffset = anchor * 4
                    val centerX = (column + bbox[boxOffset]) * stride * scaleX
                    val centerY = (row + bbox[boxOffset + 1]) * stride * scaleY
                    val width = exp(bbox[boxOffset + 2]) * stride * scaleX
                    val height = exp(bbox[boxOffset + 3]) * stride * scaleY
                    val left = centerX - width / 2f
                    val top = centerY - height / 2f
                    val right = centerX + width / 2f
                    val bottom = centerY + height / 2f
                    require(
                        listOf(score, left, top, right, bottom).all(Float::isFinite),
                    ) {
                        "YuNet LiteRT produced a non-finite decoded box"
                    }
                    if (right <= left || bottom <= top) return@repeat
                    val landmarks = FloatArray(10)
                    repeat(5) { landmark ->
                        val offset = anchor * 10 + landmark * 2
                        landmarks[landmark * 2] =
                            (column + kps[offset]) * stride * scaleX
                        landmarks[landmark * 2 + 1] =
                            (row + kps[offset + 1]) * stride * scaleY
                    }
                    require(landmarks.all(Float::isFinite)) {
                        "YuNet LiteRT produced a non-finite decoded landmark"
                    }
                    add(
                        YuNetLiteRtDetection(
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            landmarks = landmarks,
                            score = score,
                        ),
                    )
                }
            }
        }.sortedByDescending(YuNetLiteRtDetection::score).take(topK)
        val selected = mutableListOf<YuNetLiteRtDetection>()
        candidates.forEach { candidate ->
            if (selected.none { rect2iIntersectionOverUnion(candidate, it) > nmsThreshold }) {
                selected += candidate
            }
        }
        return selected
    }

    /**
     * OpenCV FaceDetectorYN passes decoded float boxes through Rect2i before DNN NMS.
     * Kotlin toInt() has the same truncation-toward-zero behavior as the C++ conversion.
     */
    private fun rect2iIntersectionOverUnion(
        left: YuNetLiteRtDetection,
        right: YuNetLiteRtDetection,
    ): Float {
        val leftX = left.left.toInt()
        val leftY = left.top.toInt()
        val leftWidth = (left.right - left.left).toInt()
        val leftHeight = (left.bottom - left.top).toInt()
        val rightX = right.left.toInt()
        val rightY = right.top.toInt()
        val rightWidth = (right.right - right.left).toInt()
        val rightHeight = (right.bottom - right.top).toInt()
        val intersectionWidth =
            max(0, min(leftX + leftWidth, rightX + rightWidth) - max(leftX, rightX))
        val intersectionHeight =
            max(0, min(leftY + leftHeight, rightY + rightHeight) - max(leftY, rightY))
        val intersection = intersectionWidth.toLong() * intersectionHeight
        val union =
            leftWidth.toLong() * leftHeight + rightWidth.toLong() * rightHeight - intersection
        return if (union <= 0L) 0f else intersection.toFloat() / union
    }
}

internal data class YuNetLiteRtDetection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val landmarks: FloatArray,
    val score: Float,
) {
    init {
        require(landmarks.size == 10)
        require(score.isFinite())
    }

    val area: Float get() = max(0f, right - left) * max(0f, bottom - top)

    fun asOpenCvRow(): FloatArray = floatArrayOf(
        left,
        top,
        right - left,
        bottom - top,
        *landmarks,
        score,
    )
}
