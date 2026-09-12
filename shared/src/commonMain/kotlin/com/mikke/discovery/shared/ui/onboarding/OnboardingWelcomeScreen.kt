package com.mikke.discovery.shared.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.ui.discovery.AppPrimaryButton
import com.mikke.discovery.shared.ui.discovery.DiscoveryColors
import com.mikke.discovery.shared.ui.discovery.DiscoverySpacing

/**
 * Step 1 — Welcome画面。
 *
 * 「Mikke」がどんなアプリかを簡潔に伝え、「はじめる」ボタンで基本情報入力へ進む。
 */
@Composable
fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DiscoveryColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DiscoverySpacing.pageHorizontal)
            .padding(top = DiscoverySpacing.xxxl, bottom = DiscoverySpacing.xxl),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✨",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.xl))

            Text(
                text = "Mikkeへようこそ",
                color = DiscoveryColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.lg))

            Text(
                text = "Mikkeは、小さな行動を通してあなた自身を発見する手助けをします。",
                color = DiscoveryColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(DiscoverySpacing.base))

            Text(
                text = "まだ好きなことが分からなくても大丈夫。\n一緒に探っていきましょう。",
                color = DiscoveryColors.TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 23.sp
            )
        }

        AppPrimaryButton(
            text = "はじめる",
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
