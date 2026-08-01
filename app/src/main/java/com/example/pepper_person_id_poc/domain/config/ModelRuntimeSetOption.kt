package com.example.pepper_person_id_poc.domain.config

import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator

enum class ModelRuntimeRole {
    FACE_DETECTOR,
    FACE_EMBEDDING,
    SPEAKER_EMBEDDING,
    VAD,
}

data class ModelRuntimeSetOption(
    val role: ModelRuntimeRole,
    val displayName: String,
    val artifactId: String,
    val runtimeId: String,
    val faceDetectorModel: FaceDetectorModelOption? = null,
    val faceDetectorRuntime: FaceDetectorRuntime? = null,
    val faceEmbeddingModel: FaceEmbeddingModelOption? = null,
    val faceEmbeddingRuntime: FaceEmbeddingRuntime? = null,
    val speakerModel: SpeakerModelOption? = null,
    val speakerRuntime: SpeakerRuntime? = null,
    val vadModel: VadModelOption? = null,
    val vadRuntime: VadRuntime? = null,
)

fun selectableModelRuntimeSets(
    role: ModelRuntimeRole,
    coordinator: ModelSelectionCoordinator,
): List<ModelRuntimeSetOption> = when (role) {
    ModelRuntimeRole.FACE_DETECTOR -> FaceDetectorArtifactResolver.logicalModels.flatMap { model ->
        FaceDetectorRuntime.entries.mapNotNull { runtime ->
            val artifact = FaceDetectorArtifactResolver.resolve(model, runtime) ?: return@mapNotNull null
            if (!coordinator.resolve(artifact.artifactId, runtime.runtimeId).selectable) return@mapNotNull null
            ModelRuntimeSetOption(
                role = role,
                displayName = "${model.displayName}／${runtime.displayName}",
                artifactId = artifact.artifactId,
                runtimeId = runtime.runtimeId,
                faceDetectorModel = artifact,
                faceDetectorRuntime = runtime,
            )
        }
    }

    ModelRuntimeRole.FACE_EMBEDDING -> FaceEmbeddingArtifactResolver.logicalModels.flatMap { model ->
        FaceEmbeddingRuntime.entries.mapNotNull { runtime ->
            val artifact = FaceEmbeddingArtifactResolver.resolve(model, runtime) ?: return@mapNotNull null
            if (!coordinator.resolve(artifact.artifactId, runtime.runtimeId).selectable) return@mapNotNull null
            ModelRuntimeSetOption(
                role = role,
                displayName = "${model.displayName}／${runtime.displayName}",
                artifactId = artifact.artifactId,
                runtimeId = runtime.runtimeId,
                faceEmbeddingModel = artifact,
                faceEmbeddingRuntime = runtime,
            )
        }
    }

    ModelRuntimeRole.SPEAKER_EMBEDDING -> listOf(
        SpeakerModelOption.WESPEAKER_RESNET34_LM,
        SpeakerModelOption.CAM_PLUS_PLUS_ZH_EN,
    ).mapNotNull { model ->
        val runtime = model.requiredRuntime
        if (!coordinator.resolve(model.artifactId, runtime.runtimeId).selectable) return@mapNotNull null
        ModelRuntimeSetOption(
            role = role,
            displayName = "${model.displayName}／${runtime.displayName}",
            artifactId = model.artifactId,
            runtimeId = runtime.runtimeId,
            speakerModel = model,
            speakerRuntime = runtime,
        )
    }

    ModelRuntimeRole.VAD -> VadModelOption.entries.mapNotNull { model ->
        val runtime = VadRuntime.SHERPA_ONNX
        if (!coordinator.resolve(model.artifactId, runtime.runtimeId).selectable) return@mapNotNull null
        ModelRuntimeSetOption(
            role = role,
            displayName = "${model.displayName}／${runtime.displayName}",
            artifactId = model.artifactId,
            runtimeId = runtime.runtimeId,
            vadModel = model,
            vadRuntime = runtime,
        )
    }
}

fun PocSettings.selectedSet(role: ModelRuntimeRole): Pair<String, String> = when (role) {
    ModelRuntimeRole.FACE_DETECTOR ->
        FaceDetectorArtifactResolver.resolve(faceDetectorModel, faceDetectorRuntime)?.artifactId.orEmpty() to
            faceDetectorRuntime.runtimeId
    ModelRuntimeRole.FACE_EMBEDDING ->
        FaceEmbeddingArtifactResolver.resolve(faceEmbeddingModel, faceEmbeddingRuntime)?.artifactId.orEmpty() to
            faceEmbeddingRuntime.runtimeId
    ModelRuntimeRole.SPEAKER_EMBEDDING -> speakerModel.artifactId to speakerRuntime.runtimeId
    ModelRuntimeRole.VAD -> vadModel.artifactId to vadRuntime.runtimeId
}
