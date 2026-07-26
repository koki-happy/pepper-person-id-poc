package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class OnnxRuntimeYuNetFaceDetectorContractTest {
    @Test
    fun exact2026Fp32ContractPinsArtifactHashInputAndRawOutputs() {
        val contract = OnnxRuntimeYuNetFaceDetector.contractFor(
            FaceDetectorModelOption.YUNET_2026MAY_FP32,
        )

        assertThat(contract.artifactId).isEqualTo("yunet-2026may-onnx-fp32")
        assertThat(contract.modelFileName).isEqualTo("face_detection_yunet_2026may.onnx")
        assertThat(contract.fileSizeBytes).isEqualTo(229_738L)
        assertThat(contract.sha256)
            .isEqualTo("ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0")
        assertExactTensorContract(contract, inputSize = 320)
        assertThat(contract.input.metadataDimensions).containsExactly(1, 3, -1, -1).inOrder()
        assertThat(
            contract.outputs.all {
                it.metadataDimensions[0] == 1 && it.metadataDimensions[1] == -1
            },
        ).isTrue()
    }

    @Test
    fun rejectsEveryNonExactYuNetOnnxArtifactWithoutFallback() {
        assertThrows(IllegalArgumentException::class.java) {
            OnnxRuntimeYuNetFaceDetector.contractFor(
                FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnnxRuntimeYuNetFaceDetector.contractFor(FaceDetectorModelOption.ML_KIT_BUNDLED)
        }
    }

    @Test
    fun requiresPackagedArm64RuntimeWithoutAbiFallback() {
        OnnxRuntimeYuNetFaceDetector.requireSupportedAbi(listOf("x86_64", "arm64-v8a"))

        assertThrows(IllegalArgumentException::class.java) {
            OnnxRuntimeYuNetFaceDetector.requireSupportedAbi(listOf("armeabi-v7a"))
        }
    }

    @Test
    fun flattenRejectsUnexpectedAndPreservesAllFiniteRawValues() {
        val raw = arrayOf(
            arrayOf(floatArrayOf(1f, 2f)),
            arrayOf(floatArrayOf(3f, 4f)),
        )

        assertThat(OnnxRuntimeYuNetFaceDetector.flattenFloatTensor(raw).asList())
            .containsExactly(1f, 2f, 3f, 4f)
            .inOrder()
        assertThrows(IllegalStateException::class.java) {
            OnnxRuntimeYuNetFaceDetector.flattenFloatTensor(doubleArrayOf(1.0))
        }
    }

    @Test
    fun rawOutputValidationRejectsWrongShapeAndNonFiniteValues() {
        val output = YuNetOnnxTensorContract("test", listOf(1, 2, 1))

        assertThat(
            OnnxRuntimeYuNetFaceDetector.validateRawOutput(
                output,
                arrayOf(arrayOf(floatArrayOf(1f), floatArrayOf(2f))),
            ).asList(),
        ).containsExactly(1f, 2f).inOrder()
        assertThrows(IllegalArgumentException::class.java) {
            OnnxRuntimeYuNetFaceDetector.validateRawOutput(output, floatArrayOf(1f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnnxRuntimeYuNetFaceDetector.validateRawOutput(
                output,
                floatArrayOf(Float.NaN, 1f),
            )
        }
    }

    @Test
    fun dynamicMetadataWildcardAcceptsOnlyDynamicOrExactInferenceAxes() {
        val input = OnnxRuntimeYuNetFaceDetector.FP32_2026_CONTRACT.input
        val output = OnnxRuntimeYuNetFaceDetector.FP32_2026_CONTRACT.outputs.first()

        assertThat(input.acceptsMetadataShape(listOf(1L, 3L, -1L, -1L))).isTrue()
        assertThat(input.acceptsMetadataShape(listOf(1L, 3L, 320L, 320L))).isTrue()
        assertThat(input.acceptsMetadataShape(listOf(1L, 3L, 640L, 640L))).isFalse()
        assertThat(output.acceptsMetadataShape(listOf(1L, -1L, 1L))).isTrue()
        assertThat(output.acceptsMetadataShape(listOf(1L, 1_600L, 1L))).isTrue()
        assertThat(output.acceptsMetadataShape(listOf(1L, 6_400L, 1L))).isFalse()
    }

    private fun assertExactTensorContract(contract: YuNetOnnxContract, inputSize: Int) {
        assertThat(contract.input.name).isEqualTo("input")
        assertThat(contract.input.dimensions)
            .containsExactly(1, 3, inputSize, inputSize)
            .inOrder()
        assertThat(contract.outputs.map { it.name }).containsExactly(
            "cls_8",
            "cls_16",
            "cls_32",
            "obj_8",
            "obj_16",
            "obj_32",
            "bbox_8",
            "bbox_16",
            "bbox_32",
            "kps_8",
            "kps_16",
            "kps_32",
        ).inOrder()
        val scale = inputSize / 320
        val anchorScale = scale * scale
        assertThat(contract.outputs.map { it.elementCount }).containsExactly(
            1_600 * anchorScale,
            400 * anchorScale,
            100 * anchorScale,
            1_600 * anchorScale,
            400 * anchorScale,
            100 * anchorScale,
            6_400 * anchorScale,
            1_600 * anchorScale,
            400 * anchorScale,
            16_000 * anchorScale,
            4_000 * anchorScale,
            1_000 * anchorScale,
        ).inOrder()
    }
}
