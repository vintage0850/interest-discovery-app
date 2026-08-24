package com.example.myapplication.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskWithSubTasksTest {

    private fun task(isCompleted: Boolean = false, progress: Int = 0) = Task(
        title = "テストタスク",
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        isCompleted = isCompleted,
        progress = progress
    )

    private fun subTask(isCompleted: Boolean) =
        SubTask(taskId = 1, title = "サブ", isCompleted = isCompleted)

    @Test
    fun `サブタスクがあればその完了率を返す`() {
        val result = TaskWithSubTasks(
            task = task(),
            subTasks = listOf(subTask(true), subTask(true), subTask(false), subTask(false))
        )
        assertEquals(50, result.progress)
    }

    @Test
    fun `サブタスクが無く完了済みなら100`() {
        val result = TaskWithSubTasks(task = task(isCompleted = true), subTasks = emptyList())
        assertEquals(100, result.progress)
    }

    @Test
    fun `サブタスクが無く未完了ならタスク自身のprogress`() {
        val result = TaskWithSubTasks(task = task(progress = 30), subTasks = emptyList())
        assertEquals(30, result.progress)
    }
}
