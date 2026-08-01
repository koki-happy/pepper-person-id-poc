package com.example.pepper_person_id_poc.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioLevelTimelineTest {
    @Test
    fun convertsDbFsRangeToPositivePercentScale() {
        assertThat(audioLevelPercent(-90f)).isEqualTo(0f)
        assertThat(audioLevelPercent(-45f)).isEqualTo(50f)
        assertThat(audioLevelPercent(0f)).isEqualTo(100f)
    }

    @Test
    fun clampsValuesOutsideDisplayRange() {
        assertThat(audioLevelPercent(-120f)).isEqualTo(0f)
        assertThat(audioLevelPercent(5f)).isEqualTo(100f)
    }
}
