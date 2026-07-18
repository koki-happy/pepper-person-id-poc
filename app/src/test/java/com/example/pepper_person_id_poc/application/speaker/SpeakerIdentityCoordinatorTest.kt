package com.example.pepper_person_id_poc.application.speaker

import com.example.pepper_person_id_poc.application.contract.BenchmarkLogger
import com.example.pepper_person_id_poc.application.contract.SpeakerEmbeddingEngine
import com.example.pepper_person_id_poc.domain.audio.PcmUtterance
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentifier
import com.example.pepper_person_id_poc.domain.speaker.SpeakerIdentityStatus
import com.example.pepper_person_id_poc.testsupport.FakePersonRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SpeakerIdentityCoordinatorTest {
    @Test
    fun registration_addsSpeakerEmbeddingToSharedPersonProfile() {
        val repository = FakePersonRepository()
        val coordinator = coordinator(SpeakerScreenMode.REGISTRATION, repository, floatArrayOf(1f, 0f))
        try {
            coordinator.requestRegistration("person1", "人物A")
            coordinator.onUtterance(sufficientUtterance(1_000L))

            await { repository.getAll().singleOrNull()?.speakerSampleCount == 1 }

            val profile = repository.getAll().single()
            assertThat(profile.personId).isEqualTo(PersonId("person1"))
            assertThat(profile.speakerModelName).isEqualTo("Fake speaker model")
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun identification_returnsRegisteredPerson() {
        val profile = PersonProfile(
            personId = PersonId("person1"),
            displayName = "人物A",
            faceEmbeddings = emptyList(),
            speakerEmbeddings = listOf(floatArrayOf(1f, 0f)),
            faceModelName = null,
            speakerModelName = "Fake speaker model",
            registeredAtMillis = 1L,
        )
        val coordinator = coordinator(
            SpeakerScreenMode.IDENTIFICATION,
            FakePersonRepository(listOf(profile)),
            floatArrayOf(0.99f, 0.01f),
        )
        try {
            coordinator.onUtterance(sufficientUtterance(2_000L))

            await { coordinator.state.value.result != null }

            assertThat(coordinator.state.value.result?.status).isEqualTo(SpeakerIdentityStatus.IDENTIFIED)
            assertThat(coordinator.state.value.result?.personId).isEqualTo(PersonId("person1"))
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun anonymousIdentification_reusesSessionSpeakerId() {
        val coordinator = coordinator(
            SpeakerScreenMode.ANONYMOUS_IDENTIFICATION,
            FakePersonRepository(),
            floatArrayOf(1f, 0f),
        )
        try {
            coordinator.onUtterance(sufficientUtterance(3_000L))
            await { coordinator.state.value.anonymousResult != null }
            val firstId = coordinator.state.value.anonymousResult?.anonymousSpeakerId

            coordinator.onUtterance(sufficientUtterance(5_000L))
            await { coordinator.state.value.anonymousResult?.utteranceId == "utterance-5000" }

            assertThat(coordinator.state.value.anonymousResult?.anonymousSpeakerId).isEqualTo(firstId)
            assertThat(coordinator.state.value.anonymousClusterCount).isEqualTo(1)
        } finally {
            coordinator.close()
        }
    }

    private fun coordinator(
        mode: SpeakerScreenMode,
        repository: FakePersonRepository,
        embedding: FloatArray,
    ) = SpeakerIdentityCoordinator(
        mode = mode,
        personRepository = repository,
        embeddingEngine = FakeSpeakerEmbeddingEngine(embedding),
        speakerIdentifier = SpeakerIdentifier(),
        speakerThreshold = 0.6f,
        benchmarkLogger = FakeBenchmarkLogger(),
    )

    private fun sufficientUtterance(startedAtMillis: Long) = PcmUtterance(
        pcm16 = ShortArray(16_000) { 1 },
        sampleRate = 16_000,
        startedAtMillis = startedAtMillis,
        endedAtMillis = startedAtMillis + 1_000L,
        voicedDurationMillis = 1_000L,
    )

    private fun await(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(10L)
        }
        error("Timed out waiting for coordinator state")
    }

    private class FakeSpeakerEmbeddingEngine(
        private val embedding: FloatArray,
    ) : SpeakerEmbeddingEngine {
        override val modelName = "Fake speaker model"
        override val embeddingDimension: Int = embedding.size
        override fun prepare() = Unit
        override fun extract(pcm16: ShortArray, sampleRate: Int): FloatArray = embedding.copyOf()
        override fun close() = Unit
    }

    private class FakeBenchmarkLogger : BenchmarkLogger {
        override fun append(event: BenchmarkEvent) = Unit
        override fun outputFile() = File("unused")
        override fun readRecent(limit: Int) = emptyList<String>()
        override fun deleteAll() = Unit
    }
}
