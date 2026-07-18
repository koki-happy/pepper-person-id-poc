package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class HeadPoseGuidanceTest {
    private val ranges = FacePoseRanges(
        frontYawDegrees = 8f,
        frontPitchDegrees = 8f,
        sideMinimumYawDegrees = 18f,
        sideMaximumYawDegrees = 32f,
    )

    @Test
    fun poseRanges_matchConfiguredFrontLeftAndRightBands() {
        assertThat(ranges.matches(RegistrationPose.FRONT, HeadPose(8f, -8f, 0f))).isTrue()
        assertThat(ranges.matches(RegistrationPose.LEFT, HeadPose(-18f, 2f, 0f))).isTrue()
        assertThat(ranges.matches(RegistrationPose.RIGHT, HeadPose(32f, -2f, 0f))).isTrue()
        assertThat(ranges.matches(RegistrationPose.FRONT, HeadPose(9f, 0f, 0f))).isFalse()
        assertThat(ranges.matches(RegistrationPose.LEFT, HeadPose(-12f, 0f, 0f))).isFalse()
    }

    @Test
    fun guidance_movesTowardTargetAndCorrectsOvershoot() {
        assertThat(ranges.guidance(RegistrationPose.FRONT, HeadPose(0f, 12f, 0f)))
            .isEqualTo("もう少し下を向いてください")
        assertThat(ranges.guidance(RegistrationPose.FRONT, HeadPose(0f, -12f, 0f)))
            .isEqualTo("もう少し上を向いてください")
        assertThat(ranges.guidance(RegistrationPose.LEFT, HeadPose(0f, 0f, 0f)))
            .isEqualTo("もう少し左を向いてください")
        assertThat(ranges.guidance(RegistrationPose.LEFT, HeadPose(-40f, 0f, 0f)))
            .isEqualTo("左を向きすぎです。もう少し右を向いてください")
        assertThat(ranges.guidance(RegistrationPose.RIGHT, HeadPose(0f, 0f, 0f)))
            .isEqualTo("もう少し右を向いてください")
        assertThat(ranges.guidance(RegistrationPose.RIGHT, HeadPose(40f, 0f, 0f)))
            .isEqualTo("右を向きすぎです。もう少し左を向いてください")
        assertThat(ranges.guidance(RegistrationPose.FRONT, HeadPose(-12f, 0f, 0f)))
            .isEqualTo("もう少し右を向いてください")
        assertThat(ranges.guidance(RegistrationPose.FRONT, HeadPose(12f, 0f, 0f)))
            .isEqualTo("もう少し左を向いてください")
    }

    @Test
    fun smoother_usesMedianOfRecentSamples() {
        val smoother = HeadPoseSmoother(sampleCount = 5)

        listOf(0f, 2f, 100f, 4f, 6f).forEach { yaw ->
            smoother.add("face-1", HeadPose(yaw, yaw / 2f, 0f))
        }

        assertThat(smoother.current("face-1")!!.yawDegrees).isEqualTo(4f)
        assertThat(smoother.current("face-1")!!.pitchDegrees).isEqualTo(2f)
    }

    @Test
    fun stability_leavingRangeOrChangingTrackResetsContinuousHold() {
        val tracker = PoseStabilityTracker(stableDurationMillis = 1_000L)
        val pose = HeadPose(0f, 0f, 0f)

        assertThat(tracker.update("face-1", inRange = true, nowMillis = 100L).completed).isFalse()
        assertThat(tracker.update("face-1", inRange = true, nowMillis = 900L).progressMillis).isEqualTo(800L)
        assertThat(tracker.update("face-1", inRange = false, nowMillis = 950L).progressMillis).isEqualTo(0L)
        assertThat(tracker.update("face-1", inRange = true, nowMillis = 1_000L).completed).isFalse()
        assertThat(tracker.update("face-2", inRange = true, nowMillis = 1_900L).progressMillis).isEqualTo(0L)
        assertThat(tracker.update("face-2", inRange = true, nowMillis = 2_900L).completed).isTrue()
        assertThat(pose.yawDegrees).isEqualTo(0f)
    }
}
