package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.BehaviorSignal
import com.example.myapplication.shared.discovery.Experiment
import com.example.myapplication.shared.discovery.FakeScenario
import com.example.myapplication.shared.discovery.SignalTrend
import com.example.myapplication.shared.discovery.SignalUiModel

/**
 * 洗練されたプライマリCTAボタン。
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor = when {
        !enabled -> DiscoveryColors.BorderStrong
        isPressed -> DiscoveryColors.AccentDark
        else -> DiscoveryColors.Accent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(DiscoveryRadius.button))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = text
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = DiscoveryColors.AccentText,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                color = DiscoveryColors.AccentText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * セカンダリアクションボタン
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(DiscoveryRadius.button))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = DiscoverySpacing.base, vertical = DiscoverySpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) DiscoveryColors.Accent else DiscoveryColors.TextTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 控えめで洗練されたメタデータバッジ（時間・行動タイプ）
 */
@Composable
fun DurationBadge(minutes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.badge))
            .background(DiscoveryColors.BadgeBackground)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics {
                contentDescription = "所要時間 $minutes 分"
            }
    ) {
        Text(
            text = "$minutes 分",
            color = DiscoveryColors.BadgeText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BehaviorBadge(signal: BehaviorSignal, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.badge))
            .background(DiscoveryColors.AccentSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics {
                contentDescription = "行動シグナル ${signal.japaneseLabel}"
            }
    ) {
        Text(
            text = signal.japaneseLabel,
            color = DiscoveryColors.Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 今日の注目実験カード（ExperimentCard）。
 */
@Composable
fun ExperimentCard(
    experiment: Experiment,
    onTryClick: () -> Unit,
    onSeeOthersClick: () -> Unit,
    modifier: Modifier = Modifier,
    alternativeCount: Int = 2,
    isCycling: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
            .padding(DiscoverySpacing.cardPadding)
    ) {
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DurationBadge(minutes = experiment.plannedMinutes)
                BehaviorBadge(signal = experiment.actionType)
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.base))

            Text(
                text = experiment.title,
                color = DiscoveryColors.TextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

            Text(
                text = experiment.description,
                color = DiscoveryColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            AppPrimaryButton(
                text = "やってみる",
                onClick = onTryClick
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AppSecondaryButton(
                    text = if (alternativeCount > 0) "他の実験を見る（あと${alternativeCount}件）" else "別の実験を見る",
                    onClick = onSeeOthersClick,
                    enabled = !isCycling
                )
            }
        }
    }
}

/**
 * 発見・インサイトカード（DiscoveryInsightCard）。
 */
@Composable
fun DiscoveryInsightCard(
    insightText: String,
    onViewDiscoveryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.insightCard))
            .background(DiscoveryColors.SurfaceSecondary)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.insightCard))
            .clickable(
                role = Role.Button,
                onClick = onViewDiscoveryClick
            )
            .padding(DiscoverySpacing.lg)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "見えてきた傾向",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "発見を見る →",
                    color = DiscoveryColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

            Text(
                text = insightText,
                color = DiscoveryColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * 行動シグナルチップ
 */
@Composable
fun SignalChip(
    signalModel: SignalUiModel,
    modifier: Modifier = Modifier
) {
    val trendArrow = when (signalModel.trend) {
        SignalTrend.GROWING -> " ↑"
        SignalTrend.DECLINING -> " ↓"
        SignalTrend.NEUTRAL -> ""
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.badge))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.badge))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${signalModel.signal.japaneseLabel}$trendArrow",
            color = if (signalModel.trend == SignalTrend.GROWING) DiscoveryColors.Accent else DiscoveryColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (signalModel.trend == SignalTrend.GROWING) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * 1〜5の評価用セレクター
 */
