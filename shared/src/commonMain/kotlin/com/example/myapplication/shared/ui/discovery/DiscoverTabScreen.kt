package com.example.myapplication.shared.ui.discovery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.DiscoveryUiState
import com.example.myapplication.shared.discovery.Experiment

/**
 * 発見（Discover）タブ画面。
 * 「今わかってきたこと」「確かめていること」「以前と変わったこと」「なぜそう表示された？」の根拠を表示。
 */
@Composable
fun DiscoverTabScreen(
    discoveryState: DiscoveryUiState,
    onToggleEvidence: () -> Unit,
    onTryNext: (Experiment) -> Unit,
    onRetry: () -> Unit,
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
                text = "見えてきたこと",
                color = DiscoveryColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

            Text(
                text = "これまでの実験から見えてきた行動パターンです。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            if (discoveryState.isLoading) {
                DiscoveryLoadingState(message = "行動シグナルを分析中...")
            } else if (discoveryState.errorMessage != null) {
                DiscoveryErrorState(
                    errorMessage = discoveryState.errorMessage,
                    onRetry = onRetry
                )
            } else {
                val data = discoveryState.discoveryData
                if (data != null) {
                    // 1. 今わかってきたこと (Observation & Hypothesis)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "💡 今わかってきたこと",
                            color = DiscoveryColors.Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                        Text(
                            text = data.hypothesis,
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                        Text(
                            text = data.observation,
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 2. 確かめていること (Testing Focus)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.SurfaceSecondary)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "🎯 いま確かめていること",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                        Text(
                            text = data.testingFocus,
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 3. 以前と変わったこと (Recent Changes)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "📈 以前と変わってきたこと",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                        Text(
                            text = data.recentChanges,
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    // 4. 「なぜそう表示された？」の根拠（アコーディオン）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    role = Role.Button,
                                    onClick = onToggleEvidence
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "❓ なぜそう表示された？（判定の根拠）",
                                color = DiscoveryColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = if (discoveryState.isEvidenceExpanded) "▲" else "▼",
                                color = DiscoveryColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        AnimatedVisibility(
                            visible = discoveryState.isEvidenceExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = DiscoverySpacing.md)) {
                                Text(
                                    text = data.evidenceReason,
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                                Text(
                                    text = data.disclaimer,
                                    color = DiscoveryColors.TextTertiary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

                    // 次の実験候補
                    val nextExp = data.nextExperiment
                    if (nextExp != null) {
                        Text(
                            text = "次に試してみるおすすめ実験",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                        Box(
                            modifier = Modifier
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
                                    DurationBadge(minutes = nextExp.plannedMinutes)
                                    BehaviorBadge(signal = nextExp.actionType)
                                }

                                Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                                Text(
                                    text = nextExp.title,
                                    color = DiscoveryColors.TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                                Text(
                                    text = nextExp.description,
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )

                                Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

                                AppPrimaryButton(
                                    text = "この実験を試す",
                                    onClick = { onTryNext(nextExp) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
