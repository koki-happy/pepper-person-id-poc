package com.example.pepper_person_id_poc.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceEmbeddingRuntimeCompatibilityTest {
    @Test
    fun onnxRuntime_allowsExactFp32PairsOnPackagedAndroidAbis() {
        listOf("arm64-v8a", "armeabi-v7a").forEach { abi ->
            listOf(
                FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32,
            ).forEach { model ->
                assertThat(
                    FaceEmbeddingRuntimeCompatibility.supportsExactPair(
                        model,
                        FaceEmbeddingRuntime.ONNX_RUNTIME,
                        abi,
                    ),
                ).isTrue()
            }
        }
        assertThat(
            FaceEmbeddingRuntimeCompatibility.supportsExactPair(
                FaceEmbeddingModelOption.SFACE_2021DEC_INT8,
                FaceEmbeddingRuntime.ONNX_RUNTIME,
                "arm64-v8a",
            ),
        ).isFalse()
    }

    @Test
    fun onnxRuntime_blocksUnknownAbiWithoutFallback() {
        assertThat(
            FaceEmbeddingRuntimeCompatibility.supportsExactPair(
                FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                FaceEmbeddingRuntime.ONNX_RUNTIME,
                "x86",
            ),
        ).isFalse()
    }

    @Test
    fun runtimeId_matchesPackagedAndroidDependency() {
        assertThat(FaceEmbeddingRuntime.ONNX_RUNTIME.runtimeId)
            .isEqualTo("onnxruntime-android-1.20.0-cpu")
    }
}
