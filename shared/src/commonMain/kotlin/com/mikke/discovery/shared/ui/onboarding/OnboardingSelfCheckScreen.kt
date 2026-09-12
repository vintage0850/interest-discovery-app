package com.mikke.discovery.shared.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.ui.discovery.AppPrimaryButton
import com.mikke.discovery.shared.ui.discovery.DiscoveryColors
import com.mikke.discovery.shared.ui.discovery.DiscoverySpacing
import com.mikke.discovery.shared.ui.discovery.RatingSelector

private val SELF_CHECK_QUESTIONS = listOf(
    "自分が何を楽しいと感じるか説明できる",
    "自分が何を嫌だと感じるか説明できる",
    "何かを選ぶときに自分が大事にしていることが分かる",
    "自然と好奇心を感じるものがいくつかある",
    "最近下した決断について、なぜそうしたか説明できる"
)

/**
 * Step 3 — 初期自己理解チェック画面。
 *
 * 性格診断ではなく、現時点での自己理解の程度を測定する5問のチェック。
 * 5問すべてに回答しないと「はじめる」ボタンは無効。
 */
@Composable
fun OnboardingSelfCheckScreen(
    onComplete: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberOnboardingSelfCheckState(onComplete = onComplete)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DiscoveryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DiscoverySpacing.pageHorizontal)
            .padding(top = DiscoverySpacing.xxxl, bottom = DiscoverySpacing.xxl)
    ) {
        Text(
            text = "初期自己理解チェック",
            color = DiscoveryColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        Text(
            text = "これは診断ではなく、現時点でどれだけ自分を理解できているかの参考情報です。",
            color = DiscoveryColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

        SELF_CHECK_QUESTIONS.forEachIndexed { index, question ->
            RatingSelector(
                question = "${index + 1}. $question",
                selectedRating = state.ratings[index] ?: 0,
                onRatingSelected = { state.setRating(index, it) },
                modifier = Modifier.fillMaxWidth()
            )

            if (index < SELF_CHECK_QUESTIONS.lastIndex) {
                Spacer(modifier = Modifier.height(DiscoverySpacing.xl))
            }
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

        AppPrimaryButton(
            text = "はじめる",
            onClick = state::submit,
            enabled = state.isComplete,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun rememberOnboardingSelfCheckState(
    onComplete: (Float) -> Unit
): OnboardingSelfCheckState {
    return androidx.compose.runtime.remember {
        OnboardingSelfCheckState(onComplete = onComplete)
    }
}
