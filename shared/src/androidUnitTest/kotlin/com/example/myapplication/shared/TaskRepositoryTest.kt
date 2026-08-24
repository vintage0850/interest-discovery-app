package com.example.myapplication.shared

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.db.SharedDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRepositoryTest {

    /** ホストJVM上でSQLDelightを動かすためのインメモリJDBCドライバ（実機/エミュレータ不要）。 */
    private class InMemoryDriverFactory : DatabaseDriverFactory {
        override fun createDriver(): SqlDriver {
            val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            SharedDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
            return driver
        }
    }

    private fun newRepository() = TaskRepository(InMemoryDriverFactory())

    private fun sampleTask(title: String = "テストタスク") = Task(
        title = title,
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        createdAt = 1_600_000_000_000L
    )

    @Test
    fun `insertしたタスクをallTasksで取得できる`() = runTest {
        val repo = newRepository()

        val id = repo.insert(sampleTask())

        val tasks = repo.allTasks.first()
        assertEquals(1, tasks.size)
        assertEquals(id, tasks.single().task.id)
        assertEquals("テストタスク", tasks.single().task.title)
    }

    @Test
    fun `updateで内容が変わる`() = runTest {
        val repo = newRepository()
        val id = repo.insert(sampleTask())
        val saved = repo.getTaskById(id)!!

        repo.update(saved.copy(title = "変更後", isCompleted = true, progress = 100))

        val updated = repo.getTaskById(id)!!
        assertEquals("変更後", updated.title)
        assertTrue(updated.isCompleted)
        assertEquals(100, updated.progress)
    }

    @Test
    fun `deleteで消える`() = runTest {
        val repo = newRepository()
        val id = repo.insert(sampleTask())

        repo.delete(repo.getTaskById(id)!!)

        assertNull(repo.getTaskById(id))
    }

    @Test
    fun `カテゴリ削除でタスクは残りcategoryIdがnullになる`() = runTest {
        val repo = newRepository()
        assertTrue(repo.addCategory("仕事"))
        val category = repo.categories.first().single { it.name == "仕事" }
        val id = repo.insert(sampleTask().copy(categoryId = category.id))

        repo.deleteCategory(category)

        val task = repo.getTaskById(id)!!
        assertNull(task.categoryId)
    }

    @Test
    fun `サブタスクの追加取得更新削除`() = runTest {
        val repo = newRepository()
        val id = repo.insert(sampleTask())

        repo.insertSubTasks(
            listOf(
                SubTask(taskId = id, title = "下書き", sortOrder = 0),
                SubTask(taskId = id, title = "清書", sortOrder = 1)
            )
        )
        val subTasks = repo.getSubTasksFor(id)
        assertEquals(listOf("下書き", "清書"), subTasks.map { it.title })

        repo.updateSubTask(subTasks[0].copy(isCompleted = true))
        assertTrue(repo.getSubTasksFor(id).first { it.title == "下書き" }.isCompleted)

        repo.deleteSubTask(subTasks[1])
        assertEquals(listOf("下書き"), repo.getSubTasksFor(id).map { it.title })
    }

    @Test
    fun `サブタスクの変更がallTasksのprogressに反映される`() = runTest {
        val repo = newRepository()
        val id = repo.insert(sampleTask())

        repo.insertSubTasks(listOf(SubTask(taskId = id, title = "下書き", sortOrder = 0)))

        val afterInsert = repo.allTasks.first().single { it.task.id == id }
        assertEquals(1, afterInsert.subTasks.size)
        assertEquals("下書き", afterInsert.subTasks.single().title)
        assertEquals(0, afterInsert.progress)

        val subTask = afterInsert.subTasks.single()
        repo.updateSubTask(subTask.copy(isCompleted = true))

        val afterUpdate = repo.allTasks.first().single { it.task.id == id }
        assertEquals(100, afterUpdate.progress)
    }

    @Test
    fun `タスク削除でサブタスクもCASCADEで消える`() = runTest {
        val repo = newRepository()
        val id = repo.insert(sampleTask())
        repo.insertSubTasks(listOf(SubTask(taskId = id, title = "下書き")))

        repo.delete(repo.getTaskById(id)!!)

        assertTrue(repo.getSubTasksFor(id).isEmpty())
    }

    @Test
    fun `同名カテゴリの追加はfalseを返し追加されない`() = runTest {
        val repo = newRepository()
        assertTrue(repo.addCategory("仕事"))

        val added = repo.addCategory("仕事")

        assertFalse(added)
        assertEquals(1, repo.categories.first().count { it.name == "仕事" })
    }

    @Test
    fun `renameCategoryは既存の別カテゴリと同名にはできない`() = runTest {
        val repo = newRepository()
        repo.addCategory("仕事")
        repo.addCategory("趣味")
        val target = repo.categories.first().single { it.name == "趣味" }

        val renamed = repo.renameCategory(target, "仕事")

        assertFalse(renamed)
        assertEquals("趣味", repo.categories.first().single { it.id == target.id }.name)
    }

    @Test
    fun `countTasksInCategoryは所属タスク数を返す`() = runTest {
        val repo = newRepository()
        repo.addCategory("仕事")
        val category = repo.categories.first().single()
        repo.insert(sampleTask().copy(categoryId = category.id))
        repo.insert(sampleTask().copy(categoryId = category.id))

        assertEquals(2, repo.countTasksInCategory(category.id))
    }
}
