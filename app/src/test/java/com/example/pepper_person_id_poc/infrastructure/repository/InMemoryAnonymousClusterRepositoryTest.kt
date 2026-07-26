package com.example.pepper_person_id_poc.infrastructure.repository

import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.BiometricModality
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InMemoryAnonymousClusterRepositoryTest {
    @Test
    fun hold_leavesClusterStateAndNextIdUnchanged() {
        val repository = InMemoryAnonymousFaceClusterRepository()
        val modelSpaceId = ModelSpaceId("face-space-a")
        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 100L,
        )
        val before = repository.getAll().single()
        val evaluationBefore = repository.evaluate(
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(1f, 0f),
            threshold = 0.8f,
            minimumLead = 0.1f,
        )

        repository.apply(
            operation = PersistenceOperation.HOLD,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(0f, 1f),
            selectedAnonymousId = before.anonymousId,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 200L,
        )

        val after = repository.getAll().single()
        val evaluationAfter = repository.evaluate(
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(1f, 0f),
            threshold = 0.8f,
            minimumLead = 0.1f,
        )
        assertThat(after.anonymousId).isEqualTo(before.anonymousId)
        assertThat(after.modality).isEqualTo(before.modality)
        assertThat(after.modelSpaceId).isEqualTo(before.modelSpaceId)
        assertThat(after.embeddingDimension).isEqualTo(before.embeddingDimension)
        assertThat(after.normalizedEmbeddingSum.asList())
            .containsExactlyElementsIn(before.normalizedEmbeddingSum.asList())
            .inOrder()
        assertThat(after.centroid.asList())
            .containsExactlyElementsIn(before.centroid.asList())
            .inOrder()
        assertThat(after.updateCount).isEqualTo(before.updateCount)
        assertThat(after.createdAtElapsedRealtime).isEqualTo(before.createdAtElapsedRealtime)
        assertThat(after.updatedAtElapsedRealtime).isEqualTo(before.updatedAtElapsedRealtime)
        assertThat(evaluationAfter).isEqualTo(evaluationBefore)

        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(0f, 1f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 300L,
        )

        assertThat(repository.getAll().map { it.anonymousId })
            .containsExactly("anonymous-face-001", "anonymous-face-002")
            .inOrder()
    }

    @Test
    fun evaluate_returnsOnlyClustersFromRequestedModelSpace() {
        val repository = InMemoryAnonymousFaceClusterRepository()
        val modelSpaceA = ModelSpaceId("face-space-a")
        val modelSpaceB = ModelSpaceId("face-space-b")
        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceA,
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 100L,
        )
        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceB,
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 200L,
        )

        val evaluationA = repository.evaluate(
            modelSpaceId = modelSpaceA,
            embedding = floatArrayOf(1f, 0f),
            threshold = 0.8f,
            minimumLead = 0.1f,
        )
        val evaluationB = repository.evaluate(
            modelSpaceId = modelSpaceB,
            embedding = floatArrayOf(1f, 0f),
            threshold = 0.8f,
            minimumLead = 0.1f,
        )

        assertThat(evaluationA.candidates.map { it.anonymousId })
            .containsExactly("anonymous-face-001")
        assertThat(evaluationB.candidates.map { it.anonymousId })
            .containsExactly("anonymous-face-002")
    }

    @Test
    fun faceAndSpeakerRepositories_keepIndependentModalitiesAndIdNamespaces() {
        val faceRepository = InMemoryAnonymousFaceClusterRepository()
        val speakerRepository = InMemoryAnonymousSpeakerClusterRepository()
        faceRepository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = ModelSpaceId("face-space"),
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 100L,
        )
        speakerRepository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = ModelSpaceId("speaker-space"),
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 100L,
        )

        val faceCluster = faceRepository.getAll().single()
        val speakerCluster = speakerRepository.getAll().single()
        assertThat(faceCluster.modality).isEqualTo(BiometricModality.FACE)
        assertThat(speakerCluster.modality).isEqualTo(BiometricModality.SPEAKER)
        assertThat(faceCluster.anonymousId).isEqualTo("anonymous-face-001")
        assertThat(speakerCluster.anonymousId).isEqualTo("anonymous-speaker-001")
        assertThat(faceRepository.count()).isEqualTo(1)
        assertThat(speakerRepository.count()).isEqualTo(1)
    }
}
