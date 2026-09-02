package com.example.myapplication.shared.ui.reversefaq

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

private const val MAX_TITLE_LENGTH = 50

/**
 * 新規案件作成画面。
 * Phase 1では案件名だけで作成し、すぐにダミー質問を生成する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCaseScreen(
    onCaseAdded: (String) -> Unit,
    onBack: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isTitleEmpty = title.isBlank()
    val canSave = !isTitleEmpty

    fun save() {
        if (title.isBlank()) {
            titleTouched = true
            return
        }
        keyboardController?.hide()
        onCaseAdded(title.trim())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新しく確認する") },
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
                    text = "契約書の確認案件を作成します。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        if (it.length <= MAX_TITLE_LENGTH) title = it
                        titleTouched = true
                    },
                    label = { Text("案件名") },
                    placeholder = { Text("例: ○○マンション 賃貸契約 / △△社 雇用契約") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    isError = titleTouched && isTitleEmpty,
                    supportingText = {
                        if (titleTouched && isTitleEmpty) {
                            Text("案件名を入力してください")
                        } else {
                            Text("${title.length} / $MAX_TITLE_LENGTH")
                        }
                    }
                )

                // Phase 2以降で文書選択・本人条件入力を追加する予定。
                // 現時点では案件名だけで作成し、ダミー質問を生成する。
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Text("確認質問を作成する")
            }
        }
    }
}
