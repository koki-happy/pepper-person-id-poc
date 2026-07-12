package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.face.DetectedFace
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.domain.face.FaceDetectionStatus
import com.example.pepper_person_id_poc.domain.face.FaceTracker
import com.example.pepper_person_id_poc.domain.face.NormalizedBoundingBox
import com.example.pepper_person_id_poc.infrastructure.camera.CameraFrameProcessor
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN

class YuNetFaceDetector(
    context: Context,
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
) : CameraFrameProcessor, Closeable {
    private val appContext = context.applicationContext
    private val tracker = FaceTracker()
    private val mutableSnapshot = MutableStateFlow(FaceDetectionSnapshot())
    private var detector: FaceDetectorYN? = null
    private var detectorInputSize: Size? = null
    private var nextAnalysisAtMillis = 0L
    private var closed = false
    private var initializationAttempted = false

    val snapshot: StateFlow<FaceDetectionSnapshot> = mutableSnapshot.asStateFlow()

    override fun process(image: ImageProxy) {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextAnalysisAtMillis) return
        nextAnalysisAtMillis = now + ANALYSIS_INTERVAL_MILLIS

        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        runCatching {
            val activeDetector = getOrCreateDetector(image.width, image.height) ?: return
            val rgba = image.toRgbaMat()
            val bgr = Mat()
            val faces = Mat()
            try {
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                val requestedSize = Size(image.width.toDouble(), image.height.toDouble())
                if (detectorInputSize != requestedSize) {
                    activeDetector.setInputSize(requestedSize)
                    detectorInputSize = requestedSize
                }
                activeDetector.detect(bgr, faces)
                val rawFaces = faces.toBoundingBoxes(image.width, image.height, image.imageInfo.rotationDegrees)
                val tracked = tracker.update(rawFaces.map { it.boundingBox })
                val processingMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L
                val detectedFaces = rawFaces.zip(tracked).map { (raw, track) ->
                    DetectedFace(track.trackId, track.boundingBox, raw.score)
                }
                val status = when (detectedFaces.size) {
                    0 -> FaceDetectionStatus.NO_FACE
                    1 -> FaceDetectionStatus.FACE_DETECTED
                    else -> FaceDetectionStatus.MULTIPLE_FACES
                }
                mutableSnapshot.value = FaceDetectionSnapshot(
                    status = status,
                    faces = detectedFaces,
                    processingTimeMillis = processingMillis,
                )
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_detection",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = processingMillis,
                        status = status.name,
                        attributes = mapOf(
                            "model" to MODEL_NAME,
                            "faceCount" to detectedFaces.size.toString(),
                            "frameWidth" to image.width.toString(),
                            "frameHeight" to image.height.toString(),
                        ),
                    ),
                )
            } finally {
                faces.release()
                bgr.release()
                rgba.release()
            }
        }.onFailure(::reportError)
    }

    override fun close() {
        closed = true
        detector = null
    }

    private fun getOrCreateDetector(width: Int, height: Int): FaceDetectorYN? {
        detector?.let { return it }
        if (initializationAttempted) return null
        initializationAttempted = true
        if (!OpenCVLoader.initLocal()) {
            reportModelUnavailable("OpenCV 5.0.0 native runtime could not be loaded")
            return null
        }
        val modelFile = copyModelToInternalStorage()
        return FaceDetectorYN.create(
            modelFile.absolutePath,
            "",
            Size(width.toDouble(), height.toDouble()),
            SCORE_THRESHOLD,
            NMS_THRESHOLD,
            TOP_K,
        ).also {
            detector = it
            detectorInputSize = Size(width.toDouble(), height.toDouble())
        }
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

    private fun reportModelUnavailable(message: String) {
        mutableSnapshot.value = FaceDetectionSnapshot(
            status = FaceDetectionStatus.MODEL_UNAVAILABLE,
            error = message,
        )
    }

    private fun reportError(throwable: Throwable) {
        val message = throwable.message ?: throwable::class.java.simpleName
        mutableSnapshot.value = FaceDetectionSnapshot(
            status = if (throwable is java.io.FileNotFoundException) {
                FaceDetectionStatus.MODEL_UNAVAILABLE
            } else {
                FaceDetectionStatus.ERROR
            },
            error = message,
        )
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "face_detection",
                timestampMillis = System.currentTimeMillis(),
                status = "ERROR",
                error = message,
            ),
        )
    }

    private fun ImageProxy.toRgbaMat(): Mat {
        val plane = planes.first()
        require(plane.pixelStride == RGBA_PIXEL_STRIDE) {
            "Unexpected RGBA pixel stride: ${plane.pixelStride}"
        }
        val bytesPerRow = width * RGBA_PIXEL_STRIDE
        val packed = ByteArray(bytesPerRow * height)
        val buffer = plane.buffer.duplicate()
        repeat(height) { row ->
            buffer.position(row * plane.rowStride)
            buffer.get(packed, row * bytesPerRow, bytesPerRow)
        }
        return Mat(height, width, CvType.CV_8UC4).apply { put(0, 0, packed) }
    }

    private fun Mat.toBoundingBoxes(
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int,
    ): List<RawFace> = (0 until rows()).map { row ->
        val values = FloatArray(FACE_RESULT_COLUMN_COUNT)
        get(row, 0, values)
        val left = (values[0] / imageWidth).coerceIn(0f, 1f)
        val top = (values[1] / imageHeight).coerceIn(0f, 1f)
        val right = ((values[0] + values[2]) / imageWidth).coerceIn(0f, 1f)
        val bottom = ((values[1] + values[3]) / imageHeight).coerceIn(0f, 1f)
        RawFace(
            boundingBox = NormalizedBoundingBox(left, top, right, bottom).rotated(rotationDegrees),
            score = values[14],
        )
    }

    private data class RawFace(
        val boundingBox: NormalizedBoundingBox,
        val score: Float,
    )

    private companion object {
        const val MODEL_NAME = "YuNet 2026may"
        const val MODEL_FILE_NAME = "face_detection_yunet_2026may.onnx"
        const val MODEL_ASSET_PATH = "models/$MODEL_FILE_NAME"
        const val SCORE_THRESHOLD = 0.80f
        const val NMS_THRESHOLD = 0.30f
        const val TOP_K = 5000
        const val ANALYSIS_INTERVAL_MILLIS = 500L
        const val RGBA_PIXEL_STRIDE = 4
        const val FACE_RESULT_COLUMN_COUNT = 15
    }
}
