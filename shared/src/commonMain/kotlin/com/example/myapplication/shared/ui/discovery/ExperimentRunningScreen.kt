package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.RunningTimerUiState

/**
 * 実験実行中（タイマー稼働中）の画面（日本語版）。
 */
@Composable
fun ExperimentRunningScreen(
    runningState: RunningTimerUiState,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val experiment = runningState.experiment
    val minutes = runningState.elapsedSeconds / 60
    val seconds = runningState.elapsedSeconds % 60
    val formattedTime = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DiscoveryColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DiscoverySpacing.pageHorizontal)
                .padding(top = DiscoverySpacing.xxxl, bottom = DiscoverySpacing.huge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (experiment != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DurationBadge(minutes = experiment.plannedMinutes)
                        BehaviorBadge(signal = experiment.actionType)
                    }

                    Spacer(modifier = Modifier.height(DiscoverySpacing.md))

                    Text(
                        text = experiment.title,
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                    Text(
                        text = experiment.description,
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = DiscoverySpacing.base)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(DiscoveryColors.Surface)
                    .padding(horizontal = 40.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "経過時間",
                    color = DiscoveryColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                Text(
                    text = formattedTime,
                    color = DiscoveryColors.Accent,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

                Text(
                    text = "やってみて「心地いい・楽しい」と感じるか意識してみよう",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppPrimaryButton(
                    text = "実験を完了する",
                    onClick = onFinish
                )

                Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

                AppSecondaryButton(
                    text = "中断してホームに戻る",
                    onClick = onCancel
                )
            }
        }
    }
}
