package com.example.pepper_person_id_poc.infrastructure.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceLandmarkAlignmentTest {
    @Test
    fun canonicalLandmarks_produceIdentityTransform() {
        val face = floatArrayOf(
            0f, 0f, 1f, 1f,
            0.31556875f * 128f, 0.4615741f * 128f,
            0.6826229f * 128f, 0.4615741f * 128f,
            0.5002625f * 128f, 0.6405054f * 128f,
            0.34947187f * 128f, 0.824692f * 128f,
            0.6534365f * 128f, 0.824692f * 128f,
        )

        val transform = FaceLandmarkAlignment.similarityTransform(face)

        assertThat(transform[0]).isWithin(1e-6).of(1.0)
        assertThat(transform[1]).isWithin(1e-6).of(0.0)
        assertThat(transform[2]).isWithin(1e-4).of(0.0)
        assertThat(transform[3]).isWithin(1e-6).of(0.0)
        assertThat(transform[4]).isWithin(1e-6).of(1.0)
        assertThat(transform[5]).isWithin(1e-4).of(0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun degenerateLandmarks_areRejected() {
        FaceLandmarkAlignment.similarityTransform(FloatArray(14) { 10f })
    }
}
