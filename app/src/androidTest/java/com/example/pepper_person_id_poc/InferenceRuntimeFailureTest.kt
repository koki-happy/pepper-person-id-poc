package com.example.pepper_person_id_poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.infrastructure.face.FaceDetectorFactory
import com.example.pepper_person_id_poc.infrastructure.face.FaceEmbeddingEngineFactory
import com.example.pepper_person_id_poc.infrastructure.face.UnsupportedModelRuntimePairException
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InferenceRuntimeFailureTest {
    @Test
    fun detectorFactoryRejectsMismatchedPairWithoutFallback() {
        val error = assertThrows(UnsupportedModelRuntimePairException::class.java) {
            FaceDetectorFactory.requireExactPair(
                FaceDetectorModelOption.ML_KIT_BUNDLED,
                FaceDetectorRuntime.OPEN_CV,
            )
        }

        assertThat(error).hasMessageThat().contains("no fallback")
    }

    @Test
    fun embeddingFactoryRejectsBlockedRuntimeWithoutFallback() {
        val error = assertThrows(UnsupportedModelRuntimePairException::class.java) {
            FaceEmbeddingEngineFactory.requireExactPair(
                FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                FaceEmbeddingRuntime.ONNX_RUNTIME,
            )
        }

        assertThat(error).hasMessageThat().contains("no fallback")
    }
}
