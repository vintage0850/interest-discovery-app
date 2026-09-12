package com.mikke.discovery.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.discovery.GoogleAccountState
import com.mikke.discovery.shared.discovery.SettingsUiState
import com.mikke.discovery.shared.ui.LocalGoogleAccountLinkHandler
import com.mikke.discovery.shared.ui.LocalGoogleAccountSignOutHandler
import com.mikke.discovery.shared.ui.LocalGoogleCalendarLinkHandler
import com.mikke.discovery.shared.ui.LocalLineLinkHandler

/**
 * 設定（Settings）タブ画面。
 * 将来のアカウント登録・LINE連携・通知設定・プライバシー設定・規約が自然に追加できる構造。
 */
@Composable
fun SettingsTabScreen(
    settingsState: SettingsUiState,
    onToggleNotifications: (Boolean) -> Unit,
    onResetData: () -> Unit,
    onPsychAxisSurveyClick: () -> Unit,
    onSessionListClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeModal by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DiscoveryColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DiscoverySpacing.pageHorizontal)
                .padding(top = DiscoverySpacing.xxxl, bottom = 80.dp)
        ) {
            Text(
                text = "設定",
                color = DiscoveryColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            // 1. アカウント
            val accountState = settingsState.googleAccountState
            if (accountState !is GoogleAccountState.NotConfigured) {
                SectionHeader(title = "アカウント")
                SettingsCard {
                    val onLinkGoogleAccount = LocalGoogleAccountLinkHandler.current
                    val onUnlinkGoogleAccount = LocalGoogleAccountSignOutHandler.current
                    when (accountState) {
                        is GoogleAccountState.NotLinked -> SettingsRow(
                            icon = "👤",
                            title = "アカウントを作成",
                            subtitle = "未ログイン",
                            onClick = { onLinkGoogleAccount?.invoke() }
                        )

                        is GoogleAccountState.Linked -> {
                            val subtitle = accountState.displayName
                                ?: accountState.email
                                ?: "連携済み"
                            val baseDescription = when {
                                accountState.displayName != null && accountState.email != null -> accountState.email
                                accountState.email != null -> accountState.email
                                else -> "タップして連携を解除"
                            }
                            // プロフィール画像のURLは画像ライブラリを追加せず、存在有無をテキストで示す。
                            val description = if (accountState.photoUrl != null) {
                                "$baseDescription\nプロフィール画像あり"
                            } else {
                                baseDescription
                            }
                            SettingsRow(
                                icon = "👤",
                                title = "Google アカウント",
                                subtitle = subtitle,
                                badge = "連携済み",
                                description = description,
                                onClick = { onUnlinkGoogleAccount?.invoke() }
                            )
                        }

                        is GoogleAccountState.LinkFailed -> SettingsRow(
                            icon = "👤",
                            title = "Google アカウント",
                            subtitle = accountState.message,
                            badge = "エラー",
                            onClick = { onLinkGoogleAccount?.invoke() }
                        )

                        GoogleAccountState.NotConfigured -> Unit
                    }
                }

                Spacer(modifier = Modifier.height(DiscoverySpacing.lg))
            }

            // 2. 連携
            SectionHeader(title = "連携")
            SettingsCard {
                Column {
                    val onLinkLine = LocalLineLinkHandler.current
                    SettingsRow(
                        icon = "💬",
                        title = "LINE連携",
                        badge = "未連携",
                        description = "公式LINEから今日の実験やリマインドを受け取れます",
                        onClick = { onLinkLine?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(DiscoveryColors.BorderSubtle))
                    val calendarLinked = settingsState.googleCalendarLinked
                    val onLinkGoogleCalendar = LocalGoogleCalendarLinkHandler.current
                    SettingsRow(
                        icon = "📅",
                        title = "Googleカレンダー連携",
                        badge = if (calendarLinked) "連携済み" else "未連携",
                        description = if (calendarLinked) "空き時間をもとに通知を届けます" else "空き時間通知に使うカレンダーを連携します",
                        onClick = { onLinkGoogleCalendar?.invoke() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            // 3. 通知
            SectionHeader(title = "通知")
            SettingsCard {
                SettingsRow(
                    icon = "🔔",
                    title = "通知設定",
                    onClick = { activeModal = "notification" }
                )
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            // 4. プライバシー
            SectionHeader(title = "プライバシー")
            SettingsCard {
                SettingsRow(
                    icon = "🔒",
                    title = "プライバシー設定",
                    onClick = { activeModal = "privacy" }
                )
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            // 5. データ・セッション
            SectionHeader(title = "データ・セッション")
            SettingsCard {
                Column {
                    SettingsRow(
                        icon = "🧠",
                        title = "興味の方向性チェック",
                        subtitle = "心理4軸アンケート",
                        onClick = onPsychAxisSurveyClick
                    )
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(DiscoveryColors.BorderSubtle))
                    SettingsRow(
                        icon = "🕒",
                        title = "過去のセッション",
                        subtitle = "別のセッションに切り替える",
                        onClick = onSessionListClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            // 6. その他
            SectionHeader(title = "その他")
            SettingsCard {
                Column {
                    SettingsRow(
                        icon = "📄",
                        title = "利用規約",
                        onClick = { activeModal = "terms" }
                    )
                    Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(DiscoveryColors.BorderSubtle))
                    SettingsRow(
                        icon = "📄",
                        title = "プライバシーポリシー",
                        onClick = { activeModal = "policy" }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = DiscoveryColors.TextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: String,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    description: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(DiscoverySpacing.base),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(DiscoveryColors.SurfaceSecondary),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 16.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.xs)
                ) {
                    Text(
                        text = title,
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (badge != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(DiscoveryRadius.badge))
                                .background(DiscoveryColors.SurfaceSecondary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                color = DiscoveryColors.TextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = DiscoveryColors.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (description != null) {
                    Text(
                        text = description,
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Text(
            text = "›",
            color = DiscoveryColors.TextTertiary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = DiscoverySpacing.sm)
        )
    }
}
