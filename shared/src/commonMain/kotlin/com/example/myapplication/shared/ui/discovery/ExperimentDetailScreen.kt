package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
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
import com.example.myapplication.shared.discovery.Experiment

/**
 * 実験詳細画面（日本語版）。
 */
@Composable
fun ExperimentDetailScreen(
    experiment: Experiment,
    onStartExperiment: () -> Unit,
    onBack: () -> Unit,
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
                        .clickable(role = Role.Button, onClick = onBack)
                        .padding(DiscoverySpacing.xs)
                ) {
                    Text(
                        text = "← 戻る",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.md))

            Text(
                text = experiment.description,
                color = DiscoveryColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 23.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            if (experiment.reason.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DiscoveryRadius.insightCard))
                        .background(DiscoveryColors.Surface)
                        .padding(DiscoverySpacing.cardPadding)
                ) {
                    Text(
                        text = "なぜこの実験？",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
                    Text(
                        text = experiment.reason,
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }

                Spacer(modifier = Modifier.height(DiscoverySpacing.base))
            }

            if (experiment.testedHypothesis.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DiscoveryRadius.insightCard))
                        .background(DiscoveryColors.SurfaceSecondary)
                        .padding(DiscoverySpacing.cardPadding)
                ) {
                    Text(
                        text = "見てみたいこと",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
                    Text(
                        text = experiment.testedHypothesis,
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }

                Spacer(modifier = Modifier.height(DiscoverySpacing.xxxl))
            }

            AppPrimaryButton(
                text = "実験をスタートする",
                onClick = onStartExperiment
            )
        }
    }
}
