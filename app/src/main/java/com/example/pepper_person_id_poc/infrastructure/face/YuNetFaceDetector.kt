package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.benchmark.RateMeter
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
    private val embeddingEngine: FaceEmbeddingEngine? = null,
    private val onFeatureObservations: (List<FaceFeatureObservation>) -> Unit = {},
    private val onEmbeddingReady: () -> Unit = {},
    private val onEmbeddingError: (Throwable) -> Unit = {},
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
    private var embeddingPreparationAttempted = false
    private var embeddingPrepared = false
    private val analysisRateMeter = RateMeter(minimumWindowMillis = 1_500L)

    val snapshot: StateFlow<FaceDetectionSnapshot> = mutableSnapshot.asStateFlow()

    override fun process(image: ImageProxy) {
        if (closed) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextAnalysisAtMillis) return
        nextAnalysisAtMillis = now + ANALYSIS_INTERVAL_MILLIS

        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val analysisFps = analysisRateMeter.record(now)
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
                val activeEmbeddingEngine = embeddingEngine
                if (activeEmbeddingEngine != null && !embeddingPreparationAttempted) {
                    embeddingPreparationAttempted = true
                    val preparationStartedAtNanos = SystemClock.elapsedRealtimeNanos()
                    runCatching(activeEmbeddingEngine::prepare)
                        .onSuccess {
                            embeddingPrepared = true
                            onEmbeddingReady()
                            onBenchmarkEvent(
                                BenchmarkEvent(
                                    event = "face_embedding_model_init",
                                    timestampMillis = System.currentTimeMillis(),
                                    durationMillis =
                                        (SystemClock.elapsedRealtimeNanos() - preparationStartedAtNanos) / 1_000_000L,
                                    status = "SUCCESS",
                                    attributes = mapOf("model" to activeEmbeddingEngine.modelName),
                                ),
                            )
                        }
                        .onFailure { throwable ->
                            onEmbeddingError(throwable)
                            onBenchmarkEvent(
                                BenchmarkEvent(
                                    event = "face_embedding_model_init",
                                    timestampMillis = System.currentTimeMillis(),
                                    status = "ERROR",
                                    attributes = mapOf("model" to activeEmbeddingEngine.modelName),
                                    error = throwable.message ?: throwable::class.java.simpleName,
                                ),
                            )
                        }
                }
                val featureObservations = if (activeEmbeddingEngine == null || !embeddingPrepared) {
                    emptyList()
                } else {
                    tracked.mapIndexedNotNull { index, track ->
                        val embeddingStartedAtNanos = SystemClock.elapsedRealtimeNanos()
                        val detectedFace = faces.row(index)
                        try {
                            runCatching {
                                activeEmbeddingEngine.extract(bgr, detectedFace)
                            }.onFailure { throwable ->
                                onEmbeddingError(throwable)
                                onBenchmarkEvent(
                                    BenchmarkEvent(
                                        event = "face_embedding",
                                        timestampMillis = System.currentTimeMillis(),
                                        status = "ERROR",
                                        attributes = mapOf("model" to activeEmbeddingEngine.modelName),
                                        error = throwable.message ?: throwable::class.java.simpleName,
                                    ),
                                )
                            }.getOrNull()?.let { embedding ->
                                val embeddingMillis =
                                    (SystemClock.elapsedRealtimeNanos() - embeddingStartedAtNanos) / 1_000_000L
                                onBenchmarkEvent(
                                    BenchmarkEvent(
                                        event = "face_embedding",
                                        timestampMillis = System.currentTimeMillis(),
                                        durationMillis = embeddingMillis,
                                        status = "SUCCESS",
                                        attributes = mapOf(
                                            "model" to activeEmbeddingEngine.modelName,
                                            "dimension" to embedding.size.toString(),
                                        ),
                                    ),
                                )
                                FaceFeatureObservation(track.trackId, embedding, embeddingMillis)
                            }
                        } finally {
                            detectedFace.release()
                        }
                    }
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
                    analysisFramesPerSecond = analysisFps,
                )
                onFeatureObservations(featureObservations)
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
        const val ANALYSIS_INTERVAL_MILLIS = 1_000L
        const val RGBA_PIXEL_STRIDE = 4
        const val FACE_RESULT_COLUMN_COUNT = 15
    }
}
