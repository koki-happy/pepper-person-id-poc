package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.testsupport.FakeBenchmarkLogger
import com.example.pepper_person_id_poc.testsupport.FakeSpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousSpeakerRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeakerIdentityCoordinatorTest {
    @Test
    fun close_doesNotDeleteRepository() {
        val repository = InMemoryAnonymousSpeakerRepository()
        repository.identify("speaker-model", floatArrayOf(1f, 0f), 0.8f, 20)
        val coordinator = SpeakerIdentityCoordinator(
            repository,
            FakeSpeakerEmbeddingEngine(floatArrayOf(1f, 0f)),
            0.8f,
            20,
            FakeBenchmarkLogger(),
        )

        coordinator.close()

        assertThat(repository.count()).isEqualTo(1)
    }
}
