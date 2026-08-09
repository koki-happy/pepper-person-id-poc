package com.example.pepper_person_id_poc.domain.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AudioLevelTest {
    @Test
    fun dbFs_silenceReturnsMinimum() {
        assertThat(AudioLevel.dbFs(ShortArray(160))).isEqualTo(AudioLevel.MIN_DB_FS)
    }

    @Test
    fun dbFs_fullScaleSignalIsNearZero() {
        val samples = ShortArray(160) { Short.MAX_VALUE }

        assertThat(AudioLevel.dbFs(samples)).isWithin(0.01f).of(0f)
    }

}
