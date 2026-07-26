package com.example.pepper_person_id_poc.domain.anonymous

object AnonymousPersistencePolicy {
    fun evaluate(
        decision: IdentificationDecision,
        createEligible: Boolean,
        updateEligible: Boolean,
        reasons: List<String> = emptyList(),
    ): Pair<PersistencePolicyResult, PersistenceOperation> {
        val policy = PersistencePolicyResult(
            createEligible = createEligible,
            updateEligible = updateEligible,
            reasons = reasons.distinct(),
        )
        val operation = when (decision) {
            IdentificationDecision.MATCHED_EXISTING ->
                if (policy.updateEligible) PersistenceOperation.UPDATE else PersistenceOperation.HOLD
            IdentificationDecision.CREATED_NEW ->
                if (policy.createEligible) PersistenceOperation.CREATE else PersistenceOperation.HOLD
            IdentificationDecision.AMBIGUOUS,
            IdentificationDecision.UNKNOWN,
            -> PersistenceOperation.HOLD
        }
        return policy to operation
    }
}
