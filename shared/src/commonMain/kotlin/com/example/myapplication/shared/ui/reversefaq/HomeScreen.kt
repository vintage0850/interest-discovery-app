package com.example.myapplication.shared.ui.reversefaq

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.reversefaq.CaseStatus
import com.example.myapplication.shared.reversefaq.DocumentCase

/**
 * Reverse FAQのホーム画面。
 * 過去案件一覧と「新しく確認する」導線を持つ。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    cases: List<DocumentCase>,
    onAddCase: () -> Unit,
    onCaseClick: (DocumentCase) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Reverse FAQ") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCase) {
                Icon(Icons.Filled.Add, contentDescription = "新しく確認する")
            }
        }
    ) { padding ->
        if (cases.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                message = "確認案件がありません。\n右下の「＋」から新しく確認しましょう。"
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(cases, key = { it.id }) { case ->
                    CaseListItem(
                        case = case,
                        onClick = { onCaseClick(case) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CaseListItem(
    case: DocumentCase,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(case.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = case.status.label(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, message: String) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

private fun CaseStatus.label(): String = when (this) {
    CaseStatus.DRAFT -> "下書き"
    CaseStatus.IN_PROGRESS -> "確認中"
    CaseStatus.COMPLETED -> "確認完了"
    CaseStatus.ARCHIVED -> "アーカイブ"
}
