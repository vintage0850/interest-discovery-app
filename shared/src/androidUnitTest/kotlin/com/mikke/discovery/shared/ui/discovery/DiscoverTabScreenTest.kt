package com.mikke.discovery.shared.ui.discovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.mikke.discovery.shared.discovery.DiscoveryData
import com.mikke.discovery.shared.discovery.DiscoveryUiState
import com.mikke.discovery.shared.discovery.DiscrepancyUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Discover タブの「気になる発見」カード表示に関する UI テスト。
 */
@RunWith(RobolectricTestRunner::class)
class DiscoverTabScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun discrepanciesが空の場合は気になる発見カードが表示されない() {
        composeTestRule.setContent {
            DiscoverTabScreen(
                discoveryState = DiscoveryUiState(
                    discoveryData = DiscoveryData(
                        observation = "観察",
                        hypothesis = "仮説",
                        discrepancies = emptyList()
                    )
                ),
                onToggleEvidence = {},
                onTryNext = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("気になる発見").assertDoesNotExist()
        composeTestRule.onNodeWithText("もう一度試してみますか？").assertDoesNotExist()
    }

    @Test
    fun discrepanciesが1件以上の場合はカードが表示されmessageが反映される() {
        val message = "'art' で高い評価が得られたが、興味シグナルは検出されていません"
        composeTestRule.setContent {
            DiscoverTabScreen(
                discoveryState = DiscoveryUiState(
                    discoveryData = DiscoveryData(
                        observation = "観察",
                        hypothesis = "仮説",
                        discrepancies = listOf(
                            DiscrepancyUiModel(
                                type = "high_result_no_signal",
                                domain = "art",
                                experimentId = 1,
                                message = message
                            )
                        )
                    )
                ),
                onToggleEvidence = {},
                onTryNext = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("気になる発見").assertIsDisplayed()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText("もう一度試してみますか？").assertIsDisplayed()
    }

    @Test
    fun discrepanciesが複数件の場合はそれぞれのmessageが表示される() {
        val message1 = "'art' で高い評価が得られたが、興味シグナルは検出されていません"
        val message2 = "'tech' で高い評価が得られたが、興味シグナルは検出されていません"
        composeTestRule.setContent {
            DiscoverTabScreen(
                discoveryState = DiscoveryUiState(
                    discoveryData = DiscoveryData(
                        observation = "観察",
                        hypothesis = "仮説",
                        discrepancies = listOf(
                            DiscrepancyUiModel(
                                type = "high_result_no_signal",
                                domain = "art",
                                experimentId = 1,
                                message = message1
                            ),
                            DiscrepancyUiModel(
                                type = "high_result_no_signal",
                                domain = "tech",
                                experimentId = 2,
                                message = message2
                            )
                        )
                    )
                ),
                onToggleEvidence = {},
                onTryNext = {},
                onRetry = {}
            )
        }

        composeTestRule.onNodeWithText("気になる発見").assertIsDisplayed()
        composeTestRule.onNodeWithText(message1).assertIsDisplayed()
        composeTestRule.onNodeWithText(message2).assertIsDisplayed()
        composeTestRule.onNodeWithText("もう一度試してみますか？")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
