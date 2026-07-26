package com.example.pepper_person_id_poc.domain.model

enum class CompatibilityStatus {
    VERIFIED,
    BUILDABLE,
    CONVERSION_REQUIRED,
    UNSUPPORTED,
    BLOCKED,
}

data class ModelRuntimeCompatibility(
    val artifactId: ArtifactId,
    val runtimeId: RuntimeId,
    val abi: String,
    val minApi: Int,
    val status: CompatibilityStatus,
    val reason: String? = null,
    val evidence: String? = null,
) {
    init {
        require(abi.isNotBlank()) { "abi must not be blank" }
        require(minApi >= 1) { "minApi must be positive" }
        if (status == CompatibilityStatus.UNSUPPORTED || status == CompatibilityStatus.BLOCKED) {
            require(!reason.isNullOrBlank()) { "$status compatibility requires a reason" }
        }
        if (status == CompatibilityStatus.VERIFIED) {
            require(!evidence.isNullOrBlank()) { "VERIFIED compatibility requires evidence" }
        }
    }

    val selectable: Boolean
        get() = status == CompatibilityStatus.VERIFIED || status == CompatibilityStatus.BUILDABLE
}
