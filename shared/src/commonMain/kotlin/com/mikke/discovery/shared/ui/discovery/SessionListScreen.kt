package com.mikke.discovery.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikke.discovery.shared.discovery.SessionListUiState
import com.mikke.discovery.shared.discovery.SessionSummaryItem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    uiState: SessionListUiState,
    onBack: () -> Unit,
    onSwitchSession: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DiscoveryColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "過去のセッション",
                        color = DiscoveryColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                .padding(horizontal = DiscoverySpacing.pageHorizontal)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DiscoveryLoadingState(message = "読み込み中...")
                    }
                }
                uiState.errorMessage != null -> {
                    DiscoveryErrorState(
                        errorMessage = uiState.errorMessage,
                        onRetry = { /* リトライは呼び出し側で制御 */ }
                    )
                }
                uiState.sessions.isEmpty() -> {
                    EmptySessionListView()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(DiscoverySpacing.md)
                    ) {
                        items(uiState.sessions, key = { it.id }) { session ->
                            SessionListItem(
                                session = session,
                                onClick = { onSwitchSession(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionListItem(
    session: SessionSummaryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DiscoveryRadius.card))
            .background(DiscoveryColors.Surface)
            .border(1.dp, DiscoveryColors.BorderSubtle, RoundedCornerShape(DiscoveryRadius.card))
            .clickable(onClick = onClick)
            .padding(DiscoverySpacing.cardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.nickname ?: "セッション #${session.id}",
                color = DiscoveryColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
            Text(
                text = "最終更新: ${formatSessionInstant(session.updatedAt)}",
                color = DiscoveryColors.TextSecondary,
                fontSize = 12.sp
            )
        }
        Text(
            text = "切り替え ›",
            color = DiscoveryColors.Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptySessionListView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📂",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(DiscoverySpacing.base))
        Text(
            text = "セッションがありません",
            color = DiscoveryColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(DiscoverySpacing.xs))
        Text(
            text = "ここに過去のセッションが表示されます。",
            color = DiscoveryColors.TextSecondary,
            fontSize = 14.sp
        )
    }
}

private fun formatSessionInstant(instant: Instant): String {
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = dt.monthNumber.toString().padStart(2, '0')
    val day = dt.dayOfMonth.toString().padStart(2, '0')
    val hour = dt.hour.toString().padStart(2, '0')
    val minute = dt.minute.toString().padStart(2, '0')
    return "${dt.year}/$month/$day $hour:$minute"
}
