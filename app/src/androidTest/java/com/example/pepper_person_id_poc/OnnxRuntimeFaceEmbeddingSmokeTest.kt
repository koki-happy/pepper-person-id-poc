package com.example.pepper_person_id_poc

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngineFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

@RunWith(AndroidJUnit4::class)
class OnnxRuntimeFaceEmbeddingSmokeTest {
    @Test
    fun exactSFaceAnd0095Pairs_executeOnArm64WithoutFallback() {
        assumeTrue(Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        assertThat(OpenCVLoader.initLocal()).isTrue()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val image = Mat(160, 160, CvType.CV_8UC3, Scalar(128.0, 128.0, 128.0))
        val detectedFace = Mat(1, 15, CvType.CV_32F)
        detectedFace.put(
            0,
            0,
            floatArrayOf(
                20f, 20f, 120f, 120f,
                58f, 68f,
                102f, 68f,
                80f, 90f,
                62f, 112f,
                98f, 112f,
                0.99f,
            ),
        )
        try {
            listOf(
                FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32,
            ).forEach { model ->
                FaceEmbeddingEngineFactory.create(
                    context = context,
                    model = model,
                    runtime = FaceEmbeddingRuntime.ONNX_RUNTIME,
                ).use { engine ->
                    engine.prepare()
                    val embedding = engine.extract(image, detectedFace)
                    assertThat(embedding).hasLength(model.embeddingSize)
                    assertThat(embedding.all(Float::isFinite)).isTrue()
                    assertThat(embedding.any { it != 0f }).isTrue()
                    assertThat(engine.modelName).contains("ONNX Runtime Android 1.20.0")
                }
            }
        } finally {
            detectedFace.release()
            image.release()
        }
    }
}
