package com.example.myapplication

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Category
import com.example.myapplication.data.SubTask
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.data.TaskWithSubTasks
import com.example.myapplication.data.calendar.AuthorizationStep
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.GoogleAuthManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * 選択中のタブ。カテゴリは増減するので、位置ではなく「何で絞り込むか」を持たせる。
 * rememberSaveable にそのまま入れられるよう Int で表す。
 */
internal const val FILTER_ALL = -1
internal const val FILTER_UNCATEGORIZED = 0

/** タスク一覧の並び順。DB を変えず画面内で切り替える。 */
internal enum class SortOrder { PRIORITY, DEADLINE }

/**
 * 選択中のカテゴリフィルターを、現在存在するカテゴリ一覧に照らして正規化する。
 * 削除済みのカテゴリ ID や未分類が無くなった場合は「すべて」に戻す。
 */
internal fun resolveSelectedFilter(
    selectedFilter: Int,
    categories: List<Category>,
    hasUncategorizedTasks: Boolean
): Int = when {
    selectedFilter == FILTER_ALL -> FILTER_ALL
    selectedFilter == FILTER_UNCATEGORIZED && hasUncategorizedTasks -> FILTER_UNCATEGORIZED
    selectedFilter == FILTER_UNCATEGORIZED && !hasUncategorizedTasks -> FILTER_ALL
    categories.any { it.id == selectedFilter } -> selectedFilter
    else -> FILTER_ALL
}

/**
 * [tasks] を [sortOrder] に従って並び替える。
 * 未完了を先頭に、その後に完了済みを置く。同点時は id で安定させる。
 */
internal fun sortedTasks(
    tasks: List<TaskWithSubTasks>,
    sortOrder: SortOrder
): List<TaskWithSubTasks> = when (sortOrder) {
    SortOrder.PRIORITY -> tasks.sortedWith(
        compareBy<TaskWithSubTasks> { it.task.isCompleted }
            .thenByDescending { it.task.priorityScore }
            .thenBy { it.task.deadline }
            .thenBy { it.task.id }
    )
    SortOrder.DEADLINE -> tasks.sortedWith(
        compareBy<TaskWithSubTasks> { it.task.isCompleted }
            .thenBy { it.task.deadline }
            .thenByDescending { it.task.priorityScore }
            .thenBy { it.task.id }
    )
}
private data class TaskTab(val filter: Int, val title: String)

/** OAuth クライアント ID 未設定時に出す案内。黙って何も起きない状態を避けるため必ず表示する。 */
const val CALENDAR_NOT_CONFIGURED_MESSAGE =
    "カレンダー連携が未設定です。SETUP.md の手順で OAuth クライアント ID を設定してください。"

/** ユーザーが同意画面で拒否した / キャンセルしたときの案内。 */
const val CALENDAR_NOT_AUTHORIZED_MESSAGE = "Google カレンダーとの連携が許可されていません"

/** タスク名の文字数上限。AddTaskScreen の新規登録時と揃える。 */
private const val TASK_TITLE_MAX_LENGTH = 50

/**
 * 同意フローの結果。通信エラーと「ユーザーが拒否した」を混同しないよう区別する。
 */
internal sealed interface CalendarAuthorizationOutcome {
    /** 認可された。カレンダー操作に進んでよい。 */
    data object Authorized : CalendarAuthorizationOutcome

    /** ユーザーが同意画面で拒否した / キャンセルした。 */
    data object Denied : CalendarAuthorizationOutcome

    /** OAuth クライアント ID が未設定。 */
    data object NotConfigured : CalendarAuthorizationOutcome

    /** 通信エラーなどで認可状態を確認できなかった。[message] はそのまま表示できる文言。 */
    data class Failed(val message: String) : CalendarAuthorizationOutcome
}

/** 結果に応じて画面に出す文言。認可できた場合は表示不要なので null。 */
internal fun CalendarAuthorizationOutcome.messageOrNull(): String? = when (this) {
    is CalendarAuthorizationOutcome.Authorized -> null
    is CalendarAuthorizationOutcome.Denied -> CALENDAR_NOT_AUTHORIZED_MESSAGE
    is CalendarAuthorizationOutcome.NotConfigured -> CALENDAR_NOT_CONFIGURED_MESSAGE
    is CalendarAuthorizationOutcome.Failed -> message
}

