package com.mikke.discovery

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mikke.discovery.data.calendar.CalendarAuthState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [SettingsHubScreen] の遷移・戻る操作と、カレンダー連携状態表示を検証する回帰テスト。
 */
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val snackbarHostState = SnackbarHostState()

    @Test
    fun `設定ハブからカテゴリ管理へ遷移できる`() {
        var navigatedToCategories = false

        composeTestRule.setContent {
            SettingsHubScreen(
                authState = CalendarAuthState.NotAuthorized,
                snackbarHostState = snackbarHostState,
                onNavigateToCategories = { navigatedToCategories = true },
                onNavigateToNotificationSettings = {},
                onSignOut = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("カテゴリ管理").performClick()
        assertTrue(navigatedToCategories)
    }

    @Test
    fun `設定ハブから通知設定へ遷移できる`() {
        var navigatedToNotificationSettings = false

        composeTestRule.setContent {
            SettingsHubScreen(
                authState = CalendarAuthState.NotAuthorized,
                snackbarHostState = snackbarHostState,
                onNavigateToCategories = {},
                onNavigateToNotificationSettings = { navigatedToNotificationSettings = true },
                onSignOut = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("通知設定").performClick()
        assertTrue(navigatedToNotificationSettings)
    }

    @Test
    fun `未接続状態では未接続と表示される`() {
        composeTestRule.setContent {
            SettingsHubScreen(
                authState = CalendarAuthState.NotAuthorized,
                snackbarHostState = snackbarHostState,
                onNavigateToCategories = {},
                onNavigateToNotificationSettings = {},
                onSignOut = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("未接続").assertIsDisplayed()
        composeTestRule.onNodeWithText("接続").assertIsDisplayed()
    }

    @Test
    fun `未設定状態では未設定と表示され接続ボタンは無効にならないが接続操作はできない`() {
        composeTestRule.setContent {
            SettingsHubScreen(
                authState = CalendarAuthState.NotConfigured,
                snackbarHostState = snackbarHostState,
                onNavigateToCategories = {},
                onNavigateToNotificationSettings = {},
                onSignOut = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("未設定", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("接続").assertDoesNotExist()
    }

    @Test
    fun `接続済み状態ではアカウントと接続済みが表示される`() {
        composeTestRule.setContent {
            SettingsHubScreen(
                authState = CalendarAuthState.Authorized("user@example.com"),
                snackbarHostState = snackbarHostState,
                onNavigateToCategories = {},
                onNavigateToNotificationSettings = {},
                onSignOut = {},
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("接続済み：user@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("サインアウト").assertIsDisplayed()
    }

    @Test
    fun `戻るボタンでonBackが呼ばれる`() {
        var backPressed = false

        composeTestRule.setContent {
            SettingsHubScreen(
                authState = CalendarAuthState.NotAuthorized,
                snackbarHostState = snackbarHostState,
                onNavigateToCategories = {},
                onNavigateToNotificationSettings = {},
                onSignOut = {},
                onBack = { backPressed = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("戻る").performClick()
        assertTrue(backPressed)
    }
}
