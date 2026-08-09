package com.example.pepper_person_id_poc.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.PocSettings
import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import java.util.Locale

private data class ParameterHelp(val title: String, val message: String)

private val SettingsCanvas = Color(0xFFF4F6F8)
private val SettingsSurface = Color.White
private val SettingsLine = Color(0xFFCBD3DA)
private val SettingsInk = Color(0xFF1D2329)
private val SettingsBlue = Color(0xFF2563EB)
private val SettingsPurple = Color(0xFF7658B5)
private val SettingsTrack = Color(0xFFE4E8ED)

private enum class SettingsCategory(val label: String) {
    BASIC("基本"),
    FACE_DETECTION("顔検出"),
    FACE_TRACKING("顔ラベル追跡"),
    SPEECH_DETECTION("発話検出"),
    AUDIO_QUALITY("音声品質"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: PocSettings,
    settingsSaved: Boolean,
    onSettingsChanged: (PocSettings) -> Unit,
    onOpenModelSelection: () -> Unit,
    onSave: () -> Unit,
    onOpenScreen: (AppScreen) -> Unit,
    onBackToIdentification: () -> Unit,
    onExit: () -> Unit,
) {
    var help by remember { mutableStateOf<ParameterHelp?>(null) }
    var confirmExit by remember { mutableStateOf(false) }
    fun showHelp(title: String, description: String, range: String, default: String) {
        help = ParameterHelp(title, "$description\n範囲: $range\n初期値: $default")
    }
    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
        Scaffold(
            containerColor = SettingsCanvas,
            topBar = {
                TopAppBar(
                    title = { Text("パラメータ設定", color = SettingsInk) },
                    actions = {
                        OutlinedButton(
                            onClick = onBackToIdentification,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SettingsLine),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SettingsInk),
                        ) {
                            Text("← 識別", color = SettingsInk)
                        }
                        TextButton(onClick = onOpenModelSelection) { Text("モデル", color = SettingsInk) }
                        TextButton(onClick = { onOpenScreen(AppScreen.DeviceDiagnostics) }) { Text("端末診断", color = SettingsInk) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SettingsSurface),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SettingsLine, RoundedCornerShape(12.dp)),
                )
            },
        ) { padding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            ) {
                SettingsCategory.entries.forEach { category ->
                    Text(category.label, style = MaterialTheme.typography.titleMedium, color = SettingsInk)
                    when (category) {
                SettingsCategory.BASIC -> BasicSettings(
                    settings = settings,
                    update = onSettingsChanged,
                    help = { title, description, range, default -> showHelp(title, description, range, default) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsCategory.FACE_DETECTION -> DetailGrid(
                    items = buildList {
                        add(ParameterItem("解析間隔", settings.faceAnalysisIntervalMillis.toFloat(), 100f..5_000f, "ms", 0) {
                            onSettingsChanged(settings.copy(faceAnalysisIntervalMillis = it.toLong()))
                        } to { showHelp("解析間隔", "顔検出と顔識別を実行する間隔です。短いほど追従性と処理負荷が高くなります。", "100～5,000 ms", "1,000 ms") })
                        if (settings.faceDetectorModel == FaceDetectorModelOption.ML_KIT_BUNDLED) {
                            add(ParameterItem("最小顔サイズ", settings.mlKitMinimumFaceSize, 0.05f..0.50f, "", 2) {
                                onSettingsChanged(settings.copy(mlKitMinimumFaceSize = it))
                            } to { showHelp("最小顔サイズ", "画像幅に対して検出する最小顔サイズです。ML Kit選択時だけ使用します。", "0.05～0.50", "0.10") })
                        } else {
                            add(ParameterItem("検出スコア閾値", settings.faceDetectionScoreThreshold, 0f..1f, "", 2) {
                                onSettingsChanged(settings.copy(faceDetectionScoreThreshold = it))
                            } to { showHelp("検出スコア閾値", "YuNetの顔検出結果を採用する最低スコアです。", "0.00～1.00", "0.80") })
                            add(ParameterItem("NMS閾値", settings.faceNmsThreshold, 0f..1f, "", 2) {
                                onSettingsChanged(settings.copy(faceNmsThreshold = it))
                            } to { showHelp("NMS閾値", "重複する顔枠をまとめるIoU境界です。", "0.00～1.00", "0.30") })
                            add(ParameterItem("最大検出候補数", settings.faceMaximumDetectionCandidates.toFloat(), 1f..10f, "件", 0) {
                                onSettingsChanged(settings.copy(faceMaximumDetectionCandidates = it.toInt()))
                            } to { showHelp("最大検出候補数", "NMS処理前に保持する顔候補数の上限です。", "1～10", "10") })
                        }
                    },
                    accent = SettingsBlue,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsCategory.FACE_TRACKING -> DetailGrid(
                    items = listOf(
                        ParameterItem("ラベル継続IoU", settings.faceLabelContinuationIou, 0f..1f, "", 2) {
                            onSettingsChanged(settings.copy(faceLabelContinuationIou = it))
                        } to { showHelp("ラベル継続IoU", "前回の顔枠と同じ顔ラベルを引き継ぐ境界です。顔照合閾値とは別の判定です。", "0.00～1.00", "0.30") },
                        ParameterItem("消失許容フレーム数", settings.faceLabelMaximumMissingFrames.toFloat(), 0f..30f, "フレーム", 0) {
                            onSettingsChanged(settings.copy(faceLabelMaximumMissingFrames = it.toInt()))
                        } to { showHelp("消失許容フレーム数", "顔を一時的に見失っても顔ラベルを保持するフレーム数です。", "0～30", "4") },
                    ),
                    accent = SettingsBlue,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsCategory.SPEECH_DETECTION -> DetailGrid(
                    items = listOf(
                        ParameterItem("VAD閾値", settings.vadThreshold, 0f..1f, "", 2) { onSettingsChanged(settings.copy(vadThreshold = it)) } to
                            { showHelp("VAD閾値", "Silero VADが音声と無音を判定する境界です。", "0.00～1.00", "0.35") },
                        ParameterItem("VAD最小無音時間", settings.vadMinimumSilenceMillis.toFloat(), 100f..2_000f, "ms", 0) { onSettingsChanged(settings.copy(vadMinimumSilenceMillis = it.toLong())) } to
                            { showHelp("VAD最小無音時間", "VADが発話終了と判定するために必要な無音時間です。", "100～2,000 ms", "400 ms") },
                        ParameterItem("VAD最小発話時間", settings.vadMinimumSpeechMillis.toFloat(), 100f..2_000f, "ms", 0) { onSettingsChanged(settings.copy(vadMinimumSpeechMillis = it.toLong())) } to
                            { showHelp("VAD最小発話時間", "VADが発話として採用する最短時間です。", "100～2,000 ms", "300 ms") },
                        ParameterItem("VAD最大発話時間", settings.vadMaximumSpeechMillis.toFloat(), 1_000f..30_000f, "ms", 0) { onSettingsChanged(settings.copy(vadMaximumSpeechMillis = it.toLong())) } to
                            { showHelp("VAD最大発話時間", "VADが1回の発話として扱う最大時間です。", "1,000～30,000 ms", "30,000 ms") },
                        ParameterItem("発話終了無音時間", settings.utteranceEndSilenceMillis.toFloat(), 100f..3_000f, "ms", 0) { onSettingsChanged(settings.copy(utteranceEndSilenceMillis = it.toLong())) } to
                        { showHelp("発話終了無音時間", "アプリが発話を確定して話者識別へ送るまでの無音時間です。", "100～3,000 ms", "500 ms") },
                        ParameterItem("最大発話時間", settings.maximumUtteranceMillis.toFloat(), 1_000f..10_000f, "ms", 0) { onSettingsChanged(settings.copy(maximumUtteranceMillis = it.toLong())) } to
                            { showHelp("最大発話時間", "話者分離が受け付ける1回の発話収集時間です。現行の話者分離エンジンは10秒窓で処理します。", "1,000～10,000 ms", "10,000 ms") },
                        ParameterItem("話者重複表示判定比率", settings.speakerOverlapDisplayThreshold, 0f..1f, "", 2) { onSettingsChanged(settings.copy(speakerOverlapDisplayThreshold = it)) } to
                            { showHelp("話者重複表示判定比率", "10秒窓内の複数話者フレームがこの比率以上の場合、窓の状態を「重複話者」と表示します。話者区間の採用判定はフレーム単位で別に行います。", "0.00～1.00", "0.50") },
                    ),
                    accent = SettingsPurple,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsCategory.AUDIO_QUALITY -> DetailGrid(
                    items = listOf(
                        ParameterItem("最小音声時間", settings.speakerMinimumAudioMillis.toFloat(), 100f..10_000f, "ms", 0) { onSettingsChanged(settings.copy(speakerMinimumAudioMillis = it.toLong())) } to
                            { showHelp("最小音声時間", "話者音声の品質判定に共通で使う最短音声時間です。新規作成と既存IDの更新に適用され、更新時は別途「更新時の最小音声時間」も適用されます。", "100～10,000 ms", "1,000 ms") },
                        ParameterItem("最小発話率", settings.speakerMinimumVoicedRatio, 0f..1f, "", 2) { onSettingsChanged(settings.copy(speakerMinimumVoicedRatio = it)) } to
                            { showHelp("最小発話率", "収集した音声に占める発話部分の最低比率です。", "0.00～1.00", "0.50") },
                        ParameterItem("最小RMS", settings.speakerMinimumRms, 0f..1f, "", 3) { onSettingsChanged(settings.copy(speakerMinimumRms = it)) } to
                            { showHelp("最小RMS", "音量不足として除外する境界です。0.00では実質無効です。", "0.00～1.00", "0.00") },
                        ParameterItem("クリッピング判定振幅", settings.clippingAmplitudeThreshold, 0.8f..1f, "", 3) { onSettingsChanged(settings.copy(clippingAmplitudeThreshold = it)) } to
                            { showHelp("クリッピング判定振幅", "音声サンプルをクリッピングとして数える振幅の境界です。", "0.800～1.000", "0.999") },
                        ParameterItem("最大クリッピング率", settings.maximumClippingRatio, 0f..1f, "", 2) { onSettingsChanged(settings.copy(maximumClippingRatio = it)) } to
                            { showHelp("最大クリッピング率", "音声全体に許容するクリッピングサンプルの最大比率です。", "0.00～1.00", "0.05") },
                        ParameterItem("更新時の最小音声時間", settings.speakerUpdateMinimumAudioMillis.toFloat(), 100f..10_000f, "ms", 0) { onSettingsChanged(settings.copy(speakerUpdateMinimumAudioMillis = it.toLong())) } to
                            { showHelp("更新時の最小音声時間", "既存の匿名話者IDを更新するために必要な最短音声時間です。", "100～10,000 ms", "1,000 ms") },
                        ParameterItem("更新時の最小発話率", settings.speakerUpdateMinimumVoicedRatio, 0f..1f, "", 2) { onSettingsChanged(settings.copy(speakerUpdateMinimumVoicedRatio = it)) } to
                            { showHelp("更新時の最小発話率", "既存の匿名話者IDを更新するために必要な最低発話率です。", "0.00～1.00", "0.50") },
                    ),
                    columnCount = 3,
                    accent = SettingsPurple,
                    modifier = Modifier.fillMaxWidth(),
                )
                    }
                }
            }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text(if (settingsSaved) "保存しました" else "保存") }
                    OutlinedButton(onClick = { confirmExit = true }) { Text("アプリ終了") }
                }
            }
        }
    }
    help?.let { item ->
        AlertDialog(
            onDismissRequest = { help = null },
            title = { Text(item.title) },
            text = { Text(item.message) },
            confirmButton = { TextButton(onClick = { help = null }) { Text("閉じる") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("アプリを終了しますか？") },
            text = { Text("今回の顔・声クラスタを削除して終了します。") },
            confirmButton = { TextButton(onClick = onExit) { Text("終了") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun BasicSettings(
    settings: PocSettings,
    update: (PocSettings) -> Unit,
    help: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        SettingsCard(Modifier.weight(0.8f).height(BasicSectionCardHeight)) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            ) {
                Text("共通", style = MaterialTheme.typography.labelLarge)
                OptionToggle("複数サンプル取得", settings.multipleSamplesEnabled, { update(settings.copy(multipleSamplesEnabled = it)) }) {
                    help("複数サンプル取得", "ONでは後続サンプルで匿名IDの代表特徴量を更新します。OFFでは実処理上1件です。", "ON／OFF", "OFF")
                }
            }
        }
        BasicIdentityGroup(
            title = "顔識別",
            thresholdTitle = "顔照合閾値",
            threshold = settings.faceClusterJoinThreshold,
            maxUpdates = settings.faceClusterMaxUpdateCount,
            updateThreshold = { update(settings.copy(faceClusterJoinThreshold = it)) },
            updateMaximum = { update(settings.copy(faceClusterMaxUpdateCount = it)) },
            thresholdHelp = { help("顔照合閾値", "顔特徴量を同じ匿名顔IDと判定する類似度の境界です。", "0.00～1.00", "0.60") },
            maximumHelp = { help("顔の最大更新件数", "匿名顔IDの代表特徴量を更新する上限です。複数サンプル取得OFF時は実質1件です。", "1～100", "20") },
            accent = SettingsBlue,
            modifier = Modifier.weight(1f),
        )
        BasicIdentityGroup(
            title = "話者識別",
            thresholdTitle = "話者照合閾値",
            threshold = settings.speakerClusterJoinThreshold,
            maxUpdates = settings.speakerClusterMaxUpdateCount,
            updateThreshold = { update(settings.copy(speakerClusterJoinThreshold = it)) },
            updateMaximum = { update(settings.copy(speakerClusterMaxUpdateCount = it)) },
            thresholdHelp = { help("話者照合閾値", "話者特徴量を同じ匿名話者IDと判定する類似度の境界です。初期値は選択モデル別です。", "0.00～1.00", String.format(Locale.US, "%.7g", settings.speakerModel.jvsCandidateThreshold)) },
            maximumHelp = { help("話者の最大更新件数", "匿名話者IDの代表特徴量を更新する上限です。複数サンプル取得OFF時は実質1件です。", "1～100", "20") },
            accent = SettingsPurple,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailGrid(
    items: List<Pair<ParameterItem, () -> Unit>>,
    columnCount: Int = 2,
    accent: Color = SettingsInk,
    modifier: Modifier = Modifier,
) {
    val columns = items.chunked((items.size + columnCount - 1) / columnCount)
    val cardHeight = (((columns.maxOfOrNull { it.size } ?: 0) * 76) + 8).dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        columns.forEach { column ->
            SettingsCard(Modifier.weight(1f).height(cardHeight)) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    column.forEach { (item, help) -> ParameterSlider(item, help, accent) }
                }
            }
        }
    }
}

private data class ParameterItem(
    val title: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val unit: String,
    val decimals: Int,
    val onChanged: (Float) -> Unit,
)

@Composable
private fun ParameterSlider(item: ParameterItem, onHelp: () -> Unit, accent: Color = SettingsInk) {
    val format = if (item.decimals == 0) "%.0f" else "%.${item.decimals}f"
    val unit = item.unit.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
    val value = String.format(Locale.US, format, item.value)
    Text(
        "${item.title} $value$unit　ⓘ",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clickable(onClick = onHelp),
    )
    Slider(
        value = item.value,
        onValueChange = item.onChanged,
        valueRange = item.range,
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent.copy(alpha = 0.42f),
            inactiveTrackColor = SettingsTrack,
            activeTickColor = accent,
            inactiveTickColor = SettingsTrack,
        ),
    )
}

@Composable
private fun OptionToggle(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    accent: Color = SettingsInk,
    onHelp: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onHelp)) {
        Text("$title ⓘ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                checkedBorderColor = accent,
                uncheckedThumbColor = Color(0xFF8A939C),
                uncheckedTrackColor = SettingsTrack,
                uncheckedBorderColor = SettingsLine,
            ),
        )
    }
}

@Composable
private fun BasicIdentityGroup(
    title: String,
    thresholdTitle: String,
    threshold: Float,
    maxUpdates: Int,
    updateThreshold: (Float) -> Unit,
    updateMaximum: (Int) -> Unit,
    thresholdHelp: () -> Unit,
    maximumHelp: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier.height(BasicSectionCardHeight)) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            ParameterSlider(
                ParameterItem(thresholdTitle, threshold, 0f..1f, "", 2, onChanged = updateThreshold),
                thresholdHelp,
                accent,
            )
            ParameterSlider(
                ParameterItem("最大更新件数", maxUpdates.toFloat(), 1f..100f, "件", 0) { updateMaximum(it.toInt()) },
                maximumHelp,
                accent,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SettingsLine),
        colors = CardDefaults.cardColors(containerColor = SettingsSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

private val BasicSectionCardHeight = 180.dp
