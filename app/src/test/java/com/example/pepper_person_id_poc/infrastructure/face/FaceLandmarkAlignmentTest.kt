package com.example.pepper_person_id_poc.infrastructure.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceLandmarkAlignmentTest {
    private val canonicalLandmarks = floatArrayOf(
        0.31556875f * 128f, 0.4615741f * 128f,
        0.6826229f * 128f, 0.4615741f * 128f,
        0.5002625f * 128f, 0.6405054f * 128f,
        0.34947187f * 128f, 0.824692f * 128f,
        0.6534365f * 128f, 0.824692f * 128f,
    )

    @Test
    fun canonicalLandmarks_produceIdentityTransform() {
        val face = floatArrayOf(
            0f, 0f, 1f, 1f,
            *canonicalLandmarks,
        )

        val transform = FaceLandmarkAlignment.similarityTransform(face)

        assertThat(transform[0]).isWithin(1e-6).of(1.0)
        assertThat(transform[1]).isWithin(1e-6).of(0.0)
        assertThat(transform[2]).isWithin(1e-4).of(0.0)
        assertThat(transform[3]).isWithin(1e-6).of(0.0)
        assertThat(transform[4]).isWithin(1e-6).of(1.0)
        assertThat(transform[5]).isWithin(1e-4).of(0.0)
    }

    @Test
    fun translatedScaledAndRotatedLandmarks_mapBackToCanonicalCoordinates() {
        val angleRadians = Math.toRadians(17.0)
        val scale = 1.35
        val cosine = Math.cos(angleRadians)
        val sine = Math.sin(angleRadians)
        val translated = FloatArray(canonicalLandmarks.size)
        repeat(canonicalLandmarks.size / 2) { index ->
            val canonicalX = canonicalLandmarks[index * 2]
            val canonicalY = canonicalLandmarks[index * 2 + 1]
            translated[index * 2] = (scale * (cosine * canonicalX - sine * canonicalY) + 31.0).toFloat()
            translated[index * 2 + 1] = (scale * (sine * canonicalX + cosine * canonicalY) - 19.0).toFloat()
        }
        val face = floatArrayOf(0f, 0f, 1f, 1f, *translated)

        val transform = FaceLandmarkAlignment.similarityTransform(face)

        repeat(canonicalLandmarks.size / 2) { index ->
            val sourceX = translated[index * 2].toDouble()
            val sourceY = translated[index * 2 + 1].toDouble()
            val alignedX = transform[0] * sourceX + transform[1] * sourceY + transform[2]
            val alignedY = transform[3] * sourceX + transform[4] * sourceY + transform[5]
            assertThat(alignedX).isWithin(1e-4).of(canonicalLandmarks[index * 2].toDouble())
            assertThat(alignedY).isWithin(1e-4).of(canonicalLandmarks[index * 2 + 1].toDouble())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun degenerateLandmarks_areRejected() {
        FaceLandmarkAlignment.similarityTransform(FloatArray(14) { 10f })
    }
}
