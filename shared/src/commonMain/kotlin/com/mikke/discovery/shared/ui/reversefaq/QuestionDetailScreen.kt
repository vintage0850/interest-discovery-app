package com.mikke.discovery.shared.ui.reversefaq

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mikke.discovery.shared.reversefaq.Question
import com.mikke.discovery.shared.reversefaq.QuestionAnswer
import com.mikke.discovery.shared.reversefaq.QuestionStatus
import com.mikke.discovery.shared.reversefaq.RiskLevel

private const val MAX_ANSWER_LENGTH = 500

/**
 * 質問詳細画面。
 * 質問の理由・契約書根拠・回答入力を表示する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailScreen(
    question: Question,
    answer: QuestionAnswer?,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onSaveAnswer: (String, String?) -> Unit
) {
    var answerText by rememberSaveable { mutableStateOf(answer?.answerText ?: "") }
    var answeredBy by rememberSaveable { mutableStateOf(answer?.answeredBy ?: "") }
    var answerTouched by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val canSave = answerText.isNotBlank()

    fun save() {
        if (answerText.isBlank()) {
            answerTouched = true
            return
        }
        keyboardController?.hide()
        onSaveAnswer(answerText.trim(), answeredBy.trim().takeIf { it.isNotEmpty() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("質問の詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QuestionSection(question = question)

            HorizontalDivider()

            if (question.status == QuestionStatus.CONFIRMED && answer != null) {
                AnswerSection(answer = answer)
            } else {
                AnswerInputSection(
                    answerText = answerText,
                    answeredBy = answeredBy,
                    answerTouched = answerTouched,
                    onAnswerTextChange = {
                        answerText = it
                        answerTouched = true
                    },
                    onAnsweredByChange = { answeredBy = it },
                    onSave = { save() },
                    canSave = canSave
                )
            }

            if (question.status == QuestionStatus.UNCONFIRMED) {
                OutlinedButton(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("確認済みにする（回答なし）")
                }
            }
        }
    }
}

@Composable
private fun QuestionSection(question: Question) {
    Column {
        Text(
            text = question.title,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "リスク: ${question.riskLevel.name}",
            style = MaterialTheme.typography.labelLarge,
            color = when (question.riskLevel) {
                RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                RiskLevel.MEDIUM -> MaterialTheme.colorScheme.tertiary
                RiskLevel.LOW -> MaterialTheme.colorScheme.secondary
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "なぜ確認が必要？",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = question.reason,
            style = MaterialTheme.typography.bodyMedium
        )
        if (!question.sourceText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "契約書の記載",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = question.sourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            question.sourcePage?.let { page ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${page}ページ",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun AnswerSection(answer: QuestionAnswer) {
    Column {
        Text(
            text = "回答",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = answer.answerText,
            style = MaterialTheme.typography.bodyLarge
        )
        answer.answeredBy?.let { by ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "回答者: $by",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AnswerInputSection(
    answerText: String,
    answeredBy: String,
    answerTouched: Boolean,
    onAnswerTextChange: (String) -> Unit,
    onAnsweredByChange: (String) -> Unit,
    onSave: () -> Unit,
    canSave: Boolean
) {
    Column {
        Text(
            text = "回答を記録する",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = answerText,
            onValueChange = {
                if (it.length <= MAX_ANSWER_LENGTH) onAnswerTextChange(it)
            },
            label = { Text("回答") },
            placeholder = { Text("担当者から聞いた内容を記録") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            isError = answerTouched && answerText.isBlank(),
            supportingText = {
                if (answerTouched && answerText.isBlank()) {
                    Text("回答を入力してください")
                } else {
                    Text("${answerText.length} / $MAX_ANSWER_LENGTH")
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = answeredBy,
            onValueChange = onAnsweredByChange,
            label = { Text("回答者（任意）") },
            placeholder = { Text("例: 相手方の担当者") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave
        ) {
            Text("回答を保存する")
        }
    }
}
