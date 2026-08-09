package com.example.pepper_person_id_poc.infrastructure.device

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.pepper_person_id_poc.application.contract.DeviceDiagnosticsProvider
import com.example.pepper_person_id_poc.domain.device.AudioConfigurationDiagnostic
import com.example.pepper_person_id_poc.domain.device.CameraDiagnostic
import com.example.pepper_person_id_poc.domain.device.DeviceDiagnostics

class AndroidDeviceDiagnosticsProvider(
    context: Context,
) : DeviceDiagnosticsProvider {
    private val appContext = context.applicationContext

    override fun collect(): DeviceDiagnostics {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val displayMetrics = appContext.resources.displayMetrics

        return DeviceDiagnostics(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            buildId = Build.ID.orEmpty(),
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            maxHeapBytes = Runtime.getRuntime().maxMemory(),
            totalMemoryBytes = memoryInfo.totalMem,
            availableMemoryBytes = memoryInfo.availMem,
            lowMemory = memoryInfo.lowMemory,
            screenWidthPixels = displayMetrics.widthPixels,
            screenHeightPixels = displayMetrics.heightPixels,
            densityDpi = displayMetrics.densityDpi,
            frontCameras = collectFrontCameras(),
            audioConfigurations = AUDIO_SAMPLE_RATES.map(::audioConfiguration),
            networkConnected = isNetworkConnected(),
            cameraPermissionGranted = hasPermission(Manifest.permission.CAMERA),
            recordAudioPermissionGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
            collectedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun collectFrontCameras(): List<CameraDiagnostic> {
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return runCatching {
            cameraManager.cameraIdList.mapNotNull { cameraId ->
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                if (characteristics.get(CameraCharacteristics.LENS_FACING) !=
                    CameraCharacteristics.LENS_FACING_FRONT
                ) {
                    return@mapNotNull null
                }
                val sizes = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java)
                    .orEmpty()
                    .map { "${it.width}x${it.height}" }
                    .distinct()
                CameraDiagnostic(
                    cameraId = cameraId,
                    orientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
                    previewSizes = sizes,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun audioConfiguration(sampleRate: Int): AudioConfigurationDiagnostic {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioConfigurationDiagnostic(
            sampleRate = sampleRate,
            minBufferSizeBytes = minBufferSize,
            supported = minBufferSize > 0,
        )
    }

    @Suppress("DEPRECATION")
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.isConnected == true
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        val AUDIO_SAMPLE_RATES = listOf(16_000, 44_100)
    }
}
