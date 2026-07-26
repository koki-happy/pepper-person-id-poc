package com.example.pepper_person_id_poc

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.pepper_person_id_poc.ui.component.DeviceLoadPanel
import com.example.pepper_person_id_poc.ui.component.StageMetricUiState
import org.junit.Rule
import org.junit.Test

class MinimalDeviceLoadPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun panelShowsOnlyCurrentLoadAndRateWithoutHistoryControls() {
        composeRule.setContent {
            MaterialTheme {
                DeviceLoadPanel(
                    stageMetrics = listOf(
                        StageMetricUiState(
                            id = "total",
                            label = "Total",
                            currentMillis = 12.5,
                            samples = emptyList(),
                        ),
                    ),
                    rateLabel = "解析FPS",
                    rateValue = 1f,
                )
            }
        }

        composeRule.onNodeWithText("Pepper負荷").assertIsDisplayed()
        composeRule.onNodeWithText("処理時間: 13 ms/回").assertIsDisplayed()
        composeRule.onNodeWithText("解析FPS: 1.00").assertIsDisplayed()
    }
}
