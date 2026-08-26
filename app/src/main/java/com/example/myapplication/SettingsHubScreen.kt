package com.example.myapplication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.GoogleAuthManager
import kotlinx.coroutines.launch

/**
 * 設定ハブで表示するカレンダー連携項目の状態。
 * UI 構築を支援し、単体テストで検証できるよう純粋データに切り出している。
 */
internal data class CalendarLinkSummary(
    val title: String,
    val subtitle: String?,
    val isConnected: Boolean,
    val canConnect: Boolean
)

/**
 * カレンダー認可状態から、設定ハブのカレンダー項目表示を組み立てる。
 */
internal fun calendarLinkSummary(authState: CalendarAuthState): CalendarLinkSummary = when (authState) {
    is CalendarAuthState.NotConfigured -> CalendarLinkSummary(
        title = "Google カレンダー連携",
        subtitle = "SETUP.md の手順で OAuth クライアント ID を設定してください",
        isConnected = false,
        canConnect = false
    )
    is CalendarAuthState.NotAuthorized -> CalendarLinkSummary(
        title = "Google カレンダー連携",
        subtitle = null,
        isConnected = false,
        canConnect = true
    )
    is CalendarAuthState.Authorized -> CalendarLinkSummary(
        title = "Google カレンダー連携",
        subtitle = if (authState.email != null) "${authState.email} で連携中" else "連携中",
        isConnected = true,
        canConnect = false
    )
}

/**
 * 設定関連の画面へ進むためのハブ画面。
 *
 * 従来はトップバーにカテゴリ管理・通知設定・カレンダー連携のアイコンが分散していたが、
 * この画面に集約し、一覧画面の操作を減らしている。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    authState: CalendarAuthState,
    snackbarHostState: SnackbarHostState,
    onNavigateToCategories: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember(context) { GoogleAuthManager.get(context) }
    val scope = rememberCoroutineScope()

    val requestAuthorization = rememberCalendarAuthorization(auth) { outcome ->
        val message = outcome.messageOrNull()
        if (message != null) {
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch { snackbarHostState.showSnackbar(message) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(vertical = 8.dp)
        ) {
            SettingsSection {
                SettingsItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    title = "カテゴリ管理",
                    subtitle = "タスクの分類を追加・変更・削除",
                    onClick = onNavigateToCategories
                )
                HorizontalDivider()
                SettingsItem(
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    title = "通知設定",
                    subtitle = "「空き時間です」通知の時間帯",
                    onClick = onNavigateToNotificationSettings
                )
            }

            val calendarSummary = calendarLinkSummary(authState)
            SettingsSection {
                ListItem(
                    headlineContent = { Text(calendarSummary.title) },
                    supportingContent = calendarSummary.subtitle?.let { { Text(it) } },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = if (calendarSummary.isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                        )
                    },
                    trailingContent = {
                        if (calendarSummary.canConnect) {
                            TextButton(onClick = requestAuthorization) {
                                Text("接続")
                            }
                        } else if (calendarSummary.isConnected) {
                            TextButton(onClick = onSignOut) {
                                Text("サインアウト")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        content()
    }
}

@Composable
private fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null
            )
        }
    )
}
