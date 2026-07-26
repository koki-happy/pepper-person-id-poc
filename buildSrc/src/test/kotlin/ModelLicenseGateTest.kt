import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files

class ModelLicenseGateTest {
    @Test
    fun approvedIncludedArtifactAndRuntimePass() {
        val result = ModelLicenseGate.evaluate(
            catalog(
                artifactLicense = "Apache-2.0",
                commercialUse = "ALLOWED",
                artifactEvidence = "https://example.test/artifact-license",
                runtimeLicense = "MIT",
                runtimeEvidence = "https://example.test/runtime-license",
            ),
            selection(),
        )

        assertTrue(result.approved)
        result.requireApproved()
    }

    @Test
    fun unverifiedIncludedArtifactFailsClosed() {
        val result = ModelLicenseGate.evaluate(
            catalog(artifactLicense = "UNVERIFIED"),
            selection(),
        )

        assertFalse(result.approved)
        assertTrue(result.blockers.single().reasons.any { "not verified" in it })
        assertThrows<IllegalStateException> { result.requireApproved() }
    }

    @Test
    fun nonCommercialIncludedArtifactFailsClosed() {
        val result = ModelLicenseGate.evaluate(
            catalog(commercialUse = "UNKNOWN"),
            selection(),
        )

        assertFalse(result.approved)
        assertTrue(result.blockers.single().reasons.any { "commercialUse" in it })
    }

    @Test
    fun unverifiedRuntimeFailsClosed() {
        val result = ModelLicenseGate.evaluate(
            catalog(runtimeLicense = "UNVERIFIED", runtimeEvidence = null),
            selection(),
        )

        assertFalse(result.approved)
        assertTrue(result.blockers.single().recordType == "runtime")
    }

    @Test
    fun missingSelectedRecordsFailClosed() {
        val result = ModelLicenseGate.evaluate(
            catalog(),
            DistributionSelection(setOf("missing-artifact"), setOf("missing-runtime")),
        )

        assertFalse(result.approved)
        assertTrue(result.blockers.map(LicenseBlocker::recordType).toSet() == setOf("artifact", "runtime"))
    }

    @Test
    fun invalidEvidenceUrlFailsClosed() {
        val result = ModelLicenseGate.evaluate(
            catalog(artifactEvidence = "file:///license.txt"),
            selection(),
        )

        assertFalse(result.approved)
        assertTrue(result.blockers.single().reasons.any { "https URL" in it })
    }

    private fun selection() = DistributionSelection(
        artifactIds = setOf("artifact-1"),
        runtimeIds = setOf("runtime-1"),
    )

    private fun catalog(
        artifactLicense: String? = "Apache-2.0",
        commercialUse: String? = "ALLOWED",
        artifactEvidence: String? = "https://example.test/artifact-license",
        runtimeLicense: String? = "MIT",
        runtimeEvidence: String? = "https://example.test/runtime-license",
    ) = Files.createTempFile("model-catalog", ".json").toFile().apply {
        deleteOnExit()
        writeText(
            """
            {
              "schemaVersion": 2,
              "distributionRevision": "test",
              "artifacts": [{
                "artifactId": "artifact-1",
                "filename": "artifact.onnx",
                "weightLicense": ${artifactLicense.json()},
                "commercialUse": ${commercialUse.json()},
                "licenseEvidence": ${artifactEvidence.json()}
              }],
              "runtimes": [{
                "runtimeId": "runtime-1",
                "version": "1.0",
                "license": ${runtimeLicense.json()},
                "licenseEvidence": ${runtimeEvidence.json()}
              }]
            }
            """.trimIndent(),
        )
    }

    private fun String?.json(): String = this?.let { "\"$it\"" } ?: "null"
}
