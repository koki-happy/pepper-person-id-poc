package com.example.pepper_person_id_poc.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun identifiersRejectBlankValues() {
        assertThrows(IllegalArgumentException::class.java) { ModelSpaceId(" ") }
        assertThrows(IllegalArgumentException::class.java) { ArtifactId("") }
        assertThrows(IllegalArgumentException::class.java) { RuntimeId("\t") }
    }

    @Test
    fun embeddingArtifactRequiresModelSpaceId() {
        assertThrows(IllegalArgumentException::class.java) {
            artifact(modelSpaceId = null)
        }
    }

    @Test
    fun convertedArtifactCannotReferenceItselfAsSource() {
        assertThrows(IllegalArgumentException::class.java) {
            artifact(conversion = conversion(sourceArtifactId = ARTIFACT_ID))
        }
    }

    @Test
    fun compatibilityMustReferenceContainingArtifact() {
        assertThrows(IllegalArgumentException::class.java) {
            artifact(
                runtimeCompatibility = listOf(
                    compatibility(artifactId = ArtifactId("different-artifact")),
                ),
            )
        }
    }

    @Test
    fun compatibilityRejectsBlankAbiAndNonPositiveMinApi() {
        assertThrows(IllegalArgumentException::class.java) {
            compatibility(abi = " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            compatibility(minApi = 0)
        }
    }

    @Test
    fun verifiedCompatibilityRequiresEvidence() {
        assertThrows(IllegalArgumentException::class.java) {
            compatibility(
                status = CompatibilityStatus.VERIFIED,
                evidence = null,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            compatibility(
                status = CompatibilityStatus.VERIFIED,
                evidence = " ",
            )
        }
    }

    @Test
    fun blockedAndUnsupportedCompatibilityRequireReason() {
        listOf(
            CompatibilityStatus.BLOCKED,
            CompatibilityStatus.UNSUPPORTED,
        ).forEach { status ->
            assertThrows(IllegalArgumentException::class.java) {
                compatibility(status = status, reason = null)
            }
        }
    }

    @Test
    fun settingsExposeOnlyVerifiedOrBuildablePairs() {
        val statuses = CompatibilityStatus.entries.associateWith { status ->
            compatibility(
                status = status,
                reason = if (
                    status == CompatibilityStatus.BLOCKED ||
                    status == CompatibilityStatus.UNSUPPORTED
                ) {
                    "Not available on this target"
                } else {
                    null
                },
                evidence = if (status == CompatibilityStatus.VERIFIED) {
                    "artifact hash, runtime version, ABI, API, and test result"
                } else {
                    null
                },
            ).selectable
        }

        assertThat(statuses).containsExactly(
            CompatibilityStatus.VERIFIED, true,
            CompatibilityStatus.BUILDABLE, true,
            CompatibilityStatus.CONVERSION_REQUIRED, false,
            CompatibilityStatus.UNSUPPORTED, false,
            CompatibilityStatus.BLOCKED, false,
        )
    }

    private fun compatibility(
        artifactId: ArtifactId = ARTIFACT_ID,
        abi: String = "armeabi-v7a",
        minApi: Int = 23,
        status: CompatibilityStatus = CompatibilityStatus.BUILDABLE,
        reason: String? = null,
        evidence: String? = null,
    ) = ModelRuntimeCompatibility(
        artifactId = artifactId,
        runtimeId = RuntimeId("opencv-5.0.0"),
        abi = abi,
        minApi = minApi,
        status = status,
        reason = reason,
        evidence = evidence,
    )

    private fun conversion(sourceArtifactId: ArtifactId) = ConversionRecord(
        sourceArtifactId = sourceArtifactId,
        tool = "onnx2tf",
        toolRevision = "v1.28.2",
        environmentDigest = "sha256:conversion-environment",
        command = "onnx2tf -i source.onnx -o output",
        outputSha256 = "a".repeat(64),
        tensorMetadata = mapOf("input" to "1,112,112,3"),
        warnings = emptyList(),
    )

    private fun artifact(
        modelSpaceId: ModelSpaceId? = ModelSpaceId("face-sface-128-l2-v1"),
        conversion: ConversionRecord? = null,
        runtimeCompatibility: List<ModelRuntimeCompatibility> = listOf(compatibility()),
    ) = ModelArtifactRecord(
        artifactId = ARTIFACT_ID,
        modelSpaceId = modelSpaceId,
        modality = BiometricModality.FACE,
        role = ModelRole.EMBEDDING,
        architecture = "SFace",
        precision = ModelPrecision.FP32,
        format = ModelFormat.ONNX,
        filename = "face_recognition_sface_2021dec.onnx",
        sourceUrl = "https://example.invalid/models/sface",
        sourceRevision = "2021dec",
        sha256 = "b".repeat(64),
        fileSizeBytes = 1L,
        inputShape = listOf(1, 3, 112, 112),
        inputLayout = "NCHW",
        inputColorOrder = "BGR",
        normalization = mapOf("scale" to "1/127.5", "mean" to "127.5"),
        outputShape = listOf(1, 128),
        outputSemantics = "face embedding",
        opset = 11,
        conversion = conversion,
        runtimeCompatibility = runtimeCompatibility,
        weightLicense = "Apache-2.0",
        commercialUse = CommercialUse.ALLOWED,
        licenseEvidence = "https://example.invalid/licenses/sface",
    )

    private companion object {
        val ARTIFACT_ID = ArtifactId("sface-onnx-fp32")
    }
}
