package com.example.pepper_person_id_poc

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.infrastructure.face.OnnxRuntimeYuNetFaceDetector
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
class OnnxRuntimeYuNetFixedImageParityTest {
    @Test
    fun fp32AndInt8ActualSessionMetadataAndFixedImageMatchOpenCvWithoutFallback() {
        assertTrue("OpenCV native runtime unavailable", OpenCVLoader.initLocal())
        listOf(
            FaceDetectorModelOption.YUNET_2026MAY_FP32,
            FaceDetectorModelOption.YUNET_2023MAR_INT8,
        ).forEach(::assertFixedImageParity)
    }

    private fun assertFixedImageParity(model: FaceDetectorModelOption) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val inputSize = OnnxRuntimeYuNetFaceDetector.contractFor(model).inputSize
        val image = fixedBgrImage("lombard-s22-plain.png", inputSize)
        val expectedFaces = Mat()
        val actualFaces = Mat()
        val modelFile = copyTargetAsset("models/${model.modelFileName}")
        val reference = FaceDetectorYN.create(
            modelFile.absolutePath,
            "",
            Size(inputSize.toDouble(), inputSize.toDouble()),
            0.80f,
            0.30f,
            5_000,
        )
        val detector = OnnxRuntimeYuNetFaceDetector(context, model)
        try {
            reference.detect(image, expectedFaces)
            detector.detect(image, actualFaces)
            assertThat(actualFaces.rows()).isEqualTo(expectedFaces.rows())
            assertThat(actualFaces.rows()).isAtLeast(1)
            repeat(expectedFaces.rows()) { row ->
                val expected = FloatArray(15)
                val actual = FloatArray(15)
                expectedFaces.get(row, 0, expected)
                actualFaces.get(row, 0, actual)
                assertThat(actual.all(Float::isFinite)).isTrue()
                repeat(14) { column ->
                    assertThat(actual[column]).isWithin(0.05f).of(expected[column])
                }
                assertThat(actual[14]).isWithin(0.001f).of(expected[14])
            }
        } finally {
            detector.close()
            detector.close()
            assertThrows(IllegalStateException::class.java) {
                detector.detect(image, actualFaces)
            }
            actualFaces.release()
            expectedFaces.release()
            image.release()
        }
    }

    private fun fixedBgrImage(filename: String, inputSize: Int): Mat {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("face-test/$filename").use(BitmapFactory::decodeStream)
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            val resized = Mat()
            Imgproc.resize(bgr, resized, Size(inputSize.toDouble(), inputSize.toDouble()))
            return resized
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
