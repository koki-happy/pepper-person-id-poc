package com.example.pepper_person_id_poc.domain.metrics

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class BenchmarkDevice(
    val manufacturer: String,
    val model: String,
    val apiLevel: Int,
    val abi: String,
) {
    init {
        require(manufacturer.isNotBlank())
        require(model.isNotBlank())
        require(apiLevel > 0)
        require(abi.isNotBlank())
    }
}

data class BenchmarkInput(
    val descriptor: String,
    val sha256: String,
    val preprocessingId: String,
    val datasetId: String,
    val repetition: Int,
) {
    init {
        require(descriptor.isNotBlank())
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(preprocessingId.isNotBlank())
        require(datasetId.isNotBlank())
        require(repetition > 0)
    }
}

data class BenchmarkModelSelection(
    val role: String,
    val modelSpaceId: String? = null,
    val artifactId: String,
    val runtimeId: String,
) {
    init {
        require(role.isNotBlank())
        require(modelSpaceId == null || modelSpaceId.isNotBlank())
        require(artifactId.isNotBlank())
        require(runtimeId.isNotBlank())
    }
}

data class BenchmarkThresholds(
    val identification: Double? = null,
    val minimumLead: Double? = null,
    val createQuality: Double? = null,
    val updateQuality: Double? = null,
) {
    init {
        validateNullableMeasurements(
            identification,
            minimumLead,
            createQuality,
            updateQuality,
        )
    }
}

data class BenchmarkRunMetadata(
    val runId: String,
    val scenarioId: String,
    val timestampEpochMillis: Long,
    val timestampElapsedRealtimeMillis: Long,
    val device: BenchmarkDevice,
    val buildVariant: String,
    val input: BenchmarkInput,
    val models: List<BenchmarkModelSelection>,
    val thresholds: BenchmarkThresholds,
) {
    init {
        require(runId.isNotBlank())
        require(scenarioId.isNotBlank())
        require(timestampEpochMillis >= 0L)
        require(timestampElapsedRealtimeMillis >= 0L)
        require(buildVariant.isNotBlank())
        require(models.isNotEmpty())
    }
}

data class FacePipelineMetrics(
    val captureMillis: Double? = null,
    val preprocessingMillis: Double? = null,
    val detectionMillis: Double? = null,
    val qualityMillis: Double? = null,
    val alignmentMillis: Double? = null,
    val embeddingMillis: Double? = null,
    val identificationMillis: Double? = null,
    val repositoryMillis: Double? = null,
    val totalMillis: Double? = null,
) {
    init {
        validateNullableDurations(
            captureMillis,
            preprocessingMillis,
            detectionMillis,
            qualityMillis,
            alignmentMillis,
            embeddingMillis,
            identificationMillis,
            repositoryMillis,
            totalMillis,
        )
    }

    fun measuredStageTotalMillis(): Double = listOfNotNull(
        captureMillis,
        preprocessingMillis,
        detectionMillis,
        qualityMillis,
        alignmentMillis,
        embeddingMillis,
        identificationMillis,
        repositoryMillis,
    ).sum()
}

data class SpeakerPipelineMetrics(
    val captureMillis: Double? = null,
    val vadMillis: Double? = null,
    val segmentationMillis: Double? = null,
    val trackingMillis: Double? = null,
    val qualityMillis: Double? = null,
    val embeddingMillis: Double? = null,
    val identificationMillis: Double? = null,
    val repositoryMillis: Double? = null,
    val processingMillis: Double? = null,
    val audioDurationMillis: Double? = null,
) {
    init {
        validateNullableDurations(
            captureMillis,
            vadMillis,
            segmentationMillis,
            trackingMillis,
            qualityMillis,
            embeddingMillis,
            identificationMillis,
            repositoryMillis,
            processingMillis,
            audioDurationMillis,
        )
    }

    fun measuredStageTotalMillis(): Double = listOfNotNull(
        captureMillis,
        vadMillis,
        segmentationMillis,
        trackingMillis,
        qualityMillis,
        embeddingMillis,
        identificationMillis,
        repositoryMillis,
    ).sum()

    fun realTimeFactor(): Double? {
        val processing = processingMillis ?: return null
        val duration = audioDurationMillis?.takeIf { it > 0.0 } ?: return null
        return processing / duration
    }
}

data class ResourceMetrics(
    val collectedAtElapsedRealtimeMillis: Long,
    val appCpuPercent: Double? = null,
    val pssBytes: Long? = null,
    val javaHeapBytes: Long? = null,
    val nativeHeapBytes: Long? = null,
    val deviceAvailableMemoryBytes: Long? = null,
) {
    init {
        require(collectedAtElapsedRealtimeMillis >= 0L)
        validateNullableMeasurements(appCpuPercent)
        require(appCpuPercent == null || appCpuPercent >= 0.0)
        listOf(pssBytes, javaHeapBytes, nativeHeapBytes, deviceAvailableMemoryBytes)
            .forEach { require(it == null || it >= 0L) }
    }
}

enum class BenchmarkStatus {
    SUCCESS,
    FAILURE,
    PARTIAL,
    SKIPPED,
}

data class BenchmarkError(
    val code: String,
    val message: String? = null,
) {
    init {
        require(code.isNotBlank())
    }
}

