package com.example.myapplication

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
private const val FILTER_ALL = -1
private const val FILTER_UNCATEGORIZED = 0

/** タブ 1 つ分。filter は上の定数、またはカテゴリの id。 */
private data class TaskTab(val filter: Int, val title: String)

/** OAuth クライアント ID 未設定時に出す案内。黙って何も起きない状態を避けるため必ず表示する。 */
const val CALENDAR_NOT_CONFIGURED_MESSAGE =
    "カレンダー連携が未設定です。SETUP.md の手順で OAuth クライアント ID を設定してください。"

/** ユーザーが同意画面で拒否した / キャンセルしたときの案内。 */
const val CALENDAR_NOT_AUTHORIZED_MESSAGE = "Google カレンダーとの連携が許可されていません"

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
    onManageCategories: () -> Unit,
    onTaskToggle: (Task) -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onTaskDelete: (Task) -> Unit,
    onUndoDelete: () -> Unit,
    authState: CalendarAuthState,
    onCalendarLinkChange: (Task, Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var selectedFilter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auth = remember(context) { GoogleAuthManager.get(context) }

    // 同意フローの最中、対象のタスクを覚えておく（同意されたらそのまま登録する）
    var pendingCalendarTask by remember { mutableStateOf<Task?>(null) }
    // アカウントメニュー（連携状況の確認とサインアウト）
    var showAccountMenu by remember { mutableStateOf(false) }

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

    val tabs = remember(categories, tasks) {
        buildList {
            add(TaskTab(FILTER_ALL, "すべて"))
            categories.forEach { add(TaskTab(it.id, it.name)) }
            // カテゴリを消されたタスクの行き先。該当が無いときはタブも出さない
            if (tasks.any { it.task.categoryId == null }) {
                add(TaskTab(FILTER_UNCATEGORIZED, Category.UNCATEGORIZED_LABEL))
            }
        }
    }

    // 選択中のカテゴリが削除されたら「すべて」に戻す
    val selectedIndex = tabs.indexOfFirst { it.filter == selectedFilter }.takeIf { it >= 0 } ?: 0
    val currentTab = tabs[selectedIndex]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("今日のタスク") },
                actions = {
                    Box {
                        IconButton(onClick = { showAccountMenu = true }) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = "カレンダー連携のアカウント",
                                // 連携中かどうかを色の濃さで示す
                                tint = if (authState is CalendarAuthState.Authorized) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    LocalContentColor.current.copy(alpha = 0.4f)
                                }
                            )
                        }
                        CalendarAccountMenu(
                            expanded = showAccountMenu,
                            authState = authState,
                            onDismiss = { showAccountMenu = false },
                            onConnect = {
                                showAccountMenu = false
                                requestAuthorization()
                            },
                            onSignOut = {
                                showAccountMenu = false
                                onSignOut()
                            }
                        )
                    }
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.Filled.Settings, contentDescription = "カテゴリの管理")
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
        val filteredTasks = remember(tasks, currentTab) {
            when (currentTab.filter) {
                FILTER_ALL -> tasks
                FILTER_UNCATEGORIZED -> tasks.filter { it.task.categoryId == null }
                else -> tasks.filter { it.task.categoryId == currentTab.filter }
            }
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
                    onCalendarClick = ::toggleCalendarLink,
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
}

/**
 * カレンダー連携の状況を見せて、接続 / サインアウトをする小さなメニュー。
 * 未設定の場合は理由を出すだけで、操作はできない。
 */
@Composable
private fun CalendarAccountMenu(
    expanded: Boolean,
    authState: CalendarAuthState,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onSignOut: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (authState) {
            is CalendarAuthState.NotConfigured -> {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = CALENDAR_NOT_CONFIGURED_MESSAGE,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {},
                    enabled = false
                )
            }
            is CalendarAuthState.NotAuthorized -> {
                DropdownMenuItem(
                    text = { Text("Google カレンダーに接続") },
                    onClick = onConnect
                )
            }
            is CalendarAuthState.Authorized -> {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = authState.email ?: "連携中の Google アカウント",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("サインアウト") },
                    onClick = onSignOut
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskList(
    tasks: List<TaskWithSubTasks>,
    categoryNames: Map<Int, String>,
    onTaskToggle: (Task) -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onCalendarClick: (Task) -> Unit,
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
                    onCalendarClick = { onCalendarClick(item.task) }
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
fun TaskItem(
    item: TaskWithSubTasks,
    categoryName: String,
    onToggle: () -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onCalendarClick: () -> Unit = {}
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
            Column(modifier = Modifier.weight(1f)) {
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
            SubTaskSection(item = item, onSubTaskToggle = onSubTaskToggle)
        }
    }
}

@Composable
private fun SubTaskSection(
    item: TaskWithSubTasks,
    onSubTaskToggle: (SubTask) -> Unit
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
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null
                )
            }
        }
    }
}
