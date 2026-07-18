package com.example.pepper_person_id_poc.domain.face

import kotlin.math.abs

data class HeadPose(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
) {
    init {
        require(yawDegrees.isFinite() && pitchDegrees.isFinite() && rollDegrees.isFinite())
    }
}

data class FacePoseObservation(
    val trackId: String,
    val headPose: HeadPose?,
)

enum class RegistrationPose(val displayName: String) {
    FRONT("正面"),
    LEFT("左"),
    RIGHT("右"),
}

data class FacePoseRanges(
    val frontYawDegrees: Float,
    val frontPitchDegrees: Float,
    val sideMinimumYawDegrees: Float,
    val sideMaximumYawDegrees: Float,
) {
    init {
        require(frontYawDegrees > 0f && frontPitchDegrees > 0f)
        require(sideMinimumYawDegrees > 0f && sideMaximumYawDegrees > sideMinimumYawDegrees)
    }

    fun matches(target: RegistrationPose, pose: HeadPose): Boolean = when (target) {
        RegistrationPose.FRONT ->
            abs(pose.yawDegrees) <= frontYawDegrees && abs(pose.pitchDegrees) <= frontPitchDegrees
        RegistrationPose.LEFT ->
            pose.yawDegrees in -sideMaximumYawDegrees..-sideMinimumYawDegrees &&
                abs(pose.pitchDegrees) <= frontPitchDegrees
        RegistrationPose.RIGHT ->
            pose.yawDegrees in sideMinimumYawDegrees..sideMaximumYawDegrees &&
                abs(pose.pitchDegrees) <= frontPitchDegrees
    }

    fun guidance(target: RegistrationPose, pose: HeadPose?): String {
        if (pose == null) return "顔を検出できる位置に移動してください"
        if (pose.pitchDegrees > frontPitchDegrees) return "もう少し下を向いてください"
        if (pose.pitchDegrees < -frontPitchDegrees) return "もう少し上を向いてください"
        if (matches(target, pose)) return "その向きで保持してください"

        return when (target) {
            RegistrationPose.FRONT -> if (pose.yawDegrees < -frontYawDegrees) {
                "もう少し右を向いてください"
            } else {
                "もう少し左を向いてください"
            }
            RegistrationPose.LEFT -> if (pose.yawDegrees > -sideMinimumYawDegrees) {
                "もう少し左を向いてください"
            } else {
                "左を向きすぎです。もう少し右を向いてください"
            }
            RegistrationPose.RIGHT -> if (pose.yawDegrees < sideMinimumYawDegrees) {
                "もう少し右を向いてください"
            } else {
                "右を向きすぎです。もう少し左を向いてください"
            }
        }
    }
}

class HeadPoseSmoother(
    private val sampleCount: Int,
) {
    init {
        require(sampleCount > 0)
    }

    private val samplesByTrack = mutableMapOf<String, ArrayDeque<HeadPose>>()

    fun add(trackId: String, pose: HeadPose): HeadPose {
        val samples = samplesByTrack.getOrPut(trackId, ::ArrayDeque)
        samples.addLast(pose)
        while (samples.size > sampleCount) samples.removeFirst()
        return samples.median()
    }

    fun current(trackId: String): HeadPose? = samplesByTrack[trackId]?.takeIf { it.isNotEmpty() }?.median()

    fun retainOnly(trackId: String?) {
        if (trackId == null) samplesByTrack.clear() else samplesByTrack.keys.retainAll(setOf(trackId))
    }

    fun clear() = samplesByTrack.clear()

    private fun Collection<HeadPose>.median(): HeadPose = HeadPose(
        yawDegrees = map(HeadPose::yawDegrees).median(),
        pitchDegrees = map(HeadPose::pitchDegrees).median(),
        rollDegrees = map(HeadPose::rollDegrees).median(),
    )

    private fun List<Float>.median(): Float {
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
    }
}

class PoseStabilityTracker(
    private val stableDurationMillis: Long,
) {
    init {
        require(stableDurationMillis > 0L)
    }

    private var trackId: String? = null
    private var enteredAtMillis: Long? = null
    private var completionReported = false

    fun update(trackId: String, inRange: Boolean, nowMillis: Long): PoseStabilityUpdate {
        if (this.trackId != trackId) {
            this.trackId = trackId
            enteredAtMillis = null
            completionReported = false
        }
        if (!inRange) {
            enteredAtMillis = null
            completionReported = false
            return PoseStabilityUpdate(0L, false)
        }
        val entered = enteredAtMillis ?: nowMillis.also { enteredAtMillis = it }
        val progress = (nowMillis - entered).coerceIn(0L, stableDurationMillis)
        val completed = progress >= stableDurationMillis && !completionReported
        if (completed) completionReported = true
        return PoseStabilityUpdate(progress, completed)
    }

    fun reset() {
        trackId = null
        enteredAtMillis = null
        completionReported = false
    }
}

data class PoseStabilityUpdate(
    val progressMillis: Long,
    val completed: Boolean,
)
