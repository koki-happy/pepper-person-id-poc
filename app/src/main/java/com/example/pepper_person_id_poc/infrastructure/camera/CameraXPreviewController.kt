package com.example.pepper_person_id_poc.infrastructure.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.example.pepper_person_id_poc.domain.benchmark.RateMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraXPreviewController(
    context: Context,
    private val frameProcessor: CameraFrameProcessor = CameraFrameProcessor.NoOp,
) : Closeable {
    private val appContext = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mutablePreviewFrame = MutableStateFlow<Bitmap?>(null)
    private val mutableState = MutableStateFlow(CameraPreviewState())
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analyzedFrameCount = 0L
    private var lastPreviewAtMillis = 0L
    private val inputFrameRateMeter = RateMeter()
    private var closed = false

    val previewFrame: StateFlow<Bitmap?> = mutablePreviewFrame.asStateFlow()
    val state: StateFlow<CameraPreviewState> = mutableState.asStateFlow()

    fun bind(lifecycleOwner: LifecycleOwner) {
        if (closed || mutableState.value.status == CameraStatus.Starting) return
        mutableState.value = CameraPreviewState(status = CameraStatus.Starting)
        analyzedFrameCount = 0L
        inputFrameRateMeter.reset()

        val providerFuture = ProcessCameraProvider.getInstance(appContext)
        providerFuture.addListener(
            {
                if (closed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                TARGET_RESOLUTION,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                            ),
                        )
                        .build()
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor) { image ->
                                try {
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastPreviewAtMillis >= PREVIEW_FRAME_INTERVAL_MILLIS) {
                                        mutablePreviewFrame.value = image.toDisplayBitmap()
                                        lastPreviewAtMillis = now
                                    }
                                    frameProcessor.process(image)
                                    analyzedFrameCount += 1
                                    val inputFps = inputFrameRateMeter.record(now)
                                    if (analyzedFrameCount == 1L ||
                                        analyzedFrameCount % STATE_UPDATE_FRAME_INTERVAL == 0L
                                    ) {
                                        mutableState.value = CameraPreviewState(
                                            status = CameraStatus.Running,
                                            frameCount = analyzedFrameCount,
                                            inputFramesPerSecond = inputFps,
                                            resolution = "${image.width}x${image.height}",
                                        )
                                    }
                                } finally {
                                    image.close()
                                }
                            }
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis,
                    )
                    cameraProvider = provider
                    imageAnalysis = analysis
                }.onFailure(::reportError)
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun unbind() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        mutablePreviewFrame.value = null
        inputFrameRateMeter.reset()
        if (!closed) {
            mutableState.value = mutableState.value.copy(status = CameraStatus.Stopped)
        }
    }

    fun closeAndAwaitAnalysis(): Boolean {
        if (!closed) {
            unbind()
            closed = true
            cameraExecutor.shutdown()
        }
        return try {
            if (cameraExecutor.awaitTermination(ANALYSIS_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                true
            } else {
                cameraExecutor.shutdownNow()
                cameraExecutor.awaitTermination(ANALYSIS_FORCE_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        } catch (_: InterruptedException) {
            cameraExecutor.shutdownNow()
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun close() {
        closeAndAwaitAnalysis()
    }

    private fun reportError(throwable: Throwable) {
        mutableState.value = CameraPreviewState(
            status = CameraStatus.Error,
            error = throwable.message ?: throwable::class.java.simpleName,
        )
    }

    private companion object {
        val TARGET_RESOLUTION = Size(640, 480)
        const val STATE_UPDATE_FRAME_INTERVAL = 15L
        const val PREVIEW_FRAME_INTERVAL_MILLIS = 1_000L
        const val ANALYSIS_SHUTDOWN_TIMEOUT_SECONDS = 3L
        const val ANALYSIS_FORCE_SHUTDOWN_TIMEOUT_SECONDS = 2L
    }
}

private fun androidx.camera.core.ImageProxy.toDisplayBitmap(): Bitmap {
    val source = toBitmap()
    val matrix = Matrix().apply {
        postRotate(imageInfo.rotationDegrees.toFloat())
        postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
        if (it !== source) source.recycle()
    }
}
