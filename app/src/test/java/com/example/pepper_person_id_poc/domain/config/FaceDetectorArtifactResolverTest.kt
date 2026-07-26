package com.example.pepper_person_id_poc.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceDetectorArtifactResolverTest {
    @Test
    fun logicalModelsDoNotDuplicateRuntimeArtifacts() {
        assertThat(FaceDetectorArtifactResolver.logicalModels).containsExactly(
            FaceDetectorModelOption.ML_KIT_BUNDLED,
            FaceDetectorModelOption.YUNET_2026MAY_FP32,
            FaceDetectorModelOption.YUNET_2023MAR_INT8,
        ).inOrder()
    }

    @Test
    fun yuNet2026ResolvesEveryAcceptedExactArtifact() {
        val model = FaceDetectorModelOption.YUNET_2026MAY_FP32

        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.OPEN_CV))
            .isEqualTo(model)
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.ONNX_RUNTIME))
            .isEqualTo(model)
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.MNN))
            .isEqualTo(FaceDetectorModelOption.YUNET_2026MAY_MNN_FP32)
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.NCNN))
            .isEqualTo(FaceDetectorModelOption.YUNET_2026MAY_NCNN_FP32)
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.LITERT))
            .isEqualTo(FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32)
    }

    @Test
    fun yuNetInt8AcceptsOnlyDirectOnnxRuntimes() {
        val model = FaceDetectorModelOption.YUNET_2023MAR_INT8

        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.OPEN_CV))
            .isEqualTo(model)
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.ONNX_RUNTIME))
            .isEqualTo(model)
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.MNN)).isNull()
        assertThat(FaceDetectorArtifactResolver.resolve(model, FaceDetectorRuntime.LITERT)).isNull()
    }
}
