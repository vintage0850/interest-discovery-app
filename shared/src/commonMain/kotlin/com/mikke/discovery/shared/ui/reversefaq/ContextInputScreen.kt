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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Screen 3: 本人条件入力画面。
 *
 * Phase 4 で PDF 読み込み・テキスト抽出に置き換えるまで、
 * 契約書本文を直接テキスト入力する暫定欄を含む。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextInputScreen(
    caseTitle: String,
    onAnalyze: (documentText: String, userContextJson: String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean = false
) {
    var documentText by rememberSaveable { mutableStateOf("") }
    var showDocumentTextError by rememberSaveable { mutableStateOf(false) }
    var context by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    /**
     * 画面入力値からバックエンド用の user_context JSON 文字列を組み立てる。
     */
    fun buildUserContextJson(): String {
        val map = mutableMapOf<String, JsonElement>()
        if (context.isNotBlank()) {
            map["context"] = JsonPrimitive(context.trim())
        }
        return Json.encodeToString(map)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本人条件の入力") },
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
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "「$caseTitle」の確認に進みます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Phase 4 で PDF からテキスト抽出に置き換える予定の暫定入力欄。
                // 削除しやすいよう、ラベルとコメントで明示している。
                OutlinedTextField(
                    value = documentText,
                    onValueChange = {
                        documentText = it
                        if (it.isNotBlank()) showDocumentTextError = false
                    },
                    label = { Text("契約書本文（Phase 4 までの暫定：直接入力）") },
                    placeholder = { Text("契約書の本文をここに貼り付けてください") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    maxLines = 12,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    isError = showDocumentTextError,
                    supportingText = if (showDocumentTextError) {
                        { Text("質問を生成するには契約書本文を入力してください") }
                    } else {
                        null
                    }
                )

                Text(
                    text = "本人条件",
                    style = MaterialTheme.typography.titleSmall
                )

                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it },
                    label = { Text("本人の状況・気になる点（任意）") },
                    placeholder = { Text("例: 学生です。ペットを飼っています。契約期間の途中解約が心配です。") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    if (documentText.isBlank()) {
                        showDocumentTextError = true
                    } else {
                        onAnalyze(documentText, buildUserContextJson())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "分析中..." else "質問を生成する")
            }
        }
    }
}
