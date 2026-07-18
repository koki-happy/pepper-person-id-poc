package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.FaceIdentityResult
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation
import com.example.pepper_person_id_poc.domain.face.FacePoseRanges
import com.example.pepper_person_id_poc.domain.face.HeadPose
import com.example.pepper_person_id_poc.domain.face.HeadPoseSmoother
import com.example.pepper_person_id_poc.domain.face.PoseStabilityTracker
import com.example.pepper_person_id_poc.domain.face.RegistrationPose
import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FaceIdentityCoordinator(
    private val personRepository: PersonRepository,
    private val faceIdentifier: FaceIdentifier,
    private val settings: PocSettings,
    private val faceModelName: String,
    private val realTimeIdentificationEnabled: Boolean = false,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
    private val onDeleteAllBenchmarkEvents: () -> Unit = {},
) {
    private val poseRanges = FacePoseRanges(
        frontYawDegrees = settings.faceFrontYawDegrees,
        frontPitchDegrees = settings.faceFrontPitchDegrees,
        sideMinimumYawDegrees = settings.faceSideMinimumYawDegrees,
        sideMaximumYawDegrees = settings.faceSideMaximumYawDegrees,
    )
    private val poseSmoother = HeadPoseSmoother(settings.faceSmoothingSampleCount)
    private val stabilityTracker = PoseStabilityTracker(settings.facePoseStableDurationMillis)
    private val registration = AtomicReference<RegistrationSession?>(null)
    private val pendingCapture = AtomicReference<CaptureRequest?>(null)
    private val pendingIdentificationTrackIds = AtomicReference<Set<String>>(emptySet())
    private val identificationRequestedAtMillis = AtomicLong(0L)
    private val mutableState = MutableStateFlow(
        FaceIdentityUiState(profiles = personRepository.getAllForFaceModel(faceModelName)),
    )
    val state: StateFlow<FaceIdentityUiState> = mutableState.asStateFlow()

    /**
     * Consumes lightweight pose observations and returns the track IDs whose embeddings are needed
     * in this frame. An empty set guarantees that the detector skips expensive embedding work.
     */
    @Synchronized
    fun onPoseObservations(observations: List<FacePoseObservation>): Set<String> {
        return onFaceAnalysis(observations.size, observations)
    }

    @Synchronized
    fun onFaceAnalysis(detectedFaceCount: Int, observations: List<FacePoseObservation>): Set<String> {
        require(detectedFaceCount >= 0)
        val activeRegistration = registration.get()
        if (activeRegistration != null) {
            return handleRegistrationPoses(activeRegistration, detectedFaceCount, observations)
        }

        val single = observations.singleOrNull().takeIf { detectedFaceCount == 1 }
        val smoothedPose = single?.headPose?.let { poseSmoother.add(single.trackId, it) }
        if (single == null) poseSmoother.clear() else poseSmoother.retainOnly(single.trackId)
        mutableState.value = mutableState.value.copy(
            visibleFaceCount = detectedFaceCount,
            currentHeadPose = smoothedPose,
        )

        if (!realTimeIdentificationEnabled) return emptySet()
        val profiles = mutableState.value.profiles
        if (profiles.isEmpty()) {
            pendingIdentificationTrackIds.set(emptySet())
            mutableState.value = mutableState.value.copy(
                results = emptyList(),
                identificationPending = false,
                identificationMessage = "登録人物がいません",
            )
            return emptySet()
        }
        val trackIds = observations.mapTo(linkedSetOf()) { it.trackId }
        if (trackIds.isEmpty()) {
            pendingIdentificationTrackIds.set(emptySet())
            mutableState.value = mutableState.value.copy(
                results = emptyList(),
                identificationPending = false,
                identificationMessage = "顔をカメラに映してください",
            )
            return emptySet()
        }
        identificationRequestedAtMillis.set(clockMillis())
        pendingIdentificationTrackIds.set(trackIds)
        mutableState.value = mutableState.value.copy(
            identificationMessage = "${trackIds.size}人をリアルタイム識別中",
            identificationPending = true,
        )
        return trackIds
    }

    @Synchronized
    fun onFeatureObservations(observations: List<FaceFeatureObservation>) {
        val capture = pendingCapture.getAndSet(null)
        if (capture != null) {
            val observation = observations.firstOrNull { it.trackId == capture.trackId }
            if (observation == null) {
                stabilityTracker.reset()
                poseSmoother.clear()
                mutableState.value = mutableState.value.copy(error = "顔特徴量を取得できませんでした。姿勢をやり直してください")
                return
            }
            saveRegistrationCapture(capture, observation)
            return
        }

        val identificationTrackIds = pendingIdentificationTrackIds.getAndSet(emptySet())
        if (identificationTrackIds.isEmpty()) return
        val requestedObservations = observations.filter { it.trackId in identificationTrackIds }
        if (requestedObservations.isEmpty()) {
            mutableState.value = mutableState.value.copy(
                results = emptyList(),
                identificationPending = false,
                identificationMessage = "顔特徴量を取得できませんでした",
            )
            return
        }
        val profiles = mutableState.value.profiles
        val results = requestedObservations.map { observation ->
            val startedAtNanos = System.nanoTime()
            val result = faceIdentifier.identify(
                trackId = observation.trackId,
                embedding = observation.embedding,
                profiles = profiles,
                threshold = settings.faceThreshold,
                minimumMargin = settings.faceMargin,
                processingTimeMillis = observation.embeddingTimeMillis +
                    (System.nanoTime() - startedAtNanos) / 1_000_000L,
            )
            val comparisonMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
            logIdentification(result, comparisonMillis)
            result
        }
        mutableState.value = mutableState.value.copy(
            results = results,
            profiles = profiles,
            identificationMessage = "${results.size}人を識別しました（リアルタイム更新）",
            identificationPending = false,
            error = null,
        )
    }

    private fun logIdentification(result: FaceIdentityResult, comparisonMillis: Long) {
        val totalRequestMillis = (clockMillis() - identificationRequestedAtMillis.get()).coerceAtLeast(0L)
        onBenchmarkEvent(
            BenchmarkEvent(
                event = "face_identification",
                timestampMillis = clockMillis(),
                durationMillis = result.processingTimeMillis,
                status = result.status.name,
                attributes = buildMap {
                    put("model", faceModelName)
                    put("threshold", settings.faceThreshold.toString())
                    put("minimumMargin", settings.faceMargin.toString())
                    put("comparisonMillis", comparisonMillis.toString())
                    put("requestTotalMillis", totalRequestMillis.toString())
                    result.score?.let { put("bestScore", it.toString()) }
                    result.secondScore?.let { put("secondScore", it.toString()) }
                    result.margin?.let { put("margin", it.toString()) }
                    result.personId?.let { put("personId", it.value) }
                },
            ),
        )
    }

    @Synchronized
    fun requestFaceRegistration(personIdText: String, displayNameText: String) {
        val session = runCatching {
            RegistrationSession(
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
        registration.set(session)
        pendingCapture.set(null)
        pendingIdentificationTrackIds.set(emptySet())
        poseSmoother.clear()
        stabilityTracker.reset()
        mutableState.value = mutableState.value.copy(
            results = emptyList(),
            registrationTarget = RegistrationPose.FRONT,
            completedRegistrationPoses = emptySet(),
            poseProgressMillis = 0L,
            registrationMessage = "${session.displayName}: 正面を向いてください",
            identificationMessage = null,
            identificationPending = false,
            error = null,
        )
    }

    @Synchronized
    fun cancelFaceRegistration() {
        if (registration.get() == null && pendingCapture.get() == null) return
        cancelPendingWork()
        mutableState.value = mutableState.value.copy(
            registrationTarget = null,
            completedRegistrationPoses = emptySet(),
            poseProgressMillis = 0L,
            registrationMessage = "顔登録をキャンセルしました。特徴量は保存していません",
        )
    }

    @Synchronized
    fun deletePerson(personId: PersonId) {
        runCatching { personRepository.deletePerson(personId) }
            .onSuccess {
                mutableState.value = mutableState.value.copy(
                    profiles = personRepository.getAllForFaceModel(faceModelName),
                    registrationMessage = "${personId.value} の登録データを削除しました",
                    error = null,
                )
            }
            .onFailure { mutableState.value = mutableState.value.copy(error = it.message ?: "削除に失敗しました") }
    }

    @Synchronized
    fun deleteAllRegistrations() {
        runCatching(personRepository::deleteAll)
            .onSuccess {
                onDeleteAllBenchmarkEvents()
                cancelPendingWork()
                mutableState.value = FaceIdentityUiState(
                    profiles = emptyList(),
                    registrationMessage = "顔・声の登録データと評価ログをすべて削除しました",
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
        mutableState.value = mutableState.value.copy(error = throwable.message ?: throwable::class.java.simpleName)
    }

    fun reportEmbeddingReady() {
        mutableState.value = mutableState.value.copy(embeddingModelReady = true, error = null)
    }

    private fun handleRegistrationPoses(
        session: RegistrationSession,
        detectedFaceCount: Int,
        observations: List<FacePoseObservation>,
    ): Set<String> {
        if (pendingCapture.get() != null) return emptySet()
        val single = observations.singleOrNull().takeIf { detectedFaceCount == 1 }
        if (single == null) {
            poseSmoother.clear()
            stabilityTracker.reset()
            mutableState.value = mutableState.value.copy(
                visibleFaceCount = detectedFaceCount,
                currentHeadPose = null,
                poseProgressMillis = 0L,
                registrationMessage = if (detectedFaceCount == 0) {
                    "登録する顔を1人だけカメラに映してください"
                } else {
                    "複数の顔があります。登録する1人だけを映してください"
                },
            )
            return emptySet()
        }
        val rawPose = single.headPose
        if (rawPose == null) {
            poseSmoother.clear()
            stabilityTracker.reset()
            mutableState.value = mutableState.value.copy(
                visibleFaceCount = 1,
                currentHeadPose = null,
                poseProgressMillis = 0L,
                registrationMessage = "顔は検出しましたが姿勢を推定できません。正面へ戻してください",
            )
            return emptySet()
        }
        poseSmoother.retainOnly(single.trackId)
        val smoothed = poseSmoother.add(single.trackId, rawPose)
        val inRange = poseRanges.matches(session.target, smoothed)
        val stability = stabilityTracker.update(single.trackId, inRange, clockMillis())
        mutableState.value = mutableState.value.copy(
            visibleFaceCount = 1,
            currentHeadPose = smoothed,
            registrationTarget = session.target,
            poseProgressMillis = stability.progressMillis,
            registrationMessage = if (inRange) {
                "${session.displayName}: ${session.target.displayName}を維持してください"
            } else {
                "${session.displayName}: ${session.target.displayName}の範囲へ動いてください"
            },
            error = null,
        )
        if (!stability.completed) return emptySet()
        pendingCapture.set(CaptureRequest(single.trackId, session.target))
        mutableState.value = mutableState.value.copy(registrationMessage = "${session.target.displayName}を保存しています")
        return setOf(single.trackId)
    }

    private fun saveRegistrationCapture(capture: CaptureRequest, observation: FaceFeatureObservation) {
        val session = registration.get() ?: return
        val completed = session.completed + capture.pose
        val capturedEmbeddings = session.capturedEmbeddings + (capture.pose to observation.embedding.copyOf())
        val next = RegistrationPose.entries.firstOrNull { it !in completed }
        runCatching {
            if (next == null) {
                personRepository.replaceFaceEmbeddings(
                    personId = session.personId,
                    displayName = session.displayName,
                    embeddings = RegistrationPose.entries.map { capturedEmbeddings.getValue(it) },
                    modelName = faceModelName,
                    registeredAtMillis = clockMillis(),
                )
            } else {
                null
            }
        }.onSuccess { profile ->
            onBenchmarkEvent(
                BenchmarkEvent(
                    event = "face_registration",
                    timestampMillis = clockMillis(),
                    durationMillis = observation.embeddingTimeMillis,
                    status = "SUCCESS",
                    attributes = mapOf(
                        "model" to faceModelName,
                        "personId" to session.personId.value,
                        "pose" to capture.pose.name,
                        "faceSampleCount" to completed.size.toString(),
                    ),
                ),
            )
            stabilityTracker.reset()
            poseSmoother.clear()
            if (next == null) {
                registration.set(null)
                mutableState.value = mutableState.value.copy(
                    profiles = personRepository.getAllForFaceModel(faceModelName),
                    registrationTarget = null,
                    completedRegistrationPoses = completed,
                    poseProgressMillis = 0L,
                    registrationMessage = "${checkNotNull(profile).displayName} の顔登録が完了しました (正面・左・右)",
                    error = null,
                )
            } else {
                registration.set(
                    session.copy(target = next, completed = completed, capturedEmbeddings = capturedEmbeddings),
                )
                mutableState.value = mutableState.value.copy(
                    registrationTarget = next,
                    completedRegistrationPoses = completed,
                    poseProgressMillis = 0L,
                    registrationMessage = "${session.displayName}: 次は${next.displayName}を向いてください",
                    error = null,
                )
            }
        }.onFailure { throwable ->
            stabilityTracker.reset()
            mutableState.value = mutableState.value.copy(
                poseProgressMillis = 0L,
                error = throwable.message ?: throwable::class.java.simpleName,
            )
        }
    }

    private fun cancelPendingWork() {
        registration.set(null)
        pendingCapture.set(null)
        pendingIdentificationTrackIds.set(emptySet())
        identificationRequestedAtMillis.set(0L)
        poseSmoother.clear()
        stabilityTracker.reset()
    }

    private data class RegistrationSession(
        val personId: PersonId,
        val displayName: String,
        val target: RegistrationPose = RegistrationPose.FRONT,
        val completed: Set<RegistrationPose> = emptySet(),
        val capturedEmbeddings: Map<RegistrationPose, FloatArray> = emptyMap(),
    )

    private data class CaptureRequest(
        val trackId: String,
        val pose: RegistrationPose,
    )
}

data class FaceIdentityUiState(
    val results: List<FaceIdentityResult> = emptyList(),
    val profiles: List<PersonProfile> = emptyList(),
    val registrationTarget: RegistrationPose? = null,
    val completedRegistrationPoses: Set<RegistrationPose> = emptySet(),
    val poseProgressMillis: Long = 0L,
    val currentHeadPose: HeadPose? = null,
    val visibleFaceCount: Int = 0,
    val registrationMessage: String? = null,
    val identificationMessage: String? = null,
    val identificationPending: Boolean = false,
    val embeddingModelReady: Boolean = false,
    val error: String? = null,
)
