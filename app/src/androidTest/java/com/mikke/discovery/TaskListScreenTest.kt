package com.mikke.discovery

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.mikke.discovery.data.Category
import com.mikke.discovery.data.SubTask
import com.mikke.discovery.data.Task
import com.mikke.discovery.data.TaskStatus
import com.mikke.discovery.data.TaskWithSubTasks
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
    fun `カレンダーボタンをタップしても編集ダイアログは開かずカレンダー連携コールバックだけが呼ばれる`() {
        val task = taskWithSubTasks(id = 1, title = "メインタスク")
        var linkChanged = false
        var editOpened = false

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
                onTaskEdit = { _, _ -> editOpened = true },
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> linkChanged = true }
            )
        }

        composeTestRule.onNodeWithContentDescription("カレンダーに登録").performClick()

        assertEquals(true, linkChanged)
        assertEquals(false, editOpened)
        composeTestRule.onNodeWithText("タスクを編集").assertDoesNotExist()
    }

    @Test
    fun `メイン完了チェックをタップしても編集ダイアログは開かずトグルコールバックだけが呼ばれる`() {
        val task = taskWithSubTasks(id = 1, title = "メインタスク")
        var toggled = false
        var editOpened = false

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = { toggled = true },
                onSubTaskToggle = {},
                onSubTaskRename = { _, _ -> },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> editOpened = true },
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()

        assertEquals(true, toggled)
        assertEquals(false, editOpened)
        composeTestRule.onNodeWithText("タスクを編集").assertDoesNotExist()
    }

    @Test
    fun `サブタスク完了チェックをタップしてもリネームダイアログは開かずトグルコールバックだけが呼ばれる`() {
        val subTask = SubTask(id = 1, taskId = 1, title = "サブタスクA")
        val task = taskWithSubTasks(id = 1, title = "メインタスク", subTasks = listOf(subTask))
        var toggled = false
        var renameOpened = false

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(task),
                categories = emptyList(),
                snackbarHostState = noOpSnackbarHostState,
                onAddTask = {},
                onOpenSettings = {},
                onTaskToggle = {},
                onSubTaskToggle = { toggled = true },
                onSubTaskRename = { _, _ -> renameOpened = true },
                onTaskDelete = {},
                onUndoDelete = {},
                onTaskEdit = { _, _ -> },
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()

        assertEquals(true, toggled)
        assertEquals(false, renameOpened)
        composeTestRule.onNodeWithText("サブタスク名を変更").assertDoesNotExist()
    }

    @Test
    fun `スワイプ削除は編集ダイアログを開かず削除コールバックだけを呼ぶ`() {
        val task = taskWithSubTasks(id = 1, title = "メインタスク")
        var deleted = false
        var editOpened = false

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
                onTaskDelete = { deleted = true },
                onUndoDelete = {},
                onTaskEdit = { _, _ -> editOpened = true },
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("メインタスク").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertEquals(true, deleted)
        assertEquals(false, editOpened)
    }

    @Test
    fun `一覧トップバーの設定導線は1個だけである`() {
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        composeTestRule.onAllNodesWithContentDescription("設定").assertCountEquals(1)
    }

    @Test
    fun `並び順切替で実際の表示順が変わる`() {
        val soonDeadline = 1_700_000_000_000L
        val laterDeadline = 1_700_050_000_000L
        // 優先度が低いタスクを締切が近い順にし、優先順位順との違いを作る
        val urgentButLowPriority = TaskWithSubTasks(
            task = Task(
                id = 1,
                title = "締切が近いタスク",
                deadline = soonDeadline,
                importance = 1,
                urgency = 1,
                isCompleted = false,
                status = TaskStatus.TODO
            ),
            subTasks = emptyList()
        )
        val highPriorityLater = TaskWithSubTasks(
            task = Task(
                id = 2,
                title = "優先度が高いタスク",
                deadline = laterDeadline,
                importance = 3,
                urgency = 3,
                isCompleted = false,
                status = TaskStatus.TODO
            ),
            subTasks = emptyList()
        )

        composeTestRule.setContent {
            TaskListScreen(
                tasks = listOf(urgentButLowPriority, highPriorityLater),
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
                onCalendarLinkChange = { _, _ -> }
            )
        }

        // 優先順位順（デフォルト）では優先度が高いタスクが先に来る
        val priorityOrderTop = composeTestRule.onNodeWithText("優先度が高いタスク")
            .fetchSemanticsNode().positionInRoot.y
        val priorityOrderBottom = composeTestRule.onNodeWithText("締切が近いタスク")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("優先順位順では優先度が高いタスクが上に来る", priorityOrderTop < priorityOrderBottom)

        composeTestRule.onNodeWithText("締切が近い順").performClick()
        composeTestRule.waitForIdle()

        // 締切順に切り替えると、締切が近いタスクが先に来る
        val deadlineOrderTop = composeTestRule.onNodeWithText("締切が近いタスク")
            .fetchSemanticsNode().positionInRoot.y
        val deadlineOrderBottom = composeTestRule.onNodeWithText("優先度が高いタスク")
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("締切が近い順では締切が近いタスクが上に来る", deadlineOrderTop < deadlineOrderBottom)
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
                authState = com.mikke.discovery.data.calendar.CalendarAuthState.NotAuthorized,
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
