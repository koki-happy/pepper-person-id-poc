package com.example.pepper_person_id_poc.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PocSettingsCompatibilityTest {
    @Test
    fun newInstallDefaultsUseExactRequestedPairs() {
        val settings = PocSettings()

        assertThat(settings.faceDetectorModel).isEqualTo(FaceDetectorModelOption.YUNET_2026MAY_FP32)
        assertThat(settings.faceDetectorRuntime).isEqualTo(FaceDetectorRuntime.OPEN_CV)
        assertThat(settings.faceEmbeddingModel).isEqualTo(FaceEmbeddingModelOption.SFACE_2021DEC_FP32)
        assertThat(settings.faceEmbeddingRuntime).isEqualTo(FaceEmbeddingRuntime.OPEN_CV)
        assertThat(settings.speakerModel).isEqualTo(SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN)
        assertThat(settings.speakerRuntime).isEqualTo(SpeakerRuntime.SHERPA_ONNX)
        assertThat(settings.vadModel).isEqualTo(VadModelOption.SILERO_VAD)
    }

    @Test
    fun detectorAndEmbeddingSelectionsAreIndependent() {
        val settings = PocSettings(
            faceDetectorModel = FaceDetectorModelOption.ML_KIT_BUNDLED,
            faceDetectorRuntime = FaceDetectorRuntime.ML_KIT,
            faceEmbeddingModel = FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32,
            faceEmbeddingRuntime = FaceEmbeddingRuntime.OPEN_CV,
        )

        assertThat(settings.faceDetectorModel.artifactId)
            .isEqualTo("mlkit-face-detection-16.1.7-bundled")
        assertThat(settings.faceDetectorRuntime.runtimeId)
            .isEqualTo("mlkit-face-16.1.7-bundled")
        assertThat(settings.faceEmbeddingModel.artifactId)
            .isEqualTo("face-0095-onnx-fp32")
        assertThat(settings.faceEmbeddingRuntime.runtimeId)
            .isEqualTo("opencv-5.0.0-android-cpu")
    }

    @Test
    fun artifactEnumsExposeExactCatalogIds() {
        assertThat(FaceDetectorModelOption.entries.map { it.artifactId }).containsNoDuplicates()
        assertThat(FaceEmbeddingModelOption.entries.map { it.artifactId }).containsNoDuplicates()
        assertThat(FaceDetectorRuntime.entries.map { it.runtimeId }).containsNoDuplicates()
        assertThat(FaceEmbeddingRuntime.entries.map { it.runtimeId }).containsNoDuplicates()
    }
}
