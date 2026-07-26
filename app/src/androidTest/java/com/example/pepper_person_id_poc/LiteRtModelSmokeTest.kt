package com.example.pepper_person_id_poc

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.infrastructure.face.LiteRtSFaceEmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.LiteRtYuNetFaceDetector
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtFloatTensorSpec
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtModelContract
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.Environment
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.assertThrows
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
class LiteRtModelSmokeTest {
    @Test
    fun officialRuntimeLoadsCpuAndClosesResources() {
        val environment = Environment.create()
        assertThat(environment.getAvailableAccelerators()).contains(Accelerator.CPU)

        environment.close()
        environment.close()

        assertThrows(IllegalStateException::class.java) {
            environment.getAvailableAccelerators()
        }
    }

    @Test
    fun missingExactConvertedAssetFailsWithoutSubstitution() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val exactContract = LiteRtModelContract(
            artifactId = "missing-exact-test-artifact",
            assetPath = "models/missing-exact-test-artifact.tflite",
            inputs = listOf(LiteRtFloatTensorSpec("input", listOf(1, 1))),
            outputs = listOf(LiteRtFloatTensorSpec("output", listOf(1, 1))),
        )

        val failure = assertThrows(Throwable::class.java) {
            LiteRtRuntime.open(context, exactContract).close()
        }

        assertThat(failure).isNotNull()
    }

    @Test
    fun yunetFixedImageLoadsInfersAndClosesExactConvertedModel() {
        assertTrue("OpenCV native runtime unavailable", OpenCVLoader.initLocal())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = fixedBgrImage("lombard-s22-plain.png")
        val faces = Mat()
        val detector = LiteRtYuNetFaceDetector(context)
        try {
            detector.detect(image, faces)
            assertThat(faces.rows()).isAtLeast(1)
            assertThat(faces.cols()).isEqualTo(15)
            val values = FloatArray(15)
            faces.get(0, 0, values)
            assertThat(values.all(Float::isFinite)).isTrue()
            assertThat(values[14]).isAtLeast(0.8f)
        } finally {
            detector.close()
            detector.close()
            faces.release()
            image.release()
        }
    }

    @Test
    fun sfaceFixedImageLoadsInfersAndClosesExactConvertedModel() {
        assertTrue("OpenCV native runtime unavailable", OpenCVLoader.initLocal())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = fixedBgrImage("lombard-s22-plain.png")
        val faces = Mat()
        val detector = LiteRtYuNetFaceDetector(context)
        val engine = LiteRtSFaceEmbeddingEngine(
            context = context,
            contract = LiteRtSFaceEmbeddingEngine.CONTRACT,
        )
        try {
            detector.detect(image, faces)
            assertThat(faces.rows()).isAtLeast(1)
            val face = faces.row(0)
            try {
                engine.prepare()
                val embedding = engine.extract(image, face)
                assertThat(embedding.size).isEqualTo(128)
                assertThat(embedding.all(Float::isFinite)).isTrue()
            } finally {
                face.release()
            }
        } finally {
            engine.close()
            engine.close()
            detector.close()
            faces.release()
            image.release()
        }
    }

    @Test
    fun yunetFixedImagePostprocessMatchesOnnxIncludingKps32Adapter() {
        assertTrue("OpenCV native runtime unavailable", OpenCVLoader.initLocal())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = fixedBgrImage("lombard-s22-plain.png")
        val image = Mat()
        Imgproc.resize(original, image, Size(320.0, 320.0))
        original.release()
        val onnxFaces = Mat()
        val liteRtFaces = Mat()
        val onnxModel = copyTargetAsset("models/face_detection_yunet_2026may.onnx")
        val onnxDetector = FaceDetectorYN.create(
            onnxModel.absolutePath,
            "",
            Size(320.0, 320.0),
            0.80f,
            0.30f,
            5_000,
        )
        val liteRtDetector = LiteRtYuNetFaceDetector(context)
        try {
            onnxDetector.detect(image, onnxFaces)
            liteRtDetector.detect(image, liteRtFaces)
            assertThat(liteRtFaces.rows()).isEqualTo(onnxFaces.rows())
            assertThat(liteRtFaces.rows()).isAtLeast(1)
            repeat(onnxFaces.rows()) { row ->
                val expected = FloatArray(15)
                val actual = FloatArray(15)
                onnxFaces.get(row, 0, expected)
                liteRtFaces.get(row, 0, actual)
                repeat(14) { column ->
                    assertThat(actual[column]).isWithin(1e-3f).of(expected[column])
                }
                assertThat(actual[14]).isWithin(1e-4f).of(expected[14])
            }
        } finally {
            liteRtDetector.close()
            liteRtFaces.release()
            onnxFaces.release()
            image.release()
        }
    }

    private fun fixedBgrImage(filename: String): Mat {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("face-test/$filename").use(BitmapFactory::decodeStream)
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            return bgr.clone()
        } finally {
            bitmap.recycle()
            bgr.release()
            rgba.release()
        }
    }

    private fun copyTargetAsset(path: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.cacheDir, path.substringAfterLast('/'))
        context.assets.open(path).use { input -> output.outputStream().use(input::copyTo) }
        return output
    }
}
