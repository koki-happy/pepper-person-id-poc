package com.example.pepper_person_id_poc.infrastructure.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class YuNetNativeFaceDetectorContractTest {
    @Test
    fun exactArtifactsAndTwelveOutputsArePinned() {
        assertEquals(
            listOf(
                1_600, 400, 100,
                1_600, 400, 100,
                6_400, 1_600, 400,
                16_000, 4_000, 1_000,
            ),
            YuNetNativeFaceDetector.OUTPUTS.map(YuNetNativeOutput::elementCount),
        )
        assertEquals(
            (0..11).map { "out$it" },
            YuNetNativeFaceDetector.OUTPUTS.map(YuNetNativeOutput::ncnnName),
        )
        assertEquals(
            "f3b6ec99c4773da6edc0950f0fb1d6d728982114114e87a673780eb243b72bbf",
            YuNetNativeFaceDetector.NCNN_PARAM.sha256,
        )
        assertEquals(
            "8faa696d61ad6bf5c13ed5d6c8ae35e376c660968db53d723a329020856ca5c9",
            YuNetNativeFaceDetector.NCNN_BIN.sha256,
        )
        assertEquals(
            "31cb825bbff3cfe1535cc40dc27e72c3614c8efcc0f3ae00beb1b557f5f04b69",
            YuNetNativeFaceDetector.MNN_MODEL.sha256,
        )
    }

    @Test
    fun rawOutputContractRejectsMissingMalformedAndNonfiniteHeads() {
        val valid = YuNetNativeFaceDetector.OUTPUTS.map {
            FloatArray(it.elementCount)
        }
        YuNetNativeFaceDetector.validateOutputs(valid)

        assertThrows(IllegalArgumentException::class.java) {
            YuNetNativeFaceDetector.validateOutputs(valid.dropLast(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            YuNetNativeFaceDetector.validateOutputs(
                valid.toMutableList().also { it[0] = FloatArray(1) },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            YuNetNativeFaceDetector.validateOutputs(
                valid.toMutableList().also { it[11][0] = Float.NaN },
            )
        }
    }
}
