package com.example.pepper_person_id_poc.application.config

import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.domain.model.CompatibilityStatus
import com.example.pepper_person_id_poc.infrastructure.model.ParsedModelCatalog

data class ModelPairAvailability(
    val artifactId: String,
    val runtimeId: String,
    val status: CompatibilityStatus,
    val selectable: Boolean,
    val reason: String?,
)

data class SettingsPairAvailability(
    val detector: ModelPairAvailability,
    val embedding: ModelPairAvailability,
    val speaker: ModelPairAvailability,
    val vad: ModelPairAvailability,
) {
    val selectable: Boolean
        get() = detector.selectable && embedding.selectable && speaker.selectable && vad.selectable
}

class ModelSelectionCoordinator(
    private val catalog: ParsedModelCatalog,
    private val bundledArtifactFileNames: Set<String>,
    private val abi: String,
    private val apiLevel: Int,
) {
    fun resolve(
        artifactId: String,
        runtimeId: String,
    ): ModelPairAvailability {
        val artifact = catalog.artifacts.firstOrNull { it.artifactId == artifactId }
            ?: return unavailable(
                artifactId,
                runtimeId,
                CompatibilityStatus.UNSUPPORTED,
                "Artifact is not declared in the model catalog",
            )
        if (artifact.filename != null && artifact.filename !in bundledArtifactFileNames) {
            return unavailable(
                artifactId,
                runtimeId,
                CompatibilityStatus.BLOCKED,
                "Required artifact is missing from this build: ${artifact.filename}",
            )
        }
        val exactAbi = artifact.runtimeCompatibility.filter { it.runtimeId == runtimeId && it.abi == abi }
        if (exactAbi.isEmpty()) {
            return unavailable(
                artifactId,
                runtimeId,
                CompatibilityStatus.UNSUPPORTED,
                "No compatibility record for ABI $abi",
            )
        }
        val compatibleApi = exactAbi
            .filter { apiLevel >= it.minApi }
            .maxByOrNull { it.minApi }
            ?: return unavailable(
                artifactId,
                runtimeId,
                CompatibilityStatus.UNSUPPORTED,
                "Requires API ${exactAbi.minOf { it.minApi }} or later; device API is $apiLevel",
            )
        val status = CompatibilityStatus.valueOf(compatibleApi.status)
        return ModelPairAvailability(
            artifactId = artifactId,
            runtimeId = runtimeId,
            status = status,
            selectable = status == CompatibilityStatus.VERIFIED || status == CompatibilityStatus.BUILDABLE,
            reason = compatibleApi.reason ?: status.defaultReason(),
        )
    }

    fun resolve(settings: PocSettings): SettingsPairAvailability = SettingsPairAvailability(
        detector = resolve(settings.faceDetectorModel.artifactId, settings.faceDetectorRuntime.runtimeId),
        embedding = resolve(settings.faceEmbeddingModel.artifactId, settings.faceEmbeddingRuntime.runtimeId),
        speaker = resolve(settings.speakerModel.artifactId, settings.speakerRuntime.runtimeId),
        vad = resolve(settings.vadModel.artifactId, settings.speakerRuntime.runtimeId),
    )

    private fun unavailable(
        artifactId: String,
        runtimeId: String,
        status: CompatibilityStatus,
        reason: String,
    ) = ModelPairAvailability(
        artifactId = artifactId,
        runtimeId = runtimeId,
        status = status,
        selectable = false,
        reason = reason,
    )
}

private fun CompatibilityStatus.defaultReason(): String? = when (this) {
    CompatibilityStatus.VERIFIED,
    CompatibilityStatus.BUILDABLE,
    -> null
    CompatibilityStatus.CONVERSION_REQUIRED -> "Model conversion is required"
    CompatibilityStatus.UNSUPPORTED -> "This artifact/runtime pair is unsupported"
    CompatibilityStatus.BLOCKED -> "This artifact/runtime pair is blocked"
}
