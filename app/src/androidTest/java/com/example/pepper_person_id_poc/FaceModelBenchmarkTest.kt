package com.example.pepper_person_id_poc

import android.graphics.BitmapFactory
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngineFactory
import com.example.pepper_person_id_poc.infrastructure.face.FaceReidentificationRetail0095EmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.SFaceEmbeddingEngine
import java.io.File
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN

@RunWith(AndroidJUnit4::class)
class FaceModelBenchmarkTest {
    @Test
    fun sface_benchmarkLombardGridFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        benchmarkLombardGridFrames(SFaceEmbeddingEngine(context), expectedDimension = 128, threshold = 0.60f)
    }

    @Test
    fun retail0095_benchmarkLombardGridFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        benchmarkLombardGridFrames(
            FaceReidentificationRetail0095EmbeddingEngine(context),
            expectedDimension = 256,
            threshold = 0.60f,
        )
    }

    @Test
    fun sfaceOnnxRuntime_benchmarkLombardGridFrames() {
        benchmarkExactRuntime(
            model = FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
            runtime = FaceEmbeddingRuntime.ONNX_RUNTIME,
        )
    }

    @Test
    fun sfaceNcnnArm64_benchmarkLombardGridFrames() {
        benchmarkExactRuntime(
            model = FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32,
            runtime = FaceEmbeddingRuntime.NCNN,
        )
    }

    @Test
    fun sfaceMnnArm64_benchmarkLombardGridFrames() {
        benchmarkExactRuntime(
            model = FaceEmbeddingModelOption.SFACE_2021DEC_MNN_FP32,
            runtime = FaceEmbeddingRuntime.MNN,
        )
    }

    private fun benchmarkExactRuntime(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = FaceEmbeddingEngineFactory.create(context, model, runtime)
        assertTrue("Factory changed the requested runtime", engine.modelName.contains(runtime.displayName))
        benchmarkLombardGridFrames(
            engine = engine,
            expectedDimension = model.embeddingSize,
            threshold = null,
        )
    }

    private fun benchmarkLombardGridFrames(
        engine: FaceEmbeddingEngine,
        expectedDimension: Int,
        threshold: Float?,
    ) {
        assertTrue("OpenCV native runtime unavailable", OpenCVLoader.initLocal())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val images = listOf(
            "enrollment" to "lombard-s22-plain.png",
            "same" to "lombard-s22-lombard.png",
            "different" to "lombard-s16-plain.png",
        ).associate { (role, asset) ->
            role to testAssets.open("face-test/$asset").use { input ->
                val bitmap = requireNotNull(BitmapFactory.decodeStream(input))
                val rgba = Mat()
                val bgr = Mat()
                try {
                    Utils.bitmapToMat(bitmap, rgba)
                    Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                    bgr.clone()
                } finally {
                    bitmap.recycle()
                    bgr.release()
                    rgba.release()
                }
            }
        }
        val detectorModel = copyTargetAsset("models/face_detection_yunet_2026may.onnx")
        val detector = FaceDetectorYN.create(
            detectorModel.absolutePath,
            "",
            Size(720.0, 480.0),
            0.80f,
            0.30f,
            5_000,
        )
        try {
            val initMillis = timed { engine.prepare() }.millis
            val results = images.mapValues { (_, image) -> detectAndExtract(detector, engine, image) }
            val enrollment = requireNotNull(results["enrollment"])
            val same = requireNotNull(results["same"])
            val different = requireNotNull(results["different"])
            val sameScore = cosine(enrollment.embedding, same.embedding)
            val differentScore = cosine(enrollment.embedding, different.embedding)
            Log.i(
                TAG,
                "dataset=LombardGRID-front model=${engine.modelName} dimension=${enrollment.embedding.size} " +
                    "initMillis=$initMillis detectionMillis=${enrollment.detectionMillis},${same.detectionMillis},${different.detectionMillis} " +
                    "embeddingMillis=${enrollment.embeddingMillis},${same.embeddingMillis},${different.embeddingMillis} " +
                    "sameScore=$sameScore differentScore=$differentScore " +
                    "nativeHeapBytes=${Debug.getNativeHeapAllocatedSize()}",
            )
            assertEquals(expectedDimension, enrollment.embedding.size)
            assertTrue(enrollment.embedding.all(Float::isFinite))
            assertTrue(sameScore.isFinite())
            assertTrue(differentScore.isFinite())
            assertTrue("same-person score must exceed different-person score", sameScore > differentScore)
            if (threshold != null) {
                assertTrue("same person must be accepted at threshold $threshold: $sameScore", sameScore >= threshold)
                assertTrue(
                    "different person must be rejected at threshold $threshold: $differentScore",
                    differentScore < threshold,
                )
            }
        } finally {
            images.values.forEach(Mat::release)
        }
    }

    private fun detectAndExtract(
        detector: FaceDetectorYN,
        engine: FaceEmbeddingEngine,
        image: Mat,
    ): FaceResult {
        detector.setInputSize(image.size())
        val faces = Mat()
        try {
            val detection = timed { detector.detect(image, faces) }
            assertEquals("Expected exactly one face", 1, faces.rows())
            val face = faces.row(0)
            try {
                val embedding = timed { engine.extract(image, face) }
                return FaceResult(embedding.value, detection.millis, embedding.millis)
            } finally {
                face.release()
            }
        } finally {
            faces.release()
        }
    }

    private fun copyTargetAsset(path: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, path.substringAfterLast('/'))
        context.assets.open(path).use { input -> output.outputStream().use(input::copyTo) }
        return output
    }

    private fun <T> timed(block: () -> T): Timed<T> {
        val started = SystemClock.elapsedRealtime()
        return Timed(block(), SystemClock.elapsedRealtime() - started)
    }

    private fun cosine(left: FloatArray, right: FloatArray): Float {
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        left.indices.forEach { index ->
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
    }

    private data class FaceResult(
        val embedding: FloatArray,
        val detectionMillis: Long,
        val embeddingMillis: Long,
    )

    private data class Timed<T>(val value: T, val millis: Long)

    private companion object {
        const val TAG = "FaceBenchmark"
    }
}
