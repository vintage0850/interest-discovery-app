package com.mikke.discovery.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.discovery.ReflectionUiState

/**
 * 実験後の振り返り（Reflection）画面（日本語版）。
 */
@Composable
fun ReflectionScreen(
    reflectionState: ReflectionUiState,
    onEnjoymentChange: (Int) -> Unit,
    onCuriosityChange: (Int) -> Unit,
    onRetryIntentChange: (Int) -> Unit,
    onSubmit: () -> Unit,
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
            Text(
                text = "やってみてどうだった？",
                color = DiscoveryColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

            Text(
                text = "今の直感を 1〜5 で教えてください。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            RatingSelector(
                question = "1. やっていて楽しかった？",
                selectedRating = reflectionState.enjoymentRating,
                onRatingSelected = onEnjoymentChange
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            RatingSelector(
                question = "2. もっと知りたくなった？",
                selectedRating = reflectionState.curiosityRating,
                onRatingSelected = onCuriosityChange
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxl))

            RatingSelector(
                question = "3. 似たようなことをまたやってみたい？",
                selectedRating = reflectionState.retryIntentRating,
                onRatingSelected = onRetryIntentChange
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xxxl))

            AppPrimaryButton(
                text = "完了",
                onClick = onSubmit,
                enabled = reflectionState.isValid,
                isLoading = reflectionState.isSubmitting
            )
        }
    }
}
