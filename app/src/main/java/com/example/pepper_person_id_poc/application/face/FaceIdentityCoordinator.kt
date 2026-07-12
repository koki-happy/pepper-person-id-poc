package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.FaceIdentityResult
import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FaceIdentityCoordinator(
    private val personRepository: PersonRepository,
    private val faceIdentifier: FaceIdentifier,
    private val faceThreshold: Float,
    private val faceModelName: String,
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
) {
    private val pendingRegistration = AtomicReference<RegistrationRequest?>(null)
    private val mutableState = MutableStateFlow(
        FaceIdentityUiState(profiles = personRepository.getAllForFaceModel(faceModelName)),
    )
    val state: StateFlow<FaceIdentityUiState> = mutableState.asStateFlow()

    fun onFeatureObservations(observations: List<FaceFeatureObservation>) {
        val profilesBeforeRegistration = personRepository.getAllForFaceModel(faceModelName)
        val identityResults = observations.map { observation ->
            val startedAtNanos = System.nanoTime()
            faceIdentifier.identify(
                trackId = observation.trackId,
                embedding = observation.embedding,
                profiles = profilesBeforeRegistration,
                threshold = faceThreshold,
                processingTimeMillis = observation.embeddingTimeMillis +
                    (System.nanoTime() - startedAtNanos) / 1_000_000L,
            ).also { result ->
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_identification",
                        timestampMillis = System.currentTimeMillis(),
                        durationMillis = result.processingTimeMillis,
                        status = result.status.name,
                        attributes = buildMap {
                            put("model", faceModelName)
                            put("threshold", faceThreshold.toString())
                            result.score?.let { put("score", it.toString()) }
                            result.personId?.let { put("personId", it.value) }
                        },
                    ),
                )
            }
        }

        val request = pendingRegistration.get()
        if (request != null && observations.size == 1 && pendingRegistration.compareAndSet(request, null)) {
            runCatching {
                personRepository.addFaceEmbedding(
                    personId = request.personId,
                    displayName = request.displayName,
                    embedding = observations.single().embedding,
                    modelName = faceModelName,
                    registeredAtMillis = System.currentTimeMillis(),
                )
            }.onSuccess { profile ->
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "face_registration",
                        timestampMillis = System.currentTimeMillis(),
                        status = "SUCCESS",
                        attributes = mapOf(
                            "model" to faceModelName,
                            "personId" to profile.personId.value,
                            "faceSampleCount" to profile.faceSampleCount.toString(),
                        ),
                    ),
                )
                mutableState.value = FaceIdentityUiState(
                    results = identityResults,
                    profiles = personRepository.getAllForFaceModel(faceModelName),
                    registrationMessage =
                        "${profile.displayName} の顔サンプルを登録しました (${profile.faceSampleCount})",
                    embeddingModelReady = mutableState.value.embeddingModelReady,
                )
            }.onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    results = identityResults,
                    profiles = personRepository.getAllForFaceModel(faceModelName),
                    registrationMessage = null,
                    error = throwable.message ?: throwable::class.java.simpleName,
                )
            }
            return
        }

        mutableState.value = mutableState.value.copy(
            results = identityResults,
            profiles = profilesBeforeRegistration,
            registrationMessage = when {
                request == null -> mutableState.value.registrationMessage
                observations.isEmpty() -> "登録する顔を1人だけカメラに映してください"
                observations.size > 1 -> "複数の顔があります。登録する1人だけを映してください"
                else -> mutableState.value.registrationMessage
            },
            error = null,
        )
    }

    fun requestFaceRegistration(personIdText: String, displayNameText: String) {
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
            registrationMessage = "${request.displayName} の顔サンプル取得を待っています",
            error = null,
        )
    }

    fun deleteAllRegistrations() {
        runCatching(personRepository::deleteAll)
            .onSuccess {
                pendingRegistration.set(null)
                mutableState.value = FaceIdentityUiState(
                    profiles = emptyList(),
                    registrationMessage = "登録データをすべて削除しました",
                    embeddingModelReady = mutableState.value.embeddingModelReady,
                )
            }
            .onFailure { throwable ->
                mutableState.value = mutableState.value.copy(
                    error = throwable.message ?: throwable::class.java.simpleName,
                )
            }
    }

    fun reportEmbeddingError(throwable: Throwable) {
        mutableState.value = mutableState.value.copy(
            error = throwable.message ?: throwable::class.java.simpleName,
        )
    }

    fun reportEmbeddingReady() {
        mutableState.value = mutableState.value.copy(
            embeddingModelReady = true,
            error = null,
        )
    }

    private data class RegistrationRequest(
        val personId: PersonId,
        val displayName: String,
    )
}

data class FaceIdentityUiState(
    val results: List<FaceIdentityResult> = emptyList(),
    val profiles: List<PersonProfile> = emptyList(),
    val registrationMessage: String? = null,
    val embeddingModelReady: Boolean = false,
    val error: String? = null,
)
