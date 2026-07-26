package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousClusterScore
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousIdentificationResult
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousPersistencePolicy
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import java.io.Closeable
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

class SpeakerIdentityCoordinator(
    private val repository: AnonymousSpeakerClusterRepository,
    private val embeddingEngine: SpeakerEmbeddingEngine,
    private val threshold: Float,
    private val maximumUpdateCount: Int,
    @Suppress("UNUSED_PARAMETER") private val benchmarkLogger: BenchmarkLogger,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SpeakerIdentityUiState(modelName = embeddingEngine.modelName))
    val state: StateFlow<SpeakerIdentityUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching { embeddingEngine.prepare() }
                .onSuccess {
                    mutableState.value = mutableState.value.copy(
                        modelReady = true,
                        modelDimension = embeddingEngine.embeddingDimension,
                        error = null,
                    )
                }
                .onFailure { mutableState.value = mutableState.value.copy(error = it.message) }
        }
    }

    fun onUtterance(utterance: PcmUtterance) {
        if (!utterance.sufficientForSpeakerIdentification) {
            mutableState.value = mutableState.value.copy(
                processing = false,
                lastUtteranceStartedAtMillis = utterance.startedAtMillis,
                lastUtteranceEndedAtMillis = utterance.endedAtMillis,
                error = "INSUFFICIENT_AUDIO",
            )
            return
        }
        mutableState.value = mutableState.value.copy(processing = true, error = null)
        scope.launch {
            mutex.withLock {
                val started = System.nanoTime()
                runCatching {
                    val embedding = embeddingEngine.extract(utterance.pcm16, utterance.sampleRate)
                    val modelSpaceId = ModelSpaceId(embeddingEngine.modelName)
                    val evaluation = repository.evaluate(
                        modelSpaceId = modelSpaceId,
                        embedding = embedding,
                        threshold = threshold,
                        minimumLead = 0f,
                    )
                    val (policy, operation) = AnonymousPersistencePolicy.evaluate(
                        decision = evaluation.decision,
                        createEligible = true,
                        updateEligible = true,
                    )
                    val selectedId = evaluation.candidates.firstOrNull { it.selected }?.anonymousId
                    val cluster = repository.apply(
                        operation = operation,
                        modelSpaceId = modelSpaceId,
                        embedding = embedding,
                        selectedAnonymousId = selectedId,
                        maximumUpdateCount = maximumUpdateCount,
                        nowElapsedRealtime = System.currentTimeMillis(),
                    )
                    AnonymousIdentificationResult(
                        anonymousId = cluster?.anonymousId ?: "unknown",
                        modelId = modelSpaceId.value,
                        bestExistingScore = evaluation.highestScore,
                        threshold = threshold,
                        isNewCluster = operation == PersistenceOperation.CREATE,
                        updateCount = cluster?.updateCount ?: 0,
                        maximumUpdateCount = maximumUpdateCount,
                        currentModelClusterCount = repository.getAll().count {
                            it.modelSpaceId == modelSpaceId &&
                                it.embeddingDimension == embedding.size
                        },
                        totalClusterCount = repository.count(),
                        candidateScores = evaluation.candidates.map {
                            AnonymousClusterScore(
                                anonymousId = it.anonymousId,
                                score = it.score,
                                selected = it.anonymousId == cluster?.anonymousId &&
                                    operation == PersistenceOperation.UPDATE,
                            )
                        },
                        evaluation = evaluation,
                        persistencePolicy = policy,
                        persistenceOperation = operation,
                    )
                }.onSuccess { result ->
                    mutableState.value = mutableState.value.copy(
                        processing = false,
                        result = result,
                        currentModelClusterCount = result.currentModelClusterCount,
                        totalClusterCount = result.totalClusterCount,
                        inferenceTimeMillis = (System.nanoTime() - started) / 1_000_000L,
                        lastUtteranceStartedAtMillis = utterance.startedAtMillis,
                        lastUtteranceEndedAtMillis = utterance.endedAtMillis,
                        error = null,
                    )
                }.onFailure {
                    mutableState.value = mutableState.value.copy(
                        processing = false,
                        error = it.message ?: it::class.java.simpleName,
                    )
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        embeddingEngine.close()
    }
}

data class SpeakerIdentityUiState(
    val modelName: String,
    val modelReady: Boolean = false,
    val modelDimension: Int? = null,
    val processing: Boolean = false,
    val result: AnonymousIdentificationResult? = null,
    val currentModelClusterCount: Int = 0,
    val totalClusterCount: Int = 0,
    val inferenceTimeMillis: Long? = null,
    val lastUtteranceStartedAtMillis: Long? = null,
    val lastUtteranceEndedAtMillis: Long? = null,
    val error: String? = null,
)
