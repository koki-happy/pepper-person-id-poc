package com.example.pepper_person_id_poc.domain.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PocSettingsTest {
    @Test fun defaultsAreValid() = assertThat(PocSettings().isValid()).isTrue()

    @Test
    fun clusterBoundsAreValidated() {
        assertThat(PocSettings(faceClusterMaxUpdateCount = 0).isValid()).isFalse()
        assertThat(PocSettings(speakerClusterJoinThreshold = 1.1f).isValid()).isFalse()
    }

    @Test
    fun selectingSpeakerModelUpdatesJoinThreshold() {
        val updated = PocSettings().withSpeakerModel(SpeakerModelOption.CAM_PLUS_PLUS)
        assertThat(updated.speakerClusterJoinThreshold).isEqualTo(SpeakerModelOption.CAM_PLUS_PLUS.jvsCandidateThreshold)
    }
}
