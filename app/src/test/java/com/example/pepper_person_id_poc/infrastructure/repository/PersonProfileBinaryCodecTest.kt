package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
}
