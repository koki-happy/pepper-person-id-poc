package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.face.FaceIdentifier
import com.example.pepper_person_id_poc.domain.face.AnonymousFaceClusterer
import com.example.pepper_person_id_poc.domain.face.AnonymousFaceResult
import com.example.pepper_person_id_poc.domain.face.FaceIdentityStatus
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
    private val anonymousLearningEnabled: Boolean = false,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val onBenchmarkEvent: (BenchmarkEvent) -> Unit = {},
    private val onDeleteAllBenchmarkEvents: () -> Unit = {},
) {
    private val anonymousClusterer = AnonymousFaceClusterer(settings.faceThreshold)
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
    private val activeIdentificationTrackIds = AtomicReference<Set<String>>(emptySet())
    private val sampledTrackIds = linkedSetOf<String>()
    private val saturatedLearningTrackIds = linkedSetOf<String>()
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
        val trackIds = observations.mapTo(linkedSetOf()) { it.trackId }
        if (trackIds.isEmpty()) {
            sampledTrackIds.clear()
            saturatedLearningTrackIds.clear()
            pendingIdentificationTrackIds.set(emptySet())
            activeIdentificationTrackIds.set(emptySet())
            mutableState.value = mutableState.value.copy(
                results = emptyList(),
                anonymousResults = emptyList(),
                identificationPending = false,
                identificationMessage = "顔をカメラに映してください",
            )
            return emptySet()
        }
        activeIdentificationTrackIds.set(trackIds)
        sampledTrackIds.retainAll(trackIds)
        saturatedLearningTrackIds.retainAll(trackIds)
        val embeddingTrackIds = if (anonymousLearningEnabled) {
            trackIds.filterTo(linkedSetOf()) { it !in saturatedLearningTrackIds }
        } else {
            trackIds.filterTo(linkedSetOf()) { it !in sampledTrackIds }
        }
        if (embeddingTrackIds.isEmpty()) {
            mutableState.value = mutableState.value.copy(
                results = mutableState.value.results.filter { it.trackId in trackIds },
                anonymousResults = mutableState.value.anonymousResults.filter { it.trackId in trackIds },
                identificationMessage = if (anonymousLearningEnabled) {
                    "${trackIds.size}人をtrackIdで追跡中（学習上限20サンプル到達）"
                } else {
                    "${trackIds.size}人をtrackIdで追跡中（再サンプルなし）"
                },
                identificationPending = false,
            )
            return emptySet()
        }
        identificationRequestedAtMillis.set(clockMillis())
        pendingIdentificationTrackIds.set(embeddingTrackIds)
        mutableState.value = mutableState.value.copy(
            identificationMessage = "${embeddingTrackIds.size}人をリアルタイム識別中",
            identificationPending = true,
        )
        return embeddingTrackIds
    }

    @Synchronized
    fun onFeatureObservations(observations: List<FaceFeatureObservation>) {
        if (observations.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(
                lastEmbeddingAverageTimeMillis = observations.sumOf { it.embeddingTimeMillis } / observations.size,
                lastEmbeddingMaximumTimeMillis = observations.maxOf { it.embeddingTimeMillis },
                lastEmbeddingFaceCount = observations.size,
            )
        }
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
        if (!anonymousLearningEnabled) {
            sampledTrackIds.addAll(requestedObservations.map { it.trackId })
        }
        val profiles = mutableState.value.profiles
        val rawResultsWithComparisonMillis = requestedObservations.map { observation ->
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
            result to ((System.nanoTime() - startedAtNanos) / 1_000_000L)
        }
        val comparisonMillisByTrackId = rawResultsWithComparisonMillis.associate { (result, millis) ->
            result.trackId to millis
        }
        val activeTrackIds = activeIdentificationTrackIds.get()
        val retainedResults = mutableState.value.results.filter {
            it.trackId in activeTrackIds && it.trackId !in identificationTrackIds
        }
        val mergedResults = enforceUniqueRegisteredPersonIds(
            retainedResults + rawResultsWithComparisonMillis.map { it.first },
        )
        val results = mergedResults.filter { it.trackId in identificationTrackIds }
        results.forEach { result ->
            logIdentification(result, comparisonMillisByTrackId[result.trackId] ?: 0L)
        }
        val resultByTrackId = results.associateBy { it.trackId }
        val reservedAnonymousIds = mutableState.value.anonymousResults
            .filter { it.trackId in activeTrackIds && it.trackId !in identificationTrackIds }
            .mapTo(linkedSetOf()) { it.anonymousId }
        val updatedAnonymousResults = if (!anonymousLearningEnabled) emptyList() else requestedObservations.mapNotNull { observation ->
            if (resultByTrackId[observation.trackId]?.status != FaceIdentityStatus.UNKNOWN) return@mapNotNull null
            anonymousClusterer.identify(
                observation.trackId,
                observation.embedding,
                reservedAnonymousIds,
            ).also { result ->
                reservedAnonymousIds += result.anonymousId
                if (result.isAtSampleLimit) saturatedLearningTrackIds += result.trackId
                onBenchmarkEvent(
                    BenchmarkEvent(
                        event = "anonymous_face_identification",
                        timestampMillis = clockMillis(),
                        durationMillis = observation.embeddingTimeMillis,
                        status = if (result.isNewCluster) "NEW_CLUSTER" else "MATCHED",
                        attributes = mapOf(
                            "model" to faceModelName,
                            "trackId" to result.trackId,
                            "anonymousId" to result.anonymousId,
                            "score" to result.score.toString(),
                            "threshold" to result.threshold.toString(),
                            "clusterCount" to anonymousClusterer.clusterCount.toString(),
                        ),
                    ),
                )
            }
        }
        val retainedAnonymousResults = mutableState.value.anonymousResults.filter {
            it.trackId in activeTrackIds && it.trackId !in identificationTrackIds
        }
        val anonymousResults = retainedAnonymousResults + updatedAnonymousResults
        mutableState.value = mutableState.value.copy(
            results = mergedResults,
            anonymousResults = anonymousResults,
            anonymousClusterCount = anonymousClusterer.clusterCount,
            profiles = profiles,
            identificationMessage = "${mergedResults.size}人を識別しました（リアルタイム更新）",
            identificationPending = false,
            error = null,
        )
    }

    private fun enforceUniqueRegisteredPersonIds(results: List<FaceIdentityResult>): List<FaceIdentityResult> {
        val winningTrackByPersonId = results
            .filter { it.status == FaceIdentityStatus.IDENTIFIED && it.personId != null }
            .groupBy { checkNotNull(it.personId) }
            .mapValues { (_, matches) -> matches.maxBy { it.score ?: Float.NEGATIVE_INFINITY }.trackId }
        return results.map { result ->
            val personId = result.personId
            if (personId != null && winningTrackByPersonId[personId] != result.trackId) {
                result.copy(
                    status = FaceIdentityStatus.UNKNOWN,
                    personId = null,
                    displayName = null,
                )
            } else {
                result
            }
        }
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
                anonymousClusterer.reset()
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

    @Synchronized
    fun resetAnonymousSession() {
        anonymousClusterer.reset()
        mutableState.value = mutableState.value.copy(
            anonymousResults = emptyList(),
            anonymousClusterCount = 0,
            identificationMessage = "未登録人物の一時IDをリセットしました",
        )
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
    val anonymousResults: List<AnonymousFaceResult> = emptyList(),
    val anonymousClusterCount: Int = 0,
    val registrationTarget: RegistrationPose? = null,
    val completedRegistrationPoses: Set<RegistrationPose> = emptySet(),
    val poseProgressMillis: Long = 0L,
    val currentHeadPose: HeadPose? = null,
    val visibleFaceCount: Int = 0,
    val registrationMessage: String? = null,
    val identificationMessage: String? = null,
    val identificationPending: Boolean = false,
    val embeddingModelReady: Boolean = false,
    val lastEmbeddingAverageTimeMillis: Long? = null,
    val lastEmbeddingMaximumTimeMillis: Long? = null,
    val lastEmbeddingFaceCount: Int = 0,
    val error: String? = null,
)
