package com.example.pepper_person_id_poc.testsupport

import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile

class FakePersonRepository(
    initialProfiles: List<PersonProfile> = emptyList(),
) : PersonRepository {
    private val profiles = initialProfiles.toMutableList()

    override fun getAll(): List<PersonProfile> = profiles.toList()

    override fun addFaceEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile = update(personId, displayName, registeredAtMillis) {
        val samples = it.faceEmbeddingsByModel[modelName].orEmpty() + embedding.copyOf()
        it.copy(
            faceEmbeddings = samples,
            faceModelName = modelName,
            faceEmbeddingsByModel = it.faceEmbeddingsByModel + (modelName to samples),
        )
    }

    override fun addSpeakerEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile = update(personId, displayName, registeredAtMillis) {
        val samples = it.speakerEmbeddingsByModel[modelName].orEmpty() + embedding.copyOf()
        it.copy(
            speakerEmbeddings = samples,
            speakerModelName = modelName,
            speakerEmbeddingsByModel = it.speakerEmbeddingsByModel + (modelName to samples),
        )
    }

    override fun replaceFaceEmbeddings(
        personId: PersonId,
        displayName: String,
        embeddings: List<FloatArray>,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile = update(personId, displayName, registeredAtMillis) {
        val copied = embeddings.map(FloatArray::copyOf)
        it.copy(
            faceEmbeddings = copied,
            faceModelName = modelName,
            registeredAtMillis = registeredAtMillis,
            faceEmbeddingsByModel = it.faceEmbeddingsByModel + (modelName to copied),
        )
    }

    override fun deleteAll() {
        profiles.clear()
    }

    override fun deletePerson(personId: PersonId) {
        profiles.removeAll { it.personId == personId }
    }

    private fun update(
        personId: PersonId,
        displayName: String,
        registeredAtMillis: Long,
        block: (PersonProfile) -> PersonProfile,
    ): PersonProfile {
        val index = profiles.indexOfFirst { it.personId == personId }
        val base = if (index >= 0) {
            profiles[index]
        } else {
            PersonProfile(
                personId = personId,
                displayName = displayName,
                faceEmbeddings = emptyList(),
                speakerEmbeddings = emptyList(),
                faceModelName = null,
                speakerModelName = null,
                registeredAtMillis = registeredAtMillis,
            )
        }
        val updated = block(base)
        if (index >= 0) profiles[index] = updated else profiles += updated
        return updated
    }
}
