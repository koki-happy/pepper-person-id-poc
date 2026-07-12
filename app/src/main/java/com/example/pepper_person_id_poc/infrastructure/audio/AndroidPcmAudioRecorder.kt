package com.example.pepper_person_id_poc.infrastructure.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.Context
import com.example.pepper_person_id_poc.application.contract.AudioRecordingState
import com.example.pepper_person_id_poc.application.contract.AudioRecordingStatus
import com.example.pepper_person_id_poc.application.contract.PcmAudioRecorder
import com.example.pepper_person_id_poc.application.contract.PcmUtteranceMetadata
import com.example.pepper_person_id_poc.domain.audio.AudioLevel
import com.example.pepper_person_id_poc.domain.audio.EnergyVoiceActivityDetector
import com.example.pepper_person_id_poc.application.contract.VoiceActivityDetector
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.audio.PcmUtteranceSegmenter
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidPcmAudioRecorder(
    context: Context,
    private val onUtterance: (PcmUtterance) -> Unit = {},
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
) : PcmAudioRecorder {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(AudioRecordingState())
    private var recordingJob: Job? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    override val state: StateFlow<AudioRecordingState> = mutableState.asStateFlow()

    override fun start() {
        if (!running.compareAndSet(false, true)) return
        mutableState.value = mutableState.value.copy(
            status = AudioRecordingStatus.STARTING,
            error = null,
        )
        recordingJob = scope.launch { recordLoop() }
    }

    override fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching {
            audioRecord?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
        }
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun recordLoop() {
        var recorder: AudioRecord? = null
        var segmenter: PcmUtteranceSegmenter? = null
        var voiceActivityDetector: VoiceActivityDetector? = null
        try {
            val initialized = createInitializedAudioRecord()
            recorder = initialized.audioRecord
            audioRecord = recorder
            segmenter = PcmUtteranceSegmenter(initialized.sampleRate)
            voiceActivityDetector = if (initialized.sampleRate == 16_000) {
                SherpaSileroVoiceActivityDetector(appContext)
            } else {
                EnergyVoiceActivityDetector()
            }
            val buffer = ShortArray(initialized.readBufferSamples)
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord did not enter RECORDSTATE_RECORDING"
            }
            mutableState.value = AudioRecordingState(
                status = AudioRecordingStatus.RECORDING,
                sampleRate = initialized.sampleRate,
                minBufferSizeBytes = initialized.minBufferSizeBytes,
                vadModelName = voiceActivityDetector.modelName,
            )
            onBenchmarkEvent(
                BenchmarkEvent(
                    event = "pcm_recording_start",
                    timestampMillis = System.currentTimeMillis(),
                    status = "SUCCESS",
                    attributes = mapOf(
                        "sampleRate" to initialized.sampleRate.toString(),
                        "channels" to "1",
                        "encoding" to "PCM_16BIT",
                        "minBufferSizeBytes" to initialized.minBufferSizeBytes.toString(),
                        "vadModel" to voiceActivityDetector.modelName,
                    ),
                ),
            )

            while (running.get()) {
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    if (!running.get()) break
                    error("AudioRecord.read failed: $read")
                }
                val chunk = buffer.copyOf(read)
                val level = AudioLevel.dbFs(chunk)
                val speech = voiceActivityDetector.isSpeech(chunk)
                val endedAtMillis = System.currentTimeMillis()
                val utterance = segmenter.process(chunk, endedAtMillis, speech)
                mutableState.value = mutableState.value.copy(
                    levelDbFs = level,
                    speechActive = segmenter.speechActive,
                )
                utterance?.let(::deliverUtterance)
            }
        } catch (throwable: Throwable) {
            if (running.get()) {
                mutableState.value = mutableState.value.copy(
                    status = AudioRecordingStatus.ERROR,
                    speechActive = false,
                    error = throwable.message ?: throwable::class.java.simpleName,
                )
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "pcm_recording",
                        timestampMillis = System.currentTimeMillis(),
                        status = "ERROR",
                        error = throwable.message ?: throwable::class.java.simpleName,
                    ),
                )
            }
        } finally {
            running.set(false)
            segmenter?.flush(System.currentTimeMillis())?.let(::deliverUtterance)
            runCatching {
                recorder?.takeIf { it.recordingState == AudioRecord.RECORDSTATE_RECORDING }?.stop()
            }
            recorder?.release()
            voiceActivityDetector?.close()
            audioRecord = null
            if (mutableState.value.status != AudioRecordingStatus.ERROR) {
                mutableState.value = mutableState.value.copy(
                    status = AudioRecordingStatus.STOPPED,
                    levelDbFs = AudioLevel.MIN_DB_FS,
                    speechActive = false,
                )
            }
        }
    }

    private fun deliverUtterance(utterance: PcmUtterance) {
        mutableState.value = mutableState.value.copy(
            lastUtterance = PcmUtteranceMetadata.from(utterance),
        )
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "pcm_utterance",
                timestampMillis = utterance.endedAtMillis,
                durationMillis = utterance.durationMillis,
                status = if (utterance.sufficientForSpeakerIdentification) "SUFFICIENT" else "INSUFFICIENT_AUDIO",
                attributes = mapOf(
                    "sampleRate" to utterance.sampleRate.toString(),
                    "sampleCount" to utterance.pcm16.size.toString(),
                    "voicedDurationMillis" to utterance.voicedDurationMillis.toString(),
                ),
            ),
        )
        onUtterance(utterance)
    }

    @SuppressLint("MissingPermission")
    private fun createInitializedAudioRecord(): InitializedAudioRecord {
        val errors = mutableListOf<String>()
        SAMPLE_RATE_CANDIDATES.forEach { sampleRate ->
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBufferSize <= 0) {
                errors += "$sampleRate Hz getMinBufferSize=$minBufferSize"
                return@forEach
            }
            val readBufferSamples = sampleRate / CHUNKS_PER_SECOND
            val recorderBufferBytes = maxOf(minBufferSize * 2, readBufferSamples * BYTES_PER_SAMPLE * 2)
            val candidate = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recorderBufferBytes,
            )
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                return InitializedAudioRecord(candidate, sampleRate, minBufferSize, readBufferSamples)
            }
            errors += "$sampleRate Hz STATE_UNINITIALIZED"
            candidate.release()
        }
        error("No supported mono PCM16 AudioRecord configuration: ${errors.joinToString()}")
    }

    private data class InitializedAudioRecord(
        val audioRecord: AudioRecord,
        val sampleRate: Int,
        val minBufferSizeBytes: Int,
        val readBufferSamples: Int,
    )

    private companion object {
        val SAMPLE_RATE_CANDIDATES = listOf(16_000, 44_100)
        const val CHUNKS_PER_SECOND = 10
        const val BYTES_PER_SAMPLE = 2
    }
}
