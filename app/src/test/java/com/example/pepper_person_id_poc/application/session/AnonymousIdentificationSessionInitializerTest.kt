package com.example.pepper_person_id_poc.application.session

import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousFaceRepository
import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousSpeakerRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AnonymousIdentificationSessionInitializerTest {
    @Test
    fun initializeMultipleTimes_clearsBothRepositoriesAndResetsCounters() = runBlocking {
        val face = InMemoryAnonymousFaceRepository()
        val speaker = InMemoryAnonymousSpeakerRepository()
        face.identify("face", floatArrayOf(1f), 0.6f, 20)
        speaker.identify("speaker", floatArrayOf(1f), 0.6f, 20)
        val initializer = AnonymousIdentificationSessionInitializer(face, speaker)

        initializer.initializeNewSession()
        initializer.initializeNewSession()

        assertThat(face.count()).isEqualTo(0)
        assertThat(speaker.count()).isEqualTo(0)
        assertThat(face.identify("face", floatArrayOf(1f), 0.6f, 20).anonymousId)
            .isEqualTo("anonymous-face-001")
        assertThat(speaker.identify("speaker", floatArrayOf(1f), 0.6f, 20).anonymousId)
            .isEqualTo("anonymous-speaker-001")
    }

    @Test
    fun repositoriesRemainIndependent() {
        val face = InMemoryAnonymousFaceRepository()
        val speaker = InMemoryAnonymousSpeakerRepository()
        face.identify("same-model", floatArrayOf(1f), 0.6f, 20)
        speaker.identify("same-model", floatArrayOf(1f), 0.6f, 20)

        assertThat(face.getAll().single().anonymousId).startsWith("anonymous-face-")
        assertThat(speaker.getAll().single().anonymousId).startsWith("anonymous-speaker-")
    }
}
