package com.example.myapplication.work

import com.example.myapplication.data.Task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BootReceiver] の単体テスト。
 * 実際の端末再起動は JVM 上で再現できないため、
 * ビジネスロジックを担う [BootReceiver.handleBoot] を検証する。
 */
class BootReceiverTest {

    @Test
    fun `未来の未完了タスクを再予約する`() = runTest {
        val now = 1_700_000_000_000L
        val futureTask = createTask(id = 1, notificationTime = now + 3_600_000L)
        val scheduler = FakeTaskNotificationScheduler()

        BootReceiver.handleBoot(
            now = now,
            getTasks = { listOf(futureTask) },
            scheduler = scheduler
        )

        assertEquals(1, scheduler.scheduled.size)
        assertEquals(futureTask, scheduler.scheduled.single())
    }

    @Test
    fun `対象タスクが無ければ何も予約しない`() = runTest {
        val scheduler = FakeTaskNotificationScheduler()

        BootReceiver.handleBoot(
            now = 1_700_000_000_000L,
            getTasks = { emptyList() },
            scheduler = scheduler
        )

        assertTrue("予約が無い", scheduler.scheduled.isEmpty())
    }

    @Test
    fun `複数タスクがあればすべて再予約する`() = runTest {
        val now = 1_700_000_000_000L
        val tasks = listOf(
            createTask(id = 1, notificationTime = now + 3_600_000L),
            createTask(id = 2, notificationTime = now + 7_200_000L)
        )
        val scheduler = FakeTaskNotificationScheduler()

        BootReceiver.handleBoot(
            now = now,
            getTasks = { tasks },
            scheduler = scheduler
        )

        assertEquals(2, scheduler.scheduled.size)
        assertEquals(tasks, scheduler.scheduled)
    }

    private fun createTask(id: Int, notificationTime: Long): Task = Task(
        id = id,
        title = "テストタスク$id",
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        notificationTime = notificationTime
    )

    private class FakeTaskNotificationScheduler : TaskNotificationScheduler {
        val scheduled = mutableListOf<Task>()
        val cancelled = mutableListOf<Int>()

        override fun schedule(task: Task) {
            scheduled.add(task)
        }

        override fun cancel(taskId: Int) {
            cancelled.add(taskId)
        }
    }
}