/**
 * OAuth の同意フローを起動する関数を返す。
 *
 * 同意画面が不要なら起動せず、その場で結果を [onResult] に渡して終わる。
 * オフラインなどで認可状態を確認できなかった場合は [CalendarAuthorizationOutcome.Failed] を渡すので、
 * 「拒否された」という誤った案内にはならない。
 * ユーザーが同意画面をキャンセルした場合は結果の Intent が null になるが、
 * [GoogleAuthManager.handleAuthorizationResult] が未同意として扱うので呼び出し側の分岐は不要。
 */
@Composable
internal fun rememberCalendarAuthorization(
    auth: GoogleAuthManager,
    onResult: (outcome: CalendarAuthorizationOutcome) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    // 再コンポーズで古いコールバックを掴んだままにならないようにする
    val currentOnResult by rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            val authorized = auth.handleAuthorizationResult(result.data)
            currentOnResult(
                if (authorized) {
                    CalendarAuthorizationOutcome.Authorized
                } else {
                    CalendarAuthorizationOutcome.Denied
                }
            )
        }
    }

    return {
        scope.launch {
            when (val step = auth.authorizationIntentSender()) {
                is AuthorizationStep.Consent ->
                    launcher.launch(IntentSenderRequest.Builder(step.intentSender).build())

                is AuthorizationStep.AlreadyAuthorized -> currentOnResult(
                    if (auth.authState.value is CalendarAuthState.Authorized) {
                        CalendarAuthorizationOutcome.Authorized
                    } else {
                        CalendarAuthorizationOutcome.Denied
                    }
                )

                is AuthorizationStep.NotConfigured ->
                    currentOnResult(CalendarAuthorizationOutcome.NotConfigured)

                is AuthorizationStep.Failed ->
                    currentOnResult(CalendarAuthorizationOutcome.Failed(step.message))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    tasks: List<TaskWithSubTasks>,
    categories: List<Category>,
    snackbarHostState: SnackbarHostState,
    onAddTask: () -> Unit,
    onOpenSettings: () -> Unit,
    onTaskToggle: (Task) -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onSubTaskRename: (SubTask, String) -> Unit,
    onTaskDelete: (Task) -> Unit,
    onUndoDelete: () -> Unit,
    onTaskEdit: (Task, TaskEditResult) -> Unit,
    authState: CalendarAuthState,
    onCalendarLinkChange: (Task, Boolean) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    var sortOrder by rememberSaveable { mutableStateOf(SortOrder.PRIORITY) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auth = remember(context) { GoogleAuthManager.get(context) }

    // 同意フローの最中、対象のタスクを覚えておく（同意されたらそのまま登録する）
    var pendingCalendarTask by remember { mutableStateOf<Task?>(null) }
    // リネーム対象のタスク。null ならダイアログを出さない
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    // リネーム対象のサブタスク。null ならダイアログを出さない
    var subTaskToEdit by remember { mutableStateOf<SubTask?>(null) }

    fun notify(message: String) {
        snackbarHostState.currentSnackbarData?.dismiss()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 登録・削除の結果メッセージは ViewModel 側から流れてくるのでここでは出さない
    val requestAuthorization = rememberCalendarAuthorization(auth) { outcome ->
        val task = pendingCalendarTask
        pendingCalendarTask = null
        // 拒否・未設定・通信エラーはそれぞれ違う文言で知らせる
        val message = outcome.messageOrNull()
        when {
            message != null -> notify(message)
            task != null -> onCalendarLinkChange(task, true)
        }
    }

    fun toggleCalendarLink(task: Task) {
        when {
            task.calendarEventId != null -> onCalendarLinkChange(task, false)
            authState is CalendarAuthState.NotConfigured -> notify(CALENDAR_NOT_CONFIGURED_MESSAGE)
            authState is CalendarAuthState.Authorized -> onCalendarLinkChange(task, true)
            else -> {
                pendingCalendarTask = task
                requestAuthorization()
            }
        }
    }

    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }

    val hasUncategorizedTasks = remember(tasks) { tasks.any { it.task.categoryId == null } }

    // カテゴリが削除されたり未分類タスクが無くなったりしたら、選択中フィルターを正規化する
    val currentFilter = resolveSelectedFilter(selectedFilter, categories, hasUncategorizedTasks)
    if (currentFilter != selectedFilter) {
        selectedFilter = currentFilter
    }

    val tabs = remember(categories, hasUncategorizedTasks) {
        buildList {
            add(TaskTab(FILTER_ALL, "すべて"))
            categories.forEach { add(TaskTab(it.id, it.name)) }
            // カテゴリを消されたタスクの行き先。該当が無いときはタブも出さない
            if (hasUncategorizedTasks) {
                add(TaskTab(FILTER_UNCATEGORIZED, Category.UNCATEGORIZED_LABEL))
            }
        }
    }

    // 選択中のカテゴリが削除されたら「すべて」に戻す
    val selectedIndex = tabs.indexOfFirst { it.filter == currentFilter }.takeIf { it >= 0 } ?: 0
    val currentTab = tabs[selectedIndex]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("今日のタスク") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "設定"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = "タスクを追加")
            }
        }
    ) { padding ->
        val filteredTasks = remember(tasks, currentTab, sortOrder) {
            val filtered = when (currentTab.filter) {
                FILTER_ALL -> tasks
                FILTER_UNCATEGORIZED -> tasks.filter { it.task.categoryId == null }
                else -> tasks.filter { it.task.categoryId == currentTab.filter }
            }
            sortedTasks(filtered, sortOrder)
        }
        val uncompletedCount = filteredTasks.count { !it.task.isCompleted }

        Column(modifier = Modifier.padding(padding)) {
            // カテゴリは何個でも増えるので、固定タブではなく横スクロールにする
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { selectedFilter = tab.filter },
                        text = { Text(tab.title, maxLines = 1) }
                    )
                }
            }

            SortOrderSelector(
                sortOrder = sortOrder,
                onSortOrderChange = { sortOrder = it }
            )

            CharacterStatusHeader(
                uncompletedCount = uncompletedCount,
                hasTasks = filteredTasks.isNotEmpty()
            )

            if (filteredTasks.isEmpty()) {
                EmptyStateView(
                    modifier = Modifier.fillMaxSize(),
                    message = when {
                        tasks.isEmpty() -> "タスクがありません。\n右下の「＋」から追加してください。"
                        currentTab.filter == FILTER_ALL ->
                            "タスクがありません。\n右下の「＋」から追加してください。"
                        else -> "「${currentTab.title}」のタスクはまだありません。"
                    }
                )
            } else {
                TaskList(
                    tasks = filteredTasks,
                    categoryNames = categoryNames,
                    onTaskToggle = onTaskToggle,
                    onSubTaskToggle = onSubTaskToggle,
                    onSubTaskRename = { subTask -> subTaskToEdit = subTask },
                    onCalendarClick = ::toggleCalendarLink,
                    onTaskTitleClick = { task -> taskToEdit = task },
                    onTaskDelete = { task ->
                        onTaskDelete(task)
                        // 直前のスナックバーは畳んで、常に最新の削除に対する取り消しを出す
                        snackbarHostState.currentSnackbarData?.dismiss()
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "「${task.title}」を削除しました",
                                actionLabel = "元に戻す",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) onUndoDelete()
                        }
                    }
                )
            }
        }
    }

    taskToEdit?.let { task ->
        TaskEditDialog(
            task = task,
            onConfirm = { result ->
                onTaskEdit(task, result)
                taskToEdit = null
            },
            onDismiss = { taskToEdit = null }
        )
    }

    subTaskToEdit?.let { subTask ->
        SubTaskRenameDialog(
            initialTitle = subTask.title,
            onConfirm = { newTitle ->
                onSubTaskRename(subTask, newTitle)
                subTaskToEdit = null
            },
            onDismiss = { subTaskToEdit = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskList(
    tasks: List<TaskWithSubTasks>,
    categoryNames: Map<Int, String>,
    onTaskToggle: (Task) -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onSubTaskRename: (SubTask) -> Unit,
    onCalendarClick: (Task) -> Unit,
    onTaskTitleClick: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp) // FAB に隠れないよう余白を確保
    ) {
        // key を渡すことで、並び替え・削除時に行の状態が混ざらないようにする
        items(tasks, key = { it.task.id }) { item ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onTaskDelete(item.task)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = { SwipeToDeleteBackground() }
            ) {
                TaskItem(
                    item = item,
                    categoryName = item.task.categoryId?.let { categoryNames[it] }
                        ?: Category.UNCATEGORIZED_LABEL,
                    onToggle = { onTaskToggle(item.task) },
                    onSubTaskToggle = onSubTaskToggle,
                    onSubTaskRename = onSubTaskRename,
                    onCalendarClick = { onCalendarClick(item.task) },
                    onTitleClick = { onTaskTitleClick(item.task) }
                )
            }
        }
    }
}

