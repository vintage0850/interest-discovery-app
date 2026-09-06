package com.example.myapplication.shared.ui.survey

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.PsychAxis
import com.example.myapplication.shared.discovery.PsychAxisUiModel
import com.example.myapplication.shared.ui.discovery.DiscoveryColors
import com.example.myapplication.shared.ui.discovery.DiscoveryRadius
import com.example.myapplication.shared.ui.discovery.DiscoverySpacing
import kotlin.math.roundToInt

/**
 * 心理4軸アンケートの結果を表示するカードComposable。
 * 4軸それぞれのスコアを棒グラフ風のゲージで視覚化する。
 */
@Composable
fun PsychAxisResultCard(
    scores: Map<PsychAxis, Float>,
    modifier: Modifier = Modifier,
    title: String = "あなたの興味の方向性"
) {
    val maxAxisEntry = scores.maxByOrNull { it.value }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
            .padding(DiscoverySpacing.cardPadding)
    ) {
        // ヘッダー部
        Text(
            text = title,
            color = DiscoveryColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

        Text(
            text = "4つの軸（各5点満点）における関心の強さを表しています。",
            color = DiscoveryColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

        // 4軸のスコアバー一覧
        PsychAxis.entries.forEachIndexed { index, axis ->
            val score = scores[axis] ?: 0f
            val isTopAxis = maxAxisEntry?.key == axis && score > 0f

            AxisScoreBarItem(
                axis = axis,
                score = score,
                isHighlight = isTopAxis,
                modifier = Modifier.fillMaxWidth()
            )

            if (index < PsychAxis.entries.lastIndex) {
                Spacer(modifier = Modifier.height(DiscoverySpacing.md))
            }
        }

        // 最高スコア軸のサマリーハイライト
        if (maxAxisEntry != null && maxAxisEntry.value > 0f) {
            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))
            TopAxisHighlight(axis = maxAxisEntry.key, score = maxAxisEntry.value)
        }
    }
}

/**
 * [PsychAxisUiModel] のリストを受け取るオーバーロード。
 */
@Composable
fun PsychAxisResultCard(
    results: List<PsychAxisUiModel>,
    modifier: Modifier = Modifier,
    title: String = "あなたの興味の方向性"
) {
    val scoresMap = results.associate { it.axis to it.score }
    PsychAxisResultCard(scores = scoresMap, modifier = modifier, title = title)
}

/**
 * 軸ごとのスコアバー表示行。
 */
@Composable
private fun AxisScoreBarItem(
    axis: PsychAxis,
    score: Float,
    isHighlight: Boolean,
    modifier: Modifier = Modifier
) {
    val iconEmoji = when (axis) {
        PsychAxis.INVESTIGATE -> "🔍"
        PsychAxis.CREATE -> "🎨"
        PsychAxis.EXECUTE -> "⚡"
        PsychAxis.COMMUNICATE -> "💬"
    }

    val fraction = (score / 5f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        label = "axis_bar_${axis.name}"
    )

    val barColor = if (isHighlight) DiscoveryColors.Accent else DiscoveryColors.TextSecondary.copy(alpha = 0.7f)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = iconEmoji,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(DiscoverySpacing.xs))
                Text(
                    text = "${axis.japaneseLabel} (${axis.name})",
                    color = if (isHighlight) DiscoveryColors.TextPrimary else DiscoveryColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium
                )
            }

            Text(
                text = "${formatScore(score)} / 5.0",
                color = if (isHighlight) DiscoveryColors.Accent else DiscoveryColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // プログレスバー背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(DiscoveryRadius.badge))
                .background(DiscoveryColors.SurfaceSecondary)
        ) {
            // プログレスバー本体
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(DiscoveryRadius.badge))
                    .background(barColor)
            )
        }
    }
}

/**
 * 最も高いスコアの軸を強調するサマリー表示。
 */
@Composable
private fun TopAxisHighlight(
    axis: PsychAxis,
    score: Float,
    modifier: Modifier = Modifier
) {
    val description = when (axis) {
        PsychAxis.INVESTIGATE -> "物事の背景や仕組みをとことん掘り下げる「探究」への関心が際立っています。"
        PsychAxis.CREATE -> "新しいモノや発想を生み出し、形にしていく「創造」への関心が際立っています。"
        PsychAxis.EXECUTE -> "計画的に段取りを整え、確実にゴールへ進める「実行」への関心が際立っています。"
        PsychAxis.COMMUNICATE -> "学んだ気づきを周りと共有し、対話を深める「伝達」への関心が際立っています。"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.selector))
            .background(DiscoveryColors.AccentSoft)
            .padding(DiscoverySpacing.md)
    ) {
        Column {
            Text(
                text = "✨ 主な強み・特徴: ${axis.japaneseLabel}",
                color = DiscoveryColors.AccentDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = DiscoveryColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * 浮動小数点数を小数点第1位までの文字列にフォーマットする純粋Kotlin関数（KMP対応）。
 */
internal fun formatScore(score: Float): String {
    val rounded = (score * 10).roundToInt()
    val integerPart = rounded / 10
    val decimalPart = kotlin.math.abs(rounded % 10)
    return "$integerPart.$decimalPart"
}
