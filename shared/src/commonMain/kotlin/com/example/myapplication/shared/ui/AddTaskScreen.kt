package com.example.myapplication.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.Category
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private const val MAX_TITLE_LENGTH = 50
private const val MAX_SUBTASKS = 10

/** カテゴリ未選択（＝未分類）を rememberSaveable の Int で表すための番兵。 */
private const val NO_CATEGORY = -1

/** 保存時に画面から受け取る入力一式。引数が増えすぎたのでまとめた。 */
data class NewTaskInput(
    val title: String,
    val deadline: Long,
    val importance: Int,
    val urgency: Int,
    /** 所属カテゴリ。null は未分類。 */
    val categoryId: Int?,
    val subTaskTitles: List<String>
)

/**
 * DatePicker が返すのは「UTC のその日の 0 時」。
 * そのままだと端末のタイムゾーン次第で前日/翌日にずれるため、
 * ローカルタイムのその日の 23:59:59 に正規化して締切として保存する。
 */
private fun toLocalEndOfDay(utcMillis: Long): Long {
    val utcDate = Instant.fromEpochMilliseconds(utcMillis)
        .toLocalDateTime(TimeZone.UTC)
        .date
    val localMidnight = utcDate.atTime(23, 59, 59)
    return localMidnight.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

/** 選択した日付を画面表示用に整形する（"2026年8月24日" 形式）。 */
private fun formatDate(utcMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}年${date.monthNumber}月${date.dayOfMonth}日"
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun currentTimeMillis(): Long =
    kotlin.time.Clock.System.now().toEpochMilliseconds()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskScreen(
    categories: List<Category>,
    onAddCategory: (String) -> Unit,
    onTaskAdded: (NewTaskInput) -> Unit,
    onBack: () -> Unit
) {
    // 画面回転で入力が消えないよう rememberSaveable を使う
    var title by rememberSaveable { mutableStateOf("") }
    var importance by rememberSaveable { mutableFloatStateOf(2f) }
    var urgency by rememberSaveable { mutableFloatStateOf(2f) }
    var selectedCategoryId by rememberSaveable { mutableIntStateOf(NO_CATEGORY) }
    // 新しいカテゴリをこの画面から作れるようにする（作成直後はそれを選択状態にする）
    var showNewCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCategoryName by rememberSaveable { mutableStateOf<String?>(null) }

    // 初回表示時と、この画面から作ったカテゴリが一覧に流れてきた時の選択合わせ
    LaunchedEffect(categories) {
        val requested = pendingCategoryName
        val created = requested?.let { name -> categories.firstOrNull { it.name == name } }
        when {
            created != null -> {
                selectedCategoryId = created.id
                pendingCategoryName = null
            }
            // 未選択なら先頭のカテゴリを既定にする
            selectedCategoryId == NO_CATEGORY -> {
                categories.firstOrNull()?.let { selectedCategoryId = it.id }
            }
            // 選択中のカテゴリが他画面で消された場合の取りこぼしを防ぐ
            categories.none { it.id == selectedCategoryId } -> {
                selectedCategoryId = categories.firstOrNull()?.id ?: NO_CATEGORY
            }
        }
    }
    // 一度でも入力に触れたか。触れる前からエラーを出さないためのフラグ
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val subTaskTitles = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentTimeMillis()
    )
    val keyboardController = LocalSoftwareKeyboardController.current

    val isTitleEmpty = title.isBlank()
    val selectedDate = datePickerState.selectedDateMillis
    val canSave = !isTitleEmpty && selectedDate != null

    fun save() {
        val date = datePickerState.selectedDateMillis ?: return
        if (title.isBlank()) {
            titleTouched = true
            return
        }
        keyboardController?.hide()
        onTaskAdded(
            NewTaskInput(
                title = title.trim(),
                deadline = toLocalEndOfDay(date),
                importance = importance.toInt(),
                urgency = urgency.toInt(),
                categoryId = selectedCategoryId.takeIf { it != NO_CATEGORY },
                subTaskTitles = subTaskTitles.toList()
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タスク登録") },
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
                // 小さい画面やキーボード表示時に下の保存ボタンへ届くようにする
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= MAX_TITLE_LENGTH) title = it
                    titleTouched = true
                },
                label = { Text("タイトル") },
                placeholder = { Text("例: プログラミングの学習") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                isError = titleTouched && isTitleEmpty,
                supportingText = {
                    if (titleTouched && isTitleEmpty) {
                        Text("タイトルを入力してください")
                    } else {
                        Text("${title.length} / $MAX_TITLE_LENGTH")
                    }
                }
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val dateText = selectedDate?.let { formatDate(it) } ?: "日付を選択"
                Text(text = "締め切り: $dateText")
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("確定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("キャンセル")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            LabeledSlider(
                label = "重要度: ${importance.toInt()} (1:低 3:高)",
                value = importance,
                onValueChange = { importance = it }
            )

            LabeledSlider(
                label = "緊急度: ${urgency.toInt()} (1:低 3:高)",
                value = urgency,
                onValueChange = { urgency = it }
            )

            Text("カテゴリ", style = MaterialTheme.typography.titleSmall)
            // カテゴリは何個でも作れるので、はみ出したら折り返す
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) },
                        leadingIcon = if (selectedCategoryId == category.id) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                }
                AssistChip(
                    onClick = { showNewCategoryDialog = true },
                    label = { Text("新しいカテゴリ") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                )
            }
            if (categories.isEmpty()) {
                Text(
                    text = "カテゴリがありません。このタスクは「${Category.UNCATEGORIZED_LABEL}」になります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            SubTaskEditor(titles = subTaskTitles)

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Text("タスクを保存する")
            }
        }
    }

    if (showNewCategoryDialog) {
        CategoryNameDialog(
            title = "カテゴリを追加",
            initialName = "",
            confirmLabel = "追加",
            onConfirm = { name ->
                // 作成は非同期なので、名前を控えておいて一覧に現れたら選択する
                pendingCategoryName = name
                onAddCategory(name)
                showNewCategoryDialog = false
            },
            onDismiss = { showNewCategoryDialog = false }
        )
    }
}

@Composable
private fun SubTaskEditor(titles: SnapshotStateList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("サブタスク（任意）", style = MaterialTheme.typography.titleSmall)

        titles.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { titles[index] = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("${index + 1} つ目") },
                    singleLine = true
                )
                IconButton(onClick = { titles.removeAt(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "このサブタスクを削除")
                }
            }
        }

        TextButton(
            onClick = { titles.add("") },
            enabled = titles.size < MAX_SUBTASKS
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (titles.size < MAX_SUBTASKS) {
                    "サブタスクを追加"
                } else {
                    "サブタスクは $MAX_SUBTASKS 件までです"
                }
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..3f,
            steps = 1
        )
    }
}
