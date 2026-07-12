package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile

interface PersonRepository {
    fun getAll(): List<PersonProfile>

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
