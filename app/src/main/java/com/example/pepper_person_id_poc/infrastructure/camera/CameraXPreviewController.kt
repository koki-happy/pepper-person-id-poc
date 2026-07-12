package com.example.pepper_person_id_poc.infrastructure.camera

import android.content.Context
import android.util.Size
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private val mutableSurfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    private val mutableState = MutableStateFlow(CameraPreviewState())
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analyzedFrameCount = 0L
    private val inputFrameRateMeter = RateMeter()
    private var closed = false

    val surfaceRequest: StateFlow<SurfaceRequest?> = mutableSurfaceRequest.asStateFlow()
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
                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .apply {
                            setSurfaceProvider { request -> mutableSurfaceRequest.value = request }
                        }
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .apply {
                            setAnalyzer(cameraExecutor) { image ->
                                try {
                                    frameProcessor.process(image)
                                    analyzedFrameCount += 1
                                    val inputFps = inputFrameRateMeter.record(SystemClock.elapsedRealtime())
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
                        preview,
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
        mutableSurfaceRequest.value = null
        inputFrameRateMeter.reset()
        if (!closed) {
            mutableState.value = mutableState.value.copy(status = CameraStatus.Stopped)
        }
    }

    override fun close() {
        if (closed) return
        unbind()
        closed = true
        cameraExecutor.shutdown()
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
    }
}
