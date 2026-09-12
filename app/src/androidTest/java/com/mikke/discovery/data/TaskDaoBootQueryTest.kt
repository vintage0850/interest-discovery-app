package com.mikke.discovery.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [TaskDao.getTasksWithFutureNotification] の実クエリを検証する。
 *
 * [com.mikke.discovery.work.BootReceiverTest] は `handleBoot` にフェイクの
 * `getTasks` を注入して分岐だけを検証しており、Room の実クエリ（isCompleted / notificationTime
 * の絞り込み）自体はカバーしていなかった（Codex Gate 4 指摘: boot対象抽出の実クエリ未検証）。
 * ここでは実際の in-memory Room DB に対してクエリを実行し、完了済み・過去時刻・
 * 通知時刻未設定のタスクが再起動復元の対象から正しく除外されることを確認する。
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoBootQueryTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        title: String,
        notificationTime: Long?,
        isCompleted: Boolean = false
    ) = Task(
        title = title,
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        notificationTime = notificationTime,
        isCompleted = isCompleted
    )

    @Test
    fun 未来かつ未完了のタスクだけを再起動復元の対象にする() = runBlocking {
        val now = 1_700_000_000_000L

        val future = task("未来・未完了", notificationTime = now + 3_600_000L)
        val past = task("過去・未完了", notificationTime = now - 3_600_000L)
        val completed = task("未来・完了済み", notificationTime = now + 3_600_000L, isCompleted = true)
        val noNotification = task("通知時刻なし", notificationTime = null)

        listOf(future, past, completed, noNotification).forEach { dao.insertTask(it) }

        val result = dao.getTasksWithFutureNotification(now)

        assertEquals("再起動復元の対象は「未来・未完了」1件だけ", 1, result.size)
        assertEquals("未来・未完了", result.single().title)
    }
}
