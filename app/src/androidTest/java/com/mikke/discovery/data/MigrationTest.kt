package com.mikke.discovery.data

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v3 の DB を持つ既存ユーザーが v4 のアプリに更新したときの挙動を検証する。
 *
 * 一番の目的は「Room の起動時スキーマ検証を通ること」。
 * MIGRATION_3_4 が作る tasks テーブルが Room の期待するスキーマと 1 文字でもズレると、
 * 実機では起動時に IllegalStateException でクラッシュする。ここで機械的に検出する。
 *
 * 【この書き方をしている理由】
 * MIGRATION_3_4 は AppDatabase.kt のファイルプライベートなので、テストから直接
 * MigrationTestHelper.runMigrationsAndValidate() に渡せない。
 * そこで v3 の DB だけを MigrationTestHelper（＝スキーマ JSON から正確に v3 を再現）で用意し、
 * 実際のマイグレーションと検証は本番と同じ AppDatabase.getDatabase() 経由で走らせている。
 * 本番に登録されている migration の並び自体も一緒に検証できるので、むしろ実態に近い。
 *
 * そのため DB 名は本番と同じ "task_database" を使う（テスト端末のアプリデータは消える）。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        resetAppDatabaseSingleton()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        resetAppDatabaseSingleton()
        context.deleteDatabase(DB_NAME)
    }

    /**
     * v3 -> v4 でスキーマ検証を通り、既存データが失われないこと。
     *
     * openHelper.writableDatabase を触った時点でマイグレーションとスキーマ検証が走るので、
     * ここで例外が飛ばないことが「実機で起動時にクラッシュしない」ことの担保になる。
     */
    @Test
    fun v3からv4へ移行してもスキーマ検証を通りデータが残る() {
        createV3DatabaseWithData()

        val db = AppDatabase.getDatabase(context)
        // ここでマイグレーション＋スキーマ検証が実行される（失敗すれば例外で落ちる）
        db.openHelper.writableDatabase

        val dao = db.taskDao()
        runBlocking {
            // --- カテゴリが残っていること ---
            val categories = queryList(db, "SELECT `id`, `name` FROM `categories` ORDER BY `id`") {
                it.getInt(0) to it.getString(1)
            }
            assertEquals(
                listOf(1 to "スキル", 2 to "提出", 3 to "やりたい"),
                categories
            )

            // --- タスクが 3 件とも残っていること ---
            val task1 = dao.getTaskById(1)
            assertNotNull("id=1 のタスクが消えている", task1)
            assertEquals("レポート提出", task1!!.title)
            assertEquals(1_700_000_000_000L, task1.deadline)
            assertEquals(3, task1.importance)
            assertEquals(2, task1.urgency)
            assertEquals(1, task1.categoryId)
            assertEquals(true, task1.isCompleted)
            assertEquals(40, task1.progress)
            assertEquals(1_699_000_000_000L, task1.notificationTime)
            assertEquals(1_600_000_000_000L, task1.createdAt)

            val task2 = dao.getTaskById(2)
            assertNotNull("id=2 のタスクが消えている", task2)
            assertEquals("未分類のタスク", task2!!.title)
            // カテゴリ未設定（NULL）もそのまま NULL で運ばれること
            assertNull(task2.categoryId)
            assertNull(task2.notificationTime)

            val task3 = dao.getTaskById(3)
            assertNotNull("id=3 のタスクが消えている", task3)
            assertEquals(2, task3!!.categoryId)

            // --- calendarEventId は仕様どおり全行 NULL ---
            // 旧 ID（端末ローカルの数値）は REST API のイベント ID と互換性が無いので捨てる
            assertNull("連携済みだったタスクの calendarEventId が残っている", task1.calendarEventId)
            assertNull(task2.calendarEventId)
            assertNull(task3.calendarEventId)
            val nonNullCount = queryInt(
                db,
                "SELECT COUNT(*) FROM `tasks` WHERE `calendarEventId` IS NOT NULL"
            )
            assertEquals("calendarEventId が NULL でない行が残っている", 0, nonNullCount)

            // --- サブタスクが残っていること（親タスクとの紐付きも維持） ---
            val subTasksOf1 = dao.getSubTasksFor(1)
            assertEquals(listOf("下書き", "清書"), subTasksOf1.map { it.title })
            assertEquals(listOf(true, false), subTasksOf1.map { it.isCompleted })
            assertEquals(listOf(0, 1), subTasksOf1.map { it.sortOrder })

            assertEquals(listOf("買い物メモ"), dao.getSubTasksFor(3).map { it.title })
            assertEquals(3, queryInt(db, "SELECT COUNT(*) FROM `subtasks`"))
        }
    }

    /** 移行後の tasks.calendarEventId が TEXT になっていること。 */
    @Test
    fun v4のcalendarEventIdはTEXT型になっている() {
        createV3DatabaseWithData()

        val db = AppDatabase.getDatabase(context)
        db.openHelper.writableDatabase

        val columnTypes = queryList(db, "PRAGMA table_info(`tasks`)") {
            it.getString(it.getColumnIndexOrThrow("name")) to
                it.getString(it.getColumnIndexOrThrow("type"))
        }.toMap()
        assertEquals("TEXT", columnTypes["calendarEventId"])
        // 巻き添えで他の列の型が変わっていないことも見ておく
        assertEquals("INTEGER", columnTypes["notificationTime"])
        assertEquals("TEXT", columnTypes["title"])
    }

    /**
     * テーブルを作り直しているので、外部キーとインデックスが失われやすい。
     * 定義が残っているかと、実際に CASCADE / SET NULL が効くかの両方を見る。
     */
    @Test
    fun 移行後も外部キーとインデックスが生きている() {
        createV3DatabaseWithData()

        val db = AppDatabase.getDatabase(context)
        db.openHelper.writableDatabase

        // --- インデックス定義 ---
        val taskIndexes = queryList(db, "PRAGMA index_list(`tasks`)") {
            it.getString(it.getColumnIndexOrThrow("name"))
        }
        assertTrue(
            "index_tasks_categoryId が無い: $taskIndexes",
            taskIndexes.contains("index_tasks_categoryId")
        )
        val subTaskIndexes = queryList(db, "PRAGMA index_list(`subtasks`)") {
            it.getString(it.getColumnIndexOrThrow("name"))
        }
        assertTrue(
            "index_subtasks_taskId が無い: $subTaskIndexes",
            subTaskIndexes.contains("index_subtasks_taskId")
        )

        // --- 外部キー定義 ---
        // subtasks.taskId -> tasks.id は tasks を DROP -> RENAME しても名前で繋がったままのはず
        val subTaskFks = queryList(db, "PRAGMA foreign_key_list(`subtasks`)") {
            Triple(
                it.getString(it.getColumnIndexOrThrow("table")),
                it.getString(it.getColumnIndexOrThrow("from")),
                it.getString(it.getColumnIndexOrThrow("on_delete"))
            )
        }
        assertEquals(listOf(Triple("tasks", "taskId", "CASCADE")), subTaskFks)

        val taskFks = queryList(db, "PRAGMA foreign_key_list(`tasks`)") {
            Triple(
                it.getString(it.getColumnIndexOrThrow("table")),
                it.getString(it.getColumnIndexOrThrow("from")),
                it.getString(it.getColumnIndexOrThrow("on_delete"))
            )
        }
        assertEquals(listOf(Triple("categories", "categoryId", "SET NULL")), taskFks)

        // --- 実際に効くか ---
        // Room は onOpen で PRAGMA foreign_keys = ON にしている
        assertEquals(1, queryInt(db, "PRAGMA foreign_keys"))

        val sqlDb = db.openHelper.writableDatabase
        // タスクを消したらサブタスクも CASCADE で消えること
        sqlDb.execSQL("DELETE FROM `tasks` WHERE `id` = 1")
        assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM `subtasks` WHERE `taskId` = 1"))
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM `subtasks` WHERE `taskId` = 3"))

        // カテゴリを消してもタスクは残り、未分類（NULL）に落ちること
        sqlDb.execSQL("DELETE FROM `categories` WHERE `id` = 2")
        assertEquals(1, queryInt(db, "SELECT COUNT(*) FROM `tasks` WHERE `id` = 3"))
        assertEquals(
            1,
            queryInt(db, "SELECT COUNT(*) FROM `tasks` WHERE `id` = 3 AND `categoryId` IS NULL")
        )
    }

    /**
     * v4 -> v5 でスキーマ検証を通り、既存タスクの status が全て 'TODO' になり、
     * notified_slots テーブルが使える状態になっていること。
     */
    @Test
    fun v4からv5へ移行してもスキーマ検証を通りstatus列がTODOで追加される() {
        createV4DatabaseWithData()

        val db = AppDatabase.getDatabase(context)
        // ここでマイグレーション＋スキーマ検証が実行される（失敗すれば例外で落ちる）
        db.openHelper.writableDatabase

        val dao = db.taskDao()
        runBlocking {
            val task1 = dao.getTaskById(1)
            assertNotNull("id=1 のタスクが消えている", task1)
            assertEquals(TaskStatus.TODO, task1!!.status)
            // 既存の列は巻き戻っていないこと
            assertEquals("レポート提出", task1.title)
            assertEquals(true, task1.isCompleted)

            val task2 = dao.getTaskById(2)
            assertNotNull("id=2 のタスクが消えている", task2)
            assertEquals(TaskStatus.TODO, task2!!.status)

            // notified_slots が使えること（挿入・重複チェック・削除）
            assertEquals(false, dao.isSlotNotified(1_000L))
            dao.insertNotifiedSlot(NotifiedSlot(startMillis = 1_000L, endMillis = 2_000L))
            assertEquals(true, dao.isSlotNotified(1_000L))
            dao.deleteNotifiedSlotsOlderThan(2_000L)
            assertEquals(false, dao.isSlotNotified(1_000L))
        }
    }

    /**
     * v5 -> v6 でスキーマ検証を通り、既存タスクの eventHasTime が全て false（0）になること。
     */
    @Test
    fun v5からv6へ移行してもスキーマ検証を通りeventHasTimeがfalseで追加される() {
        createV5DatabaseWithData()

        val db = AppDatabase.getDatabase(context)
        db.openHelper.writableDatabase

        val dao = db.taskDao()
        runBlocking {
            val task1 = dao.getTaskById(1)
            assertNotNull("id=1 のタスクが消えている", task1)
            assertEquals(false, task1!!.eventHasTime)
            // 既存の列は巻き戻っていないこと
            assertEquals("レポート提出", task1.title)
            assertEquals(TaskStatus.TODO, task1.status)

            val task2 = dao.getTaskById(2)
            assertNotNull("id=2 のタスクが消えている", task2)
            assertEquals(false, task2!!.eventHasTime)
        }
    }

    private fun createV5DatabaseWithData() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            Category.DEFAULTS.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT INTO `categories` (`id`, `name`, `sortOrder`) VALUES (?, ?, ?)",
                    arrayOf<Any>(index + 1, name, index)
                )
            }
            db.execSQL(
                "INSERT INTO `tasks` (" +
                    "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                    "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, " +
                    "`createdAt`, `status`) " +
                    "VALUES (1, 'レポート提出', 1700000000000, 3, 2, 1, 1, 40, NULL, NULL, " +
                    "1600000000000, 'TODO')"
            )
            db.execSQL(
                "INSERT INTO `tasks` (" +
                    "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                    "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, " +
                    "`createdAt`, `status`) " +
                    "VALUES (2, '未分類のタスク', 1700000000000, 1, 1, NULL, 0, 0, NULL, NULL, " +
                    "1600000000000, 'TODO')"
            )
        }
    }

    private fun createV4DatabaseWithData() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            Category.DEFAULTS.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT INTO `categories` (`id`, `name`, `sortOrder`) VALUES (?, ?, ?)",
                    arrayOf<Any>(index + 1, name, index)
                )
            }
            db.execSQL(
                "INSERT INTO `tasks` (" +
                    "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                    "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, `createdAt`) " +
                    "VALUES (1, 'レポート提出', 1700000000000, 3, 2, 1, 1, 40, NULL, NULL, 1600000000000)"
            )
            db.execSQL(
                "INSERT INTO `tasks` (" +
                    "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                    "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, `createdAt`) " +
                    "VALUES (2, '未分類のタスク', 1700000000000, 1, 1, NULL, 0, 0, NULL, NULL, 1600000000000)"
            )
        }
    }

    // ---- ヘルパー ----

    /**
     * v3 のスキーマ（app/schemas/.../3.json）どおりの DB を作り、テストデータを入れる。
     * calendarEventId は「数値が入っている行」と「NULL の行」の両方を用意する。
     */
    private fun createV3DatabaseWithData() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            Category.DEFAULTS.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT INTO `categories` (`id`, `name`, `sortOrder`) VALUES (?, ?, ?)",
                    arrayOf<Any>(index + 1, name, index)
                )
            }

            // calendarEventId あり（旧・端末ローカルの数値 ID）
            insertV3Task(
                db,
                id = 1,
                title = "レポート提出",
                categoryId = 1,
                isCompleted = 1,
                progress = 40,
                notificationTime = 1_699_000_000_000L,
                calendarEventId = 123_456_789L
            )
            // calendarEventId が NULL、かつカテゴリも未設定
            insertV3Task(
                db,
                id = 2,
                title = "未分類のタスク",
                categoryId = null,
                isCompleted = 0,
                progress = 0,
                notificationTime = null,
                calendarEventId = null
            )
            insertV3Task(
                db,
                id = 3,
                title = "買い物",
                categoryId = 2,
                isCompleted = 0,
                progress = 10,
                notificationTime = null,
                calendarEventId = 987_654_321L
            )

            db.execSQL(
                "INSERT INTO `subtasks` (`id`, `taskId`, `title`, `isCompleted`, `sortOrder`) " +
                    "VALUES (1, 1, '下書き', 1, 0), (2, 1, '清書', 0, 1), (3, 3, '買い物メモ', 0, 0)"
            )
        }
    }

    private fun insertV3Task(
        db: SupportSQLiteDatabase,
        id: Int,
        title: String,
        categoryId: Int?,
        isCompleted: Int,
        progress: Int,
        notificationTime: Long?,
        calendarEventId: Long?
    ) {
        db.execSQL(
            "INSERT INTO `tasks` (" +
                "`id`, `title`, `deadline`, `importance`, `urgency`, `categoryId`, " +
                "`isCompleted`, `progress`, `notificationTime`, `calendarEventId`, `createdAt`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                id,
                title,
                1_700_000_000_000L,
                3,
                2,
                categoryId,
                isCompleted,
                progress,
                notificationTime,
                calendarEventId,
                1_600_000_000_000L
            )
        )
    }

    private fun <T> queryList(db: AppDatabase, sql: String, read: (Cursor) -> T): List<T> =
        db.query(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(read(cursor))
            }
        }

    private fun queryInt(db: AppDatabase, sql: String): Int =
        db.query(sql, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    /**
     * AppDatabase はシングルトンを抱えているので、テストごとに作り直せるよう捨てる。
     * 本番コードにテスト専用の口を開けたくないのでリフレクションで触っている。
     */
    private fun resetAppDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        (field.get(null) as? AppDatabase)?.takeIf { it.isOpen }?.close()
        field.set(null, null)
    }

    private companion object {
        /** AppDatabase.getDatabase() 経由で検証するため、本番と同じ DB 名を使う。 */
        const val DB_NAME = "task_database"
    }
}
