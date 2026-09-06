package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.EvidenceUiModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 行動シグナルから集約されたエビデンス一覧画面。
 * [com.example.myapplication.shared.ui.reflection.ReflectionListScreen] と同様の一覧 UI 構造を踏襲。
 *
 * @param evidences 表示するエビデンス一覧（新しい順）
 * @param isLoading 読み込み中フラグ
 * @param errorMessage エラーメッセージ
 * @param onRetry リトライコールバック
 * @param onBack 戻るボタン押下時のコールバック
 * @param modifier 修飾子
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceListScreen(
    evidences: List<EvidenceUiModel> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DiscoveryColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "行動エビデンス",
                            color = DiscoveryColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "蓄積された行動シグナルの要約です",
                            color = DiscoveryColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                            tint = DiscoveryColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DiscoveryColors.Background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    DiscoveryLoadingState(message = "エビデンスを読み込み中...")
                }
                errorMessage != null -> {
                    DiscoveryErrorState(
                        errorMessage = errorMessage,
                        onRetry = onRetry
                    )
                }
                evidences.isEmpty() -> {
                    EmptyEvidenceView(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = DiscoverySpacing.pageHorizontal,
                            vertical = DiscoverySpacing.base
                        ),
                        verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.md)
                    ) {
                        items(evidences, key = { it.id }) { evidence ->
                            EvidenceCard(
                                evidence = evidence,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * エビデンスアイテムカード。
 */
@Composable
private fun EvidenceCard(
    evidence: EvidenceUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
            .padding(DiscoverySpacing.cardPadding)
    ) {
        // ヘッダー: ドメイン名、シグナル数バッジ、作成日時
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = domainLabel(evidence.domain),
                color = DiscoveryColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(DiscoverySpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // シグナル件数バッジ
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(DiscoveryRadius.badge))
                        .background(DiscoveryColors.BadgeBackground)
                        .padding(horizontal = DiscoverySpacing.sm, vertical = DiscoverySpacing.xs)
                ) {
                    Text(
                        text = "シグナル ${evidence.signalCount}件",
                        color = DiscoveryColors.BadgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = formatInstant(evidence.createdAt),
                    color = DiscoveryColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(DiscoverySpacing.sm))

        // 要約テキスト
        Text(
            text = evidence.summaryText,
            color = DiscoveryColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

/**
 * ドメイン識別子を日本語＋絵文字表示に変換。
 */
private fun domainLabel(domain: String): String = when (domain.lowercase()) {
    "tech" -> "💻 テクノロジー"
    "art" -> "🎨 アート・デザイン"
    "music" -> "🎵 音楽"
    "sports" -> "🏃 スポーツ"
    "science" -> "🔬 サイエンス"
    "social" -> "🗣️ 社会・コミュニケーション"
    "making" -> "📦 モノづくり"
    "nature" -> "🌿 自然・環境"
    "business" -> "📈 ビジネス"
    else -> "✨ $domain"
}

/**
 * 空状態の表示。
 */
@Composable
private fun EmptyEvidenceView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = DiscoverySpacing.pageHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔬",
            fontSize = 48.sp
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.base))

        Text(
            text = "まだエビデンスがありません",
            color = DiscoveryColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))

        Text(
            text = "実験に取り組むと、蓄積された行動シグナルが集計されて分野ごとのエビデンスが自動生成されます。",
            color = DiscoveryColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * KMP共通の日時フォーマット処理（Instant -> YYYY/MM/DD HH:mm）。
 */
@Suppress("DEPRECATION")
internal fun formatInstant(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dt.monthNumber.toString().padStart(2, '0')
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "${dt.year}/$month/$day $hour:$minute"
}

/**
 * プレビュー用Composable（ダミーデータ付き）。
 */
@Composable
fun EvidenceListScreenPreview() {
    val sampleEvidences = listOf(
        EvidenceUiModel(
            id = 1,
            domain = "tech",
            signalCount = 3,
            summaryText = "プログラミングやアルゴリズムの実験に高い関心を示し、想定時間を超えて取り組みました。",
            createdAt = Instant.fromEpochMilliseconds(1725624000000L)
        ),
        EvidenceUiModel(
            id = 2,
            domain = "art",
            signalCount = 2,
            summaryText = "UIの配色やレイアウトの比較において、細かな違いに気づく傾向が観察されました。",
            createdAt = Instant.fromEpochMilliseconds(1725537600000L)
        )
    )

    EvidenceListScreen(
        evidences = sampleEvidences,
        onBack = {}
    )
}
