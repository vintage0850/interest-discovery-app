package com.mikke.discovery.shared.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.ui.discovery.AppPrimaryButton
import com.mikke.discovery.shared.ui.discovery.DiscoveryColors
import com.mikke.discovery.shared.ui.discovery.DiscoveryRadius
import com.mikke.discovery.shared.ui.discovery.DiscoverySpacing

private val AGE_RANGES = listOf("12歳以下", "13〜15歳", "16〜18歳", "19歳以上")
private val SCHOOL_STAGES = listOf("小学", "中学", "高校", "大学・専門", "その他")
private val INTEREST_OPTIONS = listOf(
    "テクノロジー",
    "アート",
    "音楽",
    "スポーツ",
    "サイエンス",
    "社会",
    "モノづくり",
    "自然",
    "ビジネス",
    "その他"
)

/**
 * Step 2 — 基本ユーザー情報画面。
 *
 * ニックネーム・年齢層・学年・興味タグを任意に入力し、「次へ」で自己理解チェックへ進む。
 * すべての項目はスキップ可能。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingBasicInfoScreen(
    onNext: (OnboardingBasicInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberOnboardingBasicInfoState(onNext = onNext)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DiscoveryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DiscoverySpacing.pageHorizontal)
            .padding(top = DiscoverySpacing.xxxl, bottom = DiscoverySpacing.xxl)
    ) {
        Text(
            text = "基本情報",
            color = DiscoveryColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        Text(
            text = "個人情報は最小限に留めています。入力しなくても先に進めます。",
            color = DiscoveryColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

        // ニックネーム
        Text(
            text = "ニックネーム（任意）",
            color = DiscoveryColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        OutlinedTextField(
            value = state.info.nickname,
            onValueChange = state::updateNickname,
            placeholder = { Text("お名前や呼び名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(DiscoveryRadius.selector),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DiscoveryColors.Surface,
                unfocusedContainerColor = DiscoveryColors.Surface,
                focusedBorderColor = DiscoveryColors.Accent,
                unfocusedBorderColor = DiscoveryColors.BorderSubtle
            )
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

        // 年齢層
        Text(
            text = "年齢層（任意）",
            color = DiscoveryColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
        ) {
            AGE_RANGES.forEach { range ->
                SelectableChip(
                    text = range,
                    selected = state.info.ageRange == range,
                    onClick = { state.updateAgeRange(range) }
                )
            }
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

        // 学年
        Text(
            text = "学年（任意）",
            color = DiscoveryColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
        ) {
            SCHOOL_STAGES.forEach { stage ->
                SelectableChip(
                    text = stage,
                    selected = state.info.schoolStage == stage,
                    onClick = { state.updateSchoolStage(stage) }
                )
            }
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

        // 興味タグ
        Text(
            text = "興味のある分野（複数選択可・スキップ可）",
            color = DiscoveryColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm)
        ) {
            INTEREST_OPTIONS.forEach { interest ->
                SelectableChip(
                    text = interest,
                    selected = interest in state.info.optionalInterests,
                    onClick = { state.toggleInterest(interest) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

        AppPrimaryButton(
            text = "次へ",
            onClick = state::submit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) DiscoveryColors.Accent else DiscoveryColors.Surface
    val textColor = if (selected) DiscoveryColors.AccentText else DiscoveryColors.TextPrimary
    val borderColor = if (selected) DiscoveryColors.Accent else DiscoveryColors.BorderSubtle

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.badge))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(DiscoveryRadius.badge))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun rememberOnboardingBasicInfoState(
    initial: OnboardingBasicInfo = OnboardingBasicInfo(),
    onNext: (OnboardingBasicInfo) -> Unit
): OnboardingBasicInfoState {
    return androidx.compose.runtime.remember {
        OnboardingBasicInfoState(initial = initial, onNext = onNext)
    }
}
