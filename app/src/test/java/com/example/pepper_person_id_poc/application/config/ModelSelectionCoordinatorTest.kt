package com.example.pepper_person_id_poc.application.config

import com.example.pepper_person_id_poc.domain.model.CompatibilityStatus
import com.example.pepper_person_id_poc.infrastructure.model.ParsedCatalogArtifact
import com.example.pepper_person_id_poc.infrastructure.model.ParsedModelCatalog
import com.example.pepper_person_id_poc.infrastructure.model.ParsedRuntimeCompatibility
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelSelectionCoordinatorTest {
    @Test
    fun buildableExactPairIsSelectable() {
        val coordinator = coordinator(status = "BUILDABLE", bundledFiles = setOf("model.onnx"))

        val result = coordinator.resolve("artifact", "runtime")

        assertThat(result.selectable).isTrue()
        assertThat(result.status).isEqualTo(CompatibilityStatus.BUILDABLE)
        assertThat(result.reason).isNull()
    }

    @Test
    fun blockedPairPreservesCatalogReason() {
        val coordinator = coordinator(
            status = "BLOCKED",
            reason = "ARMv7 runtime evidence is missing",
            bundledFiles = setOf("model.onnx"),
        )

        val result = coordinator.resolve("artifact", "runtime")

        assertThat(result.selectable).isFalse()
        assertThat(result.reason).isEqualTo("ARMv7 runtime evidence is missing")
    }

    @Test
    fun missingArtifactBlocksAnOtherwiseBuildablePair() {
        val result = coordinator(status = "BUILDABLE", bundledFiles = emptySet())
            .resolve("artifact", "runtime")

        assertThat(result.selectable).isFalse()
        assertThat(result.status).isEqualTo(CompatibilityStatus.BLOCKED)
        assertThat(result.reason).contains("model.onnx")
    }

    @Test
    fun runtimeOrAbiWithoutExactCatalogRecordIsUnsupported() {
        val coordinator = coordinator(status = "BUILDABLE", bundledFiles = setOf("model.onnx"))

        assertThat(coordinator.resolve("artifact", "other-runtime").selectable).isFalse()
        assertThat(coordinator.resolve("missing-artifact", "runtime").selectable).isFalse()
    }

    private fun coordinator(
        status: String,
        reason: String? = null,
        bundledFiles: Set<String>,
    ) = ModelSelectionCoordinator(
        catalog = ParsedModelCatalog(
            schemaVersion = 2,
            modelSpaceIds = emptySet(),
            runtimeIds = setOf("runtime"),
            artifactIds = setOf("artifact"),
            artifacts = listOf(
                ParsedCatalogArtifact(
                    artifactId = "artifact",
                    role = "EMBEDDING",
                    format = "ONNX",
                    filename = "model.onnx",
                    runtimeCompatibility = listOf(
                        ParsedRuntimeCompatibility(
                            runtimeId = "runtime",
                            abi = "armeabi-v7a",
                            minApi = 23,
                            status = status,
                            reason = reason,
                        ),
                    ),
                ),
            ),
        ),
        bundledArtifactFileNames = bundledFiles,
        abi = "armeabi-v7a",
        apiLevel = 23,
    )
}
