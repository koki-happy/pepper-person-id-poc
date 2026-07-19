package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchmarkResultsScreen(
    outputPath: String,
    recentEvents: List<String>,
    error: String?,
    onBackToSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("測定結果") },
                navigationIcon = {
                    Button(onClick = onBackToSettings, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("← 設定")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("JSON Lines出力", style = MaterialTheme.typography.headlineSmall)
            Text(outputPath, style = MaterialTheme.typography.bodySmall)
            Text(
                "JSONL出力対象: 処理時間・状態・メタデータ / 除外: 画像・音声・特徴量",
                color = MaterialTheme.colorScheme.primary,
            )
            Button(onClick = onRefresh) { Text("直近イベントを更新") }
            error?.let { Text("読込エラー: $it", color = MaterialTheme.colorScheme.error) }
            if (recentEvents.isEmpty()) {
                Text("測定イベント件数: 0")
            }
            recentEvents.asReversed().forEach { event ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = event,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
