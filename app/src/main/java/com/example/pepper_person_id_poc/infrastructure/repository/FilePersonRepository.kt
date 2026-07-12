package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import android.util.AtomicFile
import com.example.pepper_person_id_poc.application.contract.PersonRepository
import com.example.pepper_person_id_poc.domain.person.PersonId
import com.example.pepper_person_id_poc.domain.person.PersonProfile
import java.io.File
import java.io.ByteArrayOutputStream

class FilePersonRepository(
    context: Context,
) : PersonRepository {
    private val file = File(context.applicationContext.filesDir, "biometric/person-profiles.bin")
    private val atomicFile = AtomicFile(file)

    @Synchronized
    override fun getAll(): List<PersonProfile> = load().map { it.deepCopy() }

    @Synchronized
    override fun addFaceEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile = updatePerson(personId, displayName, registeredAtMillis) { profile ->
        profile.copy(
            faceEmbeddings = profile.faceEmbeddings + embedding.copyOf(),
            faceModelName = modelName,
        )
    }

    @Synchronized
    override fun addSpeakerEmbedding(
        personId: PersonId,
        displayName: String,
        embedding: FloatArray,
        modelName: String,
        registeredAtMillis: Long,
    ): PersonProfile = updatePerson(personId, displayName, registeredAtMillis) { profile ->
        profile.copy(
            speakerEmbeddings = profile.speakerEmbeddings + embedding.copyOf(),
            speakerModelName = modelName,
        )
    }

    @Synchronized
    override fun deleteAll() {
        atomicFile.delete()
    }

    private fun updatePerson(
        personId: PersonId,
        displayName: String,
        registeredAtMillis: Long,
        update: (PersonProfile) -> PersonProfile,
    ): PersonProfile {
        require(displayName.isNotBlank())
        val profiles = load().toMutableList()
        val index = profiles.indexOfFirst { it.personId == personId }
        if (index < 0) {
            profiles += PersonProfile(
                personId = personId,
                displayName = displayName.trim(),
                faceEmbeddings = emptyList(),
                speakerEmbeddings = emptyList(),
                faceModelName = null,
                speakerModelName = null,
                registeredAtMillis = registeredAtMillis,
            )
        } else {
            profiles[index] = profiles[index].copy(displayName = displayName.trim())
        }
        val targetIndex = profiles.indexOfFirst { it.personId == personId }
        val updated = update(profiles[targetIndex])
        profiles[targetIndex] = updated
        save(profiles)
        return updated.deepCopy()
    }

    private fun load(): List<PersonProfile> =
        if (!file.exists()) emptyList() else atomicFile.openRead().use(PersonProfileBinaryCodec::read)

    private fun save(profiles: List<PersonProfile>) {
        file.parentFile?.mkdirs()
        val encoded = ByteArrayOutputStream().also { PersonProfileBinaryCodec.write(it, profiles) }.toByteArray()
        val output = atomicFile.startWrite()
        try {
            output.write(encoded)
            atomicFile.finishWrite(output)
        } catch (throwable: Throwable) {
            atomicFile.failWrite(output)
            throw throwable
        }
    }

    private fun PersonProfile.deepCopy(): PersonProfile = copy(
        faceEmbeddings = faceEmbeddings.map(FloatArray::copyOf),
        speakerEmbeddings = speakerEmbeddings.map(FloatArray::copyOf),
    )

}
