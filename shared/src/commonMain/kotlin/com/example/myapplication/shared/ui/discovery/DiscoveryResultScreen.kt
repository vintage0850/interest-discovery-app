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
 * 発見・仮説画面（日本語版）。
 */
@Composable
fun DiscoveryResultScreen(
    discoveryState: DiscoveryUiState,
    onTryNext: (Experiment) -> Unit,
    onBackHome: () -> Unit,
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
                .padding(top = DiscoverySpacing.xxxl, bottom = DiscoverySpacing.huge)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DiscoveryRadius.badge))
                        .clickable(role = Role.Button, onClick = onBackHome)
                        .padding(DiscoverySpacing.xs)
                ) {
                    Text(
                        text = "← ホームへ戻る",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            Text(
                text = "見えてきたこと",
                color = DiscoveryColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

            Text(
                text = "これまでの実験から見えてきた行動パターンです。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 14.sp
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "行動の観察",
                            color = DiscoveryColors.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                        Text(
                            text = data.observation,
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DiscoveryRadius.card))
                            .background(DiscoveryColors.SurfaceSecondary)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                            .padding(DiscoverySpacing.cardPadding)
                    ) {
                        Text(
                            text = "現在の仮説",
                            color = DiscoveryColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                        Text(
                            text = data.hypothesis,
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                        Text(
                            text = data.disclaimer,
                            color = DiscoveryColors.TextTertiary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

                    val nextExp = data.nextExperiment
                    if (nextExp != null) {
                        Text(
                            text = "次に試してみるおすすめ実験",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(DiscoveryRadius.card))
                                .background(DiscoveryColors.Surface)
                                .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
                                .padding(DiscoverySpacing.cardPadding)
                        ) {
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

                    Spacer(modifier = Modifier.height(DiscoverySpacing.base))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppSecondaryButton(
                            text = "ホームに戻る",
                            onClick = onBackHome
                        )
                    }
                }
            }
        }
    }
}
