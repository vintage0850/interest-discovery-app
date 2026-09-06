package com.example.myapplication.shared.ui.survey

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.PsychAxis
import com.example.myapplication.shared.ui.discovery.AppPrimaryButton
import com.example.myapplication.shared.ui.discovery.DiscoveryColors
import com.example.myapplication.shared.ui.discovery.DiscoveryRadius
import com.example.myapplication.shared.ui.discovery.DiscoverySpacing
import com.example.myapplication.shared.ui.discovery.RatingSelector

/**
 * 心理4軸アンケート画面。
 *
 * 既存の [com.example.myapplication.shared.ui.onboarding.OnboardingSelfCheckScreen] と
 * 同様のUI構造（縦スクロールColumn、RatingSelector、AppPrimaryButton）を踏襲。
 * 8問の5件法（1〜5）にすべて回答すると「結果を確認する」ボタンが活性化。
 * 送信後は同一画面上で [PsychAxisResultCard] を表示し、結果を確認・完了できる。
 *
 * @param onComplete 回答完了時のコールバック（4軸の平均スコアを渡す）
 * @param onBack 戻るボタン押下時のコールバック
 * @param modifier 修飾子
 */
@Composable
fun PsychAxisSurveyScreen(
    onComplete: (Map<PsychAxis, Float>) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 質問ごとの回答状態（questionId: 1..8 -> rating: 1..5）
    val ratings = remember { mutableStateMapOf<Int, Int>() }
    // 結果表示モードのフラグ
    var showResult by remember { mutableStateOf(false) }

    val answeredCount = ratings.size
    val totalCount = PSYCH_AXIS_QUESTIONS.size
    val isAllAnswered = answeredCount == totalCount

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DiscoveryColors.Background)
            .verticalScroll(scrollState)
            .padding(horizontal = DiscoverySpacing.pageHorizontal)
            .padding(top = DiscoverySpacing.base, bottom = DiscoverySpacing.xxxl)
    ) {
        // トップナビゲーション（戻るボタン）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "戻る",
                    tint = DiscoveryColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(DiscoverySpacing.xs))
            Text(
                text = "設定へ戻る",
                color = DiscoveryColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onBack)
            )
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.base))

        if (!showResult) {
            // ---- アンケート回答モード ----

            Text(
                text = "興味の方向性チェック",
                color = DiscoveryColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

            Text(
                text = "あなたが自然と引きつけられる活動の傾向（探究・創造・実行・伝達）を把握するためのアンケートです。8つの質問に直感でお答えください。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            // 進捗バー & カウンター
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "回答状況",
                    color = DiscoveryColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$answeredCount / $totalCount 問",
                    color = if (isAllAnswered) DiscoveryColors.Accent else DiscoveryColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { answeredCount.toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(DiscoveryRadius.badge)),
                color = DiscoveryColors.Accent,
                trackColor = DiscoveryColors.SurfaceSecondary,
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            // 8問の質問リスト
            PSYCH_AXIS_QUESTIONS.forEachIndexed { index, question ->
                RatingSelector(
                    question = "${index + 1}. ${question.text}",
                    selectedRating = ratings[question.id] ?: 0,
                    onRatingSelected = { selected ->
                        ratings[question.id] = selected
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (index < PSYCH_AXIS_QUESTIONS.lastIndex) {
                    Spacer(modifier = Modifier.height(DiscoverySpacing.xl))
                }
            }

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxxl))

            // 完了ボタン
            AppPrimaryButton(
                text = if (isAllAnswered) "結果を確認する" else "すべての質問にお答えください",
                onClick = {
                    if (isAllAnswered) {
                        showResult = true
                    }
                },
                enabled = isAllAnswered,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // ---- 結果表示モード ----
            val scores = calculatePsychAxisScores(ratings)

            Text(
                text = "アンケート完了！",
                color = DiscoveryColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

            Text(
                text = "回答ありがとうございます。あなたの興味・関心の4軸バランスが集計されました。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            // 結果カード
            PsychAxisResultCard(
                scores = scores,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            // 確定・完了ボタン
            AppPrimaryButton(
                text = "この結果で保存して完了",
                onClick = {
                    onComplete(scores)
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.md))

            // やり直しボタン
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        showResult = false
                    }
                ) {
                    Text(
                        text = "回答を修正する",
                        color = DiscoveryColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * プレビュー用のダミー画面。
 */
@Composable
fun PsychAxisSurveyScreenPreview() {
    PsychAxisSurveyScreen(
        onComplete = {},
        onBack = {}
    )
}
