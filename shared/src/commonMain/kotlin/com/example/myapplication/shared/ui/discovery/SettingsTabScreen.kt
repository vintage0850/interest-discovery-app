package com.example.myapplication.shared.ui.discovery

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.myapplication.shared.discovery.SettingsUiState

/**
 * 設定・マイデータ（Settings）タブ画面。
 * 保存されているシグナル、通知設定、プライバシー保護、データ初期化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(
    settingsState: SettingsUiState,
    onToggleNotifications: (Boolean) -> Unit,
    onResetData: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetConfirm by remember { mutableStateOf(false) }

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
                text = "設定・データ管理",
                color = DiscoveryColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

            Text(
                text = "プライバシー保護と学習データの管理です。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            // 1. 保存されているSignal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DiscoveryRadius.card))
                    .background(DiscoveryColors.Surface)
                    .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                    .padding(DiscoverySpacing.cardPadding)
            ) {
                Text(
                    text = "📊 保存されている行動シグナル",
                    color = DiscoveryColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                Text(
                    text = "これまでの実験から ${settingsState.settings.savedSignalCount} 件の行動ログを記録しています。性格診断テストではなく、あなたの直感と行動からのみ興味を導き出します。",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.base))

            // 2. 通知設定
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DiscoveryRadius.card))
                    .background(DiscoveryColors.Surface)
                    .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                    .padding(DiscoverySpacing.cardPadding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔔 毎日の5分実験リマインド",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "毎日 ${settingsState.settings.reminderTime} に通知",
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Switch(
                        checked = settingsState.settings.notificationsEnabled,
                        onCheckedChange = onToggleNotifications,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DiscoveryColors.Surface,
                            checkedTrackColor = DiscoveryColors.Accent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.base))

            // 3. プライバシー保護
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DiscoveryRadius.card))
                    .background(DiscoveryColors.SurfaceSecondary)
                    .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                    .padding(DiscoverySpacing.cardPadding)
            ) {
                Text(
                    text = "🔒 プライバシーと安全への約束",
                    color = DiscoveryColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                Text(
                    text = "あなたの行動データや興味の仮説は、学校や第三者、広告会社に共有されることは一切ありません。あなただけの探索のために安全に保管されます。",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            // 4. データのリセット
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DiscoveryRadius.card))
                    .background(DiscoveryColors.ErrorSurface)
                    .border(1.dp, DiscoveryColors.ErrorBorder, RoundedCornerShape(DiscoveryRadius.card))
                    .clickable(
                        role = Role.Button,
                        onClick = { showResetConfirm = true }
                    )
                    .padding(DiscoverySpacing.cardPadding)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ すべての実験・シグナルデータをリセット",
                        color = DiscoveryColors.ErrorText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // リセット確認ダイアログ
        if (showResetConfirm) {
            BasicAlertDialog(
                onDismissRequest = { showResetConfirm = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DiscoveryRadius.card))
                        .background(DiscoveryColors.Surface)
                        .padding(DiscoverySpacing.cardPadding)
                ) {
                    Column {
                        Text(
                            text = "データをリセットしますか？",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                        Text(
                            text = "これまでに完了した実験履歴や行動シグナルがすべて消去され、初期状態に戻ります。",
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppSecondaryButton(
                                text = "キャンセル",
                                onClick = { showResetConfirm = false }
                            )

                            Spacer(modifier = Modifier.width(DiscoverySpacing.sm))

                            AppPrimaryButton(
                                text = "リセットする",
                                modifier = Modifier.width(130.dp),
                                onClick = {
                                    showResetConfirm = false
                                    onResetData()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
