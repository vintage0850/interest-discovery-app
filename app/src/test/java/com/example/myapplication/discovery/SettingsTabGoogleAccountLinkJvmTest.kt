package com.example.myapplication.discovery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.shared.discovery.GoogleAccountState
import com.example.myapplication.shared.discovery.SettingsUiState
import com.example.myapplication.shared.ui.LocalGoogleAccountLinkHandler
import com.example.myapplication.shared.ui.LocalGoogleAccountSignOutHandler
import com.example.myapplication.shared.ui.discovery.SettingsTabScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 案件32：設定画面の Google アカウント連携行の表示・タップ動作を検証する。
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
                    settingsState = SettingsUiState(googleAccountState = GoogleAccountState.NotLinked),
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
    fun Googleアカウント連携済みの行をタップするとLocalGoogleAccountSignOutHandlerが呼ばれる() {
        var signOutHandlerCalled = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalGoogleAccountSignOutHandler provides { signOutHandlerCalled = true }
            ) {
                SettingsTabScreen(
                    settingsState = SettingsUiState(
                        googleAccountState = GoogleAccountState.Linked(
                            displayName = "Test User",
                            email = "test@example.com",
                            photoUrl = null
                        )
                    ),
                    onToggleNotifications = {},
                    onResetData = {},
                    onPsychAxisSurveyClick = {},
                    onSessionListClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Test User").performClick()

        assertTrue("連携済み行タップ時に解除ハンドラが呼ばれるべき", signOutHandlerCalled)
    }

    @Test
    fun Googleアカウント連携済みの場合はアカウント名が表示される() {
        composeTestRule.setContent {
            SettingsTabScreen(
                settingsState = SettingsUiState(
                    googleAccountState = GoogleAccountState.Linked(
                        displayName = "Test User",
                        email = "test@example.com",
                        photoUrl = null
                    )
                ),
                onToggleNotifications = {},
                onResetData = {},
                onPsychAxisSurveyClick = {},
                onSessionListClick = {}
            )
        }

        composeTestRule.onNodeWithText("Test User").assertExists()
        composeTestRule.onNodeWithText("test@example.com").assertExists()
    }

    @Test
    fun displayNameがnullでもLinkedは連携済みとして表示される() {
        composeTestRule.setContent {
            SettingsTabScreen(
                settingsState = SettingsUiState(
                    googleAccountState = GoogleAccountState.Linked(
                        displayName = null,
                        email = "no-name@example.com",
                        photoUrl = null
                    )
                ),
                onToggleNotifications = {},
                onResetData = {},
                onPsychAxisSurveyClick = {},
                onSessionListClick = {}
            )
        }

        // アカウント作成行ではなく、連携済みの Google アカウント行が表示されている。
        composeTestRule.onNodeWithText("Google アカウント").assertExists()
        composeTestRule.onNodeWithText("no-name@example.com").assertExists()
        composeTestRule.onNodeWithText("アカウントを作成").assertDoesNotExist()
    }

    @Test
    fun NotConfiguredの場合はアカウントセクションが表示されない() {
        composeTestRule.setContent {
            SettingsTabScreen(
                settingsState = SettingsUiState(googleAccountState = GoogleAccountState.NotConfigured),
                onToggleNotifications = {},
                onResetData = {},
                onPsychAxisSurveyClick = {},
                onSessionListClick = {}
            )
        }

        composeTestRule.onNodeWithText("アカウント").assertDoesNotExist()
        composeTestRule.onNodeWithText("アカウントを作成").assertDoesNotExist()
        composeTestRule.onNodeWithText("Google アカウント").assertDoesNotExist()
    }

    @Test
    fun LinkFailedの場合はエラーメッセージが表示され再試行できる() {
        var linkHandlerCalled = false

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalGoogleAccountLinkHandler provides { linkHandlerCalled = true }
            ) {
                SettingsTabScreen(
                    settingsState = SettingsUiState(
                        googleAccountState = GoogleAccountState.LinkFailed("設定が無効です")
                    ),
                    onToggleNotifications = {},
                    onResetData = {},
                    onPsychAxisSurveyClick = {},
                    onSessionListClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("設定が無効です").assertExists()
        composeTestRule.onNodeWithText("エラー").assertExists()
        composeTestRule.onNodeWithText("Google アカウント").performClick()

        assertTrue("エラー状態の行タップ時に再連携ハンドラが呼ばれるべき", linkHandlerCalled)
    }
}
