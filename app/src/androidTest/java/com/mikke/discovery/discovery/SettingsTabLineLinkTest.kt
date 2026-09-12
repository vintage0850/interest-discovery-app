package com.mikke.discovery.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikke.discovery.shared.discovery.SettingsUiState
import com.mikke.discovery.shared.ui.LocalLineLinkHandler
import com.mikke.discovery.shared.ui.discovery.SettingsTabScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 設定画面の「LINE連携」行タップ時に [LocalLineLinkHandler] が呼ばれることを検証する。
 *
 * 案件29の回帰テスト。以前は onClick が activeModal を書き換えるだけで
 * 実際の処理が存在しなかったため、ボタンが無反応になっていた。
 */
@RunWith(AndroidJUnit4::class)
class SettingsTabLineLinkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun line行をタップするとLocalLineLinkHandlerが呼ばれる() {
        var handlerCalled = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLineLinkHandler provides { handlerCalled = true }
            ) {
                SettingsTabScreen(
                    settingsState = SettingsUiState(),
                    onToggleNotifications = {},
                    onResetData = {},
                    onPsychAxisSurveyClick = {},
                    onSessionListClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("LINE連携").performClick()

        assertTrue("LINE連携行タップ時にハンドラが呼ばれるべき", handlerCalled)
    }
}
