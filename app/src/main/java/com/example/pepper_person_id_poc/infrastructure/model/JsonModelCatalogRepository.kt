package com.example.pepper_person_id_poc.infrastructure.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

data class ParsedModelCatalog(
    val schemaVersion: Int,
    val modelSpaceIds: Set<String>,
    val runtimeIds: Set<String>,
    val artifactIds: Set<String>,
)

class JsonModelCatalogRepository {
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(source: String): ParsedModelCatalog {
        val root = json.parseToJsonElement(source).jsonObject
        val schemaVersion = root.requiredInt("schemaVersion")
        require(schemaVersion == 2) { "Unsupported model catalog schemaVersion=$schemaVersion" }

        val modelSpaces = root.requiredObjects("modelSpaces")
        val runtimes = root.requiredObjects("runtimes")
        val artifacts = root.requiredObjects("artifacts")
        val modelSpaceIds = modelSpaces.uniqueIds("modelSpaceId")
        val runtimeIds = runtimes.uniqueIds("runtimeId")
        val artifactIds = artifacts.uniqueIds("artifactId")

        artifacts.forEach { artifact ->
            val artifactId = artifact.requiredString("artifactId")
            val role = artifact.requiredString("role")
            val modelSpaceId = artifact.optionalString("modelSpaceId")
            if (role == "EMBEDDING") {
                require(modelSpaceId != null) { "$artifactId embedding artifact requires modelSpaceId" }
            }
            if (modelSpaceId != null) {
                require(modelSpaceId in modelSpaceIds) {
                    "$artifactId references unknown modelSpaceId=$modelSpaceId"
                }
            }

            val format = artifact.requiredString("format")
            val filename = artifact.optionalString("filename")
            require(filename != null || format == "SDK_INTERNAL") {
                "$artifactId requires filename"
            }
            artifact.optionalString("sha256")?.let { requireSha256("$artifactId sha256", it) }
            artifact.optionalLong("fileSizeBytes")?.let {
                require(it > 0) { "$artifactId fileSizeBytes must be positive" }
            }

            artifact.requiredObjects("runtimeCompatibility").forEach { compatibility ->
                require(compatibility.requiredString("artifactId") == artifactId) {
                    "runtime compatibility must reference $artifactId"
                }
                val runtimeId = compatibility.requiredString("runtimeId")
                require(runtimeId in runtimeIds) {
                    "$artifactId references unknown runtimeId=$runtimeId"
                }
                require(compatibility.requiredString("abi").isNotBlank())
                require(compatibility.requiredInt("minApi") >= 1)
                when (val status = compatibility.requiredString("status")) {
                    "VERIFIED" -> require(!compatibility.optionalString("evidence").isNullOrBlank()) {
                        "VERIFIED compatibility requires evidence"
                    }
                    "BLOCKED", "UNSUPPORTED" ->
                        require(!compatibility.optionalString("reason").isNullOrBlank()) {
                            "$status compatibility requires a reason"
                        }
                    "BUILDABLE", "CONVERSION_REQUIRED" -> Unit
                    else -> error("Unknown compatibility status=$status")
                }
            }

            artifact.optionalObject("conversion")?.let { conversion ->
                val sourceArtifactId = conversion.requiredString("sourceArtifactId")
                require(sourceArtifactId != artifactId) {
                    "$artifactId conversion cannot reference itself"
                }
                require(sourceArtifactId in artifactIds) {
                    "$artifactId conversion references unknown sourceArtifactId=$sourceArtifactId"
                }
                requireSha256(
                    "$artifactId conversion outputSha256",
                    conversion.requiredString("outputSha256"),
                )
            }
        }

        return ParsedModelCatalog(
            schemaVersion = schemaVersion,
            modelSpaceIds = modelSpaceIds,
            runtimeIds = runtimeIds,
            artifactIds = artifactIds,
        )
    }
}

private fun List<JsonObject>.uniqueIds(field: String): Set<String> {
    val values = map { it.requiredString(field) }
    require(values.size == values.toSet().size) { "$field values must be unique" }
    return values.toSet()
}

private fun JsonObject.requiredObjects(field: String): List<JsonObject> =
    (get(field) as? JsonArray)
        ?.map(JsonElement::jsonObject)
        ?: error("$field must be an array")

private fun JsonObject.requiredString(field: String): String =
    optionalString(field)?.also { require(it.isNotBlank()) { "$field must not be blank" } }
        ?: error("$field must be a string")

private fun JsonObject.optionalString(field: String): String? =
    when (val element = get(field)) {
        null, JsonNull -> null
        is JsonPrimitive -> element.content
        else -> error("$field must be a string or null")
    }

private fun JsonObject.requiredInt(field: String): Int =
    get(field)?.jsonPrimitive?.int ?: error("$field must be an integer")

private fun JsonObject.optionalLong(field: String): Long? =
    when (val element = get(field)) {
        null, JsonNull -> null
        is JsonPrimitive -> element.long
        else -> error("$field must be an integer or null")
    }

private fun JsonObject.optionalObject(field: String): JsonObject? =
    when (val element = get(field)) {
        null, JsonNull -> null
        is JsonObject -> element
        else -> error("$field must be an object or null")
    }

private fun requireSha256(field: String, value: String) {
    require(value.matches(Regex("[0-9a-f]{64}"))) {
        "$field must be 64 lowercase hex digits"
    }
}
