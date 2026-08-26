package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Category
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.GoogleAuthManager
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

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
    val subTaskTitles: List<String>,
    val addToCalendar: Boolean,
    val eventHasTime: Boolean,
    val notificationTime: Long?
)

/**
 * DatePicker が返すのは「UTC のその日の 0 時」。
 * そのままだと端末のタイムゾーン次第で前日/翌日にずれるため、
 * ローカルタイムのその日の 23:59:59 に正規化して締切として保存する。
 */
private fun toLocalEndOfDay(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            23, 59, 59
        )
    }.timeInMillis
}

/**
 * DatePicker が返す「UTC のその日の 0 時」から、指定した時（ローカル）を合成する。
 * 通知時刻・予定時刻の両方で、選んだ締切日はそのまま使う。
 */
private fun toLocalDateTime(utcMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskScreen(
    categories: List<Category>,
    authState: CalendarAuthState,
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

    var addToCalendar by rememberSaveable { mutableStateOf(false) }
    var eventHasTime by rememberSaveable { mutableStateOf(false) }
    var eventHour by rememberSaveable { mutableIntStateOf(9) }
    var eventMinute by rememberSaveable { mutableIntStateOf(0) }
    var notificationEnabled by rememberSaveable { mutableStateOf(false) }
    var notificationHour by rememberSaveable { mutableIntStateOf(9) }
    var notificationMinute by rememberSaveable { mutableIntStateOf(0) }
    // 認可できなかった理由（拒否 / 通信エラーなど）。null なら問題なし。トグルも戻す
    var calendarAuthorizationError by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val auth = remember(context) { GoogleAuthManager.get(context) }
    val requestAuthorization = rememberCalendarAuthorization(auth) { outcome ->
        // 通信エラーを「拒否された」と表示しないよう、文言は結果ごとに決める
        val message = outcome.messageOrNull()
        addToCalendar = message == null
        calendarAuthorizationError = message
    }

    // クライアント ID 未設定のときは、黙って無反応にせず理由を出して操作を止める
    val calendarNotConfigured = authState is CalendarAuthState.NotConfigured

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
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
        val deadline = if (eventHasTime) {
            toLocalDateTime(date, eventHour, eventMinute)
        } else {
            toLocalEndOfDay(date)
        }
        val notificationTime = if (notificationEnabled) {
            toLocalDateTime(date, notificationHour, notificationMinute)
        } else {
            null
        }
        onTaskAdded(
            NewTaskInput(
                title = title.trim(),
                deadline = deadline,
                importance = importance.toInt(),
                urgency = urgency.toInt(),
                categoryId = selectedCategoryId.takeIf { it != NO_CATEGORY },
                subTaskTitles = subTaskTitles.toList(),
                addToCalendar = addToCalendar,
                eventHasTime = eventHasTime,
                notificationTime = notificationTime
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
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { save() }
                ),
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
                val dateText = selectedDate?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(toLocalEndOfDay(it)))
                } ?: "日付を選択"
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

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google カレンダーに登録", style = MaterialTheme.typography.titleSmall)
                    // 未設定 / 未同意 の理由を必ず見せる。連携中はどのアカウントかも出す
                    val authorizationError = calendarAuthorizationError
                    val isProblem = calendarNotConfigured || authorizationError != null
                    val description = when {
                        calendarNotConfigured -> CALENDAR_NOT_CONFIGURED_MESSAGE
                        authorizationError != null -> authorizationError
                        authState is CalendarAuthState.Authorized && authState.email != null ->
                            "締切日の終日予定として書き出します（${authState.email}）"
                        else -> "締切日の終日予定として書き出します"
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isProblem) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = addToCalendar,
                    enabled = !calendarNotConfigured,
                    onCheckedChange = { checked ->
                        calendarAuthorizationError = null
                        when {
                            !checked -> addToCalendar = false
                            // 同意済みならそのまま ON、そうでなければ同意フローを起動する
                            authState is CalendarAuthState.Authorized -> addToCalendar = true
                            else -> requestAuthorization()
                        }
                    }
                )
            }

            HorizontalDivider()

            OptionalTimePicker(
                label = "予定の時刻を指定する",
                description = "指定しない場合は締切日の終日予定になります",
                enabled = eventHasTime,
                onEnabledChange = { eventHasTime = it },
                hour = eventHour,
                minute = eventMinute,
                onTimeChange = { h, m -> eventHour = h; eventMinute = m }
            )

            HorizontalDivider()

            OptionalTimePicker(
                label = "通知時刻を指定する",
                description = "指定した時刻に必ず通知します（締切日と同じ日）",
                enabled = notificationEnabled,
                onEnabledChange = { notificationEnabled = it },
                hour = notificationHour,
                minute = notificationMinute,
                onTimeChange = { h, m -> notificationHour = h; notificationMinute = m }
            )

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
