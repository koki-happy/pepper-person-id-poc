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
                output.writeEmbeddingGroups(profile.faceEmbeddingsByModel)
                output.writeEmbeddingGroups(profile.speakerEmbeddingsByModel)
            }
        }
    }

    fun read(inputStream: InputStream): List<PersonProfile> =
        DataInputStream(inputStream.buffered()).use { input ->
            require(input.readInt() == MAGIC) { "Invalid person profile file" }
            val version = input.readInt()
            require(version in MIN_SUPPORTED_VERSION..VERSION) { "Unsupported person profile file version" }
            val count = input.readInt()
            require(count >= 0) { "Invalid person profile count" }
            List(count) {
                val personId = PersonId(input.readUTF())
                val displayName = input.readUTF()
                val registeredAtMillis = input.readLong()
                val faceModelName = input.readNullableUtf()
                val speakerModelName = input.readNullableUtf()
                val faceEmbeddings = input.readEmbeddings()
                val speakerEmbeddings = input.readEmbeddings()
                val faceGroups = if (version >= 2) input.readEmbeddingGroups() else {
                    faceModelName?.let { mapOf(it to faceEmbeddings) }.orEmpty()
                }
                val speakerGroups = if (version >= 2) input.readEmbeddingGroups() else {
                    speakerModelName?.let { mapOf(it to speakerEmbeddings) }.orEmpty()
                }
                PersonProfile(
                    personId = personId,
                    displayName = displayName,
                    registeredAtMillis = registeredAtMillis,
                    faceModelName = faceModelName,
                    speakerModelName = speakerModelName,
                    faceEmbeddings = faceEmbeddings,
                    speakerEmbeddings = speakerEmbeddings,
                    faceEmbeddingsByModel = faceGroups,
                    speakerEmbeddingsByModel = speakerGroups,
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

    private fun DataOutputStream.writeEmbeddingGroups(groups: Map<String, List<FloatArray>>) {
        require(groups.size <= MAX_MODEL_COUNT)
        writeInt(groups.size)
        groups.toSortedMap().forEach { (modelName, embeddings) ->
            require(modelName.isNotBlank())
            writeUTF(modelName)
            writeEmbeddings(embeddings)
        }
    }

    private fun DataInputStream.readEmbeddingGroups(): Map<String, List<FloatArray>> {
        val count = readInt()
        require(count in 0..MAX_MODEL_COUNT) { "Invalid model count" }
        return buildMap(count) {
            repeat(count) {
                val modelName = readUTF()
                require(modelName.isNotBlank()) { "Invalid model name" }
                require(modelName !in this) { "Duplicate model name" }
                put(modelName, readEmbeddings())
            }
        }
    }

    private const val MAGIC = 0x50495031
    private const val VERSION = 2
    private const val MIN_SUPPORTED_VERSION = 1
    private const val MAX_MODEL_COUNT = 16
    private const val MAX_SAMPLE_COUNT = 100
    private const val MAX_EMBEDDING_DIMENSION = 4096
}
