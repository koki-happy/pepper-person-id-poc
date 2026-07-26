package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiteRtYuNetPostprocessorTest {
    @Test
    fun exactContractPinsConvertedTensorNamesAndShapes() {
        val contract = LiteRtYuNetFaceDetector.CONTRACT

        assertThat(contract.artifactId).isEqualTo("yunet-2026may-litert-fp32-320")
        assertThat(contract.runtimeId).isEqualTo(LiteRtRuntime.RUNTIME_ID)
        assertThat(contract.inputs.single().name).isEqualTo("input")
        assertThat(contract.inputs.single().dimensions).containsExactly(1, 3, 320, 320).inOrder()
        assertThat(contract.outputs.map { it.name }).containsExactly(
            "Identity",
            "Identity_1",
            "Identity_2",
            "Identity_3",
            "Identity_4",
            "Identity_5",
            "Identity_6",
            "Identity_7",
            "Identity_8",
            "Identity_9",
            "Identity_10",
            "Identity_11",
        ).inOrder()
    }

    @Test
    fun logicalYuNetSelectionResolvesTheExactLiteRtArtifact() {
        FaceDetectorFactory.requireExactPair(
            FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32,
            FaceDetectorRuntime.LITERT,
        )

        FaceDetectorFactory.requireExactPair(
            FaceDetectorModelOption.YUNET_2026MAY_FP32,
            FaceDetectorRuntime.LITERT,
        )
        FaceDetectorFactory.requireExactPair(
            FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32,
            FaceDetectorRuntime.OPEN_CV,
        )
    }

    @Test
    fun kps32AdapterExactlyInvertsConversionOnlyPermutation() {
        val onnxOrder = FloatArray(1_000) { it.toFloat() }
        val convertedOrder = FloatArray(1_000)
        repeat(10) { row ->
            repeat(10) { column ->
                repeat(10) { component ->
                    val onnxIndex = (row * 10 + column) * 10 + component
                    val convertedIndex = (component * 10 + row) * 10 + column
                    convertedOrder[convertedIndex] = onnxOrder[onnxIndex]
                }
            }
        }

        assertThat(invertYuNetKps32ConversionPermutation(convertedOrder).asList())
            .containsExactlyElementsIn(onnxOrder.asList())
            .inOrder()
    }

    @Test
    fun decoderProducesOpenCvCompatibleBoxLandmarksAndScore() {
        val outputs = listOf(
            FloatArray(1_600),
            FloatArray(400),
            FloatArray(100),
            FloatArray(1_600),
            FloatArray(400),
            FloatArray(100),
            FloatArray(1_600 * 4),
            FloatArray(400 * 4),
            FloatArray(100 * 4),
            FloatArray(1_600 * 10),
            FloatArray(400 * 10),
            FloatArray(100 * 10),
        )
        val anchor = 2 * 10 + 3
        outputs[2][anchor] = 0.81f
        outputs[5][anchor] = 1f

        val result = YuNetLiteRtPostprocessor.decode(
            outputs = outputs,
            imageWidth = 320,
            imageHeight = 320,
            scoreThreshold = 0.8f,
            nmsThreshold = 0.3f,
            topK = 5_000,
        ).single()

        val row = result.asOpenCvRow()
        assertThat(row.copyOfRange(0, 14).asList()).containsExactly(
            80f,
            48f,
            32f,
            32f,
            96f,
            64f,
            96f,
            64f,
            96f,
            64f,
            96f,
            64f,
            96f,
            64f,
        ).inOrder()
        assertThat(row[14]).isWithin(1e-6f).of(0.9f)
    }
}
