package com.example.pepper_person_id_poc.infrastructure.litert

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteRtRuntimeTest {
    private val contract = LiteRtModelContract(
        artifactId = "exact-converted-artifact",
        assetPath = "models/exact-converted-artifact.tflite",
        inputs = listOf(LiteRtFloatTensorSpec("input", listOf(1, 2))),
        outputs = listOf(LiteRtFloatTensorSpec("output", listOf(1, 2))),
    )

    @Test
    fun inferenceUsesOnlyExactSessionAndValidatesShape() {
        val session = RecordingSession(listOf(floatArrayOf(0.25f, 0.75f)))
        LiteRtRuntime(contract, session).use { runtime ->
            val output = runtime.infer(listOf(floatArrayOf(1f, 2f)))
            assertThat(output.single().toList()).containsExactly(0.25f, 0.75f).inOrder()
            assertThat(session.inputs.single().toList()).containsExactly(1f, 2f).inOrder()
        }

        assertThat(session.closed).isTrue()
    }

    @Test
    fun invalidInputNeverInvokesSessionOrFallsBack() {
        val session = RecordingSession(listOf(floatArrayOf(0.25f, 0.75f)))
        val runtime = LiteRtRuntime(contract, session)

        assertThrows(IllegalArgumentException::class.java) {
            runtime.infer(listOf(floatArrayOf(1f)))
        }
        assertThat(session.inputs).isEmpty()
        runtime.close()
    }

    @Test
    fun nonFiniteOutputFailsClosed() {
        val session = RecordingSession(listOf(floatArrayOf(Float.NaN, 0f)))
        val runtime = LiteRtRuntime(contract, session)

        assertThrows(IllegalArgumentException::class.java) {
            runtime.infer(listOf(floatArrayOf(1f, 2f)))
        }
        runtime.close()
    }

    @Test
    fun closeIsIdempotentAndInferenceAfterCloseIsRejected() {
        val session = RecordingSession(listOf(floatArrayOf(0.25f, 0.75f)))
        val runtime = LiteRtRuntime(contract, session)

        runtime.close()
        runtime.close()

        assertThat(session.closeCount).isEqualTo(1)
        assertThrows(IllegalStateException::class.java) {
            runtime.infer(listOf(floatArrayOf(1f, 2f)))
        }
    }

    @Test
    fun contractRejectsAnyOtherRuntimeId() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            contract.copy(runtimeId = "some-other-runtime")
        }

        assertThat(error).hasMessageThat().contains("no fallback")
    }

    private class RecordingSession(
        private val outputs: List<FloatArray>,
    ) : LiteRtSession {
        val inputs = mutableListOf<FloatArray>()
        var closeCount = 0
        val closed: Boolean
            get() = closeCount > 0

        override fun infer(inputs: List<FloatArray>): List<FloatArray> {
            this.inputs += inputs.map(FloatArray::clone)
            return outputs
        }

        override fun close() {
            closeCount += 1
        }
    }
}
