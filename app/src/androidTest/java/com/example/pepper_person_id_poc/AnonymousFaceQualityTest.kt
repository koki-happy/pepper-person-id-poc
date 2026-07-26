package com.example.pepper_person_id_poc

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pepper_person_id_poc.domain.anonymous.AnonymousPersistencePolicy
import com.example.pepper_person_id_poc.domain.anonymous.IdentificationDecision
import com.example.pepper_person_id_poc.domain.anonymous.PersistenceOperation
import com.example.pepper_person_id_poc.domain.model.ModelSpaceId
import com.example.pepper_person_id_poc.infrastructure.repository.InMemoryAnonymousFaceClusterRepository
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnonymousFaceQualityTest {
    @Test
    fun lowQualityHold_keepsRepositoryAndNextAnonymousIdUnchanged() {
        val repository = InMemoryAnonymousFaceClusterRepository()
        val modelSpaceId = ModelSpaceId("face-space")
        repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(1f, 0f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 10L,
        )
        val before = repository.getAll().single()
        val evaluation = repository.evaluate(
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(1f, 0f),
            threshold = 0.8f,
            minimumLead = 0f,
        )
        val (policy, operation) = AnonymousPersistencePolicy.evaluate(
            decision = evaluation.decision,
            createEligible = false,
            updateEligible = false,
            reasons = listOf("FACE_TOO_SMALL", "FACE_BLURRED"),
        )

        val held = repository.apply(
            operation = operation,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(Float.NaN, Float.NaN),
            selectedAnonymousId = evaluation.candidates.single { it.selected }.anonymousId,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 20L,
        )

        assertThat(evaluation.decision).isEqualTo(IdentificationDecision.MATCHED_EXISTING)
        assertThat(policy.createEligible).isFalse()
        assertThat(policy.updateEligible).isFalse()
        assertThat(policy.reasons).containsExactly("FACE_TOO_SMALL", "FACE_BLURRED").inOrder()
        assertThat(operation).isEqualTo(PersistenceOperation.HOLD)
        assertThat(held).isNull()
        assertThat(repository.count()).isEqualTo(1)
        val after = repository.getAll().single()
        assertThat(after.anonymousId).isEqualTo(before.anonymousId)
        assertThat(after.modelSpaceId).isEqualTo(before.modelSpaceId)
        assertThat(after.embeddingDimension).isEqualTo(before.embeddingDimension)
        assertThat(after.normalizedEmbeddingSum.asList())
            .containsExactlyElementsIn(before.normalizedEmbeddingSum.asList())
            .inOrder()
        assertThat(after.centroid.asList()).containsExactlyElementsIn(before.centroid.asList()).inOrder()
        assertThat(after.updateCount).isEqualTo(before.updateCount)
        assertThat(after.createdAtElapsedRealtime).isEqualTo(before.createdAtElapsedRealtime)
        assertThat(after.updatedAtElapsedRealtime).isEqualTo(before.updatedAtElapsedRealtime)

        val next = repository.apply(
            operation = PersistenceOperation.CREATE,
            modelSpaceId = modelSpaceId,
            embedding = floatArrayOf(0f, 1f),
            selectedAnonymousId = null,
            maximumUpdateCount = 20,
            nowElapsedRealtime = 30L,
        )
        assertThat(next!!.anonymousId).isEqualTo("anonymous-face-002")
    }
}
