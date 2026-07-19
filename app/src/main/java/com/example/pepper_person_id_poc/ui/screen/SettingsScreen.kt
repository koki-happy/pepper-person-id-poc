package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PocSettings,
    settingsSaved: Boolean,
    onFaceThresholdChanged: (Float) -> Unit,
    onFaceMarginChanged: (Float) -> Unit,
    onFaceRegistrationAnalysisIntervalChanged: (Long) -> Unit,
    onFaceIdentificationAnalysisIntervalChanged: (Long) -> Unit,
    onFacePoseStableDurationChanged: (Long) -> Unit,
    onFaceFrontYawChanged: (Float) -> Unit,
    onFaceFrontPitchChanged: (Float) -> Unit,
    onFaceSideMinimumYawChanged: (Float) -> Unit,
    onFaceSideMaximumYawChanged: (Float) -> Unit,
    onFaceSmoothingSampleCountChanged: (Int) -> Unit,
    onSpeakerThresholdChanged: (Float) -> Unit,
    onSpeakerMarginChanged: (Float) -> Unit,
    onCombinedThresholdChanged: (Float) -> Unit,
    onObservationWindowChanged: (Long) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onOpenModelSelection: () -> Unit,
    onSave: () -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("pepper-person-id-poc") }) },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("設定", style = MaterialTheme.typography.headlineMedium)
            Text("起動時はこの画面を表示します。各機能画面の戻る操作でもここへ戻ります。")

            SettingsCard(title = "使用モデル") {
                Text("顔: ${settings.faceModel.displayName}")
                Text("話者: ${settings.speakerModel.displayName}")
                Text(
                    "話者の閾値・margin候補はJVS studio由来・Pepper実機で再調整必須です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = onOpenModelSelection, modifier = Modifier.fillMaxWidth()) {
                    Text("顔・話者モデルを選択")
                }
            }

            SettingsCard(title = "識別閾値") {
                ThresholdSlider("Face Identification", settings.faceThreshold, onFaceThresholdChanged)
                ThresholdSlider("Speaker Identification", settings.speakerThreshold, onSpeakerThresholdChanged)
                ThresholdSlider(
                    "Speaker Top-2 Margin",
                    settings.speakerMargin,
                    onSpeakerMarginChanged,
                    PocSettings.MARGIN_RANGE,
                )
                ThresholdSlider("Combined", settings.combinedThreshold, onCombinedThresholdChanged)
                Text("顔観測時間: ${settings.observationWindowMillis} ms")
                Slider(
                    value = settings.observationWindowMillis.toFloat(),
                    onValueChange = { onObservationWindowChanged(it.toLong()) },
                    valueRange = PocSettings.OBSERVATION_WINDOW_RANGE.first.toFloat()..
                        PocSettings.OBSERVATION_WINDOW_RANGE.last.toFloat(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("デバッグ表示")
                        Text(
                            "Unknown時の最上位候補とスコアを表示します",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = settings.debugMode,
                        onCheckedChange = onDebugModeChanged,
                    )
                }
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settingsSaved) "保存しました" else "設定を保存")
                }
            }

            Text("PoC機能", style = MaterialTheme.typography.titleLarge)
            AppScreen.entries
                .filterNot { it == AppScreen.Settings || it == AppScreen.ModelSelection }
                .forEach { screen ->
                    Card(onClick = { onOpenScreen(screen) }, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Text(screen.title, style = MaterialTheme.typography.titleMedium)
                            Text(screen.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

            SettingsCard(title = "顔パラメータ") {
                Text("顔識別閾値と候補差、3姿勢登録の実機調整値です。")
                ThresholdSlider("顔識別閾値", settings.faceThreshold, onFaceThresholdChanged)
                ThresholdSlider(
                    "顔 Top-2 最小候補差",
                    settings.faceMargin,
                    onFaceMarginChanged,
                    PocSettings.MARGIN_RANGE,
                )
                LongSlider(
                    "登録時解析間隔",
                    settings.faceRegistrationAnalysisIntervalMillis,
                    PocSettings.FACE_REGISTRATION_INTERVAL_RANGE,
                    onFaceRegistrationAnalysisIntervalChanged,
                )
                LongSlider(
                    "識別時解析間隔",
                    settings.faceIdentificationAnalysisIntervalMillis,
                    PocSettings.FACE_IDENTIFICATION_INTERVAL_RANGE,
                    onFaceIdentificationAnalysisIntervalChanged,
                )
                LongSlider(
                    "姿勢安定時間",
                    settings.facePoseStableDurationMillis,
                    PocSettings.FACE_POSE_STABLE_DURATION_RANGE,
                    onFacePoseStableDurationChanged,
                )
                AngleSlider(
                    "正面Yaw許容",
                    settings.faceFrontYawDegrees,
                    PocSettings.FACE_FRONT_ANGLE_RANGE,
                    onFaceFrontYawChanged,
                )
                AngleSlider(
                    "正面Pitch許容",
                    settings.faceFrontPitchDegrees,
                    PocSettings.FACE_FRONT_ANGLE_RANGE,
                    onFaceFrontPitchChanged,
                )
                AngleSlider(
                    "左右範囲 下限",
                    settings.faceSideMinimumYawDegrees,
                    PocSettings.FACE_SIDE_ANGLE_RANGE,
                    onFaceSideMinimumYawChanged,
                )
                AngleSlider(
                    "左右範囲 上限",
                    settings.faceSideMaximumYawDegrees,
                    PocSettings.FACE_SIDE_ANGLE_RANGE,
                    onFaceSideMaximumYawChanged,
                )
                Text("平滑化サンプル数: ${settings.faceSmoothingSampleCount}")
                Slider(
                    value = settings.faceSmoothingSampleCount.toFloat(),
                    onValueChange = { onFaceSmoothingSampleCountChanged(it.toInt()) },
                    valueRange = PocSettings.FACE_SMOOTHING_SAMPLE_COUNT_RANGE.first.toFloat()..
                        PocSettings.FACE_SMOOTHING_SAMPLE_COUNT_RANGE.last.toFloat(),
                    steps = PocSettings.FACE_SMOOTHING_SAMPLE_COUNT_RANGE.last -
                        PocSettings.FACE_SMOOTHING_SAMPLE_COUNT_RANGE.first - 1,
                )
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settingsSaved) "保存しました" else "顔パラメータを保存")
                }
            }

            SettingsCard(title = "保存データとプライバシー") {
                Text("保存: personId、表示名、モデル名、登録日時、顔・声特徴量、設定、評価ログ")
                Text("保存しない: 顔画像、動画、PCM、WAV")
                Text("保存先: この端末のアプリ専用内部ストレージ。外部送信は行いません。")
                Text("用途: 登録人物との端末内照合とPoC評価。人物登録画面で人物単位または全件を削除できます。")
                Text("保持期間はPoC運用者と決定し、PoC終了・本人依頼・端末返却時には全削除してください。")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Text("$label: ${String.format(Locale.US, "%.2f", value)}")
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
}

@Composable
private fun LongSlider(
    label: String,
    value: Long,
    range: LongRange,
    onValueChange: (Long) -> Unit,
) {
    Text("$label: $value ms")
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toLong()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
    )
}

@Composable
private fun AngleSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Text("$label: ${String.format(Locale.US, "%.1f", value)}°")
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
    )
}
