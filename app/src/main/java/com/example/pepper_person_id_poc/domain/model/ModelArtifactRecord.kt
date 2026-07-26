package com.example.pepper_person_id_poc.domain.model

enum class ModelRole {
    DETECTION,
    EMBEDDING,
    VAD,
    SEGMENTATION,
}

enum class ModelPrecision {
    FP32,
    FP16,
    INT8,
    MIXED,
}

enum class ModelFormat {
    ONNX,
    TFLITE,
    NCNN,
    MNN,
    OPENVINO_IR,
    SDK_INTERNAL,
}

enum class CommercialUse {
    ALLOWED,
    PROHIBITED,
    UNKNOWN,
}

data class ConversionRecord(
    val sourceArtifactId: ArtifactId,
    val tool: String,
    val toolRevision: String,
    val environmentDigest: String,
    val command: String,
    val outputSha256: String,
    val tensorMetadata: Map<String, String>,
    val warnings: List<String>,
) {
    init {
        require(tool.isNotBlank())
        require(toolRevision.isNotBlank())
        require(environmentDigest.isNotBlank())
        require(command.isNotBlank())
        require(outputSha256.isSha256()) { "conversion outputSha256 must be 64 lowercase hex digits" }
    }
}

data class ModelArtifactRecord(
    val artifactId: ArtifactId,
    val modelSpaceId: ModelSpaceId?,
    val modality: BiometricModality,
    val role: ModelRole,
    val architecture: String,
    val precision: ModelPrecision,
    val format: ModelFormat,
    val filename: String?,
    val sourceUrl: String,
    val sourceRevision: String,
    val sha256: String?,
    val fileSizeBytes: Long?,
    val inputShape: List<Int?>,
    val inputLayout: String,
    val inputColorOrder: String?,
    val normalization: Map<String, String>,
    val outputShape: List<Int?>,
    val outputSemantics: String,
    val opset: Int?,
    val conversion: ConversionRecord?,
    val runtimeCompatibility: List<ModelRuntimeCompatibility>,
    val weightLicense: String,
    val commercialUse: CommercialUse,
    val licenseEvidence: String?,
) {
    init {
        require(architecture.isNotBlank())
        require(sourceUrl.startsWith("https://")) { "sourceUrl must use https" }
        require(sourceRevision.isNotBlank())
        require(inputLayout.isNotBlank())
        require(outputSemantics.isNotBlank())
        require(weightLicense.isNotBlank())
        require(filename != null || format == ModelFormat.SDK_INTERNAL)
        require(sha256 == null || sha256.isSha256())
        require(fileSizeBytes == null || fileSizeBytes > 0)
        require(role != ModelRole.EMBEDDING || modelSpaceId != null) {
            "embedding artifacts require modelSpaceId"
        }
        require(conversion == null || conversion.sourceArtifactId != artifactId) {
            "converted artifact cannot reference itself"
        }
        require(runtimeCompatibility.all { it.artifactId == artifactId }) {
            "runtime compatibility must reference the containing artifact"
        }
    }
}

private fun String.isSha256(): Boolean = matches(Regex("[0-9a-f]{64}"))
