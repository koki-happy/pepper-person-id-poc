package com.example.pepper_person_id_poc.infrastructure.device

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.example.pepper_person_id_poc.domain.device.DeviceLoadSnapshot
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidDeviceLoadMonitor(context: Context) : Closeable {
    private val activityManager =
        context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(DeviceLoadSnapshot())
    val state: StateFlow<DeviceLoadSnapshot> = mutableState.asStateFlow()
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        mutableState.value = DeviceLoadSnapshot(measuringCpu = true)
        job = scope.launch {
            var previousCpu = Process.getElapsedCpuTime()
            var previousElapsed = SystemClock.elapsedRealtime()
            collect(cpuPercent = null, measuring = true)
            while (isActive) {
                delay(1_000L)
                val cpu = Process.getElapsedCpuTime()
                val elapsed = SystemClock.elapsedRealtime()
                val elapsedDelta = elapsed - previousElapsed
                val cpuPercent = if (elapsedDelta > 0L) {
                    ((cpu - previousCpu).toDouble() / elapsedDelta * 100.0 /
                        Runtime.getRuntime().availableProcessors().coerceAtLeast(1)).coerceAtLeast(0.0)
                } else null
                previousCpu = cpu
                previousElapsed = elapsed
                collect(cpuPercent, measuring = false)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private fun collect(cpuPercent: Double?, measuring: Boolean) {
        val runtime = Runtime.getRuntime()
        val appMemory = runCatching {
            Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss.toLong() * 1024L
        }.getOrNull()
        val javaHeap = runCatching {
            runtime.totalMemory() - runtime.freeMemory()
        }.getOrNull()
        val nativeHeap = runCatching {
            Debug.getNativeHeapAllocatedSize()
        }.getOrNull()
        val threadCount = runCatching {
            File("/proc/self/task").list()?.size ?: Thread.getAllStackTraces().size
        }.getOrNull()
        val deviceMemory = runCatching {
            ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        }.getOrNull()
        val elapsedRealtime = SystemClock.elapsedRealtime()
        val epochMillis = System.currentTimeMillis()
        mutableState.value = DeviceLoadSnapshot(
            measuringCpu = measuring,
            appCpuAllCoresPercent = cpuPercent,
            appPssBytes = appMemory,
            appJavaHeapBytes = javaHeap,
            appNativeHeapBytes = nativeHeap,
            appThreadCount = threadCount,
            totalMemoryBytes = deviceMemory?.totalMem,
            availableMemoryBytes = deviceMemory?.availMem,
            lowMemory = deviceMemory?.lowMemory,
            collectedAtElapsedRealtimeMillis = elapsedRealtime,
            collectedAtEpochMillis = epochMillis,
            collectedAtMillis = epochMillis,
        )
    }

    override fun close() {
        stop()
        scope.cancel()
    }
}
