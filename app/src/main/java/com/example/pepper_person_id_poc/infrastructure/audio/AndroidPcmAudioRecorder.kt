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
import com.example.pepper_person_id_poc.application.contract.VoiceActivityDetector
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.audio.PcmUtteranceSegmenter
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.speaker.AudioCaptureInitialization
import com.example.pepper_person_id_poc.domain.speaker.AudioCaptureInitializationFailure
import com.example.pepper_person_id_poc.domain.speaker.AudioCaptureInitializationStage
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
            captureInitialization = null,
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
            voiceActivityDetector = SherpaSileroVoiceActivityDetector(appContext)
            val buffer = ShortArray(initialized.readBufferSamples)
            try {
                recorder.startRecording()
            } catch (throwable: Throwable) {
                throw AudioCaptureInitializationException(
                    diagnostics = AudioCaptureInitialization.failure(
                        stage = AudioCaptureInitializationStage.START_RECORDING,
                        failure = AudioCaptureInitializationFailure.START_RECORDING_FAILED,
                        minBufferSizeBytes = initialized.minBufferSizeBytes,
                        recorderBufferSizeBytes = initialized.recorderBufferSizeBytes,
                        audioRecordState = recorder.state,
                        recordingState = recorder.recordingState,
                        message = throwable.message,
                    ),
                    cause = throwable,
                )
            }
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw AudioCaptureInitializationException(
                    AudioCaptureInitialization.failure(
                        stage = AudioCaptureInitializationStage.START_RECORDING,
                        failure = AudioCaptureInitializationFailure.RECORDING_STATE_NOT_RECORDING,
                        minBufferSizeBytes = initialized.minBufferSizeBytes,
                        recorderBufferSizeBytes = initialized.recorderBufferSizeBytes,
                        audioRecordState = recorder.state,
                        recordingState = recorder.recordingState,
                        message = "AudioRecord did not enter RECORDSTATE_RECORDING",
                    ),
                )
            }
            val captureInitialization = AudioCaptureInitialization.success(
                minBufferSizeBytes = initialized.minBufferSizeBytes,
                recorderBufferSizeBytes = initialized.recorderBufferSizeBytes,
                audioRecordState = recorder.state,
                recordingState = recorder.recordingState,
            )
            mutableState.value = AudioRecordingState(
                status = AudioRecordingStatus.RECORDING,
                sampleRate = initialized.sampleRate,
                minBufferSizeBytes = initialized.minBufferSizeBytes,
                vadModelName = voiceActivityDetector.modelName,
                captureInitialization = captureInitialization,
            )
            onBenchmarkEvent(
                BenchmarkEvent(
                    event = "pcm_recording_start",
                    timestampMillis = System.currentTimeMillis(),
                    status = "SUCCESS",
                    attributes = captureInitialization.diagnosticAttributes() +
                        ("vadModel" to voiceActivityDetector.modelName),
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
                val initialization = (throwable as? AudioCaptureInitializationException)?.diagnostics
                mutableState.value = mutableState.value.copy(
                    status = AudioRecordingStatus.ERROR,
                    speechActive = false,
                    captureInitialization = initialization,
                    error = throwable.message ?: throwable::class.java.simpleName,
                )
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = if (initialization == null) {
                            "pcm_recording"
                        } else {
                            "pcm_capture_initialization"
                        },
                        timestampMillis = System.currentTimeMillis(),
                        status = "ERROR",
                        error = throwable.message ?: throwable::class.java.simpleName,
                        attributes = initialization?.diagnosticAttributes().orEmpty(),
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
        SAMPLE_RATE_CANDIDATES.forEach { sampleRate ->
            val minBufferSize = try {
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            } catch (throwable: Throwable) {
                throw AudioCaptureInitializationException(
                    diagnostics = AudioCaptureInitialization.failure(
                        stage = AudioCaptureInitializationStage.MIN_BUFFER_QUERY,
                        failure = AudioCaptureInitializationFailure.MIN_BUFFER_QUERY_FAILED,
                        message = throwable.message,
                    ),
                    cause = throwable,
                )
            }
            if (minBufferSize <= 0) {
                throw AudioCaptureInitializationException(
                    AudioCaptureInitialization.failure(
                        stage = AudioCaptureInitializationStage.MIN_BUFFER_QUERY,
                        failure = AudioCaptureInitializationFailure.MIN_BUFFER_QUERY_FAILED,
                        platformCode = minBufferSize,
                        message = "AudioRecord.getMinBufferSize failed: $minBufferSize",
                    ),
                )
            }
            val readBufferSamples = sampleRate / CHUNKS_PER_SECOND
            val recorderBufferBytes = maxOf(minBufferSize * 2, readBufferSamples * BYTES_PER_SAMPLE * 2)
            val candidate = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recorderBufferBytes,
                )
            } catch (throwable: Throwable) {
                throw AudioCaptureInitializationException(
                    diagnostics = AudioCaptureInitialization.failure(
                        stage = AudioCaptureInitializationStage.AUDIO_RECORD_CONSTRUCTION,
                        failure = AudioCaptureInitializationFailure.AUDIO_RECORD_CONSTRUCTION_FAILED,
                        minBufferSizeBytes = minBufferSize,
                        recorderBufferSizeBytes = recorderBufferBytes,
                        message = throwable.message,
                    ),
                    cause = throwable,
                )
            }
            if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                return InitializedAudioRecord(
                    audioRecord = candidate,
                    sampleRate = sampleRate,
                    minBufferSizeBytes = minBufferSize,
                    recorderBufferSizeBytes = recorderBufferBytes,
                    readBufferSamples = readBufferSamples,
                )
            }
            val initialization = AudioCaptureInitialization.failure(
                stage = AudioCaptureInitializationStage.AUDIO_RECORD_INITIALIZATION,
                failure = AudioCaptureInitializationFailure.AUDIO_RECORD_UNINITIALIZED,
                minBufferSizeBytes = minBufferSize,
                recorderBufferSizeBytes = recorderBufferBytes,
                audioRecordState = candidate.state,
                message = "AudioRecord remained uninitialized",
            )
            candidate.release()
            throw AudioCaptureInitializationException(initialization)
        }
        error("Strict 16 kHz mono PCM16 capture configuration is missing")
    }

    private data class InitializedAudioRecord(
        val audioRecord: AudioRecord,
        val sampleRate: Int,
        val minBufferSizeBytes: Int,
        val recorderBufferSizeBytes: Int,
        val readBufferSamples: Int,
    )

    private class AudioCaptureInitializationException(
        val diagnostics: AudioCaptureInitialization,
        cause: Throwable? = null,
    ) : IllegalStateException(
        diagnostics.message ?: diagnostics.failure?.name ?: "Audio capture initialization failed",
        cause,
    )

    private companion object {
        val SAMPLE_RATE_CANDIDATES = listOf(16_000)
        const val CHUNKS_PER_SECOND = 10
        const val BYTES_PER_SAMPLE = 2
    }
}
