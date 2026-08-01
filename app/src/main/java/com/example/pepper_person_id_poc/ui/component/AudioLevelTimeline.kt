package com.example.pepper_person_id_poc.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class AudioLevelSample(
    val elapsedMillis: Long,
    val capturedAtMillis: Long,
    val levelDbFs: Float,
)

@Composable
fun AudioLevelTimeline(
    levelDbFs: Float,
    sampling: Boolean,
    modifier: Modifier = Modifier,
) {
    val samples = remember { mutableStateListOf<AudioLevelSample>() }
    val currentLevel by rememberUpdatedState(levelDbFs)
    val isSampling by rememberUpdatedState(sampling)
    val scrollState = rememberScrollState()
    val userDragging by scrollState.interactionSource.collectIsDraggedAsState()
    var autoFollow by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            if (isSampling) {
                samples += AudioLevelSample(
                    elapsedMillis = (samples.lastOrNull()?.elapsedMillis ?: -250L) + 250L,
                    capturedAtMillis = System.currentTimeMillis(),
                    levelDbFs = currentLevel.coerceIn(-90f, 0f),
                )
                if (samples.size > 300) samples.removeAt(0)
            }
            delay(250L)
        }
    }
    LaunchedEffect(userDragging) {
        if (userDragging) autoFollow = false
    }
    LaunchedEffect(samples.size) {
        if (autoFollow) {
            delay(16L)
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(modifier.testTag("audio-level-timeline")) {
        Text(
            "縦軸: 音量レベル（%）　横軸: 時間（秒）　" +
                (if (autoFollow) "最新へ自動追従" else "表示位置を固定"),
            style = MaterialTheme.typography.labelMedium,
        )
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(48.dp).fillMaxHeight(),
                ) {
                    Text("100%")
                    Text("50%")
                    Text("0%")
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(220.dp)
                        .horizontalScroll(scrollState),
                ) {
                    Canvas(
                        Modifier
                            .height(220.dp)
                            .width(maxOf(560, samples.size * 18).dp)
                            .padding(start = 4.dp),
                    ) {
                        val labelArea = 18.dp.toPx()
                        val chartHeight = size.height - labelArea
                        drawLine(Color.Gray, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
                        drawLine(Color.Gray, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 2f)
                        samples.forEachIndexed { index, sample ->
                            val normalized = audioLevelPercent(sample.levelDbFs) / 100f
                            val blockWidth = 14.dp.toPx()
                            val gap = 4.dp.toPx()
                            val left = index * (blockWidth + gap)
                            val top = chartHeight * (1f - normalized)
                            drawRect(
                                color = if (sample.levelDbFs > -35f) Color(0xFFFF8A3D) else Color(0xFF8DD36F),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(blockWidth, chartHeight - top),
                            )
                            if (index % 20 == 0) {
                                drawLine(
                                    color = Color.Gray,
                                    start = Offset(left, 0f),
                                    end = Offset(left, chartHeight),
                                    strokeWidth = 1f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                                )
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "${sample.elapsedMillis / 1_000}秒",
                                        left + 2.dp.toPx(),
                                        size.height - 2.dp.toPx(),
                                        android.graphics.Paint().apply {
                                            color = android.graphics.Color.LTGRAY
                                            textSize = 9.dp.toPx()
                                            isAntiAlias = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun audioLevelPercent(levelDbFs: Float): Float =
    ((levelDbFs.coerceIn(-90f, 0f) + 90f) / 90f) * 100f
