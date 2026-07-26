package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedPreferencesSettingsRepositoryTest {
    @Test
    fun legacyChoicesMigrateWithoutChangingTheirMeaning() {
        val migrated = SettingsSchemaMigration.migrate(
            mapOf(
                "face_detector" to "ML_KIT_BUNDLED",
                "face_model" to "SFACE_2021DEC_INT8",
                "face_inference_backend" to "OPEN_CV",
                "speaker_model" to "ERES2NET",
            ),
        )

        assertThat(migrated.faceDetectorModel).isEqualTo(FaceDetectorModelOption.ML_KIT_BUNDLED)
        assertThat(migrated.faceDetectorRuntime).isEqualTo(FaceDetectorRuntime.ML_KIT)
        assertThat(migrated.faceEmbeddingModel).isEqualTo(FaceEmbeddingModelOption.SFACE_2021DEC_INT8)
        assertThat(migrated.faceEmbeddingRuntime).isEqualTo(FaceEmbeddingRuntime.OPEN_CV)
        assertThat(migrated.speakerModel).isEqualTo(SpeakerModelOption.ERES2NET)
    }

    @Test
    fun removedYuNet2023ChoiceMigratesToYuNet2026() {
        val migrated = SettingsSchemaMigration.migrate(
            mapOf(
                "settings_schema_version" to 2,
                "face_detector_model" to "YUNET_2023MAR_INT8",
                "face_detector_runtime" to FaceDetectorRuntime.OPEN_CV.name,
                "face_embedding_model" to FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32.name,
                "face_embedding_runtime" to FaceEmbeddingRuntime.NCNN.name,
                "speaker_model" to SpeakerModelOption.CAM_PLUS_PLUS.name,
                "speaker_runtime" to "SHERPA_ONNX",
                "vad_model" to "SILERO_VAD",
            ),
        )

        assertThat(migrated.faceDetectorModel).isEqualTo(FaceDetectorModelOption.YUNET_2026MAY_FP32)
        assertThat(migrated.faceEmbeddingModel).isEqualTo(FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32)
        assertThat(migrated.faceEmbeddingRuntime).isEqualTo(FaceEmbeddingRuntime.NCNN)
        assertThat(migrated.speakerModel).isEqualTo(SpeakerModelOption.CAM_PLUS_PLUS)
    }

    @Test
    fun legacyNativeRuntimeMigratesToItsExactConvertedArtifact() {
        val migrated = SettingsSchemaMigration.migrate(
            mapOf(
                "face_model" to "SFACE_2021DEC",
                "face_inference_backend" to "NCNN",
            ),
        )

        assertThat(migrated.faceEmbeddingModel)
            .isEqualTo(FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32)
        assertThat(migrated.faceEmbeddingRuntime).isEqualTo(FaceEmbeddingRuntime.NCNN)
    }

    @Test
    fun unknownLegacyChoiceIsAnExplicitMigrationError() {
        val error = assertThrows(SettingsMigrationException::class.java) {
            SettingsSchemaMigration.migrate(mapOf("face_model" to "REMOVED_MODEL"))
        }

        assertThat(error).hasMessageThat().contains("face_model")
        assertThat(error).hasMessageThat().contains("REMOVED_MODEL")
    }

    @Test
    fun futureSchemaIsRejectedInsteadOfDowngraded() {
        assertThrows(SettingsMigrationException::class.java) {
            SettingsSchemaMigration.migrate(mapOf("settings_schema_version" to 99))
        }
    }
}
