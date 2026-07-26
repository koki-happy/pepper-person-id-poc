package com.example.pepper_person_id_poc

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.infrastructure.face.OnnxRuntimeYuNetFaceDetector
import com.example.pepper_person_id_poc.infrastructure.face.YuNetNativeFaceDetector
import com.example.pepper_person_id_poc.infrastructure.face.YuNetNativeRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

@RunWith(AndroidJUnit4::class)
class YuNetNativeFixedImageParityTest {
    @Test
    fun ncnnAndMnnLoadInferReleaseAndMatchExactOnnxWithoutFallback() {
        assertTrue(OpenCVLoader.initLocal())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = InstrumentationRegistry.getInstrumentation()
            .context.assets.open("face-test/lombard-s22-plain.png")
            .use(BitmapFactory::decodeStream)
        val rgba = Mat()
        val bgr = Mat()
        try {
            Utils.bitmapToMat(image, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            val reference = infer(
                OnnxRuntimeYuNetFaceDetector(
                    context,
                    FaceDetectorModelOption.YUNET_2026MAY_FP32,
                ),
                bgr,
            )
            YuNetNativeRuntime.entries.forEach { runtime ->
                val candidate = infer(YuNetNativeFaceDetector(context, runtime), bgr)
                assertRowsNear(reference, candidate, runtime)
            }
        } finally {
            bgr.release()
            rgba.release()
            image.recycle()
        }
    }

    private fun infer(
        detector: com.example.pepper_person_id_poc.infrastructure.face.YuNetDetectionBackend,
        image: Mat,
    ): Array<FloatArray> {
        val faces = Mat()
        return detector.use {
            it.detect(image, faces)
            Array(faces.rows()) { row ->
                FloatArray(15).also { values -> faces.get(row, 0, values) }
            }
        }.also { faces.release() }
    }

    private fun assertRowsNear(
        expected: Array<FloatArray>,
        actual: Array<FloatArray>,
        runtime: YuNetNativeRuntime,
    ) {
        assertEquals("$runtime detection count", expected.size, actual.size)
        expected.indices.forEach { row ->
            expected[row].indices.forEach { column ->
                assertEquals(
                    "$runtime row=$row column=$column",
                    expected[row][column],
                    actual[row][column],
                    if (column == 14) 1e-4f else 2e-2f,
                )
            }
        }
    }
}
