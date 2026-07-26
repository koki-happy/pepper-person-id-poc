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
class WeSpeakerResNet34LmOnnxSmokeTest {
    @Test
    fun pinnedOnnxRunsFixedPcmThroughSherpaWithoutFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = SpeakerModelOption.WESPEAKER_RESNET34_LM
        val assetPath = "models/${model.modelFileName}"
        val hash = context.assets.open(assetPath).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        assertThat(hash).isEqualTo(EXPECTED_SHA256)

        val fixedPcm = ShortArray(SAMPLE_RATE * 3) { index ->
            val seconds = index.toDouble() / SAMPLE_RATE
            (9_000 * sin(2.0 * PI * 220.0 * seconds) +
                3_000 * sin(2.0 * PI * 440.0 * seconds)).toInt().toShort()
        }
        val engine = SherpaOnnxSpeakerEmbeddingEngine(context, model, numThreads = 1)
        try {
            engine.prepare()
            assertThat(engine.modelName).isEqualTo("wespeaker-resnet34-lm")
            assertThat(engine.embeddingDimension).isEqualTo(256)
            val first = engine.extract(fixedPcm, SAMPLE_RATE)
            val second = engine.extract(fixedPcm, SAMPLE_RATE)
            assertThat(first).hasLength(256)
            assertThat(first.all(Float::isFinite)).isTrue()
            assertThat(first.any { it != 0f }).isTrue()
            assertThat(second.toList()).containsExactlyElementsIn(first.toList()).inOrder()
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val EXPECTED_SHA256 = "df0cec64c3bba5dbc3637e50c4259de348a24124f4bb399f413fa1d4b44ba605"
    }
}
