package com.example.pepper_person_id_poc.domain.face

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class NormalizedBoundingBoxTest {
    @Test
    fun intersectionOverUnion_returnsExpectedValue() {
        val first = NormalizedBoundingBox(0f, 0f, 0.5f, 0.5f)
        val second = NormalizedBoundingBox(0.25f, 0.25f, 0.75f, 0.75f)

        assertThat(first.intersectionOverUnion(second)).isWithin(0.0001f).of(1f / 7f)
    }

    @Test
    fun rotate90_mapsBoundingBoxClockwise() {
        val original = NormalizedBoundingBox(0.1f, 0.2f, 0.4f, 0.6f)

        val rotated = original.rotated(90)
        assertThat(rotated.left).isWithin(0.0001f).of(0.4f)
        assertThat(rotated.top).isWithin(0.0001f).of(0.1f)
        assertThat(rotated.right).isWithin(0.0001f).of(0.8f)
        assertThat(rotated.bottom).isWithin(0.0001f).of(0.4f)
    }
}
