package com.mikke.discovery.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskTest {

    private fun task(
        importance: Int = 2,
        urgency: Int = 2,
        deadline: Long = 1_700_000_000_000L,
        isCompleted: Boolean = false
    ) = Task(
        title = "テストタスク",
        deadline = deadline,
        importance = importance,
        urgency = urgency,
        isCompleted = isCompleted
    )

    @Test
    fun `priorityScoreは重要度times3プラス緊急度`() {
        val result = task(importance = 3, urgency = 2).priorityScore
        assertEquals(11, result)
    }

    @Test
    fun `未完了かつ締切を過ぎていればisOverdueはtrue`() {
        val overdue = task(deadline = 1_000L, isCompleted = false)
        assertTrue(overdue.isOverdue(now = 2_000L))
    }

    @Test
    fun `完了済みならisOverdueはfalse`() {
        val completed = task(deadline = 1_000L, isCompleted = true)
        assertFalse(completed.isOverdue(now = 2_000L))
    }

    @Test
    fun `締切前ならisOverdueはfalse`() {
        val notYet = task(deadline = 3_000L, isCompleted = false)
        assertFalse(notYet.isOverdue(now = 2_000L))
    }
}
