package com.example.pepper_person_id_poc.infrastructure.speaker

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class PyannoteSegmentationOnnxEngineContractTest {
    @Test
    fun exactArtifactAndRuntimeAreExposed() {
        assertThat(PyannoteSegmentationOnnxEngine.ARTIFACT_ID)
            .isEqualTo("pyannote-segmentation-3.0-sherpa-onnx-fp32")
        assertThat(PyannoteSegmentationOnnxEngine.RUNTIME_ID)
            .isEqualTo("onnxruntime-android-1.20.0-cpu")
    }

    @Test
    fun outputIsSplitIntoCompleteSevenClassFrames() {
        val output = arrayOf(
            arrayOf(
                floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f),
                floatArrayOf(7f, 8f, 9f, 10f, 11f, 12f, 13f),
            ),
        )

        val frames = PyannoteSegmentationOnnxEngine.decodeClassScores(output)

        assertThat(frames).hasSize(2)
        assertThat(frames[0].asList()).containsExactly(0f, 1f, 2f, 3f, 4f, 5f, 6f).inOrder()
        assertThat(frames[1].asList()).containsExactly(7f, 8f, 9f, 10f, 11f, 12f, 13f).inOrder()
    }

    @Test
    fun incompleteOrNonFiniteOutputFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            PyannoteSegmentationOnnxEngine.decodeClassScores(floatArrayOf(1f, 2f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PyannoteSegmentationOnnxEngine.decodeClassScores(
                floatArrayOf(Float.NaN, 0f, 0f, 0f, 0f, 0f, 0f),
            )
        }
    }
}
