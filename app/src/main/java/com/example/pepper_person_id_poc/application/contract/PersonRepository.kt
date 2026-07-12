package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile

interface PersonRepository {
    fun getAll(): List<PersonProfile>

    fun getAllForFaceModel(modelName: String): List<PersonProfile> =
        getAll().map { it.forFaceModel(modelName) }

    fun getAllForSpeakerModel(modelName: String): List<PersonProfile> =
        getAll().map { it.forSpeakerModel(modelName) }

    fun addFaceEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile

    fun addSpeakerEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile

    fun deleteAll()
}
