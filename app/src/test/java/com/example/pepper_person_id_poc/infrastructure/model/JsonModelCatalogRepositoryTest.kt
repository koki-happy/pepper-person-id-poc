package com.example.pepper_person_id_poc.infrastructure.model

import org.junit.Assert.assertThrows
import org.junit.Test

class JsonModelCatalogRepositoryTest {
    private val repository = JsonModelCatalogRepository()

    @Test
    fun parseAcceptsValidSchemaV2Catalog() {
        repository.parse(catalogJson())
    }

    @Test
    fun parseRejectsNonV2Schema() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(catalogJson(schemaVersion = 1))
        }
    }

    @Test
    fun parseRejectsDuplicateArtifactIds() {
        val artifact = artifactJson()

        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(catalogJson(artifacts = listOf(artifact, artifact)))
        }
    }

    @Test
    fun parseRejectsMalformedArtifactSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(
                catalogJson(
                    artifacts = listOf(artifactJson(sha256 = "not-a-sha256")),
                ),
            )
        }
    }

    @Test
    fun parseRejectsMalformedConversionOutputSha256() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(
                catalogJson(
                    artifacts = listOf(
                        artifactJson(),
                        artifactJson(
                            artifactId = "sface-litert-fp32",
                            format = "TFLITE",
                            conversion = conversionJson(
                                sourceArtifactId = "sface-onnx-fp32",
                                outputSha256 = "ABC",
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun parseRejectsUnknownModelSpaceReference() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(
                catalogJson(
                    artifacts = listOf(artifactJson(modelSpaceId = "missing-model-space")),
                ),
            )
        }
    }

    @Test
    fun parseRejectsUnknownRuntimeReference() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(
                catalogJson(
                    artifacts = listOf(artifactJson(runtimeId = "missing-runtime")),
                ),
            )
        }
    }

    @Test
    fun parseRejectsUnknownConversionSourceArtifactReference() {
        assertThrows(IllegalArgumentException::class.java) {
            repository.parse(
                catalogJson(
                    artifacts = listOf(
                        artifactJson(
                            artifactId = "sface-litert-fp32",
                            format = "TFLITE",
                            conversion = conversionJson(sourceArtifactId = "missing-source"),
                        ),
                    ),
                ),
            )
        }
    }

    private fun catalogJson(
        schemaVersion: Int = 2,
        artifacts: List<String> = listOf(artifactJson()),
    ) = """
        {
          "schemaVersion": $schemaVersion,
          "modelSpaces": [
            {
              "modelSpaceId": "face-sface-128-l2-v1",
              "modality": "FACE",
              "embeddingDimension": 128,
              "normalization": "L2"
            }
          ],
          "runtimes": [
            {
              "runtimeId": "opencv-5.0.0",
              "name": "OpenCV",
              "version": "5.0.0",
              "provider": "CPU"
            }
          ],
          "artifacts": [
            ${artifacts.joinToString(",\n")}
          ]
        }
    """.trimIndent()

    private fun artifactJson(
        artifactId: String = "sface-onnx-fp32",
        modelSpaceId: String = "face-sface-128-l2-v1",
        runtimeId: String = "opencv-5.0.0",
        sha256: String = "a".repeat(64),
        format: String = "ONNX",
        conversion: String = "null",
    ) = """
        {
          "artifactId": "$artifactId",
          "modelSpaceId": "$modelSpaceId",
          "modality": "FACE",
          "role": "EMBEDDING",
          "architecture": "SFace",
          "precision": "FP32",
          "format": "$format",
          "filename": "face_recognition_sface_2021dec.${format.lowercase()}",
          "sourceUrl": "https://example.invalid/models/sface",
          "sourceRevision": "2021dec",
          "sha256": "$sha256",
          "fileSizeBytes": 1,
          "inputShape": [1, 3, 112, 112],
          "inputLayout": "NCHW",
          "inputColorOrder": "BGR",
          "normalization": {
            "scale": "1/127.5",
            "mean": "127.5",
            "channelOrder": "BGR"
          },
          "outputShape": [1, 128],
          "outputSemantics": "face embedding",
          "opset": ${if (format == "ONNX") "11" else "null"},
          "conversion": $conversion,
          "runtimeCompatibility": [
            {
              "artifactId": "$artifactId",
              "runtimeId": "$runtimeId",
              "abi": "armeabi-v7a",
              "minApi": 23,
              "status": "BUILDABLE",
              "reason": null,
              "evidence": null
            }
          ],
          "weightLicense": "Apache-2.0",
          "commercialUse": "ALLOWED",
          "licenseEvidence": "https://example.invalid/licenses/sface"
        }
    """.trimIndent()

    private fun conversionJson(
        sourceArtifactId: String,
        outputSha256: String = "b".repeat(64),
    ) = """
        {
          "sourceArtifactId": "$sourceArtifactId",
          "tool": "onnx2tf",
          "toolRevision": "v1.28.2",
          "environmentDigest": "sha256:conversion-environment",
          "command": "onnx2tf -i source.onnx -o output",
          "outputSha256": "$outputSha256",
          "tensorMetadata": {
            "input": "1,112,112,3",
            "output": "1,128"
          },
          "warnings": []
        }
    """.trimIndent()
}
