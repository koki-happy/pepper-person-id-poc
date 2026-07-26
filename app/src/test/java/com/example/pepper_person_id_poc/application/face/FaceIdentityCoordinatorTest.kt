package com.example.pepper_person_id_poc.application.face

import com.example.pepper_person_id_poc.testsupport.InMemoryAnonymousFaceRepository
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationDecision
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.face.FaceQualityAssessment
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.infrastructure.face.FaceFeatureObservation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FaceIdentityCoordinatorTest {
    @Test
    fun close_doesNotDeleteRepository() {
        val repository = InMemoryAnonymousFaceRepository()
        val coordinator = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)
        coordinator.onFeatureObservations(listOf(feature("track-1", floatArrayOf(1f, 0f), 2L)))

        coordinator.close()

        assertThat(repository.count()).isEqualTo(1)
    }

    @Test
    fun reappearingFace_reusesPersistentAnonymousId() {
        val repository = InMemoryAnonymousFaceRepository()
        val first = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)
        first.onFeatureObservations(listOf(feature("track-1", floatArrayOf(1f, 0f), 1L)))
        val id = first.state.value.results.getValue("track-1").anonymousId
        first.close()

        val recreated = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)
        recreated.onFeatureObservations(listOf(feature("track-9", floatArrayOf(0.99f, 0.01f), 1L)))

        assertThat(recreated.state.value.results.getValue("track-9").anonymousId).isEqualTo(id)
    }

    @Test
    fun multipleFaces_returnEveryCandidateAndReserveSelectedAnonymousIdWithinFrame() {
        val repository = InMemoryAnonymousFaceRepository()
        val modelSpaceId = ModelSpaceId("face-model")
        listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
        ).forEachIndexed { index, embedding ->
            repository.apply(
                operation = PersistenceOperation.CREATE,
                modelSpaceId = modelSpaceId,
                embedding = embedding,
                selectedAnonymousId = null,
                maximumUpdateCount = 20,
                nowElapsedRealtime = index.toLong(),
            )
        }
        val coordinator = FaceIdentityCoordinator(repository, modelSpaceId.value, 0.8f, 20)

        coordinator.onFeatureObservations(
            listOf(
                feature("track-1", floatArrayOf(1f, 0f, 0f), 3L),
                feature("track-2", floatArrayOf(0.999f, 0.001f, 0f), 4L),
            ),
        )

        val first = coordinator.state.value.results.getValue("track-1")
        val second = coordinator.state.value.results.getValue("track-2")
        assertThat(first.evaluation!!.candidates.map { it.anonymousId }).containsExactly(
            "anonymous-face-001",
            "anonymous-face-002",
            "anonymous-face-003",
        ).inOrder()
        assertThat(second.evaluation!!.candidates.map { it.anonymousId }).containsExactly(
            "anonymous-face-001",
            "anonymous-face-002",
            "anonymous-face-003",
        ).inOrder()
        assertThat(first.evaluation.candidates.single { it.selected }.anonymousId)
            .isEqualTo("anonymous-face-001")
        assertThat(first.persistenceOperation).isEqualTo(PersistenceOperation.UPDATE)
        assertThat(second.evaluation.decision).isEqualTo(IdentificationDecision.AMBIGUOUS)
        assertThat(second.evaluation.candidates.any { it.selected }).isFalse()
        assertThat(second.persistenceOperation).isEqualTo(PersistenceOperation.HOLD)
        assertThat(second.anonymousId).isEqualTo("unknown")
        assertThat(repository.count()).isEqualTo(3)
        assertThat(repository.getAll().associate { it.anonymousId to it.updateCount }).containsExactly(
            "anonymous-face-001", 2,
            "anonymous-face-002", 1,
            "anonymous-face-003", 1,
        )
    }

    @Test
    fun qualityAssessment_doesNotBlockFaceUpdate() {
        val repository = InMemoryAnonymousFaceRepository()
        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = ModelSpaceId("face-model"),
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 1L,
        )
        val coordinator = FaceIdentityCoordinator(repository, "face-model", 0.8f, 20)

        coordinator.onFeatureObservations(
            listOf(
                feature(
                    trackId = "track-low-quality",
                    embedding = floatArrayOf(1f, 0f),
                    embeddingTimeMillis = 2L,
                    qualityAssessment = FaceQualityAssessment(
                        createEligible = false,
                        updateEligible = false,
                        rejectionReasons = listOf("INSUFFICIENT_SHARPNESS"),
                        detectionConfidence = 0.9f,
                    ),
                ),
            ),
        )

        val result = coordinator.state.value.results.getValue("track-low-quality")
        assertThat(result.evaluation!!.candidates).hasSize(1)
        assertThat(result.persistenceOperation).isEqualTo(PersistenceOperation.UPDATE)
        assertThat(result.persistencePolicy!!.reasons).isEmpty()
        assertThat(repository.getAll().single().updateCount).isEqualTo(2)
    }

    @Test
    fun stageTimings_includeUpstreamAndCoordinatorStages() {
        val repository = InMemoryAnonymousFaceRepository()
        var nanos = 0L
        val coordinator = FaceIdentityCoordinator(
            repository = repository,
            modelId = "face-model",
            threshold = 0.8f,
            maximumUpdateCount = 20,
            nanoTime = {
                nanos += 1_000_000L
                nanos
            },
            elapsedRealtimeMillis = { 123L },
        )

        coordinator.onFeatureObservations(
            listOf(
                feature("track-1", floatArrayOf(1f, 0f), 7L).copy(
                    preprocessingTimeMillis = 2L,
                    detectionTimeMillis = 3L,
                    alignmentTimeMillis = 5L,
                ),
            ),
        )

        val metrics = coordinator.state.value.pipelineMetrics!!
        assertThat(metrics.preprocessingMillis).isEqualTo(2.0)
        assertThat(metrics.detectionMillis).isEqualTo(3.0)
        assertThat(metrics.qualityMillis).isNull()
        assertThat(metrics.alignmentMillis).isEqualTo(5.0)
        assertThat(metrics.embeddingMillis).isEqualTo(7.0)
        assertThat(metrics.scoringMillis).isGreaterThan(0.0)
        assertThat(metrics.policyMillis).isGreaterThan(0.0)
        assertThat(metrics.repositoryMillis).isGreaterThan(0.0)
        assertThat(metrics.uiMillis).isGreaterThan(0.0)
        assertThat(metrics.totalMillis).isAtLeast(metrics.measuredStageTotalMillis())
    }

    private fun feature(
        trackId: String,
        embedding: FloatArray,
        embeddingTimeMillis: Long,
        qualityAssessment: FaceQualityAssessment = FaceQualityAssessment(
            createEligible = true,
            updateEligible = true,
            rejectionReasons = emptyList(),
            detectionConfidence = 0.9f,
        ),
    ) = FaceFeatureObservation(
        trackId = trackId,
        embedding = embedding,
        embeddingTimeMillis = embeddingTimeMillis,
        qualityAssessment = qualityAssessment,
    )
}
