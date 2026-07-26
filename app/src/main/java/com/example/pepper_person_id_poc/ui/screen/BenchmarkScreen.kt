package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.metrics.BenchmarkModelSelection
import com.example.pepper_person_id_poc.infrastructure.metrics.BenchmarkDistribution

data class BenchmarkConfigurationUiState(
    val scenarioId: String = "",
    val inputDescriptor: String = "",
    val inputSha256: String = "",
    val preprocessingId: String = "",
    val datasetId: String = "",
    val repetitions: String = "1",
)

fun BenchmarkConfigurationUiState.validationErrors(): List<String> = buildList {
    if (scenarioId.isBlank()) add("シナリオIDを入力してください")
    if (inputDescriptor.isBlank()) add("入力記述を入力してください")
    if (!inputSha256.matches(Regex("[0-9a-f]{64}"))) add("入力SHA-256は小文字64桁で入力してください")
    if (preprocessingId.isBlank()) add("前処理IDを入力してください")
    if (datasetId.isBlank()) add("データセットIDを入力してください")
    if (repetitions.toIntOrNull()?.let { it > 0 } != true) add("反復回数は1以上の整数で入力してください")
}

@Composable
fun BenchmarkScreen(
    configuration: BenchmarkConfigurationUiState,
    modelSelections: List<BenchmarkModelSelection>,
    distribution: BenchmarkDistribution,
    candidateExportEnabled: Boolean,
    statusMessage: String? = null,
    onConfigurationChange: (BenchmarkConfigurationUiState) -> Unit,
    onRun: () -> Unit,
    onEnableCandidateExport: () -> Unit,
    onBack: () -> Unit,
) {
    val errors = configuration.validationErrors()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("benchmark-screen"),
    ) {
        Text("ベンチマーク構成")
        BenchmarkTextField(
            label = "シナリオID",
            value = configuration.scenarioId,
            onValueChange = { onConfigurationChange(configuration.copy(scenarioId = it)) },
        )
        BenchmarkTextField(
            label = "入力記述",
            value = configuration.inputDescriptor,
            onValueChange = { onConfigurationChange(configuration.copy(inputDescriptor = it)) },
        )
        BenchmarkTextField(
            label = "入力SHA-256",
            value = configuration.inputSha256,
            onValueChange = {
                onConfigurationChange(configuration.copy(inputSha256 = it.trim().lowercase()))
            },
        )
        BenchmarkTextField(
            label = "前処理ID",
            value = configuration.preprocessingId,
            onValueChange = { onConfigurationChange(configuration.copy(preprocessingId = it)) },
        )
        BenchmarkTextField(
            label = "データセットID",
            value = configuration.datasetId,
            onValueChange = { onConfigurationChange(configuration.copy(datasetId = it)) },
        )
        OutlinedTextField(
            value = configuration.repetitions,
            onValueChange = { onConfigurationChange(configuration.copy(repetitions = it)) },
            label = { Text("反復回数") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("選択モデル")
        if (modelSelections.isEmpty()) {
            Text("未選択")
        } else {
            modelSelections.forEach { selection ->
                Text(
                    "${selection.role}: artifact=${selection.artifactId}, " +
                        "runtime=${selection.runtimeId}, modelSpace=${selection.modelSpaceId ?: "N/A"}",
                )
            }
        }
        if (errors.isNotEmpty()) {
            errors.forEach { Text(it) }
        }
        statusMessage?.let { Text(it, modifier = Modifier.testTag("benchmark-status")) }
        when (distribution) {
            BenchmarkDistribution.BENCHMARK -> Text("JSONL: 有効（benchmark）")
            BenchmarkDistribution.CANDIDATE -> {
                Text(
                    if (candidateExportEnabled) {
                        "JSONL: 明示エクスポート有効"
                    } else {
                        "JSONL: 無効（candidate既定）"
                    },
                )
                if (!candidateExportEnabled) {
                    Button(
                        onClick = onEnableCandidateExport,
                        modifier = Modifier.testTag("enable-candidate-export"),
                    ) {
                Text("candidateのLogcat出力を有効化")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) {
                Text("戻る")
            }
            Button(
                onClick = onRun,
                enabled = errors.isEmpty() && modelSelections.isNotEmpty(),
                modifier = Modifier.testTag("run-benchmark"),
            ) {
                Text("実行")
            }
        }
    }
}

@Composable
private fun BenchmarkTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
