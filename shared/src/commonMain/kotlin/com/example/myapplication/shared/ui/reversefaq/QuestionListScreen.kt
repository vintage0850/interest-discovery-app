package com.example.myapplication.shared.ui.reversefaq

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.reversefaq.CaseProgress
import com.example.myapplication.shared.reversefaq.Question
import com.example.myapplication.shared.reversefaq.QuestionStatus
import com.example.myapplication.shared.reversefaq.RiskLevel

/**
 * 案件内の質問一覧画面。
 * 未確認/確認済みの状態と進捗を表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionListScreen(
    caseTitle: String,
    questions: List<Question>,
    progress: CaseProgress,
    onBack: () -> Unit,
    onQuestionClick: (Question) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(caseTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ProgressHeader(progress = progress)

            if (questions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "質問がありません。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(questions, key = { it.id }) { question ->
                        QuestionListItem(
                            question = question,
                            onClick = { onQuestionClick(question) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: CaseProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "${progress.totalCount} 件中 ${progress.confirmedCount} 件確認済み",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.percentage / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QuestionListItem(
    question: Question,
    onClick: () -> Unit
) {
    val statusColor = when (question.status) {
        QuestionStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        QuestionStatus.UNCONFIRMED -> MaterialTheme.colorScheme.outline
    }

    val riskColor = when (question.riskLevel) {
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error
        RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
        RiskLevel.LOW -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question.riskLevel.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = riskColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = question.status.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = question.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = question.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

private fun RiskLevel.label(): String = when (this) {
    RiskLevel.HIGH -> "HIGH"
    RiskLevel.MEDIUM -> "MEDIUM"
    RiskLevel.LOW -> "LOW"
}

private fun QuestionStatus.label(): String = when (this) {
    QuestionStatus.UNCONFIRMED -> "未確認"
    QuestionStatus.CONFIRMED -> "確認済み"
}
