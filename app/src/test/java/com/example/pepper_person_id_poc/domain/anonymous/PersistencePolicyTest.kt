package com.example.pepper_person_id_poc.domain.anonymous

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PersistencePolicyTest {
    @Test
    fun createdNewWithCreateEligible_selectsCreate() {
        val operation = operation(
            decision = IdentificationDecision.CREATED_NEW,
            createEligible = true,
            updateEligible = false,
        )

        assertThat(operation).isEqualTo(PersistenceOperation.CREATE)
    }

    @Test
    fun matchedExistingWithUpdateEligible_selectsUpdate() {
        val operation = operation(
            decision = IdentificationDecision.MATCHED_EXISTING,
            createEligible = true,
            updateEligible = true,
        )

        assertThat(operation).isEqualTo(PersistenceOperation.UPDATE)
    }

    @Test
    fun ambiguousAlwaysHoldsEvenWhenQualityIsEligible() {
        val operation = operation(
            decision = IdentificationDecision.AMBIGUOUS,
            createEligible = true,
            updateEligible = true,
        )

        assertThat(operation).isEqualTo(PersistenceOperation.HOLD)
    }

    @Test
    fun ineligibleQualityHoldsCreateAndUpdate() {
        val createOperation = operation(
            decision = IdentificationDecision.CREATED_NEW,
            createEligible = false,
            updateEligible = false,
            reasons = listOf("LOW_QUALITY"),
        )
        val updateOperation = operation(
            decision = IdentificationDecision.MATCHED_EXISTING,
            createEligible = false,
            updateEligible = false,
            reasons = listOf("LOW_QUALITY"),
        )

        assertThat(createOperation).isEqualTo(PersistenceOperation.HOLD)
        assertThat(updateOperation).isEqualTo(PersistenceOperation.HOLD)
    }

    @Test
    fun createEligibleButUpdateIneligible_holdsExistingClusterUpdate() {
        val operation = operation(
            decision = IdentificationDecision.MATCHED_EXISTING,
            createEligible = true,
            updateEligible = false,
        )

        assertThat(operation).isEqualTo(PersistenceOperation.HOLD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateEligibilityCannotBeLooserThanCreateEligibility() {
        policy(createEligible = false, updateEligible = true)
    }

    private fun operation(
        decision: IdentificationDecision,
        createEligible: Boolean,
        updateEligible: Boolean,
        reasons: List<String> = emptyList(),
    ) = AnonymousPersistencePolicy.evaluate(
        decision = decision,
        createEligible = createEligible,
        updateEligible = updateEligible,
        reasons = reasons,
    ).second

    private fun policy(
        createEligible: Boolean,
        updateEligible: Boolean,
        reasons: List<String> = emptyList(),
    ) = PersistencePolicyResult(
        createEligible = createEligible,
        updateEligible = updateEligible,
        reasons = reasons,
    )
}
