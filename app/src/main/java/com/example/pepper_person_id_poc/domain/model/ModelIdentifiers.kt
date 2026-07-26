package com.example.pepper_person_id_poc.domain.model

@JvmInline
value class ModelSpaceId(val value: String) {
    init {
        require(value.isNotBlank()) { "modelSpaceId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class ArtifactId(val value: String) {
    init {
        require(value.isNotBlank()) { "artifactId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class RuntimeId(val value: String) {
    init {
        require(value.isNotBlank()) { "runtimeId must not be blank" }
    }

    override fun toString(): String = value
}

enum class BiometricModality {
    FACE,
    SPEAKER,
    AUDIO_ACTIVITY,
    UNSPECIFIED,
}
