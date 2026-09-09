package com.example.myapplication.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.shared.discovery.SettingsUiState
import com.example.myapplication.shared.ui.LocalGoogleAccountLinkHandler
import com.example.myapplication.shared.ui.discovery.SettingsTabScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 案件32：設定画面の「アカウントを作成」行タップ時に [LocalGoogleAccountLinkHandler] が呼ばれることを検証する。
 *
 * Robolectric 上で実行し、実機/エミュレータ無しでも回帰を確認できるようにする。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SettingsTabGoogleAccountLinkJvmTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun アカウントを作成行をタップするとLocalGoogleAccountLinkHandlerが呼ばれる() {
        var handlerCalled = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalGoogleAccountLinkHandler provides { handlerCalled = true }
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

        composeTestRule.onNodeWithText("アカウントを作成").performClick()

        assertTrue("アカウントを作成行タップ時にハンドラが呼ばれるべき", handlerCalled)
    }

    @Test
    fun Googleアカウント連携済みの場合はアカウント名が表示される() {
        composeTestRule.setContent {
            SettingsTabScreen(
                settingsState = SettingsUiState(googleAccountDisplayName = "Test User"),
                onToggleNotifications = {},
                onResetData = {},
                onPsychAxisSurveyClick = {},
                onSessionListClick = {}
            )
        }

        composeTestRule.onNodeWithText("Test User").assertExists()
    }
}
