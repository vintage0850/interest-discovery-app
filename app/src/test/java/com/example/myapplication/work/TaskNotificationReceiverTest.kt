package com.example.myapplication.work

import com.example.myapplication.data.Task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TaskNotificationReceiver] の単体テスト。
 * 実際の BroadcastReceiver ライフサイクルは JVM 上で再現できないため、
 * ビジネスロジックを担う [TaskNotificationReceiver.handleReceive] を検証する。
 */
class TaskNotificationReceiverTest {

    @Test
    fun `未完了タスクがあれば通知を発行する`() = runTest {
        val task = createTask(isCompleted = false)
        val notifier = FakeFreeTimeNotifier()

        TaskNotificationReceiver.handleReceive(
            taskId = 1,
            getTask = { task },
            notifier = notifier
        )

        assertEquals(listOf(task), notifier.notifiedTasks)
    }

    @Test
    fun `完了済みタスクでは通知を発行しない`() = runTest {
        val task = createTask(isCompleted = true)
        val notifier = FakeFreeTimeNotifier()

        TaskNotificationReceiver.handleReceive(
            taskId = 1,
            getTask = { task },
            notifier = notifier
        )

        assertTrue("通知が発行されない", notifier.notifiedTasks.isEmpty())
    }

    @Test
    fun `削除済みタスクでは通知を発行しない`() = runTest {
        val notifier = FakeFreeTimeNotifier()

        TaskNotificationReceiver.handleReceive(
            taskId = 1,
            getTask = { null },
            notifier = notifier
        )

        assertTrue("通知が発行されない", notifier.notifiedTasks.isEmpty())
    }

    private fun createTask(isCompleted: Boolean): Task = Task(
        id = 1,
        title = "テストタスク",
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        isCompleted = isCompleted
    )

    private class FakeFreeTimeNotifier : FreeTimeNotifier {
        val notifiedTasks = mutableListOf<Task>()
        override fun notifyTaskStart(task: Task) {
            notifiedTasks.add(task)
        }
    }
}
