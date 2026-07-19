package com.example.pepper_person_id_poc.domain.person

import com.example.pepper_person_id_poc.testsupport.FakePersonRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PersonProfileTest {
    @Test
    fun repository_keepsFaceSamplesSeparatedWhenSwitchingModels() {
        val repository = FakePersonRepository()
        val id = PersonId("person1")

        repository.addFaceEmbedding(id, "人物A", floatArrayOf(1f, 0f), "SFace", 1L)
        repository.addFaceEmbedding(id, "人物A", floatArrayOf(0f, 1f), "0095", 2L)
        repository.addFaceEmbedding(id, "人物A", floatArrayOf(.5f, .5f), "SFace", 3L)

        assertThat(repository.getAllForFaceModel("SFace").single().faceEmbeddings.map { it.toList() })
            .containsExactly(listOf(1f, 0f), listOf(.5f, .5f)).inOrder()
        assertThat(repository.getAllForFaceModel("0095").single().faceEmbeddings.single().toList())
            .containsExactly(0f, 1f).inOrder()
    }

    @Test
    fun repository_keepsSpeakerSamplesSeparatedWhenSwitchingModels() {
        val repository = FakePersonRepository()
        val id = PersonId("person1")

        repository.addSpeakerEmbedding(id, "人物A", floatArrayOf(1f, 0f), "CAM++", 1L)
        repository.addSpeakerEmbedding(id, "人物A", floatArrayOf(0f, 1f), "ERes2Net", 2L)
        repository.addSpeakerEmbedding(id, "人物A", floatArrayOf(.5f, .5f), "CAM++", 3L)

        assertThat(repository.getAllForSpeakerModel("CAM++").single().speakerEmbeddings.map { it.toList() })
            .containsExactly(listOf(1f, 0f), listOf(.5f, .5f)).inOrder()
        assertThat(repository.getAllForSpeakerModel("ERes2Net").single().speakerEmbeddings.single().toList())
            .containsExactly(0f, 1f).inOrder()
    }

    @Test
    fun repository_readsLegacyCampPlusDisplayNameUnderStableModelId() {
        val repository = FakePersonRepository()
        val id = PersonId("person1")

        repository.addSpeakerEmbedding(
            id,
            "人物A",
            floatArrayOf(1f, 0f),
            "3D-Speaker CAM++",
            1L,
        )
        repository.addSpeakerEmbedding(
            id,
            "人物A",
            floatArrayOf(0f, 1f),
            "campplus-en",
            2L,
        )

        assertThat(repository.getAllForSpeakerModel("campplus-en").single().speakerEmbeddings.map { it.toList() })
            .containsExactly(listOf(0f, 1f), listOf(1f, 0f))
    }
}
