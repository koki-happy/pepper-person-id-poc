import groovy.json.JsonSlurper
import java.io.File

data class DistributionSelection(
    val artifactIds: Set<String>,
    val runtimeIds: Set<String>,
) {
    init {
        require(artifactIds.isNotEmpty()) { "At least one artifact must be selected" }
        require(runtimeIds.isNotEmpty()) { "At least one runtime must be selected" }
        require(artifactIds.none(String::isBlank)) { "Artifact IDs must not be blank" }
        require(runtimeIds.none(String::isBlank)) { "Runtime IDs must not be blank" }
    }
}

data class LicenseBlocker(
    val recordType: String,
    val recordId: String,
    val reasons: List<String>,
)

data class LicenseGateResult(
    val includedArtifactIds: Set<String>,
    val includedRuntimeIds: Set<String>,
    val blockers: List<LicenseBlocker>,
) {
    val approved: Boolean get() = blockers.isEmpty()

    fun requireApproved() {
        check(approved) {
            blockers.joinToString(
                prefix = "Distribution license gate rejected included records: ",
                separator = "; ",
            ) { blocker ->
                "${blocker.recordType}/${blocker.recordId}: ${blocker.reasons.joinToString()}"
            }
        }
    }
}

object ModelLicenseGate {
    private val rejectedValues = setOf(
        "UNVERIFIED",
        "UNKNOWN",
        "TBD",
        "TODO",
        "PENDING",
        "N/A",
        "NA",
        "NONE",
    )

    fun evaluate(catalogFile: File, selection: DistributionSelection): LicenseGateResult {
        require(catalogFile.isFile) { "Model catalog is missing: ${catalogFile.absolutePath}" }
        val catalog = JsonSlurper().parse(catalogFile) as? Map<*, *>
            ?: error("Model catalog must be a JSON object")
        check((catalog["schemaVersion"] as? Number)?.toInt() == 2) {
            "Distribution license gate requires model catalog schemaVersion=2"
        }

        val artifacts = catalog.records("artifacts", "artifactId")
        val runtimes = catalog.records("runtimes", "runtimeId")
        val blockers = mutableListOf<LicenseBlocker>()

        selection.artifactIds.sorted().forEach { artifactId ->
            val artifact = artifacts[artifactId]
            if (artifact == null) {
                blockers += LicenseBlocker("artifact", artifactId, listOf("record is missing"))
            } else {
                val reasons = buildList {
                    validateLicense(
                        license = artifact.string("weightLicense"),
                        commercialUse = artifact.string("commercialUse"),
                        evidence = artifact.string("licenseEvidence"),
                        requireCommercialApproval = true,
                    )
                }
                if (reasons.isNotEmpty()) {
                    blockers += LicenseBlocker("artifact", artifactId, reasons)
                }
            }
        }

        selection.runtimeIds.sorted().forEach { runtimeId ->
            val runtime = runtimes[runtimeId]
            if (runtime == null) {
                blockers += LicenseBlocker("runtime", runtimeId, listOf("record is missing"))
            } else {
                val reasons = buildList {
                    validateLicense(
                        license = runtime.string("license"),
                        commercialUse = null,
                        evidence = runtime.string("licenseEvidence"),
                        requireCommercialApproval = false,
                    )
                }
                if (reasons.isNotEmpty()) {
                    blockers += LicenseBlocker("runtime", runtimeId, reasons)
                }
            }
        }

        return LicenseGateResult(
            includedArtifactIds = selection.artifactIds,
            includedRuntimeIds = selection.runtimeIds,
            blockers = blockers,
        )
    }

    private fun MutableList<String>.validateLicense(
        license: String?,
        commercialUse: String?,
        evidence: String?,
        requireCommercialApproval: Boolean,
    ) {
        when {
            license.isNullOrBlank() -> add("license is missing")
            license.trim().uppercase() in rejectedValues -> add("license is not verified ('$license')")
        }
        if (requireCommercialApproval && commercialUse != "ALLOWED") {
            add("commercialUse must be ALLOWED (found '$commercialUse')")
        }
        when {
            evidence.isNullOrBlank() -> add("licenseEvidence is missing")
            evidence.trim().uppercase() in rejectedValues ->
                add("licenseEvidence is not verified ('$evidence')")
            !evidence.startsWith("https://") -> add("licenseEvidence must be an https URL")
        }
    }

    private fun Map<*, *>.records(arrayName: String, idName: String): Map<String, Map<*, *>> {
        val records = (this[arrayName] as? List<*>)
            ?.mapIndexed { index, value ->
                value as? Map<*, *> ?: error("$arrayName[$index] must be an object")
            }
            ?: error("Model catalog must contain $arrayName[]")
        val keyed = records.associateBy { record ->
            record.string(idName)?.takeIf(String::isNotBlank)
                ?: error("$arrayName[].$idName must be a nonblank string")
        }
        check(keyed.size == records.size) { "$arrayName[].$idName values must be unique" }
        return keyed
    }

    private fun Map<*, *>.string(name: String): String? = this[name] as? String
}
