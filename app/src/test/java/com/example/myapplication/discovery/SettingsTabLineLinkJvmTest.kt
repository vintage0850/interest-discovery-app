package com.example.myapplication.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.shared.discovery.SettingsUiState
import com.example.myapplication.shared.ui.LocalLineLinkHandler
import com.example.myapplication.shared.ui.discovery.SettingsTabScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 案件29：設定画面の「LINE連携」行タップ時に [LocalLineLinkHandler] が呼ばれることを検証する。
 *
 * Robolectric 上で実行し、実機/エミュレータ無しでも回帰を確認できるようにする。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SettingsTabLineLinkJvmTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

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
