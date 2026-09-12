package com.mikke.discovery.shared.ui.discovery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 興味発見アプリのデザインシステムトークン。
 * Linear / Apple Journal のような、洗練されたウォームニュートラルと
 * 抑制されたバイオレットアクセントを基調とする。
 */
object DiscoveryColors {
    val Background = Color(0xFFFBF9F5)       // warm off-white
    val Surface = Color(0xFFFFFFFF)          // near-white
    val SurfaceSecondary = Color(0xFFF4F1EA) // slightly warmer neutral
    val SurfaceElevated = Color(0xFFFAF8F3)  // subtle elevated surface

    val TextPrimary = Color(0xFF1C1B1F)      // near-black
    val TextSecondary = Color(0xFF6E6A63)    // warm gray
    val TextTertiary = Color(0xFF9C978E)     // light muted gray

    val Accent = Color(0xFF4E447E)           // restrained muted violet / indigo
    val AccentDark = Color(0xFF3B3363)
    val AccentSoft = Color(0xFFEFEFF8)       // very pale violet
    val AccentText = Color(0xFFFFFFFF)

    val BorderSubtle = Color(0xFFE8E4DC)     // very subtle neutral gray
    val BorderStrong = Color(0xFFD6D0C4)

    val BadgeBackground = Color(0xFFF0EDE5)  // understated badge fill
    val BadgeText = Color(0xFF5A564F)

    val ErrorSurface = Color(0xFFFDF2F2)
    val ErrorText = Color(0xFF991B1B)
    val ErrorBorder = Color(0xFFF87171)
}

object DiscoverySpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp

    val pageHorizontal = 24.dp
    val cardPadding = 24.dp
}

object DiscoveryRadius {
    val badge = 8.dp
    val button = 16.dp
    val card = 24.dp
    val insightCard = 20.dp
    val selector = 12.dp
}
