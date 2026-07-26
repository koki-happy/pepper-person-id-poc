package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiteRtSFaceContractTest {
    @Test
    fun exactContractPinsConvertedArtifactAndTensorNames() {
        val contract = LiteRtSFaceEmbeddingEngine.CONTRACT

        assertThat(contract.artifactId).isEqualTo("sface-2021dec-litert-fp32")
        assertThat(contract.runtimeId).isEqualTo(LiteRtRuntime.RUNTIME_ID)
        assertThat(contract.assetPath)
            .isEqualTo("models/face_recognition_sface_2021dec.tflite")
        assertThat(contract.inputs.single().name).isEqualTo("data")
        assertThat(contract.inputs.single().dimensions)
            .containsExactly(1, 3, 112, 112)
            .inOrder()
        assertThat(contract.outputs.single().name).isEqualTo("Identity")
        assertThat(contract.outputs.single().dimensions)
            .containsExactly(1, 128)
            .inOrder()
    }
}
