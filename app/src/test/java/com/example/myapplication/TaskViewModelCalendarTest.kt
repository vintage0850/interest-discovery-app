package com.example.myapplication

import android.app.Application
import android.content.Context
import com.example.myapplication.data.Category
import com.example.myapplication.data.NotifiedSlot
import com.example.myapplication.data.SubTask
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskDao
import com.example.myapplication.data.TaskRepository
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.data.TaskWithSubTasks
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.CalendarResult
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.data.calendar.GoogleCalendarSync
import com.example.myapplication.data.calendar.SignOutResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [TaskViewModel] のカレンダー連携に関する排他制御と部分更新の単体テスト。
 *
 * 実際の Room や Play Services、ネットワークには頼らず、Repository・CalendarSync・AuthManager を
 * テスト用の偽装クラスに差し替えて動作を確認する。
 */
class TaskViewModelCalendarTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        // viewModelScope が Dispatchers.Main を使うため、テスト用ディスパッチャーで上書きする
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `連続でカレンダー連携を有効化してもinsertEventは1回だけ呼ばれる`() = runTest {
        val task = createTask(id = 1, calendarEventId = null)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        // 素早く 2 回連続でトグル ON にする（実際の UI でも起こりうる操作）
        val jobs = listOf(
            async { viewModel.setCalendarLinked(task, true) },
            async { viewModel.setCalendarLinked(task, true) }
        )
        advanceUntilIdle()
        jobs.awaitAll()

        // 排他制御が効いていれば、予定作成 API は 1 回だけ呼ばれる
        assertEquals("insertEvent の呼び出し回数", 1, calendar.insertCallCount.get())
        // calendarEventId の部分更新も 1 回だけ
        assertEquals("calendarEventId 更新回数", 1, repo.calendarEventIdUpdates.size)
        assertEquals("event123", repo.calendarEventIdUpdates.single().second)
    }

    @Test
    fun `連携結果はcalendarEventIdだけを更新し他の列は上書きしない`() = runTest {
        val task = createTask(
            id = 1,
            title = "元のタイトル",
            calendarEventId = null
        )
        val repo = FakeRepository().apply {
            save(task)
            // ユーザーが別の操作でタイトルを変更した状態をシミュレート
            save(task.copy(title = "後から変えたタイトル"))
        }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.setCalendarLinked(task, true)
        advanceUntilIdle()

        // 部分更新で calendarEventId だけが変わる
        val updated = repo.getTaskById(1)
        assertEquals("event123", updated?.calendarEventId)
        // タイトルは巻き戻っていない
        assertEquals("後から変えたタイトル", updated?.title)
        // 全列更新は呼ばれていない
        assertTrue("全列更新は使われていない", repo.fullTaskUpdates.isEmpty())
    }

    @Test
    fun `連携解除時もcalendarEventIdだけをnullにする`() = runTest {
        val task = createTask(id = 1, calendarEventId = "event_old")
        val repo = FakeRepository().apply {
            save(task)
            save(task.copy(title = "後から変えたタイトル"))
        }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.setCalendarLinked(task, false)
        advanceUntilIdle()

        val updated = repo.getTaskById(1)
        assertNull(updated?.calendarEventId)
        assertEquals("後から変えたタイトル", updated?.title)
        assertTrue(repo.fullTaskUpdates.isEmpty())
    }

    @Test
    fun `新規タスク追加時のカレンダー登録もcalendarEventIdだけを更新する`() = runTest {
        val repo = FakeRepository()
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.addTask(
            title = "新規タスク",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null,
            addToCalendar = true
        )
        advanceUntilIdle()

        // タスクが insert され、calendarEventId のみが更新される
        val inserted = repo.storedTasks.singleOrNull()
        assertEquals("event123", inserted?.calendarEventId)
        assertEquals("新規タスク", inserted?.title)
        assertTrue(repo.fullTaskUpdates.isEmpty())
    }

    @Test
    fun `renameTaskは前後の空白を取り除いてタイトルだけを更新する`() = runTest {
        val task = createTask(id = 1, title = "元のタイトル", calendarEventId = null)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.renameTask(task, "  新しいタイトル  ")
        advanceUntilIdle()

        assertEquals("新しいタイトル", repo.getTaskById(1)?.title)
        assertEquals(listOf(1 to "新しいタイトル"), repo.titleUpdates)
        assertTrue("全列更新は使われていない", repo.fullTaskUpdates.isEmpty())
    }

    @Test
    fun `renameTaskは空文字や変更なしの場合は何もしない`() = runTest {
        val task = createTask(id = 1, title = "元のタイトル")
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.renameTask(task, "   ")
        viewModel.renameTask(task, "元のタイトル")
        advanceUntilIdle()

        assertTrue(repo.titleUpdates.isEmpty())
    }

    @Test
    fun `連携済みタスクをリネームするとカレンダーの予定タイトルも更新される`() = runTest {
        val task = createTask(id = 1, title = "元のタイトル", calendarEventId = "event_old")
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.renameTask(task, "新しいタイトル")
        advanceUntilIdle()

        assertEquals(1, calendar.updateCallCount.get())
        assertEquals("新しいタイトル", calendar.lastUpdatedTask?.title)
    }

    @Test
    fun `未連携タスクをリネームしてもカレンダーの更新は呼ばれない`() = runTest {
        val task = createTask(id = 1, title = "元のタイトル", calendarEventId = null)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.renameTask(task, "新しいタイトル")
        advanceUntilIdle()

        assertEquals(0, calendar.updateCallCount.get())
    }

    private fun createViewModel(
        repository: FakeRepository,
        calendarSync: FakeCalendarSync
    ): TaskViewModel {
        return TaskViewModel(
            application = FakeApplication(),
            repository = repository,
            authManager = FakeAuthManager(),
            calendarSync = calendarSync,
            // WorkManager は単体テストでは初期化されていないため、実際の登録は行わない
            freeTimeCheckScheduler = FreeTimeCheckScheduler {}
        )
    }

    private fun createTask(
        id: Int = 0,
        title: String = "テストタスク",
        calendarEventId: String? = null
    ): Task = Task(
        id = id,
        title = title,
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        categoryId = null,
        isCompleted = false,
        calendarEventId = calendarEventId
    )

    /**
     * テスト用の偽装リポジトリ。
     * 全列更新と部分更新が区別して記録されるよう、[updateTask] と [updateCalendarEventId] を分けて実装する。
     */
    private class FakeRepository : TaskRepository(FakeTaskDao()) {
        private val tasks = mutableMapOf<Int, Task>()
        val calendarEventIdUpdates = mutableListOf<Pair<Int, String?>>()
        val fullTaskUpdates = mutableListOf<Task>()
        val titleUpdates = mutableListOf<Pair<Int, String>>()

        val storedTasks: List<Task> get() = tasks.values.toList()

        fun save(task: Task) {
            tasks[task.id] = task
        }

        override suspend fun getTaskById(id: Int): Task? = tasks[id]

        override suspend fun update(task: Task) {
            fullTaskUpdates.add(task)
            tasks[task.id] = task
        }

        override suspend fun updateCalendarEventId(taskId: Int, calendarEventId: String?) {
            calendarEventIdUpdates.add(taskId to calendarEventId)
            tasks[taskId]?.let { tasks[taskId] = it.copy(calendarEventId = calendarEventId) }
        }

        override suspend fun updateTitle(taskId: Int, title: String) {
            titleUpdates.add(taskId to title)
            tasks[taskId]?.let { tasks[taskId] = it.copy(title = title) }
        }

        override suspend fun insert(task: Task): Int {
            val id = if (task.id == 0) tasks.keys.maxOrNull()?.plus(1) ?: 1 else task.id
            tasks[id] = task.copy(id = id)
            return id
        }
    }

    /**
     * テスト用の偽装カレンダー同期クラス。
     * [insertEvent] を呼んだ回数を数え、一定時間待つことで連続操作の重なりを再現する。
     */
    private class FakeCalendarSync : GoogleCalendarSync(FakeAuthManager()) {
        val insertCallCount = AtomicInteger(0)
        val updateCallCount = AtomicInteger(0)
        var lastUpdatedTask: Task? = null

        override suspend fun insertEvent(
            task: Task,
            categoryName: String,
            subTasks: List<SubTask>
        ): CalendarResult<String> {
            insertCallCount.incrementAndGet()
            delay(50)
            return CalendarResult.Success("event123")
        }

        override suspend fun updateEvent(
            eventId: String,
            task: Task,
            categoryName: String,
            subTasks: List<SubTask>
        ): CalendarResult<Unit> {
            updateCallCount.incrementAndGet()
            lastUpdatedTask = task
            return CalendarResult.Success(Unit)
        }

        override suspend fun deleteEvent(eventId: String): CalendarResult<Unit> {
            return CalendarResult.Success(Unit)
        }
    }

    private class FakeAuthManager : GoogleAuthManager(FakeApplication()) {
        override suspend fun refreshAuthState() {
            // テストでは Play Services を使わないので何もしない
        }

        override suspend fun getAccessToken(): String? = null
    }

    private class FakeApplication : Application() {
        override fun getApplicationContext(): Context = this
    }

    /**
     * [TaskRepository] のコンストラクタに必要な最小限の [TaskDao] ダミー。
     * テストでは使わないメソッドは空実装で済ませる。
     */
    private class FakeTaskDao : TaskDao {
        override fun getAllTasks(): Flow<List<TaskWithSubTasks>> = MutableStateFlow(emptyList())
        override suspend fun getTaskById(id: Int): Task? = null
        override suspend fun insertTask(task: Task): Long = 1L
        override suspend fun updateTask(task: Task) = Unit
        override suspend fun deleteTask(task: Task) = Unit
        override fun getActiveTasks(): Flow<List<Task>> = MutableStateFlow(emptyList())
        override suspend fun insertSubTasks(subTasks: List<SubTask>) = Unit
        override suspend fun updateSubTask(subTask: SubTask) = Unit
        override suspend fun deleteSubTask(subTask: SubTask) = Unit
        override suspend fun getSubTasksFor(taskId: Int): List<SubTask> = emptyList()
        override fun getCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun insertCategory(category: Category): Long = 1L
        override suspend fun updateCategory(category: Category): Int = 1
        override suspend fun deleteCategory(category: Category) = Unit
        override suspend fun getMaxCategorySortOrder(): Int? = null
        override suspend fun countTasksInCategory(categoryId: Int): Int = 0
        override suspend fun updateCalendarEventId(taskId: Int, calendarEventId: String?) = Unit
        override suspend fun updateTaskTitle(taskId: Int, title: String) = Unit
        override suspend fun updateTaskStatus(taskId: Int, status: TaskStatus) = Unit
        override suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean) = Unit
        override suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?) = Unit
        override suspend fun getTasksWithFutureNotification(now: Long): List<Task> = emptyList()
        override suspend fun getTopEligibleTaskForNotification(): Task? = null
        override suspend fun isSlotNotified(startMillis: Long): Boolean = false
        override suspend fun insertNotifiedSlot(slot: NotifiedSlot) = Unit
        override suspend fun deleteNotifiedSlotsOlderThan(cutoffMillis: Long) = Unit
    }
}
