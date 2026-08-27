package com.example.myapplication.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TaskDao] のサブタスク列単位更新が、他の列を巻き戻さないことを検証する DAO 回帰テスト。
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoSubTaskTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.taskDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `updateSubTaskTitleはタイトルだけを更新し完了状態と表示順は保持する`() = runBlocking {
        val taskId = dao.insertTask(
            Task(title = "メインタスク", deadline = 1_700_000_000_000L, importance = 2, urgency = 2)
        ).toInt()
        val subTask = SubTask(taskId = taskId, title = "下書き", isCompleted = false, sortOrder = 2)
        dao.insertSubTasks(listOf(subTask))
        val inserted = dao.getSubTasksFor(taskId).single()

        dao.updateSubTaskTitle(inserted.id, "清書")

        val updated = dao.getSubTasksFor(taskId).single()
        assertEquals("清書", updated.title)
        assertFalse(updated.isCompleted)
        assertEquals(2, updated.sortOrder)
    }

    @Test
    fun `updateSubTaskCompletedは完了状態だけを更新しタイトルと表示順は保持する`() = runBlocking {
        val taskId = dao.insertTask(
            Task(title = "メインタスク", deadline = 1_700_000_000_000L, importance = 2, urgency = 2)
        ).toInt()
        val subTask = SubTask(taskId = taskId, title = "下書き", isCompleted = false, sortOrder = 3)
        dao.insertSubTasks(listOf(subTask))
        val inserted = dao.getSubTasksFor(taskId).single()

        dao.updateSubTaskCompleted(inserted.id, true)

        val updated = dao.getSubTasksFor(taskId).single()
        assertTrue(updated.isCompleted)
        assertEquals("下書き", updated.title)
        assertEquals(3, updated.sortOrder)
    }

    @Test
    fun `updateSubTaskTitleは50文字超の名前も保持する`() = runBlocking {
        val taskId = dao.insertTask(
            Task(title = "メインタスク", deadline = 1_700_000_000_000L, importance = 2, urgency = 2)
        ).toInt()
        val longTitle = "あ".repeat(60)
        val subTask = SubTask(taskId = taskId, title = "下書き", isCompleted = false, sortOrder = 0)
        dao.insertSubTasks(listOf(subTask))
        val inserted = dao.getSubTasksFor(taskId).single()

        dao.updateSubTaskTitle(inserted.id, longTitle)

        val updated = dao.getSubTasksFor(taskId).single()
        assertEquals(longTitle, updated.title)
    }
}
