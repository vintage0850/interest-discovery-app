package com.example.myapplication.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.myapplication.data.Category
import com.example.myapplication.data.NotifiedSlot
import com.example.myapplication.data.SubTask
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskDao
import com.example.myapplication.data.TaskRepository
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.data.TaskWithSubTasks
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.CalendarEventListResponse
import com.example.myapplication.data.calendar.CalendarResult
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.data.calendar.GoogleCalendarApi
import com.example.myapplication.data.calendar.GoogleCalendarSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [FreeTimeCheckWorker] の分岐（未連携／時間帯外／空き無し／通知済み／正常系）を
 * [TestListenableWorkerBuilder] で検証する。
 *
 * 実際の Play Services やカレンダー API・DB には依存せず、フェイクに差し替える。
 */
@RunWith(AndroidJUnit4::class)
class FreeTimeCheckWorkerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneOffset.UTC

    /** 8:00〜22:00 の範囲内。テストの基準時刻。 */
    private fun clockAt(hour: Int): Clock =
        Clock.fixed(Instant.parse("2026-08-23T%02d:00:00Z".format(hour)), zone)

    private fun buildWorker(
        authState: CalendarAuthState,
        calendarSync: GoogleCalendarSync,
        repository: FakeRepository,
        clock: Clock,
        notifier: FakeNotifier
    ): FreeTimeCheckWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = FreeTimeCheckWorker(
                context = appContext,
                params = workerParameters,
                repository = repository,
                authManager = GoogleAuthManager.get(appContext),
                calendarSync = calendarSync,
                authState = { authState },
                clock = clock,
                notifier = notifier
            )
        }
        return TestListenableWorkerBuilder<FreeTimeCheckWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun 未連携ならスキップする() = runBlocking {
        val notifier = FakeNotifier()
        val worker = buildWorker(
            authState = CalendarAuthState.NotAuthorized,
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = FakeRepository(),
            clock = clockAt(10),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("未連携なのに通知が発行された", notifier.notifiedTasks.isEmpty())
    }

    @Test
    fun 時間帯外ならスキップする() = runBlocking {
        val notifier = FakeNotifier()
        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = FakeRepository(topTask = someTask()),
            clock = clockAt(23), // 22:00 以降
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("時間帯外なのに通知が発行された", notifier.notifiedTasks.isEmpty())
    }

    @Test
    fun 空き時間が無ければスキップする() = runBlocking {
        val notifier = FakeNotifier()
        // 今から22:00まで隙間なく予定で埋まっている
        val now = Instant.parse("2026-08-23T10:00:00Z")
        val windowEnd = Instant.parse("2026-08-23T22:00:00Z")
        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(
                events = listOf(
                    com.example.myapplication.data.calendar.CalendarEventSlot(now, windowEnd)
                )
            ),
            repository = FakeRepository(topTask = someTask()),
            clock = Clock.fixed(now, zone),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("空き時間が無いのに通知が発行された", notifier.notifiedTasks.isEmpty())
    }

    @Test
    fun 通知済みスロットならスキップする() = runBlocking {
        val notifier = FakeNotifier()
        val now = Instant.parse("2026-08-23T10:00:00Z")
        val repository = FakeRepository(topTask = someTask())
        // 直近の空きスロットの開始時刻(=now)を既に通知済みとして登録しておく
        repository.notifiedSlotStarts.add(now.toEpochMilli())

        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = repository,
            clock = Clock.fixed(now, zone),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("通知済みスロットなのに再通知された", notifier.notifiedTasks.isEmpty())
    }

    @Test
    fun 正常系で通知が発行されNotifiedSlotが保存される() = runBlocking {
        val notifier = FakeNotifier()
        val now = Instant.parse("2026-08-23T10:00:00Z")
        val task = someTask()
        val repository = FakeRepository(topTask = task)

        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = repository,
            clock = Clock.fixed(now, zone),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(task), notifier.notifiedTasks)
        assertEquals(1, repository.insertedSlots.size)
        assertEquals(now.toEpochMilli(), repository.insertedSlots[0].startMillis)
    }

    private fun someTask(): Task = Task(
        id = 1,
        title = "テストタスク",
        deadline = 1_700_000_000_000L,
        importance = 3,
        urgency = 3,
        categoryId = null,
        isCompleted = false,
        status = TaskStatus.TODO
    )

    /** listTodayEvents だけ差し替える。他のメソッドはこのテストで使わない。 */
    private class FakeCalendarSync(
        private val events: List<com.example.myapplication.data.calendar.CalendarEventSlot>
    ) : GoogleCalendarSync(GoogleAuthManager.get(dummyContext()), DummyApi, NoOpLogger) {
        override suspend fun listTodayEvents(
            now: Instant,
            zoneId: ZoneId
        ): CalendarResult<List<com.example.myapplication.data.calendar.CalendarEventSlot>> =
            CalendarResult.Success(events)

        companion object {
            fun dummyContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext
        }
    }

    private object DummyApi : GoogleCalendarApi {
        override suspend fun insertEvent(
            authorization: String,
            event: com.example.myapplication.data.calendar.CalendarEventRequest
        ) = throw UnsupportedOperationException()

        override suspend fun patchEvent(
            authorization: String,
            eventId: String,
            event: com.example.myapplication.data.calendar.CalendarEventRequest
        ) = throw UnsupportedOperationException()

        override suspend fun deleteEvent(authorization: String, eventId: String): Response<Unit> =
            throw UnsupportedOperationException()

        override suspend fun listEvents(
            authorization: String,
            timeMin: String,
            timeMax: String,
            singleEvents: Boolean,
            orderBy: String
        ): Response<CalendarEventListResponse> = throw UnsupportedOperationException()
    }

    private object NoOpLogger : com.example.myapplication.data.calendar.CalendarLogger {
        override fun d(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private class FakeNotifier : FreeTimeNotifier {
        val notifiedTasks = mutableListOf<Task>()
        override fun notifyTaskStart(task: Task) {
            notifiedTasks.add(task)
        }
    }

    /** [TaskRepository] のフェイク。テストに必要な部分だけ振る舞いを持つ。 */
    private class FakeRepository(
        private val topTask: Task? = null
    ) : TaskRepository(FakeTaskDao()) {
        val notifiedSlotStarts = mutableListOf<Long>()
        val insertedSlots = mutableListOf<NotifiedSlot>()

        override suspend fun isSlotNotified(startMillis: Long): Boolean =
            notifiedSlotStarts.contains(startMillis)

        override suspend fun getTopEligibleTaskForNotification(): Task? = topTask

        override suspend fun insertNotifiedSlot(slot: NotifiedSlot) {
            insertedSlots.add(slot)
        }

        override suspend fun deleteNotifiedSlotsOlderThan(cutoffMillis: Long) = Unit
    }

    /** [TaskRepository] のコンストラクタに必要な最小限の [TaskDao] ダミー。 */
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