@Composable
private fun SwipeToDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "削除",
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun CharacterStatusHeader(uncompletedCount: Int, hasTasks: Boolean) {
    val (imageRes, message) = when {
        !hasTasks -> R.drawable.cat_happy to "のんびり待ってるよ〜"
        uncompletedCount == 0 -> R.drawable.cat_happy to "お見事！全部完了です！"
        uncompletedCount >= 5 -> R.drawable.cat_flustered to "残り${uncompletedCount}件…慌ててます！"
        else -> R.drawable.cat_sad to "あと${uncompletedCount}件、がんばろう…"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun EmptyStateView(modifier: Modifier, message: String) {
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

@Composable
private fun SortOrderSelector(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = sortOrder == SortOrder.PRIORITY,
            onClick = { onSortOrderChange(SortOrder.PRIORITY) },
            label = { Text("優先順位順") }
        )
        FilterChip(
            selected = sortOrder == SortOrder.DEADLINE,
            onClick = { onSortOrderChange(SortOrder.DEADLINE) },
            label = { Text("締切が近い順") }
        )
    }
}

@Composable
fun TaskItem(
    item: TaskWithSubTasks,
    categoryName: String,
    onToggle: () -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onSubTaskRename: (SubTask) -> Unit,
    onCalendarClick: () -> Unit = {},
    onTitleClick: () -> Unit = {}
) {
    val task = item.task
    // 優先度スコアは 4〜12。テーマ由来の色を使い、ダークテーマでも読めるようにする
    val containerColor by animateColorAsState(
        targetValue = when {
            task.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
            task.priorityScore >= 10 -> MaterialTheme.colorScheme.errorContainer
            task.priorityScore >= 7 -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "cardColor"
    )

    val dateText = remember(task.deadline) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(task.deadline))
    }
    val overdue = task.isOverdue()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    // タイトルだけでなく左側の情報領域全体をタップ可能にする。
                    // 48dp 以上のタップ領域を確保し、カレンダー・完了・スワイプとは分離する。
                    .clickable(onClick = onTitleClick)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "締切: $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                    )
                    if (overdue) {
                        Text(
                            text = "期限切れ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.status == TaskStatus.IN_PROGRESS) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("進行中", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            val linked = task.calendarEventId != null
            IconButton(onClick = onCalendarClick) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = if (linked) {
                        "カレンダーの予定を削除"
                    } else {
                        "カレンダーに登録"
                    },
                    // 連携済みかどうかを色の濃さで示す
                    tint = if (linked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    }
                )
            }
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() }
            )
        }

        if (item.subTasks.isNotEmpty()) {
            SubTaskSection(
                item = item,
                onSubTaskToggle = onSubTaskToggle,
                onSubTaskRename = onSubTaskRename
            )
        }
    }
}

@Composable
private fun SubTaskSection(
    item: TaskWithSubTasks,
    onSubTaskToggle: (SubTask) -> Unit,
    onSubTaskRename: (SubTask) -> Unit
) {
    val doneCount = item.subTasks.count { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        LinearProgressIndicator(
            progress = { item.progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "サブタスク $doneCount / ${item.subTasks.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        item.subTasks.forEach { subTask ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = subTask.isCompleted,
                    onCheckedChange = { onSubTaskToggle(subTask) }
                )
                Text(
                    text = subTask.title,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSubTaskRename(subTask) }
                        .padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null
                )
            }
        }
    }
}

/**
 * サブタスク名変更ダイアログ。
 * タスク名と同じ文字数上限を適用し、空白のみや変更なしの場合は確定できない。
 */
@Composable
private fun SubTaskRenameDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    val trimmed = title.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("サブタスク名を変更") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= TASK_TITLE_MAX_LENGTH) title = it
                },
                label = { Text("サブタスク名") },
                singleLine = true,
                supportingText = { Text("${title.length} / $TASK_TITLE_MAX_LENGTH") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && trimmed != initialTitle
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
