package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.ReportUiState

/**
 * レポート（Report）タブ画面。
 * 週次レポート、月次サマリー、過去の自分との変化を表示。
 */
@Composable
fun ReportTabScreen(
    reportState: ReportUiState,
    onRetry: () -> Unit,
    onReflectionListClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                text = "行動レポート",
                color = DiscoveryColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

            Text(
                text = "今週の取り組みと、過去からの変化の振り返りです。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            if (reportState.isLoading) {
                DiscoveryLoadingState(message = "レポートを作成中...")
            } else if (reportState.errorMessage != null) {
                DiscoveryErrorState(
                    errorMessage = reportState.errorMessage,
                    onRetry = onRetry
                )
            } else {
                val data = reportState.reportData
                if (data != null) {
                    // 1. 日々の振り返りへの導線
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .clickable(onClick = onReflectionListClick)
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📝 日々の振り返り",
                                    color = DiscoveryColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
                                Text(
                                    text = "気づきや想いを書き留める",
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "開く ›",
                                color = DiscoveryColors.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 2. 今週の数字サマリー（完了数 / 取り組み時間）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.md)
                    ) {
                        // 完了数カード
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(DiscoveryRadius.card))
                                .background(DiscoveryColors.Surface)
                                .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                                .padding(DiscoverySpacing.base)
                        ) {
                            Column {
                                Text(
                                    text = "完了した実験",
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
                                Text(
                                    text = "${data.totalCompletedCount} 件",
                                    color = DiscoveryColors.Accent,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 取り組み時間カード
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(DiscoveryRadius.card))
                                .background(DiscoveryColors.Surface)
                                .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                                .padding(DiscoverySpacing.base)
                        ) {
                            Column {
                                Text(
                                    text = "合計時間",
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
                                Text(
                                    text = "${data.totalMinutesSpent} 分",
                                    color = DiscoveryColors.TextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 2. 最も伸びたシグナル & 分析インサイト
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "今週もっとも強かったシグナル",
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
                        ) {
                            Text(
                                text = "🔥 ${data.topSignal.japaneseLabel}",
                                color = DiscoveryColors.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                        Text(
                            text = data.weeklyInsights,
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 3. シグナル分布（バーチャート風）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "シグナル分布",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                        data.signalDistribution.forEach { (name, count) ->
                            val maxCount = 5f
                            val progress = (count / maxCount).coerceIn(0.1f, 1f)

                            Column(modifier = Modifier.padding(vertical = DiscoverySpacing.xs)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 13.sp,
                                        color = DiscoveryColors.TextPrimary
                                    )
                                    Text(
                                        text = "${count}回",
                                        fontSize = 13.sp,
                                        color = DiscoveryColors.TextSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DiscoveryColors.SurfaceSecondary)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(DiscoveryColors.Accent)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 4. 過去の自分との変化
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.SurfaceSecondary)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "🌱 過去の自分との変化",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                        Text(
                            text = data.changeFromPast,
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }
        }
    }
}
