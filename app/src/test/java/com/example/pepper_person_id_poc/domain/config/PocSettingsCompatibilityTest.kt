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
        assertThat(SpeakerModelOption.entries.map { it.artifactId }).containsNoDuplicates()
        assertThat(SpeakerModelOption.WESPEAKER_RESNET34_LM.modelFileName)
            .isEqualTo("wespeaker_en_voxceleb_resnet34_LM.onnx")
        assertThat(SpeakerModelOption.WESPEAKER_RESNET34_LM.embeddingSize).isEqualTo(256)
        assertThat(FaceDetectorRuntime.entries.map { it.runtimeId }).containsNoDuplicates()
        assertThat(FaceEmbeddingRuntime.entries.map { it.runtimeId }).containsNoDuplicates()
    }

    @Test
    fun liteRtOptionsExposeOnlyConvertedArtifacts() {
        assertThat(FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32.artifactId)
            .isEqualTo("yunet-2026may-litert-fp32-320")
        assertThat(FaceDetectorRuntime.LITERT.runtimeId)
            .isEqualTo("litert-2.1.6-android-cpu")
        assertThat(FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32.format)
            .isEqualTo(FaceModelFormat.TFLITE)
        assertThat(
            FaceEmbeddingRuntimeCompatibility.supportsExactPair(
                FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32,
                FaceEmbeddingRuntime.LITERT,
                "armeabi-v7a",
            ),
        ).isTrue()
        assertThat(
            FaceEmbeddingRuntimeCompatibility.supportsExactPair(
                FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                FaceEmbeddingRuntime.LITERT,
                "armeabi-v7a",
            ),
        ).isTrue()
        assertThat(
            FaceEmbeddingArtifactResolver.resolve(
                FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                FaceEmbeddingRuntime.LITERT,
            ),
        ).isEqualTo(FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32)
    }
}
