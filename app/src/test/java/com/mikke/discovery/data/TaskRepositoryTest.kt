package com.mikke.discovery.data

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TaskRepositoryTest {

    @Test
    fun `updateSubTaskTitleはDAOの部分更新を呼ぶ`() = runTest {
        val dao = mockk<TaskDao>(relaxed = true)
        val repository = TaskRepository(dao)

        repository.updateSubTaskTitle(1, "新しいタイトル")

        coVerify { dao.updateSubTaskTitle(1, "新しいタイトル") }
    }

    @Test
    fun `updateSubTaskCompletedはDAOの部分更新を呼ぶ`() = runTest {
        val dao = mockk<TaskDao>(relaxed = true)
        val repository = TaskRepository(dao)

        repository.updateSubTaskCompleted(1, true)

        coVerify { dao.updateSubTaskCompleted(1, true) }
    }
}
