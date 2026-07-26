package com.example.pepper_person_id_poc.domain.anonymous

data class PersistencePolicyResult(
    val createEligible: Boolean,
    val updateEligible: Boolean,
    val reasons: List<String>,
) {
    init {
        require(!updateEligible || createEligible) {
            "update eligibility cannot be less strict than create eligibility"
        }
        require(reasons.none(String::isBlank))
    }
}

enum class PersistenceOperation {
    CREATE,
    UPDATE,
    HOLD,
}
