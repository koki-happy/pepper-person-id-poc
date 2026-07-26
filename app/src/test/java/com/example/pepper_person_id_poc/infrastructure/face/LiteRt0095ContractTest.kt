package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import android.content.ContextWrapper
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtSession
import com.example.pepper_person_id_poc.infrastructure.litert.LiteRtRuntime
import com.google.common.truth.Truth.assertThat
import kotlin.math.sqrt
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteRt0095ContractTest {
    @Test
    fun exactContractPinsConvertedArtifactAndTensorMetadata() {
        val contract = LiteRt0095EmbeddingEngine.CONTRACT

        assertThat(contract.artifactId).isEqualTo("face-0095-litert-fp32")
        assertThat(contract.runtimeId).isEqualTo(LiteRtRuntime.RUNTIME_ID)
        assertThat(contract.assetPath)
            .isEqualTo("models/face-reidentification-retail-0095.tflite")
        assertThat(contract.inputs.single().name).isEqualTo("0")
        assertThat(contract.inputs.single().dimensions)
            .containsExactly(1, 3, 128, 128)
            .inOrder()
        assertThat(contract.outputs.single().name).isEqualTo("Identity")
        assertThat(contract.outputs.single().dimensions)
            .containsExactly(1, 1, 1, 256)
            .inOrder()
    }

    @Test
    fun bgrBytesArePackedAsRawUnsignedNchwWithoutHostPreprocessing() {
        val plane = 128 * 128
        val input = ByteArray(plane * 3)
        input[0] = 1
        input[1] = 127
        input[2] = 0xff.toByte()

        val output = pack0095BgrNchw(input)

        assertThat(output.size).isEqualTo(1 * 3 * 128 * 128)
        assertThat(output[0]).isEqualTo(1f)
        assertThat(output[plane]).isEqualTo(127f)
        assertThat(output[plane * 2]).isEqualTo(255f)
    }

    @Test
    fun outputIsFinite256AndL2Normalized() {
        val raw = FloatArray(256) { index -> (index + 1).toFloat() }

        val normalized = normalize0095Embedding(raw)

        assertThat(normalized).hasLength(256)
        assertThat(normalized.all(Float::isFinite)).isTrue()
        val norm = sqrt(normalized.sumOf { it.toDouble() * it.toDouble() })
        assertThat(norm).isWithin(1e-6).of(1.0)
    }

    @Test
    fun invalidOutputFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            normalize0095Embedding(FloatArray(255) { 1f })
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalize0095Embedding(FloatArray(256).also { it[0] = Float.NaN })
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalize0095Embedding(FloatArray(256))
        }
    }

    @Test
    fun enginePrepareAndRepeatedCloseReleaseOnlyTheExactLiteRtSession() {
        var opened = 0
        var closed = 0
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        val engine = LiteRt0095EmbeddingEngine(
            context = context,
            contract = LiteRt0095EmbeddingEngine.CONTRACT,
            runtimeProvider = { _, contract, _ ->
                opened += 1
                LiteRtRuntime(
                    contract,
                    object : LiteRtSession {
                        override fun infer(inputs: List<FloatArray>): List<FloatArray> =
                            error("not used")

                        override fun close() {
                            closed += 1
                        }
                    },
                )
            },
        )

        engine.prepare()
        engine.prepare()
        engine.close()
        engine.close()

        assertThat(opened).isEqualTo(1)
        assertThat(closed).isEqualTo(1)
    }
}
