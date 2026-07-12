package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PersonProfileBinaryCodecTest {
    @Test
    fun writeThenRead_preservesProfileAndEmbeddings() {
        val profile = PersonProfile(
            personId = PersonId("person1"),
            displayName = "人物A",
            faceEmbeddings = listOf(floatArrayOf(0.1f, 0.2f, 0.3f)),
            speakerEmbeddings = listOf(floatArrayOf(0.4f, 0.5f)),
            faceModelName = "SFace 2021dec",
            speakerModelName = "CAM++",
            registeredAtMillis = 123L,
        )
        val output = ByteArrayOutputStream()

        PersonProfileBinaryCodec.write(output, listOf(profile))
        val decoded = PersonProfileBinaryCodec.read(ByteArrayInputStream(output.toByteArray())).single()

        assertThat(decoded.personId).isEqualTo(profile.personId)
        assertThat(decoded.displayName).isEqualTo(profile.displayName)
        assertThat(decoded.faceModelName).isEqualTo(profile.faceModelName)
        assertThat(decoded.speakerModelName).isEqualTo(profile.speakerModelName)
        assertThat(decoded.registeredAtMillis).isEqualTo(123L)
        assertThat(decoded.faceEmbeddings.single().toList()).containsExactly(0.1f, 0.2f, 0.3f).inOrder()
        assertThat(decoded.speakerEmbeddings.single().toList()).containsExactly(0.4f, 0.5f).inOrder()
    }

    @Test
    fun writeThenRead_supportsMoreThanTwoPeople() {
        val profiles = (1..10).map { index ->
            PersonProfile(
                personId = PersonId("person$index"),
                displayName = "人物$index",
                faceEmbeddings = listOf(floatArrayOf(index.toFloat())),
                speakerEmbeddings = emptyList(),
                faceModelName = "SFace 2021dec",
                speakerModelName = null,
                registeredAtMillis = index.toLong(),
            )
        }
        val output = ByteArrayOutputStream()

        PersonProfileBinaryCodec.write(output, profiles)
        val decoded = PersonProfileBinaryCodec.read(ByteArrayInputStream(output.toByteArray()))

        assertThat(decoded.map { it.personId.value })
            .containsExactlyElementsIn(profiles.map { it.personId.value })
            .inOrder()
    }

    @Test
    fun writeThenRead_preservesEmbeddingsForEachModel() {
        val profile = PersonProfile(
            personId = PersonId("person1"),
            displayName = "人物A",
            faceEmbeddings = listOf(floatArrayOf(1f, 0f)),
            speakerEmbeddings = listOf(floatArrayOf(1f, 0f, 0f)),
            faceModelName = "SFace",
            speakerModelName = "CAM++",
            registeredAtMillis = 123L,
            faceEmbeddingsByModel = mapOf(
                "SFace" to listOf(floatArrayOf(1f, 0f)),
                "0095" to listOf(floatArrayOf(0f, 1f)),
            ),
            speakerEmbeddingsByModel = mapOf(
                "CAM++" to listOf(floatArrayOf(1f, 0f, 0f)),
                "ERes2Net" to listOf(floatArrayOf(0f, 1f, 0f)),
            ),
        )
        val output = ByteArrayOutputStream()

        PersonProfileBinaryCodec.write(output, listOf(profile))
        val decoded = PersonProfileBinaryCodec.read(ByteArrayInputStream(output.toByteArray())).single()

        assertThat(decoded.forFaceModel("SFace").faceEmbeddings.single().toList())
            .containsExactly(1f, 0f).inOrder()
        assertThat(decoded.forFaceModel("0095").faceEmbeddings.single().toList())
            .containsExactly(0f, 1f).inOrder()
        assertThat(decoded.forSpeakerModel("CAM++").speakerEmbeddings.single().toList())
            .containsExactly(1f, 0f, 0f).inOrder()
        assertThat(decoded.forSpeakerModel("ERes2Net").speakerEmbeddings.single().toList())
            .containsExactly(0f, 1f, 0f).inOrder()
    }

    @Test
    fun read_migratesVersion1SamplesIntoTheirRecordedModel() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(0x50495031)
            data.writeInt(1)
            data.writeInt(1)
            data.writeUTF("person1")
            data.writeUTF("人物A")
            data.writeLong(123L)
            data.writeBoolean(true)
            data.writeUTF("SFace")
            data.writeBoolean(true)
            data.writeUTF("CAM++")
            data.writeEmbedding(floatArrayOf(1f, 0f))
            data.writeEmbedding(floatArrayOf(0f, 1f, 0f))
        }

        val decoded = PersonProfileBinaryCodec.read(ByteArrayInputStream(output.toByteArray())).single()

        assertThat(decoded.faceEmbeddingsByModel.keys).containsExactly("SFace")
        assertThat(decoded.speakerEmbeddingsByModel.keys).containsExactly("CAM++")
        assertThat(decoded.forFaceModel("SFace").faceSampleCount).isEqualTo(1)
        assertThat(decoded.forFaceModel("0095").faceSampleCount).isEqualTo(0)
        assertThat(decoded.forSpeakerModel("CAM++").speakerSampleCount).isEqualTo(1)
        assertThat(decoded.forSpeakerModel("ERes2Net").speakerSampleCount).isEqualTo(0)
    }

    private fun DataOutputStream.writeEmbedding(embedding: FloatArray) {
        writeInt(1)
        writeInt(embedding.size)
        embedding.forEach(::writeFloat)
    }
}
