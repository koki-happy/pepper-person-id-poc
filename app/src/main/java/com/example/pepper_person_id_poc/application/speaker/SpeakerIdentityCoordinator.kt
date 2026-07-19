package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.example.pepper_person_id_poc.domain.speaker.AnonymousSpeakerClusterer
import com.example.pepper_person_id_poc.domain.speaker.AnonymousSpeakerResult
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentifier
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentityResult
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentityStatus
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SpeakerScreenMode {
    REGISTRATION,
    IDENTIFICATION,
    ANONYMOUS_IDENTIFICATION,
}

class SpeakerIdentityCoordinator(
    private val mode: SpeakerScreenMode,
    private val personRepository: PersonRepository,
    private val embeddingEngine: SpeakerEmbeddingEngine,
    private val speakerIdentifier: SpeakerIdentifier,
    speakerThreshold: Float,
    private val benchmarkLogger: BenchmarkLogger,
    private val speakerMargin: Float = 0f,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inferenceMutex = Mutex()
    private val pendingRegistration = AtomicReference<RegistrationRequest?>(null)
    private val anonymousClusterer = AnonymousSpeakerClusterer(speakerThreshold)
    private val threshold = speakerThreshold
    private val mutableState = MutableStateFlow(
        SpeakerIdentityUiState(
            profiles = personRepository.getAllForSpeakerModel(embeddingEngine.modelName),
            modelName = embeddingEngine.modelName,
        ),
    )
    val state: StateFlow<SpeakerIdentityUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            val started = System.nanoTime()
            runCatching(embeddingEngine::prepare)
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        modelReady = true,
                        modelDimension = embeddingEngine.embeddingDimension,
                        error = null,
                    )
                    log("speaker_embedding_model_init", elapsedMillis(started), "SUCCESS")
                }
                .onFailure { throwable ->
                    mutableState.value = mutableState.value.copy(
                        error = throwable.message ?: throwable::class.java.simpleName,
                    )
                    log("speaker_embedding_model_init", elapsedMillis(started), "ERROR", throwable)
                }
        }
    }

    fun onUtterance(utterance: PcmUtterance) {
        val utteranceId = "utterance-${utterance.startedAtMillis}"
        if (!utterance.sufficientForSpeakerIdentification) {
            mutableState.value = mutableState.value.copy(
                processing = false,
                lastUtteranceStartedAtMillis = utterance.startedAtMillis,
                lastUtteranceEndedAtMillis = utterance.endedAtMillis,
                result = insufficientResult(utteranceId),
                anonymousResult = null,
                error = null,
            )
            return
        }
        mutableState.value = mutableState.value.copy(processing = true, error = null)
        scope.launch {
            inferenceMutex.withLock { processUtterance(utteranceId, utterance) }
        }
    }

    fun requestRegistration(personIdText: String, displayNameText: String) {
        val request = runCatching {
            RegistrationRequest(
                personId = PersonId(personIdText.trim()),
                displayName = displayNameText.trim().also { require(it.isNotBlank()) },
            )
        }.getOrElse { throwable ->
            mutableState.value = mutableState.value.copy(
                registrationMessage = null,
                error = throwable.message ?: "personIdと表示名を入力してください",
            )
            return
        }
        pendingRegistration.set(request)
        mutableState.value = mutableState.value.copy(
            registrationMessage = "${request.displayName} の1秒以上の発話を待っています",
            error = null,
        )
    }

    fun resetAnonymousSession() {
        anonymousClusterer.reset()
        mutableState.value = mutableState.value.copy(
            anonymousResult = null,
            anonymousClusterCount = 0,
        )
    }

    override fun close() {
        scope.cancel()
        embeddingEngine.close()
    }

    private fun processUtterance(utteranceId: String, utterance: PcmUtterance) {
        val started = System.nanoTime()
        runCatching { embeddingEngine.extract(utterance.pcm16, utterance.sampleRate) }
            .onSuccess { embedding ->
                val processingMillis = elapsedMillis(started)
                when (mode) {
                    SpeakerScreenMode.REGISTRATION -> register(embedding, processingMillis)
                    SpeakerScreenMode.IDENTIFICATION -> identify(utteranceId, embedding, processingMillis)
                    SpeakerScreenMode.ANONYMOUS_IDENTIFICATION -> identifyAnonymous(utteranceId, embedding)
                }
                mutableState.value = mutableState.value.copy(
                    processing = false,
                    lastUtteranceStartedAtMillis = utterance.startedAtMillis,
                    lastUtteranceEndedAtMillis = utterance.endedAtMillis,
                    inferenceTimeMillis = processingMillis,
                    error = null,
                )
                log("speaker_embedding", processingMillis, "SUCCESS")
            }
            .onFailure { throwable ->
                val status = if (throwable.message?.contains("INSUFFICIENT_AUDIO") == true) {
                    SpeakerIdentityStatus.INSUFFICIENT_AUDIO
                } else {
                    null
                }
                mutableState.value = mutableState.value.copy(
                    processing = false,
                    result = status?.let { insufficientResult(utteranceId) },
                    error = if (status == null) throwable.message ?: throwable::class.java.simpleName else null,
                )
                log("speaker_embedding", elapsedMillis(started), "ERROR", throwable)
            }
    }

    private fun register(embedding: FloatArray, processingMillis: Long) {
        val request = pendingRegistration.getAndSet(null)
        if (request == null) {
            mutableState.value = mutableState.value.copy(
                registrationMessage = "登録ボタンを押してから発話してください",
            )
            return
        }
        val profile = personRepository.addSpeakerEmbedding(
            personId = request.personId,
            displayName = request.displayName,
            embedding = embedding,
            modelName = embeddingEngine.modelName,
            registeredAtMillis = System.currentTimeMillis(),
        )
        mutableState.value = mutableState.value.copy(
            profiles = personRepository.getAllForSpeakerModel(embeddingEngine.modelName),
            registrationMessage = "${profile.displayName} の声サンプルを登録しました (${profile.speakerSampleCount})",
            inferenceTimeMillis = processingMillis,
        )
        log(
            event = "speaker_registration",
            durationMillis = processingMillis,
            status = "SUCCESS",
            attributes = mapOf(
                "personId" to profile.personId.value,
                "speakerSampleCount" to profile.speakerSampleCount.toString(),
            ),
        )
    }

    private fun identify(utteranceId: String, embedding: FloatArray, processingMillis: Long) {
        val result = speakerIdentifier.identify(
            utteranceId = utteranceId,
            embedding = embedding,
            profiles = personRepository.getAllForSpeakerModel(embeddingEngine.modelName),
            threshold = threshold,
            processingTimeMillis = processingMillis,
            minimumMargin = speakerMargin,
        )
        mutableState.value = mutableState.value.copy(result = result, anonymousResult = null)
        log(
            event = "speaker_identification",
            durationMillis = processingMillis,
            status = result.status.name,
            attributes = buildMap {
                result.personId?.let { put("personId", it.value) }
                result.bestCandidatePersonId?.let { put("bestCandidatePersonId", it.value) }
                result.score?.let { put("score", it.toString()) }
                result.secondBestCandidatePersonId?.let { put("secondBestCandidatePersonId", it.value) }
                result.secondBestScore?.let { put("secondBestScore", it.toString()) }
                result.margin?.let { put("margin", it.toString()) }
                if (result.unknownReasons.isNotEmpty()) {
                    put("unknownReasons", result.unknownReasons.joinToString(",") { it.name })
                }
                put("threshold", result.threshold.toString())
                put("minimumMargin", result.minimumMargin.toString())
            },
        )
    }

    private fun identifyAnonymous(utteranceId: String, embedding: FloatArray) {
        val result = anonymousClusterer.identify(utteranceId, embedding)
        mutableState.value = mutableState.value.copy(
            result = null,
            anonymousResult = result,
            anonymousClusterCount = anonymousClusterer.clusterCount,
        )
        log(
            event = "anonymous_speaker_identification",
            durationMillis = null,
            status = if (result.isNewCluster) "NEW_CLUSTER" else "MATCHED",
            attributes = mapOf(
                "anonymousSpeakerId" to result.anonymousSpeakerId,
                "score" to result.score.toString(),
                "threshold" to threshold.toString(),
                "clusterCount" to anonymousClusterer.clusterCount.toString(),
            ),
        )
    }

    private fun insufficientResult(utteranceId: String) = SpeakerIdentityResult(
        utteranceId = utteranceId,
        status = SpeakerIdentityStatus.INSUFFICIENT_AUDIO,
        personId = null,
        displayName = null,
        score = null,
        bestCandidatePersonId = null,
        threshold = threshold,
        processingTimeMillis = 0L,
        minimumMargin = speakerMargin,
    )

    private fun log(
        event: String,
        durationMillis: Long?,
        status: String,
        throwable: Throwable? = null,
        attributes: Map<String, String> = emptyMap(),
    ) {
        benchmarkLogger.append(
            BenchmarkEvent(
                event = event,
                timestampMillis = System.currentTimeMillis(),
                durationMillis = durationMillis,
                status = status,
                attributes = mapOf("model" to embeddingEngine.modelName) + attributes,
                error = throwable?.message,
            ),
        )
    }

    private fun elapsedMillis(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L

    private data class RegistrationRequest(val personId: PersonId, val displayName: String)
}

data class SpeakerIdentityUiState(
    val profiles: List<PersonProfile> = emptyList(),
    val modelName: String,
    val modelReady: Boolean = false,
    val modelDimension: Int? = null,
    val processing: Boolean = false,
    val inferenceTimeMillis: Long? = null,
    val lastUtteranceStartedAtMillis: Long? = null,
    val lastUtteranceEndedAtMillis: Long? = null,
    val result: SpeakerIdentityResult? = null,
    val anonymousResult: AnonymousSpeakerResult? = null,
    val anonymousClusterCount: Int = 0,
    val registrationMessage: String? = null,
    val error: String? = null,
)
