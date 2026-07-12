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
        it.copy(faceEmbeddings = it.faceEmbeddings + embedding.copyOf(), faceModelName = modelName)
    }

    override fun addSpeakerEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile = update(personId, displayName, registeredAtMillis) {
        it.copy(speakerEmbeddings = it.speakerEmbeddings + embedding.copyOf(), speakerModelName = modelName)
    }

    override fun deleteAll() {
        profiles.clear()
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
