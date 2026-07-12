package com.example.pepper_person_id_poc.domain.person

@JvmInline
value class PersonId(
    val value: String,
) {
    init {
        require(value.matches(VALID_PATTERN)) {
            "personId must contain 1-32 ASCII letters, numbers, '_' or '-'"
        }
    }

    companion object {
        private val VALID_PATTERN = Regex("[A-Za-z0-9_-]{1,32}")
    }
}

data class PersonProfile(
    val personId: PersonId,
    val displayName: String,
    val faceEmbeddings: List<FloatArray>,
    val speakerEmbeddings: List<FloatArray>,
    val faceModelName: String?,
    val speakerModelName: String?,
    val registeredAtMillis: Long,
    val faceEmbeddingsByModel: Map<String, List<FloatArray>> =
        faceModelName?.let { mapOf(it to faceEmbeddings) }.orEmpty(),
    val speakerEmbeddingsByModel: Map<String, List<FloatArray>> =
        speakerModelName?.let { mapOf(it to speakerEmbeddings) }.orEmpty(),
) {
    init {
        require(displayName.isNotBlank())
    }

    val faceSampleCount: Int get() = faceEmbeddings.size
    val speakerSampleCount: Int get() = speakerEmbeddings.size

    /** Returns a view containing only samples produced by [modelName]. */
    fun forFaceModel(modelName: String): PersonProfile = copy(
        faceEmbeddings = faceEmbeddingsByModel[modelName].orEmpty(),
        faceModelName = modelName,
    )

    /** Returns a view containing only samples produced by [modelName]. */
    fun forSpeakerModel(modelName: String): PersonProfile = copy(
        speakerEmbeddings = speakerEmbeddingsByModel[modelName].orEmpty(),
        speakerModelName = modelName,
    )
}
