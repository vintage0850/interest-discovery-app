package com.mikke.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikke.discovery.data.Category

/**
 * カテゴリ（タスクの枠）の追加・リネーム・削除をする画面。
 *
 * 削除してもタスク自体は消えず「未分類」に移るので、
 * 何件動くのかを確認ダイアログで先に見せる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    categories: List<Category>,
    snackbarHostState: SnackbarHostState,
    onAdd: (String) -> Unit,
    onRename: (Category, String) -> Unit,
    onDelete: (Category) -> Unit,
    countTasksIn: suspend (Int) -> Int,
    onBack: () -> Unit
) {
    // 追加ダイアログ / 編集ダイアログ（編集中のカテゴリを保持）
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    // 削除確認に出す「未分類に移る件数」。数え終わるまでは null
    var deletingTaskCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(deleting) {
        val target = deleting
        deletingTaskCount = if (target == null) null else countTasksIn(target.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("カテゴリの管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("カテゴリを追加") }
            )
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "カテゴリがありません。\n「カテゴリを追加」から作ってください。",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = category }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "「${category.name}」の名前を変更"
                                    )
                                }
                                IconButton(onClick = { deleting = category }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "「${category.name}」を削除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryNameDialog(
            title = "カテゴリを追加",
            initialName = "",
            confirmLabel = "追加",
            onConfirm = {
                onAdd(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editing?.let { category ->
        CategoryNameDialog(
            title = "名前を変更",
            initialName = category.name,
            confirmLabel = "変更",
            onConfirm = {
                onRename(category, it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { category ->
        val count = deletingTaskCount
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("「${category.name}」を削除しますか?") },
            text = {
                Text(
                    when {
                        count == null -> "確認しています…"
                        count > 0 -> "このカテゴリの${count}件のタスクは削除されず、" +
                            "「${Category.UNCATEGORIZED_LABEL}」に移動します。"
                        else -> "このカテゴリにタスクはありません。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    // 件数を数え終わるまでは押せないようにして、確認なしの削除を防ぐ
                    enabled = count != null,
                    onClick = {
                        onDelete(category)
                        deleting = null
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("キャンセル") }
            }
        )
    }
}

/**
 * 追加・リネームで共通の入力ダイアログ。
 * カテゴリ名だけでなくタスク名のリネームでも使うため、ラベルと文字数上限は呼び出し側から渡す。
 */
@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "カテゴリ名",
    maxLength: Int = Category.MAX_NAME_LENGTH
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= maxLength) name = it
                },
                label = { Text(label) },
                singleLine = true,
                supportingText = { Text("${name.length} / $maxLength") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