@Composable
fun RatingSelector(
    question: String,
    selectedRating: Int,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = question,
            color = DiscoveryColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (1..5).forEach { score ->
                val isSelected = selectedRating == score
                val bgColor = if (isSelected) DiscoveryColors.Accent else DiscoveryColors.Surface
                val textColor = if (isSelected) DiscoveryColors.AccentText else DiscoveryColors.TextPrimary
                val borderColor = if (isSelected) DiscoveryColors.Accent else DiscoveryColors.BorderSubtle

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(DiscoveryRadius.selector))
                        .background(bgColor)
                        .border(1.5.dp, borderColor, RoundedCornerShape(DiscoveryRadius.selector))
                        .clickable(
                            role = Role.RadioButton,
                            onClick = { onRatingSelected(score) }
                        )
                        .semantics {
                            role = Role.RadioButton
                            contentDescription = "$score 点"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = score.toString(),
                        color = textColor,
                        fontSize = 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "1: あまり思わない",
                color = DiscoveryColors.TextTertiary,
                fontSize = 11.sp
            )
            Text(
                text = "5: とてもそう思う",
                color = DiscoveryColors.TextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * ローディング状態コンポーネント
 */
@Composable
fun DiscoveryLoadingState(
    modifier: Modifier = Modifier,
    message: String = "今日の実験を読み込み中..."
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(DiscoverySpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            color = DiscoveryColors.Accent,
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(DiscoverySpacing.base))
        Text(
            text = message,
            color = DiscoveryColors.TextSecondary,
            fontSize = 14.sp
        )
    }
}

/**
 * エラー復旧用コンポーネント
 */
@Composable
fun DiscoveryErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.ErrorSurface)
            .border(1.dp, DiscoveryColors.ErrorBorder, RoundedCornerShape(DiscoveryRadius.card))
            .padding(DiscoverySpacing.cardPadding)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage,
                color = DiscoveryColors.ErrorText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(DiscoverySpacing.base))
            AppSecondaryButton(
                text = "もう一度試す",
                onClick = onRetry
            )
        }
    }
}

/**
 * 開発・実機検証用のシナリオ切替ダイアログ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScenarioSelectorDialog(
    currentScenario: FakeScenario,
    onScenarioSelected: (FakeScenario) -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DiscoveryRadius.card))
                .background(DiscoveryColors.Surface)
                .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                .padding(DiscoverySpacing.cardPadding)
        ) {
            Column {
                Text(
                    text = "検証用シナリオの切り替え",
                    color = DiscoveryColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                Text(
                    text = "アプリの状態を瞬時に切り替えてUIの挙動を確認できます。",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                FakeScenario.entries.forEach { scenario ->
                    val isSelected = scenario == currentScenario
                    val bgColor = if (isSelected) DiscoveryColors.AccentSoft else DiscoveryColors.SurfaceSecondary
                    val textColor = if (isSelected) DiscoveryColors.Accent else DiscoveryColors.TextPrimary

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DiscoverySpacing.xs)
                            .clip(RoundedCornerShape(DiscoveryRadius.selector))
                            .background(bgColor)
                            .clickable(
                                role = Role.RadioButton,
                                onClick = { onScenarioSelected(scenario) }
                            )
                            .padding(horizontal = DiscoverySpacing.base, vertical = DiscoverySpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = when (scenario) {
                                        FakeScenario.NORMAL -> "通常モード (NORMAL)"
                                        FakeScenario.FIRST_TIME_USER -> "初回利用モード (FIRST_TIME_USER)"
                                        FakeScenario.LOADING -> "遅延シミュレーション (LOADING)"
                                        FakeScenario.ERROR -> "エラー発生モード (ERROR)"
                                        FakeScenario.EMPTY_DISCOVERY -> "分析中モード (EMPTY_DISCOVERY)"
                                    },
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = when (scenario) {
                                        FakeScenario.NORMAL -> "完了2件、シグナルあり、仮説あり"
                                        FakeScenario.FIRST_TIME_USER -> "完了0件、シグナルなし、初心者案内UI"
                                        FakeScenario.LOADING -> "各操作に1.2秒のローディング遅延を挿入"
                                        FakeScenario.ERROR -> "例外発生時のエラー・再試行UIを確認"
                                        FakeScenario.EMPTY_DISCOVERY -> "実験はあるが仮説生成前の状態"
                                    },
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = DiscoveryColors.Accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AppSecondaryButton(
                        text = "閉じる",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}
