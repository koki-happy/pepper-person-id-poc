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
) {
    init {
        require(displayName.isNotBlank())
    }

    val faceSampleCount: Int get() = faceEmbeddings.size
    val speakerSampleCount: Int get() = speakerEmbeddings.size
}
