package com.example.myapplication.shared.ui

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.shared.Category
import com.example.myapplication.shared.SubTask
import com.example.myapplication.shared.Task
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.db.SharedDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AppState` の内部処理（`repository.insert` 等）は `TaskRepository` 側で
 * `withContext(Dispatchers.Default)` を挟むため、実時間の別スレッドに処理が渡る。
 * `runTest` の仮想時間スケジューラ（`advanceUntilIdle()`）はそのDispatchers.Default側の
 * 完了を認識できないため、「`advanceUntilIdle()` の直後に `Flow.first()` で即値を読む」だけでは
 * 更新前の値を読んでしまいレースになる（実測でも8件中8件が失敗した）。
 * そのため、更新後の値を待つ箇所は `Flow.first { 条件 }` や `Deferred.await()` など
 * 「本物のsuspendで完了を待つ」形にしている（`runTest` はテスト本体のcoroutineが
 * 未完了の別Dispatcher発の処理を待っている間、実時間でそれを待機してくれる）。
 * 何も起こらないことを確認するだけの箇所（早期returnで何もsuspendしない）は
 * 元のまま `advanceUntilIdle()` + 値読み取りでよい。
 *
 * また `AppState.allTasks`/`categories` は `SharingStarted.WhileSubscribed(5000)` で
 * 共有している。`AppState` に渡す `CoroutineScope` として `runTest` 本体の `this` を使うと、
 * `first { }` で購読して抜けたあとに残る「5秒（仮想時間）購読者ゼロが続いたら止める」
 * タイマーjob（および内部でDispatchers.Defaultを挟むSQLDelightのリスナー）が
 * テスト終了時点で完了しきっておらず `UncompletedCoroutinesError` になる
 * （`advanceUntilIdle()` を末尾に足しても解消しなかった）。
 * `kotlinx-coroutines-test` 公式が推奨する対処が `TestScope.backgroundScope` で、
 * ここに起動したcoroutineはテスト終了時に自動キャンセルされ「完了必須」の対象から外れる。
 * `AppState` に渡す `CoroutineScope` を `backgroundScope` にすることで解決する。
 */
class AppStateTest {

    private class InMemoryDriverFactory : DatabaseDriverFactory {
        override fun createDriver(): SqlDriver {
            val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            SharedDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
            return driver
        }
    }

    private fun kotlinx.coroutines.test.TestScope.newAppState(): AppState =
        AppState(TaskRepository(InMemoryDriverFactory()), backgroundScope)

    @Test
    fun `addTaskで前後の空白を取り除いたタイトルが保存される`() = runTest {
        val state = newAppState()

        state.addTask(
            title = "  買い物  ",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        val tasks = state.allTasks.first { it.isNotEmpty() }

        assertEquals("買い物", tasks.single().task.title)
        advanceUntilIdle()
    }

    @Test
    fun `addTaskは空白のみのタイトルなら何もしない`() = runTest {
        val state = newAppState()

        state.addTask(
            title = "   ",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        advanceUntilIdle()

        assertTrue(state.allTasks.value.isEmpty())
    }

    @Test
    fun `addTaskはサブタスクの前後の空白を取り除き空文字は無視する`() = runTest {
        val state = newAppState()

        state.addTask(
            title = "レポート",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null,
            subTaskTitles = listOf("  下書き  ", "", "   ", "清書")
        )
        advanceUntilIdle()

        // タスク本体とサブタスクは別クエリの結果をcombineしているため、
        // タスクだけ先に見えてサブタスクがまだ空の中間状態がありうる。
        // サブタスクが入るまで待つ。
        val tasks = state.allTasks.first { it.singleOrNull()?.subTasks?.isNotEmpty() == true }

        val subTasks = tasks.single().subTasks
        assertEquals(listOf("下書き", "清書"), subTasks.map { it.title })
    }

    @Test
    fun `deleteTaskで削除しundoDeleteで同じ内容が復元される`() = runTest {
        val state = newAppState()
        state.addTask(
            title = "タスクA",
            deadline = 1_700_000_000_000L,
            importance = 3,
            urgency = 1,
            categoryId = null,
            subTaskTitles = listOf("サブ1")
        )
        val task = state.allTasks
            .first { it.singleOrNull()?.subTasks?.isNotEmpty() == true }
            .single().task

        state.deleteTask(task)
        val afterDelete = state.allTasks.first { it.isEmpty() }
        assertTrue(afterDelete.isEmpty())

        state.undoDelete()
        val restored = state.allTasks
            .first { it.singleOrNull()?.subTasks?.isNotEmpty() == true }
            .single()

        assertEquals("タスクA", restored.task.title)
        assertEquals(listOf("サブ1"), restored.subTasks.map { it.title })
        advanceUntilIdle()
    }

    @Test
    fun `undoDeleteは削除直後以外は何もしない`() = runTest {
        val state = newAppState()

        state.undoDelete()
        advanceUntilIdle()

        assertTrue(state.allTasks.value.isEmpty())
    }

    @Test
    fun `renameTaskは空白のみなら変更しない`() = runTest {
        val state = newAppState()
        state.addTask(
            title = "元の名前",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        val task = state.allTasks.first { it.isNotEmpty() }.single().task

        state.renameTask(task, "   ")
        advanceUntilIdle()

        assertEquals("元の名前", state.allTasks.value.single().task.title)
    }

    @Test
    fun `toggleCompletedで完了状態が反転する`() = runTest {
        val state = newAppState()
        state.addTask(
            title = "タスクB",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        val task = state.allTasks.first { it.isNotEmpty() }.single().task

        state.toggleCompleted(task)
        val afterFirstToggle = state.allTasks.first { it.single().task.isCompleted }
        assertTrue(afterFirstToggle.single().task.isCompleted)

        state.toggleCompleted(afterFirstToggle.single().task)
        val afterSecondToggle = state.allTasks.first { !it.single().task.isCompleted }
        assertTrue(!afterSecondToggle.single().task.isCompleted)
        advanceUntilIdle()
    }

    @Test
    fun `addCategoryで重複した名前ならmessagesに失敗を知らせる`() = runTest {
        val state = newAppState()
        state.addCategory("仕事")
        state.categories.first { categories -> categories.any { it.name == "仕事" } }

        // 2回目のaddCategory（重複）が発するmessagesを、発生前に購読しておいて確実に受け取る。
        val messageDeferred = async { state.messages.first() }
        state.addCategory("仕事")
        val message = messageDeferred.await()

        assertTrue(message.contains("すでにあります"))
        advanceUntilIdle()
    }
}
