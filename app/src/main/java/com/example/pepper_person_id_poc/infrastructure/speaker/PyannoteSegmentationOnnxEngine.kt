package com.example.pepper_person_id_poc.infrastructure.speaker

import android.content.Context
import android.os.SystemClock
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationEngine
import com.example.pepper_person_id_poc.application.contract.SpeakerSegmentationInput
import com.example.pepper_person_id_poc.domain.model.ArtifactId
import com.example.pepper_person_id_poc.domain.model.RuntimeId
import com.example.pepper_person_id_poc.domain.speaker.DiarizationWindow
import com.example.pepper_person_id_poc.domain.speaker.SpeakerActivityState
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.Collections

/**
 * CPU-only pyannote segmentation 3.0 inference through the exact ONNX Runtime pair.
 *
 * The converted model accepts `[batch, channel, samples]` float PCM and returns
 * `[batch, frame, 7]` powerset scores. No alternate runtime or activity heuristic is used.
 */
class PyannoteSegmentationOnnxEngine(
    context: Context,
    private val numThreads: Int = 1,
    private val postprocessor: PyannoteSegmentationPostprocessor =
        PyannoteSegmentationPostprocessor(),
) : SpeakerSegmentationEngine {
    private val appContext = context.applicationContext
    private var environment: Any? = null
    private var sessionOptions: AutoCloseable? = null
    private var session: AutoCloseable? = null
    private var inputName: String? = null
    private var initializationFailure: Throwable? = null

    override val artifactId = ArtifactId(ARTIFACT_ID)
    override val runtimeId = RuntimeId(RUNTIME_ID)
    override val requiredWindowSamples = WINDOW_SAMPLES

    @Synchronized
    override fun prepare() {
        getOrCreateSession()
    }

    @Synchronized
    override fun segment(input: SpeakerSegmentationInput): DiarizationWindow {
        require(input.pcm16.size == WINDOW_SAMPLES) {
            "pyannote segmentation 3.0 requires an exact 10-second/160000-sample window; " +
                "actual=${input.pcm16.size}"
        }
        val activeSession = getOrCreateSession()
        val env = checkNotNull(environment)
        val samples = FloatArray(input.pcm16.size) { index ->
            input.pcm16[index] / PCM_NORMALIZATION
        }
        val tensorClass = Class.forName("ai.onnxruntime.OnnxTensor")
        val environmentClass = Class.forName("ai.onnxruntime.OrtEnvironment")
        val tensor = tensorClass.getMethod(
            "createTensor",
            environmentClass,
            FloatBuffer::class.java,
            LongArray::class.java,
        ).invoke(
            null,
            env,
            FloatBuffer.wrap(samples),
            longArrayOf(1L, 1L, samples.size.toLong()),
        ) as AutoCloseable
        val startedAt = SystemClock.elapsedRealtime()
        try {
            val result = activeSession.javaClass.getMethod("run", Map::class.java)
                .invoke(
                    activeSession,
                    Collections.singletonMap(checkNotNull(inputName), tensor),
                ) as AutoCloseable
            try {
                val onnxValue = result.javaClass
                    .getMethod("get", Int::class.javaPrimitiveType)
                    .invoke(result, 0)
                val rawOutput = onnxValue.javaClass.getMethod("getValue").invoke(onnxValue)
                val classScores = decodeClassScores(rawOutput)
                val frames = postprocessor.decodeFrames(classScores)
                check(frames.isNotEmpty()) { "pyannote segmentation returned no frames" }
                val overlapFrames = frames.count {
                    it.activityState == SpeakerActivityState.OVERLAPPED_SPEECH ||
                        it.activityState == SpeakerActivityState.MULTIPLE_ACTIVE_SPEAKERS
                }
                return DiarizationWindow(
                    windowId = input.windowId,
                    startSample = input.startSample,
                    endSample = input.endSample,
                    sampleRate = input.sampleRate,
                    frames = frames,
                    overlapRatio = overlapFrames.toFloat() / frames.size,
                    runtimeId = runtimeId.value,
                    inferenceTimeMillis = SystemClock.elapsedRealtime() - startedAt,
                )
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    @Synchronized
    override fun close() {
        session?.close()
        sessionOptions?.close()
        session = null
        sessionOptions = null
        environment = null
    }

    private fun getOrCreateSession(): AutoCloseable {
        session?.let { return it }
        initializationFailure?.let {
            throw IllegalStateException("pyannote ONNX Runtime initialization failed", it)
        }
        return runCatching {
            require(numThreads > 0) { "numThreads must be positive" }
            val environmentClass = Class.forName("ai.onnxruntime.OrtEnvironment")
            val sessionOptionsClass = Class.forName("ai.onnxruntime.OrtSession\$SessionOptions")
            val env = environmentClass.getMethod("getEnvironment").invoke(null)
            val options = sessionOptionsClass.getConstructor().newInstance() as AutoCloseable
            sessionOptionsClass.getMethod(
                "setInterOpNumThreads",
                Int::class.javaPrimitiveType,
            ).invoke(options, numThreads)
            sessionOptionsClass.getMethod(
                "setIntraOpNumThreads",
                Int::class.javaPrimitiveType,
            ).invoke(options, numThreads)
            val modelFile = copyModelToInternalStorage()
            val created = environmentClass.getMethod(
                "createSession",
                String::class.java,
                sessionOptionsClass,
            ).invoke(env, modelFile.absolutePath, options) as AutoCloseable
            @Suppress("UNCHECKED_CAST")
            val names = created.javaClass.getMethod("getInputNames").invoke(created) as Set<String>
            check(names == setOf(INPUT_NAME)) {
                "Unexpected pyannote input names: $names"
            }
            environment = env
            sessionOptions = options
            inputName = INPUT_NAME
            created
        }.onFailure { initializationFailure = it }
            .getOrThrow()
            .also { session = it }
    }

    private fun copyModelToInternalStorage(): File {
        val output = File(appContext.filesDir, "models/$MODEL_FILENAME")
        if (
            output.isFile &&
            output.length() == MODEL_SIZE_BYTES &&
            output.sha256() == MODEL_SHA256
        ) {
            return output
        }
        output.parentFile?.mkdirs()
        appContext.assets.open("models/$MODEL_FILENAME").use { input ->
            output.outputStream().use(input::copyTo)
        }
        check(output.length() == MODEL_SIZE_BYTES) {
            "Unexpected pyannote model size: ${output.length()}"
        }
        check(output.sha256() == MODEL_SHA256) {
            "Unexpected pyannote model SHA-256"
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
        const val ARTIFACT_ID = "pyannote-segmentation-3.0-sherpa-onnx-fp32"
        const val RUNTIME_ID = "onnxruntime-android-1.20.0-cpu"
        const val MODEL_FILENAME = "pyannote-segmentation-3.0.onnx"
        const val MODEL_SIZE_BYTES = 5_992_913L
        const val MODEL_SHA256 =
            "220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079"
        const val WINDOW_SAMPLES = 160_000
        const val CLASS_COUNT = 7
        const val INPUT_NAME = "x"
        const val PCM_NORMALIZATION = 32_768f

        internal fun decodeClassScores(rawOutput: Any?): List<FloatArray> {
            val flattened = ArrayList<Float>()
            fun visit(value: Any?) {
                when (value) {
                    is Float -> flattened += value
                    is FloatArray -> value.forEach { flattened += it }
                    is Array<*> -> value.forEach(::visit)
                    else -> error(
                        "Unsupported pyannote ONNX output type: ${value?.javaClass?.name}",
                    )
                }
            }
            visit(rawOutput)
            require(flattened.isNotEmpty() && flattened.size % CLASS_COUNT == 0) {
                "pyannote output must contain complete 7-class frames; values=${flattened.size}"
            }
            return flattened.chunked(CLASS_COUNT) { frame ->
                frame.toFloatArray().also {
                    require(it.all(Float::isFinite)) {
                        "pyannote output contains non-finite values"
                    }
                }
            }
        }
    }
}
