package com.example.pepper_person_id_poc.domain.metrics

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class BenchmarkEventTest {
    private val run = BenchmarkRunMetadata(
        runId = "run-001",
        scenarioId = "A-FACE-001",
        timestampEpochMillis = 1_000L,
        timestampElapsedRealtimeMillis = 500L,
        device = BenchmarkDevice(
            manufacturer = "Example",
            model = "ARM64 phone",
            apiLevel = 30,
            abi = "arm64-v8a",
        ),
        buildVariant = "benchmarkDebug",
        input = BenchmarkInput(
            descriptor = "fixed-face-frame-001",
            sha256 = "a".repeat(64),
            preprocessingId = "opencv-sface-aligncrop-v1",
            datasetId = "local-fixed-inputs-v1",
            repetition = 1,
        ),
        models = listOf(
            BenchmarkModelSelection(
                role = "FACE_EMBEDDING",
                modelSpaceId = "sface-fp32-l2-v1",
                artifactId = "sface-fp32-onnx",
                runtimeId = "opencv-5.0.0",
            ),
        ),
        thresholds = BenchmarkThresholds(
            identification = 0.55,
            minimumLead = 0.05,
        ),
    )

    @Test
    fun serialize_keepsNullableMeasurementsAsJsonNull() {
        val encoded = BenchmarkEventJson.encode(
            BenchmarkEvent(
                eventType = "face_pipeline",
                run = run,
                face = FacePipelineMetrics(
                    detectionMillis = 12.5,
                    embeddingMillis = null,
                ),
                resources = ResourceMetrics(
                    collectedAtElapsedRealtimeMillis = 550L,
                    appCpuPercent = null,
                    pssBytes = 123_456L,
                ),
                status = BenchmarkStatus.SUCCESS,
            ),
        )
        val root = Json.parseToJsonElement(encoded).jsonObject

        assertThat(root.getValue("face").jsonObject.getValue("embeddingMillis"))
            .isEqualTo(JsonNull)
        assertThat(root.getValue("resources").jsonObject.getValue("appCpuPercent"))
            .isEqualTo(JsonNull)
        assertThat(root.getValue("run").jsonObject.getValue("runId").toString())
            .isEqualTo("\"run-001\"")
    }

    @Test
    fun serialize_includesRunConditionAndModelMetadata() {
        val encoded = BenchmarkEventJson.encode(
            BenchmarkEvent(
                eventType = "face_pipeline",
                run = run,
                status = BenchmarkStatus.SUCCESS,
            ),
        )

        assertThat(encoded).contains("\"scenarioId\":\"A-FACE-001\"")
        assertThat(encoded).contains("\"abi\":\"arm64-v8a\"")
        assertThat(encoded).contains("\"buildVariant\":\"benchmarkDebug\"")
        assertThat(encoded).contains("\"artifactId\":\"sface-fp32-onnx\"")
        assertThat(encoded).contains("\"runtimeId\":\"opencv-5.0.0\"")
        assertThat(encoded).contains("\"preprocessingId\":\"opencv-sface-aligncrop-v1\"")
        assertThat(encoded).contains("\"datasetId\":\"local-fixed-inputs-v1\"")
        assertThat(encoded).contains("\"identification\":0.55")
        assertThat(encoded).doesNotContain("embeddingVector")
        assertThat(encoded).doesNotContain("rawMedia")
    }
}
