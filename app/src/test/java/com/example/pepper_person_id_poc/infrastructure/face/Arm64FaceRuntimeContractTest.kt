package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class Arm64FaceRuntimeContractTest {
    private val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) {
        it.parentFile
    }.first { File(it, "config/models.json").isFile }

    @Test
    fun arm64NativeLibrariesAndConvertedModelsArePresent() {
        listOf("libncnn.so", "libMNN.so", "libc++_shared.so").forEach { fileName ->
            assertThat(File(repositoryRoot, "app/src/main/jniLibs/arm64-v8a/$fileName").length())
                .isGreaterThan(0L)
        }
        listOf(
            "face_recognition_sface_2021dec.ncnn.param",
            "face_recognition_sface_2021dec.ncnn.bin",
            "face_recognition_sface_2021dec.mnn",
            "face-reidentification-retail-0095.ncnn.param",
            "face-reidentification-retail-0095.ncnn.bin",
            "face-reidentification-retail-0095.mnn",
        ).forEach { fileName ->
            assertThat(File(repositoryRoot, "app/src/benchmark/assets/models/$fileName").length())
                .isGreaterThan(0L)
        }
    }

    @Test
    fun catalogAllowsEveryPackagedNcnnAndMnnArtifactOnArm64WithoutClaimingVerified() {
        val catalog = File(repositoryRoot, "config/models.json").readText()
        listOf(
            "sface-2021dec-ncnn-fp32" to "ncnn-20260526-android-cpu",
            "sface-2021dec-mnn-fp32" to "mnn-3.5.0-android-cpu",
            "face-0095-ncnn-fp32" to "ncnn-20260526-android-cpu",
            "face-0095-mnn-fp32" to "mnn-3.5.0-android-cpu",
        ).forEach { (artifactId, runtimeId) ->
            val record = Regex(
                """\{\s*"artifactId":\s*"$artifactId",\s*"runtimeId":\s*"$runtimeId",\s*"abi":\s*"arm64-v8a",\s*"minApi":\s*23,\s*"status":\s*"([^"]+)"""",
            ).find(catalog)
            assertThat(record).isNotNull()
            assertThat(record!!.groupValues[1]).isEqualTo("BUILDABLE")
        }
    }

    @Test
    fun catalogAllowsExactOnnxFp32PairsOnlyOnArm64() {
        val catalog = File(repositoryRoot, "config/models.json").readText()
        listOf(
            "sface-2021dec-onnx-fp32",
            "face-0095-onnx-fp32",
        ).forEach { artifactId ->
            val statuses = listOf("armeabi-v7a", "arm64-v8a").associateWith { abi ->
                Regex(
                    """\{\s*"artifactId":\s*"$artifactId",\s*"runtimeId":\s*"onnxruntime-android-1.20.0-cpu",\s*"abi":\s*"$abi",\s*"minApi":\s*23,\s*"status":\s*"([^"]+)"""",
                ).find(catalog)?.groupValues?.get(1)
            }
            assertThat(statuses["armeabi-v7a"]).isEqualTo("BLOCKED")
            assertThat(statuses["arm64-v8a"]).isEqualTo("BUILDABLE")
        }
    }

    @Test
    fun factoryAcceptsExactOnnxNcnnAndMnnPairsAndStillRejectsCrossFormatPairs() {
        listOf(
            FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.ONNX_RUNTIME,
            FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to
                FaceEmbeddingRuntime.ONNX_RUNTIME,
            FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32 to FaceEmbeddingRuntime.NCNN,
            FaceEmbeddingModelOption.SFACE_2021DEC_MNN_FP32 to FaceEmbeddingRuntime.MNN,
            FaceEmbeddingModelOption.FACE_0095_NCNN_FP32 to FaceEmbeddingRuntime.NCNN,
            FaceEmbeddingModelOption.FACE_0095_MNN_FP32 to FaceEmbeddingRuntime.MNN,
        ).forEach { (model, runtime) ->
            FaceEmbeddingEngineFactory.requireExactPair(model, runtime, "arm64-v8a")
        }

        val failure = runCatching {
            FaceEmbeddingEngineFactory.requireExactPair(
                FaceEmbeddingModelOption.SFACE_2021DEC_INT8,
                FaceEmbeddingRuntime.MNN,
                "arm64-v8a",
            )
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(UnsupportedModelRuntimePairException::class.java)
        assertThat(failure).hasMessageThat().contains("no fallback")
    }
}
