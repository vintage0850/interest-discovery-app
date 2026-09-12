package com.mikke.discovery.shared.ui.discovery

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.mikke.discovery.shared.discovery.DomainField
import com.mikke.discovery.shared.discovery.ExploreStatus
import com.mikke.discovery.shared.discovery.ExploreUiState

/**
 * 探索（Explore）タブ画面。
 * 分野一覧（未探索 / Explore済み / Try済み / Dive候補）を探索する。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreTabScreen(
    exploreState: ExploreUiState,
    onFilterChange: (ExploreStatus?) -> Unit,
    onFieldClick: (DomainField) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredFields = if (exploreState.selectedFilter == null) {
        exploreState.fields
    } else {
        exploreState.fields.filter { it.status == exploreState.selectedFilter }
    }

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
                text = "分野を探索する",
                color = DiscoveryColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

            Text(
                text = "まだ試していない分野や、さらに深掘りできるテーマです。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            // フィルターチップ群
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
                verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
            ) {
                // 「すべて」
                val isAllSelected = exploreState.selectedFilter == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DiscoveryRadius.badge))
                        .background(if (isAllSelected) DiscoveryColors.Accent else DiscoveryColors.Surface)
                        .border(1.dp, if (isAllSelected) DiscoveryColors.Accent else DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.badge))
                        .clickable(role = Role.RadioButton, onClick = { onFilterChange(null) })
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "すべて",
                        color = if (isAllSelected) DiscoveryColors.AccentText else DiscoveryColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }

                ExploreStatus.entries.forEach { status ->
                    val isSelected = exploreState.selectedFilter == status
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(DiscoveryRadius.badge))
                            .background(if (isSelected) DiscoveryColors.Accent else DiscoveryColors.Surface)
                            .border(1.dp, if (isSelected) DiscoveryColors.Accent else DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.badge))
                            .clickable(role = Role.RadioButton, onClick = { onFilterChange(status) })
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = status.label,
                            color = if (isSelected) DiscoveryColors.AccentText else DiscoveryColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            if (exploreState.isLoading) {
                DiscoveryLoadingState(message = "分野データを読み込み中...")
            } else if (exploreState.errorMessage != null) {
                DiscoveryErrorState(
                    errorMessage = exploreState.errorMessage,
                    onRetry = onRetry
                )
            } else {
                filteredFields.forEach { field ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DiscoverySpacing.xs)
                            .clip(RoundedCornerShape(DiscoveryRadius.insightCard))
                            .background(DiscoveryColors.Surface)
                            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.insightCard))
                            .clickable(
                                role = Role.Button,
                                onClick = { onFieldClick(field) }
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
                                ) {
                                    Text(
                                        text = field.iconEmoji,
                                        fontSize = 22.sp
                                    )

                                    // ステータスバッジ
                                    val statusBg = when (field.status) {
                                        ExploreStatus.DIVE_CANDIDATE -> DiscoveryColors.AccentSoft
                                        ExploreStatus.TRIED -> DiscoveryColors.BadgeBackground
                                        ExploreStatus.EXPLORED -> DiscoveryColors.SurfaceSecondary
                                        ExploreStatus.UNEXPLORED -> DiscoveryColors.Background
                                    }
                                    val statusColor = when (field.status) {
                                        ExploreStatus.DIVE_CANDIDATE -> DiscoveryColors.Accent
                                        ExploreStatus.TRIED -> DiscoveryColors.TextPrimary
                                        ExploreStatus.EXPLORED -> DiscoveryColors.TextSecondary
                                        ExploreStatus.UNEXPLORED -> DiscoveryColors.TextTertiary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(DiscoveryRadius.badge))
                                            .background(statusBg)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = field.status.label,
                                            color = statusColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = "実験 ${field.triedCount}/${field.experimentCount}",
                                    color = DiscoveryColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                            Text(
                                text = field.title,
                                color = DiscoveryColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                            Text(
                                text = field.description,
                                color = DiscoveryColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
