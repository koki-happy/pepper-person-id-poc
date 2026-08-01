package com.example.pepper_person_id_poc.infrastructure.audio

import android.content.Context
import com.example.pepper_person_id_poc.application.contract.VoiceActivityDetector
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

class SherpaSileroVoiceActivityDetector(
    context: Context,
    threshold: Float = 0.35f,
    minimumSilenceMillis: Long = 400L,
    minimumSpeechMillis: Long = 300L,
    maximumSpeechMillis: Long = 10_000L,
) : VoiceActivityDetector {
    private val vad = Vad(
        assetManager = context.applicationContext.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = MODEL_ASSET_PATH,
                threshold = threshold,
                minSilenceDuration = minimumSilenceMillis / 1_000f,
                minSpeechDuration = minimumSpeechMillis / 1_000f,
                windowSize = 512,
                maxSpeechDuration = maximumSpeechMillis / 1_000f,
            ),
            sampleRate = 16_000,
            numThreads = 1,
            provider = "cpu",
            debug = false,
        ),
    )

    override val modelName: String = "Silero VAD"

    @Synchronized
    override fun isSpeech(samples: ShortArray): Boolean {
        vad.acceptWaveform(FloatArray(samples.size) { index -> samples[index] / 32_768f })
        return vad.isSpeechDetected()
    }

    @Synchronized
    override fun reset() = vad.reset()

    @Synchronized
    override fun close() = vad.release()

    private companion object {
        const val MODEL_ASSET_PATH = "models/silero_vad.onnx"
    }
}
