package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.benchmark.RateMeter
import com.example.pepper_person_id_poc.domain.face.DetectedFace
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.domain.face.FaceDetectionStatus
import com.example.pepper_person_id_poc.domain.face.FaceLandmark
import com.example.pepper_person_id_poc.domain.face.FaceLandmarkType
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
import com.example.pepper_person_id_poc.domain.face.FaceTracker
import com.example.pepper_person_id_poc.domain.face.HeadPose
import com.example.pepper_person_id_poc.domain.face.NormalizedBoundingBox
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark as MlKitLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class MlKitFaceDetector(
    context: Context,
    private val embeddingEngine: FaceEmbeddingEngine? = null,
    private val analysisIntervalMillis: Long,
    private val estimateHeadPose: Boolean = true,
    private val onPoseObservations: (Int, List<FacePoseObservation>) -> Set<String> = { _, _ -> emptySet() },
    private val onFeatureObservations: (List<FaceFeatureObservation>) -> Unit = {},
    private val onEmbeddingReady: () -> Unit = {},
    private val onEmbeddingError: (Throwable) -> Unit = {},
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
) : FaceDetectorPipeline {
    init {
        require(analysisIntervalMillis > 0L)
    }

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .setMinFaceSize(MINIMUM_FACE_SIZE)
            .build(),
    )
    private val fallbackTracker = FaceTracker()
    private val mutableSnapshot = MutableStateFlow(FaceDetectionSnapshot(modelName = MODEL_NAME))
    private val analysisRateMeter = RateMeter(minimumWindowMillis = 1_500L)
    private var nextAnalysisAtMillis = 0L
    private var closed = false
    private var embeddingPreparationAttempted = false
    private var embeddingPrepared = false
    private var analyzerInputFrameCount = 0L
    private var analyzedFrameCount = 0L
    private var throttleSkippedFrameCount = 0L

    override val snapshot: StateFlow<FaceDetectionSnapshot> = mutableSnapshot.asStateFlow()

    override fun process(image: ImageProxy) {
        if (closed) return
        analyzerInputFrameCount += 1L
        val now = SystemClock.elapsedRealtime()
        if (now < nextAnalysisAtMillis) {
            throttleSkippedFrameCount += 1L
            return
        }
        nextAnalysisAtMillis = now + analysisIntervalMillis
        analyzedFrameCount += 1L
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val analysisFps = analysisRateMeter.record(now)
        runCatching {
            require(OpenCVLoader.initLocal()) { "OpenCV 5.0.0 native runtime could not be loaded for image conversion" }
            val rgba = image.toRgbaMat()
            val rawBgr = Mat()
            val bgr = Mat()
            val displayRgba = Mat()
            try {
                Imgproc.cvtColor(rgba, rawBgr, Imgproc.COLOR_RGBA2BGR)
                rawBgr.rotateInto(bgr, image.imageInfo.rotationDegrees)
                Imgproc.cvtColor(bgr, displayRgba, Imgproc.COLOR_BGR2RGBA)
                val bitmap = Bitmap.createBitmap(bgr.cols(), bgr.rows(), Bitmap.Config.ARGB_8888)
                try {
                    Utils.matToBitmap(displayRgba, bitmap)
                    val faces = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                    processFaces(faces, bgr, image, analysisFps, startedAtNanos)
                } finally {
                    bitmap.recycle()
                }
            } finally {
                displayRgba.release()
                bgr.release()
                rawBgr.release()
                rgba.release()
            }
        }.onFailure(::reportError)
    }

    private fun processFaces(
        faces: List<Face>,
        bgr: Mat,
        image: ImageProxy,
        analysisFps: Float,
        startedAtNanos: Long,
    ) {
        val boxes = faces.map { it.normalizedBoundingBox(bgr.cols(), bgr.rows()) }
        val fallbackTracks = fallbackTracker.update(boxes)
        val observations = faces.mapIndexed { index, face ->
            val trackId = face.trackingId?.let { "mlkit-face-${it.toString().padStart(3, '0')}" }
                ?: fallbackTracks[index].trackId
            MlKitFaceObservation(
                trackId = trackId,
                boundingBox = boxes[index],
                landmarks = face.fiveLandmarks(bgr.cols(), bgr.rows()),
                headPose = if (estimateHeadPose) {
                    HeadPose(
                        yawDegrees = face.headEulerAngleY,
                        pitchDegrees = -face.headEulerAngleX,
                        rollDegrees = face.headEulerAngleZ,
                    )
                } else {
                    null
                },
            )
        }
        val processingMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L
        val detectedFaces = observations.map { observation ->
            DetectedFace(
                trackId = observation.trackId,
                boundingBox = observation.boundingBox,
                // ML Kit does not expose face-detection confidence. The overlay labels this as N/A.
                detectionScore = 1f,
                landmarks = observation.landmarks,
            )
        }
        val embeddingTrackIds = onPoseObservations(
            detectedFaces.size,
            observations.map { FacePoseObservation(it.trackId, it.headPose) },
        )
        prepareEmbeddingIfNeeded()
        val activeEngine = embeddingEngine
        val features = if (activeEngine == null || !embeddingPrepared) {
            emptyList()
        } else {
            observations.mapNotNull { observation ->
                if (observation.trackId !in embeddingTrackIds) return@mapNotNull null
                val row = observation.toDetectorRow(bgr.cols(), bgr.rows())
                val embeddingStartedAtNanos = SystemClock.elapsedRealtimeNanos()
                try {
                    runCatching { activeEngine.extract(bgr, row) }
                        .onFailure(::reportEmbeddingFailure)
                        .getOrNull()
                        ?.let { embedding ->
                            val elapsed = (SystemClock.elapsedRealtimeNanos() - embeddingStartedAtNanos) / 1_000_000L
                            onBenchmarkEvent(
                                BenchmarkEvent(
                                    event = "face_embedding",
                                    timestampMillis = System.currentTimeMillis(),
                                    durationMillis = elapsed,
                                    status = "SUCCESS",
                                    attributes = mapOf(
                                        "model" to activeEngine.modelName,
                                        "dimension" to embedding.size.toString(),
                                    ),
                                ),
                            )
                            FaceFeatureObservation(observation.trackId, embedding, elapsed)
                        }
                } finally {
                    row.release()
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
            modelName = MODEL_NAME,
            orientedFrameWidth = bgr.cols(),
            orientedFrameHeight = bgr.rows(),
        )
        onFeatureObservations(features)
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "face_detection",
                timestampMillis = System.currentTimeMillis(),
                durationMillis = processingMillis,
                status = status.name,
                attributes = mapOf(
                    "model" to MODEL_NAME,
                    "faceCount" to detectedFaces.size.toString(),
                    "trackingIdCount" to faces.count { it.trackingId != null }.toString(),
                    "frameWidth" to image.width.toString(),
                    "frameHeight" to image.height.toString(),
                    "orientedFrameWidth" to bgr.cols().toString(),
                    "orientedFrameHeight" to bgr.rows().toString(),
                    "analysisIntervalMillis" to analysisIntervalMillis.toString(),
                    "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize().toString(),
                    "analyzerInputFrameCount" to analyzerInputFrameCount.toString(),
                    "analyzedFrameCount" to analyzedFrameCount.toString(),
                    "throttleSkippedFrameCount" to throttleSkippedFrameCount.toString(),
                ),
            ),
        )
    }

    private fun prepareEmbeddingIfNeeded() {
        val activeEngine = embeddingEngine ?: return
        if (embeddingPreparationAttempted) return
        embeddingPreparationAttempted = true
        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        runCatching(activeEngine::prepare)
            .onSuccess {
                embeddingPrepared = true
                onEmbeddingReady()
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_embedding_model_init",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L,
                        status = "SUCCESS",
                        attributes = mapOf("model" to activeEngine.modelName),
                    ),
                )
            }
            .onFailure(::reportEmbeddingFailure)
    }

    private fun reportEmbeddingFailure(throwable: Throwable) {
        onEmbeddingError(throwable)
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "face_embedding",
                timestampMillis = System.currentTimeMillis(),
                status = "ERROR",
                attributes = mapOf("model" to (embeddingEngine?.modelName ?: "none")),
                error = throwable.message ?: throwable::class.java.simpleName,
            ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        detector.close()
        embeddingEngine?.close()
    }

    private fun reportError(throwable: Throwable) {
        val message = throwable.message ?: throwable::class.java.simpleName
        mutableSnapshot.value = FaceDetectionSnapshot(
            status = FaceDetectionStatus.ERROR,
            modelName = MODEL_NAME,
            error = message,
        )
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "face_detection",
                timestampMillis = System.currentTimeMillis(),
                status = "ERROR",
                attributes = mapOf("model" to MODEL_NAME),
                error = message,
            ),
        )
    }

    private fun ImageProxy.toRgbaMat(): Mat {
        val plane = planes.first()
        require(plane.pixelStride == RGBA_PIXEL_STRIDE) { "Unexpected RGBA pixel stride: ${plane.pixelStride}" }
        val bytesPerRow = width * RGBA_PIXEL_STRIDE
        val packed = ByteArray(bytesPerRow * height)
        val buffer = plane.buffer.duplicate()
        repeat(height) { row ->
            buffer.position(row * plane.rowStride)
            buffer.get(packed, row * bytesPerRow, bytesPerRow)
        }
        return Mat(height, width, CvType.CV_8UC4).apply { put(0, 0, packed) }
    }

    private fun Mat.rotateInto(output: Mat, rotationDegrees: Int) {
        when (rotationDegrees) {
            0 -> copyTo(output)
            90 -> Core.rotate(this, output, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(this, output, Core.ROTATE_180)
            270 -> Core.rotate(this, output, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> error("Unsupported image rotation: $rotationDegrees")
        }
    }

    private fun Face.normalizedBoundingBox(width: Int, height: Int): NormalizedBoundingBox =
        NormalizedBoundingBox(
            left = (boundingBox.left.toFloat() / width).coerceIn(0f, 1f),
            top = (boundingBox.top.toFloat() / height).coerceIn(0f, 1f),
            right = (boundingBox.right.toFloat() / width).coerceIn(0f, 1f),
            bottom = (boundingBox.bottom.toFloat() / height).coerceIn(0f, 1f),
        )

    private fun Face.fiveLandmarks(width: Int, height: Int): List<FaceLandmark> {
        val box = boundingBox
        fun point(mlKitType: Int, fallbackX: Float, fallbackY: Float): Pair<Float, Float> {
            val position = getLandmark(mlKitType)?.position
            val x = position?.x ?: (box.left + box.width() * fallbackX)
            val y = position?.y ?: (box.top + box.height() * fallbackY)
            return x.coerceIn(0f, width.toFloat()) to y.coerceIn(0f, height.toFloat())
        }
        val raw = listOf(
            FaceLandmarkType.RIGHT_EYE to point(MlKitLandmark.RIGHT_EYE, 0.32f, 0.38f),
            FaceLandmarkType.LEFT_EYE to point(MlKitLandmark.LEFT_EYE, 0.68f, 0.38f),
            FaceLandmarkType.NOSE_TIP to point(MlKitLandmark.NOSE_BASE, 0.50f, 0.56f),
            FaceLandmarkType.RIGHT_MOUTH_CORNER to point(MlKitLandmark.MOUTH_RIGHT, 0.37f, 0.75f),
            FaceLandmarkType.LEFT_MOUTH_CORNER to point(MlKitLandmark.MOUTH_LEFT, 0.63f, 0.75f),
        )
        return raw.map { (type, position) ->
            FaceLandmark(type, (position.first / width).coerceIn(0f, 1f), (position.second / height).coerceIn(0f, 1f))
        }
    }

    private data class MlKitFaceObservation(
        val trackId: String,
        val boundingBox: NormalizedBoundingBox,
        val landmarks: List<FaceLandmark>,
        val headPose: HeadPose?,
    ) {
        fun toDetectorRow(width: Int, height: Int): Mat {
            val values = FloatArray(15)
            values[0] = boundingBox.left * width
            values[1] = boundingBox.top * height
            values[2] = boundingBox.width * width
            values[3] = boundingBox.height * height
            FaceLandmarkType.entries.forEachIndexed { index, type ->
                val landmark = landmarks.first { it.type == type }
                values[4 + index * 2] = landmark.x * width
                values[5 + index * 2] = landmark.y * height
            }
            values[14] = 1f
            return Mat(1, values.size, CvType.CV_32F).apply { put(0, 0, values) }
        }
    }

    private companion object {
        const val MODEL_NAME = "ML Kit Face Detection 16.1.7 (Bundled)"
        const val MINIMUM_FACE_SIZE = 0.10f
        const val RGBA_PIXEL_STRIDE = 4
    }
}
