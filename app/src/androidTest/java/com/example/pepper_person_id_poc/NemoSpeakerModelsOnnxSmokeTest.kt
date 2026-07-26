package com.example.pepper_person_id_poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pepper_person_id_poc.domain.config.SpeakerModelOption
import com.example.pepper_person_id_poc.infrastructure.speaker.SherpaOnnxSpeakerEmbeddingEngine
import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NemoSpeakerModelsOnnxSmokeTest {
    @Test
    fun speakerNetMAndTitaNetSRunPinnedOnnxWithoutFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixedPcm = ShortArray(SAMPLE_RATE * 3) { index ->
            val seconds = index.toDouble() / SAMPLE_RATE
            (8_000 * sin(2.0 * PI * 180.0 * seconds) +
                2_000 * sin(2.0 * PI * 360.0 * seconds)).toInt().toShort()
        }

        MODELS.forEach { expected ->
            val model = expected.model
            val hash = context.assets.open("models/${model.modelFileName}").use(::sha256)
            assertThat(hash).isEqualTo(expected.sha256)

            val engine = SherpaOnnxSpeakerEmbeddingEngine(context, model, numThreads = 1)
            try {
                engine.prepare()
                assertThat(engine.modelName).isEqualTo(model.configModelId)
                assertThat(engine.embeddingDimension).isEqualTo(model.embeddingSize)
                val embedding = engine.extract(fixedPcm, SAMPLE_RATE)
                assertThat(embedding).hasLength(model.embeddingSize)
                assertThat(embedding.all(Float::isFinite)).isTrue()
                assertThat(embedding.any { it != 0f }).isTrue()
            } finally {
                engine.close()
            }
        }
    }

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class ExpectedModel(
        val model: SpeakerModelOption,
        val sha256: String,
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        val MODELS = listOf(
            ExpectedModel(
                SpeakerModelOption.SPEAKERNET_M,
                "d204dc8aac0014b8543f05fc8e310510c7022bc65b6452c203ec205ef7a66b23",
            ),
            ExpectedModel(
                SpeakerModelOption.TITANET_S,
                "ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e",
            ),
        )
    }
}
