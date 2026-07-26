package com.example.pepper_person_id_poc

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.infrastructure.face.FaceReidentificationRetail0095EmbeddingEngine
import com.example.pepper_person_id_poc.infrastructure.face.LiteRt0095EmbeddingEngine
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.math.sqrt
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
class LiteRt0095ParityTest {
    @Test
    fun fixedImageLiteRtEmbeddingMatchesOpenCvOnnxWithoutFallback() {
        assertTrue("OpenCV native runtime unavailable", OpenCVLoader.initLocal())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = fixedBgrImage("lombard-s22-plain.png")
        val faces = Mat()
        val detectorModel = copyTargetAsset("models/face_detection_yunet_2026may.onnx")
        val detector = FaceDetectorYN.create(
            detectorModel.absolutePath,
            "",
            Size(image.cols().toDouble(), image.rows().toDouble()),
            0.80f,
            0.30f,
            5_000,
        )
        val openCv = FaceReidentificationRetail0095EmbeddingEngine(context)
        val liteRt = LiteRt0095EmbeddingEngine(
            context = context,
            contract = LiteRt0095EmbeddingEngine.CONTRACT,
        )
        try {
            detector.detect(image, faces)
            assertThat(faces.rows()).isAtLeast(1)
            val face = faces.row(0)
            try {
                openCv.prepare()
                liteRt.prepare()
                val expected = normalize(openCv.extract(image, face))
                val actual = liteRt.extract(image, face)

                assertThat(actual).hasLength(256)
                assertThat(actual.all(Float::isFinite)).isTrue()
                assertThat(l2(actual)).isWithin(1e-5).of(1.0)
                assertThat(cosine(expected, actual)).isAtLeast(0.9999)
            } finally {
                face.release()
            }
        } finally {
            liteRt.close()
            liteRt.close()
            openCv.close()
            faces.release()
            image.release()
        }
    }

    private fun normalize(values: FloatArray): FloatArray {
        require(values.size == 256 && values.all(Float::isFinite))
        val norm = l2(values)
        require(norm > 0.0)
        return FloatArray(values.size) { index -> (values[index] / norm).toFloat() }
    }

    private fun cosine(left: FloatArray, right: FloatArray): Double =
        left.indices.sumOf { index -> left[index].toDouble() * right[index].toDouble() }

    private fun l2(values: FloatArray): Double =
        sqrt(values.sumOf { value -> value.toDouble() * value.toDouble() })

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
