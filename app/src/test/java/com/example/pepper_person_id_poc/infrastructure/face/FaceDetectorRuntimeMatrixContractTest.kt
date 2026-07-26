package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceDetectorRuntimeMatrixContractTest {
    @Test
    fun yuNet2026AcceptsTheExactOnnxRuntimePair() {
        FaceDetectorFactory.requireExactPair(
            FaceDetectorModelOption.YUNET_2026MAY_FP32,
            FaceDetectorRuntime.ONNX_RUNTIME,
        )
    }

    @Test
    fun sdkModelDoesNotCrossIntoOnnxRuntime() {
        val failure = runCatching {
            FaceDetectorFactory.requireExactPair(
                FaceDetectorModelOption.ML_KIT_BUNDLED,
                FaceDetectorRuntime.ONNX_RUNTIME,
            )
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(UnsupportedModelRuntimePairException::class.java)
        assertThat(failure).hasMessageThat().contains("no fallback")
    }

    @Test
    fun persistedRuntimeArtifactCanSwitchBackToTheLogicalOnnxRuntime() {
        FaceDetectorFactory.requireExactPair(
            FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32,
            FaceDetectorRuntime.ONNX_RUNTIME,
        )
    }
}
