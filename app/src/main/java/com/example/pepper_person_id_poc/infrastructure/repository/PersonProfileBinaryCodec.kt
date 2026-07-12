package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object PersonProfileBinaryCodec {
    fun write(outputStream: OutputStream, profiles: List<PersonProfile>) {
        DataOutputStream(outputStream.buffered()).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(profiles.size)
            profiles.forEach { profile ->
                output.writeUTF(profile.personId.value)
                output.writeUTF(profile.displayName)
                output.writeLong(profile.registeredAtMillis)
                output.writeNullableUtf(profile.faceModelName)
                output.writeNullableUtf(profile.speakerModelName)
                output.writeEmbeddings(profile.faceEmbeddings)
                output.writeEmbeddings(profile.speakerEmbeddings)
            }
        }
    }

    fun read(inputStream: InputStream): List<PersonProfile> =
        DataInputStream(inputStream.buffered()).use { input ->
            require(input.readInt() == MAGIC) { "Invalid person profile file" }
            require(input.readInt() == VERSION) { "Unsupported person profile file version" }
            val count = input.readInt()
            require(count >= 0) { "Invalid person profile count" }
            List(count) {
                PersonProfile(
                    personId = PersonId(input.readUTF()),
                    displayName = input.readUTF(),
                    registeredAtMillis = input.readLong(),
                    faceModelName = input.readNullableUtf(),
                    speakerModelName = input.readNullableUtf(),
                    faceEmbeddings = input.readEmbeddings(),
                    speakerEmbeddings = input.readEmbeddings(),
                )
            }
        }

    private fun DataOutputStream.writeNullableUtf(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataInputStream.readNullableUtf(): String? = if (readBoolean()) readUTF() else null

    private fun DataOutputStream.writeEmbeddings(embeddings: List<FloatArray>) {
        require(embeddings.size <= MAX_SAMPLE_COUNT)
        writeInt(embeddings.size)
        embeddings.forEach { embedding ->
            require(embedding.size in 1..MAX_EMBEDDING_DIMENSION)
            writeInt(embedding.size)
            embedding.forEach(::writeFloat)
        }
    }

    private fun DataInputStream.readEmbeddings(): List<FloatArray> {
        val count = readInt()
        require(count in 0..MAX_SAMPLE_COUNT) { "Invalid embedding count" }
        return List(count) {
            val dimension = readInt()
            require(dimension in 1..MAX_EMBEDDING_DIMENSION) { "Invalid embedding dimension" }
            FloatArray(dimension) { readFloat() }
        }
    }

    private const val MAGIC = 0x50495031
    private const val VERSION = 1
    private const val MAX_SAMPLE_COUNT = 100
    private const val MAX_EMBEDDING_DIMENSION = 4096
}