data class BenchmarkEvent(
    val eventType: String,
    val run: BenchmarkRunMetadata,
    val face: FacePipelineMetrics? = null,
    val speaker: SpeakerPipelineMetrics? = null,
    val resources: ResourceMetrics? = null,
    val candidateCount: Int? = null,
    val dropCount: Long? = null,
    val stallCount: Long? = null,
    val status: BenchmarkStatus,
    val error: BenchmarkError? = null,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion > 0)
        require(eventType.isNotBlank())
        require(face == null || speaker == null) {
            "A benchmark event must contain face or speaker stages, not both"
        }
        require(candidateCount == null || candidateCount >= 0)
        require(dropCount == null || dropCount >= 0L)
        require(stallCount == null || stallCount >= 0L)
        require(status == BenchmarkStatus.FAILURE || error == null) {
            "Only FAILURE events may contain error details"
        }
    }
}

object BenchmarkEventJson {
    fun encode(event: BenchmarkEvent): String = event.toJson().toString()

    private fun BenchmarkEvent.toJson(): JsonObject = buildJsonObject {
        put("schemaVersion", schemaVersion)
        put("eventType", eventType)
        put("run", run.toJson())
        putNullable("face", face?.toJson())
        putNullable("speaker", speaker?.toJson())
        putNullable("resources", resources?.toJson())
        putNullable("candidateCount", candidateCount)
        putNullable("dropCount", dropCount)
        putNullable("stallCount", stallCount)
        put("status", status.name)
        putNullable("error", error?.toJson())
    }

    private fun BenchmarkRunMetadata.toJson(): JsonObject = buildJsonObject {
        put("runId", runId)
        put("scenarioId", scenarioId)
        put("timestampEpochMillis", timestampEpochMillis)
        put("timestampElapsedRealtimeMillis", timestampElapsedRealtimeMillis)
        put("device", device.toJson())
        put("buildVariant", buildVariant)
        put("input", input.toJson())
        put("models", JsonArray(models.map { it.toJson() }))
        put("thresholds", thresholds.toJson())
    }

    private fun BenchmarkDevice.toJson(): JsonObject = buildJsonObject {
        put("manufacturer", manufacturer)
        put("model", model)
        put("apiLevel", apiLevel)
        put("abi", abi)
    }

    private fun BenchmarkInput.toJson(): JsonObject = buildJsonObject {
        put("descriptor", descriptor)
        put("sha256", sha256)
        put("preprocessingId", preprocessingId)
        put("datasetId", datasetId)
        put("repetition", repetition)
    }

    private fun BenchmarkModelSelection.toJson(): JsonObject = buildJsonObject {
        put("role", role)
        putNullable("modelSpaceId", modelSpaceId)
        put("artifactId", artifactId)
        put("runtimeId", runtimeId)
    }

    private fun BenchmarkThresholds.toJson(): JsonObject = buildJsonObject {
        putNullable("identification", identification)
        putNullable("minimumLead", minimumLead)
        putNullable("createQuality", createQuality)
        putNullable("updateQuality", updateQuality)
    }

    private fun FacePipelineMetrics.toJson(): JsonObject = buildJsonObject {
        putNullable("captureMillis", captureMillis)
        putNullable("preprocessingMillis", preprocessingMillis)
        putNullable("detectionMillis", detectionMillis)
        putNullable("qualityMillis", qualityMillis)
        putNullable("alignmentMillis", alignmentMillis)
        putNullable("embeddingMillis", embeddingMillis)
        putNullable("identificationMillis", identificationMillis)
        putNullable("repositoryMillis", repositoryMillis)
        putNullable("totalMillis", totalMillis)
    }

    private fun SpeakerPipelineMetrics.toJson(): JsonObject = buildJsonObject {
        putNullable("captureMillis", captureMillis)
        putNullable("vadMillis", vadMillis)
        putNullable("segmentationMillis", segmentationMillis)
        putNullable("trackingMillis", trackingMillis)
        putNullable("qualityMillis", qualityMillis)
        putNullable("embeddingMillis", embeddingMillis)
        putNullable("identificationMillis", identificationMillis)
        putNullable("repositoryMillis", repositoryMillis)
        putNullable("processingMillis", processingMillis)
        putNullable("audioDurationMillis", audioDurationMillis)
        putNullable("realTimeFactor", realTimeFactor())
    }

    private fun ResourceMetrics.toJson(): JsonObject = buildJsonObject {
        put("collectedAtElapsedRealtimeMillis", collectedAtElapsedRealtimeMillis)
        putNullable("appCpuPercent", appCpuPercent)
        putNullable("pssBytes", pssBytes)
        putNullable("javaHeapBytes", javaHeapBytes)
        putNullable("nativeHeapBytes", nativeHeapBytes)
        putNullable("deviceAvailableMemoryBytes", deviceAvailableMemoryBytes)
    }

    private fun BenchmarkError.toJson(): JsonObject = buildJsonObject {
        put("code", code)
        putNullable("message", message)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        name: String,
        value: JsonElement?,
    ) {
        put(name, value ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        name: String,
        value: String?,
    ) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(
        name: String,
        value: Number?,
    ) {
        put(name, value?.let(::JsonPrimitive) ?: JsonNull)
    }
}

private fun validateNullableDurations(vararg values: Double?) {
    validateNullableMeasurements(*values)
    values.forEach { require(it == null || it >= 0.0) }
}

private fun validateNullableMeasurements(vararg values: Double?) {
    values.forEach { require(it == null || it.isFinite()) }
}
