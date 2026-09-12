package com.mikke.discovery.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import com.mikke.discovery.shared.discovery.Experiment
import com.mikke.discovery.shared.discovery.FakeScenario
import com.mikke.discovery.shared.discovery.HomeUiState

/**
 * 高校生向け興味発見アプリのホーム画面。
 * 「今日これやってみない？」を1つ大きく目立たせ、迷わずすぐ始められるUI。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryHomeScreen(
    uiState: HomeUiState,
    onStartExperiment: (Experiment) -> Unit,
    onViewDiscovery: () -> Unit,
    onSelectScenario: (FakeScenario) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showScenarioDialog by remember { mutableStateOf(false) }
    var isAlternativesOpen by remember { mutableStateOf(false) }
    var featuredIndex by remember { mutableStateOf(0) }

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
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.xs)
                ) {
                    Text(text = "✨", fontSize = 22.sp)
                    Text(
                        text = "Mikke",
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // デバッグ・シナリオ切替バッジ
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DiscoveryRadius.badge))
                        .background(DiscoveryColors.SurfaceSecondary)
                        .clickable(
                            role = Role.Button,
                            onClick = { showScenarioDialog = true }
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "設定: ${when (uiState.activeScenario) {
                            FakeScenario.NORMAL -> "通常"
                            FakeScenario.FIRST_TIME_USER -> "初回"
                            FakeScenario.LOADING -> "読込中"
                            FakeScenario.ERROR -> "エラー"
                            FakeScenario.EMPTY_DISCOVERY -> "分析中"
                        }}",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.md))

            // エラー状態の表示
            if (uiState.errorMessage != null) {
                DiscoveryErrorState(
                    errorMessage = uiState.errorMessage,
                    onRetry = onRetry
                )
                Spacer(modifier = Modifier.height(DiscoverySpacing.base))
            }

            // ローディング状態
            if (uiState.isLoading) {
                DiscoveryLoadingState()
            } else {
                val data = uiState.homeData

                if (data != null) {
                    val completed = data.completedThisWeek
                    val target = 3
                    val progress = (completed.toFloat() / target.toFloat()).coerceIn(0f, 1f)

                    // 1. 達成感プログレスバー（最上部）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.base)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.xs)
                                ) {
                                    Text(text = "🔥", fontSize = 14.sp)
                                    Text(
                                        text = "今週の実験: ",
                                        color = DiscoveryColors.TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$completed / $target 完了",
                                        color = DiscoveryColors.Accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }

                                Text(
                                    text = if (completed >= target) "🎉 目標達成！" else "あと ${target - completed} 回で達成！",
                                    color = DiscoveryColors.TextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = DiscoveryColors.Accent,
                                trackColor = DiscoveryColors.SurfaceSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

                    // 2. 🔥 主役カード (Hero Card): 今日これやってみない？
                    val experiments = data.todayExperiments
                    val heroExperiment = experiments.getOrNull(featuredIndex) ?: experiments.firstOrNull()

                    if (heroExperiment != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✨ 今日これやってみない？",
                                color = DiscoveryColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "イチオシ！",
                                color = DiscoveryColors.Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(DiscoveryRadius.card))
                                .background(DiscoveryColors.Surface)
                                .border(1.5.dp, DiscoveryColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(DiscoveryRadius.card))
                                .padding(DiscoverySpacing.cardPadding)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DurationBadge(minutes = heroExperiment.plannedMinutes)
                                        BehaviorBadge(signal = heroExperiment.actionType)
                                    }
                                }

                                Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                                Text(
                                    text = heroExperiment.title,
                                    color = DiscoveryColors.TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    lineHeight = 24.sp
                                )

                                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                                Text(
                                    text = heroExperiment.description,
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                                // 特大「やってみる」ボタン
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(DiscoveryRadius.button))
                                        .background(DiscoveryColors.Accent)
                                        .clickable(
                                            role = Role.Button,
                                            onClick = { onStartExperiment(heroExperiment) }
                                        )
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.xs)
                                    ) {
                                        Text(text = "▶", color = DiscoveryColors.AccentText, fontSize = 12.sp)
                                        Text(
                                            text = "今すぐやってみる（${heroExperiment.plannedMinutes}分）",
                                            color = DiscoveryColors.AccentText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                    // 3. ほかの実験を見る（アコーディオン）
                    if (experiments.size > 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(DiscoveryRadius.button))
                                .background(DiscoveryColors.Surface)
                                .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.button))
                                .clickable { isAlternativesOpen = !isAlternativesOpen }
                                .padding(horizontal = DiscoverySpacing.base, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ほかの実験も見てみる（あと ${experiments.size - 1}件）",
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAlternativesOpen) "▲" else "▼",
                                    color = DiscoveryColors.TextTertiary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        if (isAlternativesOpen) {
                            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                            experiments.forEachIndexed { index, exp ->
                                if (index != featuredIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                                            .background(DiscoveryColors.Surface)
                                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                                            .padding(DiscoverySpacing.base)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.xs),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    DurationBadge(minutes = exp.plannedMinutes)
                                                    BehaviorBadge(signal = exp.actionType)
                                                }

                                                Text(
                                                    text = "メインにする",
                                                    color = DiscoveryColors.TextTertiary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable {
                                                        featuredIndex = index
                                                        isAlternativesOpen = false
                                                    }
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                                            Text(
                                                text = exp.title,
                                                color = DiscoveryColors.TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.End)
                                                    .clip(RoundedCornerShape(DiscoveryRadius.badge))
                                                    .background(DiscoveryColors.SurfaceSecondary)
                                                    .clickable { onStartExperiment(exp) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "これにする →",
                                                    color = DiscoveryColors.Accent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 4. 見えてきた傾向ミニバナー
                    val insight = data.discoveryInsight
                    if (insight != null) {
                        DiscoveryInsightCard(
                            insightText = insight,
                            onViewDiscoveryClick = onViewDiscovery
                        )
                    }
                }
            }
        }

        // デバッグダイアログ
        if (showScenarioDialog) {
            DebugScenarioSelectorDialog(
                currentScenario = uiState.activeScenario,
                onScenarioSelected = { scenario ->
                    onSelectScenario(scenario)
                    showScenarioDialog = false
                },
                onDismiss = { showScenarioDialog = false }
            )
        }
    }
}
