package com.example.myapplication.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.Category
import com.example.myapplication.shared.SubTask
import com.example.myapplication.shared.Task
import com.example.myapplication.shared.TaskStatus
import com.example.myapplication.shared.TaskWithSubTasks
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import com.example.myapplication.shared.resources.Res
import com.example.myapplication.shared.resources.cat_flustered
import com.example.myapplication.shared.resources.cat_happy
import com.example.myapplication.shared.resources.cat_sad

/**
 * 選択中のタブ。カテゴリは増減するので、位置ではなく「何で絞り込むか」を持たせる。
 * rememberSaveable にそのまま入れられるよう Int で表す。
 */
private const val FILTER_ALL = -1
private const val FILTER_UNCATEGORIZED = 0

/** タブ 1 つ分。filter は上の定数、またはカテゴリの id。 */
private data class TaskTab(val filter: Int, val title: String)

/** タスク名の文字数上限。AddTaskScreen の新規登録時と揃える。 */
private const val TASK_TITLE_MAX_LENGTH = 50

private fun formatDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}年${date.monthNumber}月${date.dayOfMonth}日"
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
    onTaskRename: (Task, String) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    val scope = rememberCoroutineScope()

    // リネーム対象のタスク。null ならダイアログを出さない
    var taskToRename by remember { mutableStateOf<Task?>(null) }

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
                    onTaskTitleClick = { task -> taskToRename = task },
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

    taskToRename?.let { task ->
        CategoryNameDialog(
            title = "タスク名を変更",
            initialName = task.title,
            confirmLabel = "変更",
            label = "タスク名",
            maxLength = TASK_TITLE_MAX_LENGTH,
            onConfirm = { newTitle ->
                onTaskRename(task, newTitle)
                taskToRename = null
            },
            onDismiss = { taskToRename = null }
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
        !hasTasks -> Res.drawable.cat_happy to "のんびり待ってるよ〜"
        uncompletedCount == 0 -> Res.drawable.cat_happy to "お見事！全部完了です！"
        uncompletedCount >= 5 -> Res.drawable.cat_flustered to "残り${uncompletedCount}件…慌ててます！"
        else -> Res.drawable.cat_sad to "あと${uncompletedCount}件、がんばろう…"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(imageRes),
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

    val dateText = remember(task.deadline) { formatDate(task.deadline) }
    val overdue = remember(task.deadline, task.isCompleted) {
        !task.isCompleted && task.deadline < currentTimeMillis()
    }

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
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    // カード全体のタップ操作（スワイプ削除など）と競合しないよう、タイトルだけをタップ対象にする
                    modifier = Modifier.clickable(onClick = onTitleClick)
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

@OptIn(kotlin.time.ExperimentalTime::class)
private fun currentTimeMillis(): Long =
    kotlin.time.Clock.System.now().toEpochMilliseconds()
