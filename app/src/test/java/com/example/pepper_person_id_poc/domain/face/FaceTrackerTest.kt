package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FaceTrackerTest {
    private val tracker = FaceTracker(
        minimumIntersectionOverUnion = 0.3f,
        maximumMissedFrames = 1,
    )

    @Test
    fun overlappingDetection_keepsTrackId() {
        val first = tracker.update(listOf(box(0.10f, 0.10f, 0.40f, 0.40f)))
        val second = tracker.update(listOf(box(0.12f, 0.11f, 0.42f, 0.41f)))

        assertThat(first.single().trackId).isEqualTo("face-001")
        assertThat(second.single().trackId).isEqualTo("face-001")
    }

    @Test
    fun separateDetections_receiveSeparateTrackIds() {
        val detections = tracker.update(
            listOf(
                box(0.05f, 0.10f, 0.30f, 0.50f),
                box(0.60f, 0.10f, 0.90f, 0.50f),
            ),
        )

        assertThat(detections.map { it.trackId }).containsExactly("face-001", "face-002").inOrder()
    }

    @Test
    fun expiredTrack_isNotReused() {
        tracker.update(listOf(box(0.10f, 0.10f, 0.40f, 0.40f)))
        tracker.update(emptyList())
        tracker.update(emptyList())

        val reappeared = tracker.update(listOf(box(0.10f, 0.10f, 0.40f, 0.40f)))

        assertThat(reappeared.single().trackId).isEqualTo("face-002")
    }

    private fun box(left: Float, top: Float, right: Float, bottom: Float) =
        NormalizedBoundingBox(left, top, right, bottom)
}
