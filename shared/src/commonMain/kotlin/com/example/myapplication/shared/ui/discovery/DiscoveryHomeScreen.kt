package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.myapplication.shared.discovery.Experiment
import com.example.myapplication.shared.discovery.FakeScenario
import com.example.myapplication.shared.discovery.HomeUiState

/**
 * 興味発見アプリのホーム画面。
 * 今日の状態 ＋ 3つのExperiment（タスクリスト形式）から選んで即実行できる。
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
                .padding(top = DiscoverySpacing.xxxl, bottom = 80.dp) // ボトムナビの余白
        ) {
            // ヘッダー
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = uiState.homeData?.greetingTitle ?: "こんにちは",
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp
                    )

                    Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                    Text(
                        text = uiState.homeData?.greetingSubtitle ?: "今日、5分だけ試してみよう",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                // デバッグ・シナリオ切替用バッジ
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DiscoveryRadius.badge))
                        .background(DiscoveryColors.SurfaceSecondary)
                        .clickable(
                            role = Role.Button,
                            onClick = { showScenarioDialog = true }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
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

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            // エラー状態の表示
            if (uiState.errorMessage != null) {
                DiscoveryErrorState(
                    errorMessage = uiState.errorMessage,
                    onRetry = onRetry
                )
                Spacer(modifier = Modifier.height(DiscoverySpacing.xl))
            }

            // ローディング状態
            if (uiState.isLoading) {
                DiscoveryLoadingState()
            } else {
                val data = uiState.homeData

                // 1. 今日の状態カード (Today's Status)
                if (data != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(if (data.todayCompleted) DiscoveryColors.AccentSoft else DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.base)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (data.todayCompleted) DiscoveryColors.Accent else DiscoveryColors.SurfaceSecondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (data.todayCompleted) "✓" else "⚡",
                                    fontSize = 18.sp,
                                    color = if (data.todayCompleted) DiscoveryColors.AccentText else DiscoveryColors.TextPrimary
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (data.todayCompleted) "今日の実験を完了しました！" else "今日の実験：まだ未実施です",
                                    color = DiscoveryColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (data.todayCompleted) "あなたのシグナルが更新されました。" else "下の3つから好きなものを1つ選んでみよう",
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

                    // 2. 3つのExperiment（タスクリスト形式）
                    Text(
                        text = "今日のおすすめ実験 3選",
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                    data.todayExperiments.forEachIndexed { index, experiment ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = DiscoverySpacing.xs)
                                .clip(RoundedCornerShape(DiscoveryRadius.insightCard))
                                .background(DiscoveryColors.Surface)
                                .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.insightCard))
                                .clickable(
                                    role = Role.Button,
                                    onClick = { onStartExperiment(experiment) }
                                )
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
                                        DurationBadge(minutes = experiment.plannedMinutes)
                                        BehaviorBadge(signal = experiment.actionType)
                                    }

                                    Text(
                                        text = "やってみる →",
                                        color = DiscoveryColors.Accent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                                Text(
                                    text = experiment.title,
                                    color = DiscoveryColors.TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 24.sp
                                )

                                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                                Text(
                                    text = experiment.description,
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

                    // 3. 今週の進捗 & 行動シグナル
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = if (data.completedThisWeek > 0)
                                "今週 ${data.completedThisWeek}件 の実験を完了"
                            else
                                "最初の実験をやってみよう",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                        Text(
                            text = if (data.signals.isNotEmpty())
                                "あなたが夢中になりやすい行動のパターンが見え始めています。"
                            else
                                "5分のアクティビティを試して、自然と惹かれることを見つけてみよう。",
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        if (data.signals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
                            ) {
                                data.signals.forEach { signal ->
                                    SignalChip(signalModel = signal)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

                    // 4. 発見・インサイトセクション
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
