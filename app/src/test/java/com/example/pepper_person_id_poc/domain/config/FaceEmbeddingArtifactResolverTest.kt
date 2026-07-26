package com.example.pepper_person_id_poc.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceEmbeddingArtifactResolverTest {
    @Test
    fun `logical face models are not duplicated by runtime format`() {
        assertThat(FaceEmbeddingArtifactResolver.logicalModels).containsExactly(
            FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
            FaceEmbeddingModelOption.SFACE_2021DEC_INT8,
            FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32,
        ).inOrder()
    }

    @Test
    fun `SFace FP32 resolves every prepared runtime artifact`() {
        val resolved = FaceEmbeddingRuntime.entries.associateWith {
            FaceEmbeddingArtifactResolver.resolve(FaceEmbeddingModelOption.SFACE_2021DEC_FP32, it)
        }

        assertThat(resolved).containsExactly(
            FaceEmbeddingRuntime.OPEN_CV, FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
            FaceEmbeddingRuntime.ONNX_RUNTIME, FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
            FaceEmbeddingRuntime.NCNN, FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32,
            FaceEmbeddingRuntime.MNN, FaceEmbeddingModelOption.SFACE_2021DEC_MNN_FP32,
            FaceEmbeddingRuntime.LITERT, FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32,
        )
    }

    @Test
    fun `0095 resolves every prepared runtime artifact`() {
        val model = FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32

        assertThat(FaceEmbeddingArtifactResolver.resolve(model, FaceEmbeddingRuntime.OPEN_CV))
            .isEqualTo(model)
        assertThat(FaceEmbeddingArtifactResolver.resolve(model, FaceEmbeddingRuntime.ONNX_RUNTIME))
            .isEqualTo(model)
        assertThat(FaceEmbeddingArtifactResolver.resolve(model, FaceEmbeddingRuntime.NCNN))
            .isEqualTo(FaceEmbeddingModelOption.FACE_0095_NCNN_FP32)
        assertThat(FaceEmbeddingArtifactResolver.resolve(model, FaceEmbeddingRuntime.MNN))
            .isEqualTo(FaceEmbeddingModelOption.FACE_0095_MNN_FP32)
        assertThat(FaceEmbeddingArtifactResolver.resolve(model, FaceEmbeddingRuntime.LITERT))
            .isEqualTo(FaceEmbeddingModelOption.FACE_0095_LITERT_FP32)
    }

    @Test
    fun `persisted runtime-specific artifact normalizes to its logical model`() {
        assertThat(
            FaceEmbeddingArtifactResolver.logicalModel(
                FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32,
            ),
        ).isEqualTo(FaceEmbeddingModelOption.SFACE_2021DEC_FP32)
        assertThat(
            FaceEmbeddingArtifactResolver.logicalModel(
                FaceEmbeddingModelOption.FACE_0095_MNN_FP32,
            ),
        ).isEqualTo(FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32)
    }
}
