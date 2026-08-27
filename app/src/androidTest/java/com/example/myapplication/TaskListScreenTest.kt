package com.example.myapplication

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Category
import com.example.myapplication.data.SubTask
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.data.TaskWithSubTasks
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [TaskListScreen] の UI 回帰テスト。
 * カテゴリタブの安定性、編集領域のタップ領域、並び順の切り替えを検証する。
 */
class TaskListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val noOpSnackbarHostState = SnackbarHostState()

    @Test
    fun `カテゴリ削除後はすべてタブが選択されゴーストタブが残らない`() {
        var categories by mutableStateOf(listOf(Category(id = 1, name = "仕事")))

        composeTestRule.setContent {
            TaskListScreen(
                tasks = emptyList(),
                categories = categories,
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("仕事").performClick()
        composeTestRule.onNodeWithText("仕事").assertIsSelected()

        // カテゴリが削除された状態に更新する
        categories = emptyList()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("仕事").assertDoesNotExist()
        composeTestRule.onNodeWithText("すべて").assertIsSelected()
    }

    @Test
    fun `メインタスク編集領域は48dp以上かつタスクを編集する操作名を持つ`() {
        val task = taskWithSubTasks(id = 1, title = "メインタスク")

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("メインタスク、タスクを編集する")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `メインタスク編集領域をタップすると編集ダイアログが開く`() {
        val task = taskWithSubTasks(id = 1, title = "メインタスク")

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("メインタスク、タスクを編集する")
            .performClick()

        composeTestRule.onNodeWithText("タスクを編集").assertIsDisplayed()
    }

    @Test
    fun `サブタスク編集領域は48dp以上かつサブタスク名を変更する操作名を持つ`() {
        val subTask = SubTask(id = 1, taskId = 1, title = "サブタスクA")
        val task = taskWithSubTasks(id = 1, title = "メインタスク", subTasks = listOf(subTask))

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("サブタスクA、サブタスクの名前を変更する")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `サブタスク編集領域をタップするとリネームダイアログが開く`() {
        val subTask = SubTask(id = 1, taskId = 1, title = "サブタスクA")
        val task = taskWithSubTasks(id = 1, title = "メインタスク", subTasks = listOf(subTask))

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("サブタスクA、サブタスクの名前を変更する")
            .performClick()

        composeTestRule.onNodeWithText("サブタスク名を変更").assertIsDisplayed()
    }

    @Test
    fun `並び順切替で締切が近い順が選択される`() {
        composeTestRule.setContent {
            TaskListScreen(
                tasks = emptyList(),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("優先順位順").assertIsSelected()
        composeTestRule.onNodeWithText("締切が近い順").assertIsNotSelected()

        composeTestRule.onNodeWithText("締切が近い順").performClick()

        composeTestRule.onNodeWithText("優先順位順").assertIsNotSelected()
        composeTestRule.onNodeWithText("締切が近い順").assertIsSelected()
    }

    @Test
    fun `サブタスク名は50文字超も編集可能`() {
        val longName = "あ".repeat(60)
        val subTask = SubTask(id = 1, taskId = 1, title = longName, isCompleted = false)
        val task = taskWithSubTasks(id = 1, title = "メインタスク", subTasks = listOf(subTask))
        var renamedTitle: String? = null

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = {},
                onSubTaskRename = { _, newTitle -> renamedTitle = newTitle },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.example.myapplication.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("${longName}、サブタスクの名前を変更する")
            .performClick()

        composeTestRule.onNodeWithText("サブタスク名").performTextClearance()
        val newLongName = "い".repeat(55)
        composeTestRule.onNodeWithText("サブタスク名").performTextInput(newLongName)
        composeTestRule.onNodeWithText("保存").performClick()

        assertEquals(newLongName, renamedTitle)
    }

    private fun taskWithSubTasks(
        id: Int,
        title: String,
        subTasks: List<SubTask> = emptyList()
    ): TaskWithSubTasks = TaskWithSubTasks(
        task = Task(
            id = id,
            title = title,
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            isCompleted = false,
            status = TaskStatus.TODO
        ),
        subTasks = subTasks
    )
}
