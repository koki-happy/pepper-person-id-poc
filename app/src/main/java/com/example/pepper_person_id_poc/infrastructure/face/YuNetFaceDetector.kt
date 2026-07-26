package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import android.os.SystemClock
import android.os.Debug
import androidx.camera.core.ImageProxy
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.benchmark.RateMeter
import com.example.pepper_person_id_poc.domain.face.DetectedFace
import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.domain.face.FaceDetectionStatus
import com.example.pepper_person_id_poc.domain.face.FaceLandmark
import com.example.pepper_person_id_poc.domain.face.FaceLandmarkType
import com.example.pepper_person_id_poc.domain.face.FaceTracker
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
import com.example.pepper_person_id_poc.domain.face.FaceQualityInput
import com.example.pepper_person_id_poc.domain.face.FaceQualityPolicy
import com.example.pepper_person_id_poc.domain.face.FaceQualityThresholds
import com.example.pepper_person_id_poc.domain.face.NormalizedBoundingBox
import com.example.pepper_person_id_poc.domain.face.PixelBoundingBox
import com.example.pepper_person_id_poc.domain.face.PixelFaceLandmark
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.infrastructure.camera.CameraFrameProcessor
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN

class YuNetFaceDetector(
    context: Context,
    private val detectorOption: FaceDetectorModelOption = FaceDetectorModelOption.YUNET_2026MAY_FP32,
    private val embeddingEngine: FaceEmbeddingEngine? = null,
    private val analysisIntervalMillis: Long = DEFAULT_ANALYSIS_INTERVAL_MILLIS,
    private val estimateHeadPose: Boolean = true,
    private val onPoseObservations: (Int, List<FacePoseObservation>) -> Set<String> = { _, _ -> emptySet() },
    private val onFeatureObservations: (List<FaceFeatureObservation>) -> Unit = {},
    private val onEmbeddingReady: () -> Unit = {},
    private val onEmbeddingError: (Throwable) -> Unit = {},
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
) : FaceDetectorPipeline {
    init {
        require(analysisIntervalMillis > 0L)
        requireNotNull(detectorOption.modelFileName) { "YuNet requires an ONNX model asset" }
    }
    private val appContext = context.applicationContext
    private val tracker = FaceTracker()
    private val headPoseEstimator = HeadPoseEstimator()
    private val qualityAnalyzer = FaceImageQualityAnalyzer()
    private val qualityPolicy = FaceQualityPolicy(FaceQualityThresholds.DEFAULT)
    private val mutableSnapshot = MutableStateFlow(FaceDetectionSnapshot(modelName = detectorOption.displayName))
    private var detector: FaceDetectorYN? = null
    private var detectorInputSize: Size? = null
    private var nextAnalysisAtMillis = 0L
    private var closed = false
    private var initializationAttempted = false
    private var embeddingPreparationAttempted = false
    private var embeddingPrepared = false
    private var analyzerInputFrameCount = 0L
    private var analyzedFrameCount = 0L
    private var throttleSkippedFrameCount = 0L
    private val analysisRateMeter = RateMeter(minimumWindowMillis = 1_500L)

    override val snapshot: StateFlow<FaceDetectionSnapshot> = mutableSnapshot.asStateFlow()

    override fun process(image: ImageProxy) {
        if (closed) return
        analyzerInputFrameCount += 1L
        val now = SystemClock.elapsedRealtime()
        if (now < nextAnalysisAtMillis) {
            throttleSkippedFrameCount += 1L
            emitPipelineCountersIfDue()
            return
        }
        nextAnalysisAtMillis = now + analysisIntervalMillis
        analyzedFrameCount += 1L

        val startedAtNanos = SystemClock.elapsedRealtimeNanos()
        val analysisFps = analysisRateMeter.record(now)
        runCatching {
            val activeDetector = getOrCreateDetector(image.width, image.height) ?: return
            val rgba = image.toRgbaMat()
            val rawBgr = Mat()
            val bgr = Mat()
            val faces = Mat()
            try {
                Imgproc.cvtColor(rgba, rawBgr, Imgproc.COLOR_RGBA2BGR)
                rawBgr.rotateInto(bgr, image.imageInfo.rotationDegrees)
                val requestedSize = Size(bgr.cols().toDouble(), bgr.rows().toDouble())
                if (detectorInputSize != requestedSize) {
                    activeDetector.setInputSize(requestedSize)
                    detectorInputSize = requestedSize
                }
                activeDetector.detect(bgr, faces)
                val rawFaces = faces.toRawFaces(bgr.cols(), bgr.rows())
                val tracked = tracker.update(
                    detections = rawFaces.map { it.boundingBox },
                    landmarkCounts = rawFaces.map { it.landmarks.size / 2 },
                )
                val processingMillis = (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000L
                val rawDetectedFaces = rawFaces.zip(tracked).map { (raw, track) ->
                    DetectedFace(
                        trackId = track.trackId,
                        boundingBox = track.boundingBox,
                        detectionScore = raw.score,
                        landmarks = raw.normalizedLandmarks(bgr.cols(), bgr.rows()),
                        inputBoundingBox = raw.inputBoundingBox,
                        inputLandmarks = raw.inputLandmarks(),
                        trackDurationMillis = track.trackDurationMillis,
                        detectedLandmarkCount = track.landmarkCount,
                    )
                }
                val poseStartedAtNanos = SystemClock.elapsedRealtimeNanos()
                val poseObservations = rawFaces.zip(tracked).map { (raw, track) ->
                    FacePoseObservation(
                        trackId = track.trackId,
                        headPose = if (estimateHeadPose) {
                            headPoseEstimator.estimate(raw.landmarks, bgr.cols(), bgr.rows())
                        } else {
                            null
                        },
                    )
                }
                val detectedFaces = rawDetectedFaces.mapIndexed { index, detectedFace ->
                    val raw = rawFaces[index]
                    val track = tracked[index]
                    val pose = poseObservations[index].headPose
                    val pixelBounds = FacePixelBounds(
                        left = raw.inputBoundingBox.left.toInt(),
                        top = raw.inputBoundingBox.top.toInt(),
                        right = raw.inputBoundingBox.right.toInt(),
                        bottom = raw.inputBoundingBox.bottom.toInt(),
                    )
                    val metrics = qualityAnalyzer.analyzeBgr(bgr, pixelBounds)
                    detectedFace.copy(
                        qualityAssessment = qualityPolicy.assess(
                            FaceQualityInput(
                                faceWidthPixels = pixelBounds.width,
                                faceHeightPixels = pixelBounds.height,
                                detectionConfidence = raw.score,
                                landmarkCount = track.landmarkCount,
                                blurScore = metrics.blurScore,
                                brightnessMean = metrics.brightnessMean,
                                clippedRatio = metrics.clippedRatio,
                                yawDegrees = pose?.yawDegrees ?: 0f,
                                pitchDegrees = pose?.pitchDegrees ?: 0f,
                                rollDegrees = pose?.rollDegrees ?: 0f,
                                edgeTruncationRatio = metrics.edgeTruncationRatio,
                                trackDurationMillis = track.trackDurationMillis,
                            ),
                        ),
                    )
                }
                val poseMillis = (SystemClock.elapsedRealtimeNanos() - poseStartedAtNanos) / 1_000_000L
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_pose",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = poseMillis,
                        status = "SUCCESS",
                        attributes = mapOf(
                            "faceCount" to detectedFaces.size.toString(),
                            "estimatedPoseCount" to poseObservations.count { it.headPose != null }.toString(),
                            "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize().toString(),
                        ),
                    ),
                )
                val embeddingTrackIds = onPoseObservations(detectedFaces.size, poseObservations)
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
                        if (track.trackId !in embeddingTrackIds) return@mapIndexedNotNull null
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
                                FaceFeatureObservation(
                                    trackId = track.trackId,
                                    embedding = embedding,
                                    embeddingTimeMillis = embeddingMillis,
                                    qualityAssessment = detectedFaces[index].qualityAssessment,
                                )
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
                    modelName = detectorOption.displayName,
                    orientedFrameWidth = bgr.cols(),
                    orientedFrameHeight = bgr.rows(),
                )
                onFeatureObservations(featureObservations)
                val pipelineFinishedAtNanos = SystemClock.elapsedRealtimeNanos()
                val pipelineMillis = (pipelineFinishedAtNanos - startedAtNanos) / 1_000_000L
                val captureToStateReadyNanos = pipelineFinishedAtNanos - image.imageInfo.timestamp
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_detection",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = processingMillis,
                        status = status.name,
                        attributes = mapOf(
                            "model" to detectorOption.displayName,
                            "faceCount" to detectedFaces.size.toString(),
                            "frameWidth" to image.width.toString(),
                            "frameHeight" to image.height.toString(),
                            "orientedFrameWidth" to bgr.cols().toString(),
                            "orientedFrameHeight" to bgr.rows().toString(),
                            "rotationDegrees" to image.imageInfo.rotationDegrees.toString(),
                            "analysisIntervalMillis" to analysisIntervalMillis.toString(),
                            "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize().toString(),
                        ),
                    ),
                )
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_pipeline_frame",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = pipelineMillis,
                        status = status.name,
                        attributes = mapOf(
                            "faceCount" to detectedFaces.size.toString(),
                            "embeddingCount" to featureObservations.size.toString(),
                            "analyzerInputFrameCount" to analyzerInputFrameCount.toString(),
                            "analyzedFrameCount" to analyzedFrameCount.toString(),
                            "throttleSkippedFrameCount" to throttleSkippedFrameCount.toString(),
                            "analysisFramesPerSecond" to analysisFps.toString(),
                            "captureToStateReadyMillis" to if (
                                captureToStateReadyNanos in 0L..MAX_REASONABLE_CAPTURE_TO_STATE_READY_NANOS
                            ) {
                                (captureToStateReadyNanos / 1_000_000L).toString()
                            } else {
                                "UNAVAILABLE_TIMEBASE_MISMATCH"
                            },
                            "imageTimestampNanos" to image.imageInfo.timestamp.toString(),
                            "stateReadyElapsedRealtimeNanos" to pipelineFinishedAtNanos.toString(),
                        ),
                    ),
                )
                emitPipelineCountersIfDue()
            } finally {
                faces.release()
                bgr.release()
                rawBgr.release()
                rgba.release()
            }
        }.onFailure(::reportError)
    }

    private fun emitPipelineCountersIfDue() {
        if (analyzerInputFrameCount % PIPELINE_COUNTER_INTERVAL != 0L) return
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "face_pipeline_counters",
                timestampMillis = System.currentTimeMillis(),
                status = "SUCCESS",
                attributes = mapOf(
                    "analyzerInputFrameCount" to analyzerInputFrameCount.toString(),
                    "analyzedFrameCount" to analyzedFrameCount.toString(),
                    "throttleSkippedFrameCount" to throttleSkippedFrameCount.toString(),
                    "analyzerThrottleDropRate" to if (analyzerInputFrameCount == 0L) {
                        "0.0"
                    } else {
                        (throttleSkippedFrameCount.toDouble() / analyzerInputFrameCount).toString()
                    },
                    "cameraProducerDroppedFrameCount" to "UNAVAILABLE_KEEP_ONLY_LATEST",
                ),
            ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        detector = null
        embeddingEngine?.close()
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
        val modelFileName = checkNotNull(detectorOption.modelFileName)
        val output = File(appContext.filesDir, "models/$modelFileName")
        if (output.exists() && output.length() > 0) return output
        output.parentFile?.mkdirs()
        appContext.assets.open("models/$modelFileName").use { input ->
            output.outputStream().use(input::copyTo)
        }
        return output
    }

    private fun reportModelUnavailable(message: String) {
        mutableSnapshot.value = FaceDetectionSnapshot(
            status = FaceDetectionStatus.MODEL_UNAVAILABLE,
            modelName = detectorOption.displayName,
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
            modelName = detectorOption.displayName,
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

    private fun Mat.toRawFaces(
        imageWidth: Int,
        imageHeight: Int,
    ): List<RawFace> = (0 until rows()).map { row ->
        val values = FloatArray(FACE_RESULT_COLUMN_COUNT)
        get(row, 0, values)
        val inputBoundingBox = PixelBoundingBox(
            left = values[0],
            top = values[1],
            right = values[0] + values[2],
            bottom = values[1] + values[3],
        )
        val left = (inputBoundingBox.left / imageWidth).coerceIn(0f, 1f)
        val top = (inputBoundingBox.top / imageHeight).coerceIn(0f, 1f)
        val right = (inputBoundingBox.right / imageWidth).coerceIn(0f, 1f)
        val bottom = (inputBoundingBox.bottom / imageHeight).coerceIn(0f, 1f)
        RawFace(
            boundingBox = NormalizedBoundingBox(left, top, right, bottom),
            inputBoundingBox = inputBoundingBox,
            score = values[14],
            landmarks = values.copyOfRange(4, 14),
        )
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

    private data class RawFace(
        val boundingBox: NormalizedBoundingBox,
        val inputBoundingBox: PixelBoundingBox,
        val score: Float,
        val landmarks: FloatArray,
    ) {
        fun normalizedLandmarks(imageWidth: Int, imageHeight: Int): List<FaceLandmark> =
            FaceLandmarkType.entries.mapIndexed { index, type ->
                FaceLandmark(
                    type = type,
                    x = (landmarks[index * 2] / imageWidth).coerceIn(0f, 1f),
                    y = (landmarks[index * 2 + 1] / imageHeight).coerceIn(0f, 1f),
                )
            }

        fun inputLandmarks(): List<PixelFaceLandmark> =
            FaceLandmarkType.entries.mapIndexed { index, type ->
                PixelFaceLandmark(
                    type = type,
                    x = landmarks[index * 2],
                    y = landmarks[index * 2 + 1],
                )
            }
    }

    private companion object {
        const val MAX_REASONABLE_CAPTURE_TO_STATE_READY_NANOS = 60_000_000_000L
        const val PIPELINE_COUNTER_INTERVAL = 30L
        const val SCORE_THRESHOLD = 0.80f
        const val NMS_THRESHOLD = 0.30f
        const val TOP_K = 5000
        const val DEFAULT_ANALYSIS_INTERVAL_MILLIS = 1_000L
        const val RGBA_PIXEL_STRIDE = 4
        const val FACE_RESULT_COLUMN_COUNT = 15
    }
}
