package com.example.pepper_person_id_poc.ui.screen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BenchmarkConfigurationUiStateTest {
    @Test
    fun completeConfiguration_hasNoValidationErrors() {
        val configuration = BenchmarkConfigurationUiState(
            scenarioId = "A-FACE-001",
            inputDescriptor = "fixed aligned face",
            inputSha256 = "a".repeat(64),
            preprocessingId = "sface-112x112-rgb",
            datasetId = "local-fixed-v1",
            repetitions = "10",
        )

        assertThat(configuration.validationErrors()).isEmpty()
    }

    @Test
    fun missingComparisonConditions_areReported() {
        val errors = BenchmarkConfigurationUiState(repetitions = "0").validationErrors()

        assertThat(errors).hasSize(6)
        assertThat(errors).contains("反復回数は1以上の整数で入力してください")
    }
}
