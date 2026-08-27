package com.example.myapplication

import com.example.myapplication.data.Category
import com.example.myapplication.data.SubTask
import com.example.myapplication.data.Task
import com.example.myapplication.data.TaskStatus
import com.example.myapplication.data.TaskWithSubTasks
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskListLogicTest {

    @Test
    fun `選択中のカテゴリが削除されたらすべてに戻す`() {
        val categories = listOf(Category(id = 1, name = "仕事"))
        val selected = resolveSelectedFilter(
            selectedFilter = 2,
            categories = categories,
            hasUncategorizedTasks = false
        )
        assertEquals(FILTER_ALL, selected)
    }

    @Test
    fun `すべてが選択中ならそのまま`() {
        val categories = listOf(Category(id = 1, name = "仕事"))
        val selected = resolveSelectedFilter(
            selectedFilter = FILTER_ALL,
            categories = categories,
            hasUncategorizedTasks = false
        )
        assertEquals(FILTER_ALL, selected)
    }

    @Test
    fun `未分類が選択中で未分類タスクが無くなったらすべてに戻す`() {
        val categories = emptyList<Category>()
        val selected = resolveSelectedFilter(
            selectedFilter = FILTER_UNCATEGORIZED,
            categories = categories,
            hasUncategorizedTasks = false
        )
        assertEquals(FILTER_ALL, selected)
    }

    @Test
    fun `存在するカテゴリIDならそのまま`() {
        val categories = listOf(Category(id = 5, name = "仕事"))
        val selected = resolveSelectedFilter(
            selectedFilter = 5,
            categories = categories,
            hasUncategorizedTasks = false
        )
        assertEquals(5, selected)
    }

    @Test
    fun `優先順位順では未完了を先頭に優先度の高い順で並ぶ`() {
        val tasks = listOf(
            taskWithSubTasks(id = 1, isCompleted = true, importance = 3, urgency = 3, deadline = 1000L),
            taskWithSubTasks(id = 2, isCompleted = false, importance = 1, urgency = 1, deadline = 1000L),
            taskWithSubTasks(id = 3, isCompleted = false, importance = 3, urgency = 3, deadline = 2000L),
            taskWithSubTasks(id = 4, isCompleted = false, importance = 3, urgency = 2, deadline = 1000L)
        )

        val sorted = sortedTasks(tasks, SortOrder.PRIORITY)

        assertEquals(listOf(3L, 4L, 2L, 1L), sorted.map { it.task.id.toLong() })
    }

    @Test
    fun `締切が近い順では未完了を先頭に締切の早い順で並ぶ`() {
        val tasks = listOf(
            taskWithSubTasks(id = 1, isCompleted = true, importance = 3, urgency = 3, deadline = 500L),
            taskWithSubTasks(id = 2, isCompleted = false, importance = 1, urgency = 1, deadline = 2000L),
            taskWithSubTasks(id = 3, isCompleted = false, importance = 3, urgency = 3, deadline = 1000L),
            taskWithSubTasks(id = 4, isCompleted = false, importance = 3, urgency = 2, deadline = 1500L)
        )

        val sorted = sortedTasks(tasks, SortOrder.DEADLINE)

        assertEquals(listOf(3L, 4L, 2L, 1L), sorted.map { it.task.id.toLong() })
    }

    @Test
    fun `優先順位順で同点時は作成日時の早い順で安定する`() {
        val tasks = listOf(
            taskWithSubTasks(
                id = 1,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 2000L
            ),
            taskWithSubTasks(
                id = 2,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 1000L
            )
        )

        val sorted = sortedTasks(tasks, SortOrder.PRIORITY)

        assertEquals(listOf(2L, 1L), sorted.map { it.task.id.toLong() })
    }

    @Test
    fun `優先順位順でcreatedAtも同点ならid昇順で安定する`() {
        val tasks = listOf(
            taskWithSubTasks(
                id = 2,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 1000L
            ),
            taskWithSubTasks(
                id = 1,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 1000L
            )
        )

        val sorted = sortedTasks(tasks, SortOrder.PRIORITY)

        assertEquals(listOf(1L, 2L), sorted.map { it.task.id.toLong() })
    }

    @Test
    fun `締切が近い順で同点時は作成日時の早い順で安定する`() {
        val tasks = listOf(
            taskWithSubTasks(
                id = 1,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 2000L
            ),
            taskWithSubTasks(
                id = 2,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 1000L
            )
        )

        val sorted = sortedTasks(tasks, SortOrder.DEADLINE)

        assertEquals(listOf(2L, 1L), sorted.map { it.task.id.toLong() })
    }

    @Test
    fun `締切が近い順でcreatedAtも同点ならid昇順で安定する`() {
        val tasks = listOf(
            taskWithSubTasks(
                id = 2,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 1000L
            ),
            taskWithSubTasks(
                id = 1,
                isCompleted = false,
                importance = 3,
                urgency = 3,
                deadline = 1000L,
                createdAt = 1000L
            )
        )

        val sorted = sortedTasks(tasks, SortOrder.DEADLINE)

        assertEquals(listOf(1L, 2L), sorted.map { it.task.id.toLong() })
    }

    private fun taskWithSubTasks(
        id: Int,
        isCompleted: Boolean,
        importance: Int,
        urgency: Int,
        deadline: Long,
        createdAt: Long = 0L
    ): TaskWithSubTasks = TaskWithSubTasks(
        task = Task(
            id = id,
            title = "タスク$id",
            deadline = deadline,
            importance = importance,
            urgency = urgency,
            isCompleted = isCompleted,
            status = TaskStatus.TODO,
            createdAt = createdAt
        ),
        subTasks = emptyList()
    )
}
