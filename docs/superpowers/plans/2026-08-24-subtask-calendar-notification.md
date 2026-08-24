# サブタスク表示改善・カレンダー手動登録・通知時間手動設定 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** カレンダー登録予定へのサブタスク反映、予定日時の手動指定（新規＋事後編集）、タスクごとの手動通知時刻（新規＋事後編集）、通知有効時間帯の設定画面を実装する。

**Architecture:** 既存の `Task` エンティティに `eventHasTime: Boolean` を追加し、既存の未使用カラム `notificationTime` を実際に使う。カレンダー書き込みは `GoogleCalendarSync.toEventRequest()` を拡張して対応し、通知は `AlarmManager` ベースの新規スケジューラで管理する。通知有効時間帯は `SharedPreferences` ラッパーで永続化する。UI は既存の `AddTaskScreen` / `TaskListScreen` を拡張し、新規に `NotificationSettingsScreen` を追加する。

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room, WorkManager, AlarmManager, Retrofit + kotlinx.serialization（Google Calendar REST API v3）, JUnit4（`app/src/test`）, AndroidJUnit4 + MigrationTestHelper（`app/src/androidTest`）

**Spec:** `docs/superpowers/specs/2026-08-24-subtask-calendar-notification-design.md`

## Global Constraints

- UI文言・コメントはすべて日本語（既存コードの慣習に合わせる）
- Red → Green の順でテストを書く。ただし公開シグネチャを広げる変更（例: `insertEvent` に引数追加）は、依存箇所を先に直さないとテストファイル自体がコンパイルできないため、その場合は「シグネチャ変更 → 依存箇所修正 → 新規テスト追加・green確認」の順に進める（各ステップでその旨明記する）
- 各タスクの最後に必ずコミットする（1タスク=1コミット、テストが通った状態でコミットする）
- 新しい `TaskDao` の抽象メンバーを追加したら、`app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt` と `app/src/androidTest/java/com/example/myapplication/work/FreeTimeCheckWorkerTest.kt` の両方にある手書きの `FakeTaskDao` を同時に更新する（この2箇所以外に `TaskDao` の実装は無い）
- `AlarmManager` / `SharedPreferences` など Context に依存するデフォルト引数は、既存の `freeTimeCheckScheduler` パラメータと同じパターン（インターフェース化 or テストでは常に明示的にフェイクを渡す）でユニットテストをクラッシュさせないこと。このプロジェクトは Robolectric を導入していない（`app/build.gradle.kts` のコメント参照）

---

## Task 1: Task エンティティに eventHasTime を追加し、DB を v5→v6 に移行する

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/data/Task.kt`
- Modify: `app/src/main/java/com/example/myapplication/data/AppDatabase.kt`
- Modify: `app/src/androidTest/java/com/example/myapplication/data/MigrationTest.kt`

**Interfaces:**
- Produces: `Task.eventHasTime: Boolean`（デフォルト `false`）。以降の全タスクがこのフィールドを参照する

- [ ] **Step 1: `Task.kt` に `eventHasTime` を追加する**

`app/src/main/java/com/example/myapplication/data/Task.kt` の `notificationTime` の直後に追加:

```kotlin
    val notificationTime: Long? = null,
    /**
     * true なら [deadline] の時刻部分をカレンダー予定の開始時刻として使う（時刻指定予定）。
     * false なら従来通り [deadline] の暦日で終日予定を作る。
     */
    val eventHasTime: Boolean = false,
```

- [ ] **Step 2: `AppDatabase.kt` に MIGRATION_5_6 を追加しバージョンを上げる**

`MIGRATION_4_5` の定義の直後（171行目付近の `@Database` より前）に追加:

```kotlin
/**
 * v5 -> v6: 予定の時刻指定手動設定のための列を追加する。
 *
 * - tasks.eventHasTime（INTEGER, デフォルト 0 = false）: true ならカレンダー予定を
 *   deadline の時刻付きで書き出す。既存タスクはすべて false（従来通りの終日予定）のまま
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `eventHasTime` INTEGER NOT NULL DEFAULT 0")
    }
}
```

`@Database` アノテーションの `version` を `6` に変更し、`getDatabase()` の `.addMigrations(...)` に `MIGRATION_5_6` を追加する:

```kotlin
@Database(
    entities = [Task::class, SubTask::class, Category::class, NotifiedSlot::class],
    version = 6,
    exportSchema = true
)
```

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

- [ ] **Step 3: マイグレーションテストを追加する**

`MigrationTest.kt` に、`v4からv5へ移行しても...` テストの直後（248行目付近）に追加:

```kotlin
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
```

- [ ] **Step 4: テストを実行して確認する**

Run: `./gradlew connectedDebugAndroidTest --tests "com.example.myapplication.data.MigrationTest"`
Expected: PASS（エミュレータ/実機が必要。実機は既に USB 接続済み）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/myapplication/data/Task.kt \
  app/src/main/java/com/example/myapplication/data/AppDatabase.kt \
  app/src/androidTest/java/com/example/myapplication/data/MigrationTest.kt \
  app/schemas
git commit -m "feat: add Task.eventHasTime and migrate DB to v6"
```

（`app/schemas/com.example.myapplication.data.AppDatabase/6.json` はビルド時に KSP が自動生成するので、コミット前に一度 `./gradlew assembleDebug` を実行しておくこと）

---

## Task 2: TaskDao / TaskRepository の拡張（部分更新・通知復元用クエリ）

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/data/TaskDao.kt`
- Modify: `app/src/main/java/com/example/myapplication/data/TaskRepository.kt`
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`（`FakeTaskDao` に新規メソッドを追加、既存テストは変更しない）
- Modify: `app/src/androidTest/java/com/example/myapplication/work/FreeTimeCheckWorkerTest.kt`（`FakeTaskDao` に新規メソッドを追加、既存テストは変更しない）

**Interfaces:**
- Consumes: `Task`（Task 1 で `eventHasTime` 追加済み）
- Produces: `TaskRepository.updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean)`, `TaskRepository.updateNotificationTime(taskId: Int, notificationTime: Long?)`, `TaskRepository.getTasksWithFutureNotification(now: Long): List<Task>` — 以降のタスクがこれらを使う

- [ ] **Step 1: `TaskDao.kt` にクエリを追加する**

`getTopEligibleTaskForNotification()` の直後に追加:

```kotlin
    /**
     * 予定の日時（deadline）と時刻指定の有無だけを更新する。
     * カレンダーへの反映は呼び出し側（TaskViewModel）の責務。
     */
    @Query("UPDATE tasks SET deadline = :deadline, eventHasTime = :eventHasTime WHERE id = :taskId")
    suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean)

    /** 通知時刻だけを更新する。null にすると手動通知を解除する。 */
    @Query("UPDATE tasks SET notificationTime = :notificationTime WHERE id = :taskId")
    suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?)

    /**
     * 端末再起動後、まだ発火していない手動通知の予約を復元するために使う。
     * 未完了かつ、指定時刻がまだ先のタスクだけを返す。
     */
    @Query(
        "SELECT * FROM tasks WHERE isCompleted = 0 AND notificationTime IS NOT NULL " +
            "AND notificationTime > :now"
    )
    suspend fun getTasksWithFutureNotification(now: Long): List<Task>
```

- [ ] **Step 2: `TaskRepository.kt` にラッパーを追加する**

`updateStatus` の直後に追加:

```kotlin
    /** 予定の日時と時刻指定の有無だけを更新する部分更新。 */
    open suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean) =
        taskDao.updateEventTime(taskId, deadline, eventHasTime)

    /** 通知時刻だけを更新する部分更新。null で手動通知を解除する。 */
    open suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?) =
        taskDao.updateNotificationTime(taskId, notificationTime)

    /** 端末再起動後にアラームを再登録する対象（未完了かつ通知時刻が未来）を返す。 */
    open suspend fun getTasksWithFutureNotification(now: Long): List<Task> =
        taskDao.getTasksWithFutureNotification(now)
```

- [ ] **Step 3: 2箇所の手書き `FakeTaskDao` を更新してビルドを通す**

`TaskDao` に抽象メンバーを追加したので、実装している2つのテスト用フェイクが壊れる。それぞれの `FakeTaskDao` クラス内、`updateTaskStatus` の直後に追加:

`app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt` と `app/src/androidTest/java/com/example/myapplication/work/FreeTimeCheckWorkerTest.kt` の両方に、同じ内容を追加する:

```kotlin
        override suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean) = Unit
        override suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?) = Unit
        override suspend fun getTasksWithFutureNotification(now: Long): List<Task> = emptyList()
```

- [ ] **Step 4: 既存テストを実行してコンパイル・グリーンを確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: PASS（既存テストの挙動は変えていないので全件グリーン）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/myapplication/data/TaskDao.kt \
  app/src/main/java/com/example/myapplication/data/TaskRepository.kt \
  app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt \
  app/src/androidTest/java/com/example/myapplication/work/FreeTimeCheckWorkerTest.kt
git commit -m "feat: add TaskDao/TaskRepository partial updates for event/notification time"
```

---

## Task 3: NotificationWindowPreferences（通知有効時間帯の永続化）

**Files:**
- Create: `app/src/main/java/com/example/myapplication/data/NotificationWindowPreferences.kt`
- Test: `app/src/test/java/com/example/myapplication/data/NotificationWindowPreferencesTest.kt`

**Interfaces:**
- Produces: `data class NotificationWindow(val start: LocalTime, val end: LocalTime)`, `val DEFAULT_NOTIFICATION_WINDOW: NotificationWindow`（8:00〜22:00）, `class NotificationWindowPreferences(prefs: SharedPreferences)` with `fun get(): NotificationWindow`, `fun set(window: NotificationWindow)`, companion `fun get(context: Context): NotificationWindowPreferences`

- [ ] **Step 1: 失敗するテストを書く**

`app/src/test/java/com/example/myapplication/data/NotificationWindowPreferencesTest.kt` を新規作成:

```kotlin
package com.example.myapplication.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class NotificationWindowPreferencesTest {

    @Test
    fun `何も保存されていなければデフォルトの8時から22時を返す`() {
        val prefs = NotificationWindowPreferences(FakeSharedPreferences())

        val window = prefs.get()

        assertEquals(LocalTime.of(8, 0), window.start)
        assertEquals(LocalTime.of(22, 0), window.end)
    }

    @Test
    fun `保存した時間帯を読み出せる`() {
        val fake = FakeSharedPreferences()
        val prefs = NotificationWindowPreferences(fake)

        prefs.set(NotificationWindow(LocalTime.of(7, 30), LocalTime.of(21, 15)))
        val window = prefs.get()

        assertEquals(LocalTime.of(7, 30), window.start)
        assertEquals(LocalTime.of(21, 15), window.end)
    }

    /**
     * Android フレームワークを使わない最小限の [SharedPreferences] フェイク。
     * このプロジェクトは Robolectric を導入していないため、Context を必要としない
     * テストにするためにインメモリの Map で代用する。
     */
    class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getInt(key: String, defValue: Int): Int =
            values[key] as? Int ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                pending.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
            }
        }
    }
}
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.data.NotificationWindowPreferencesTest"`
Expected: FAIL（`NotificationWindowPreferences` が存在せずコンパイルエラー）

- [ ] **Step 3: 実装する**

`app/src/main/java/com/example/myapplication/data/NotificationWindowPreferences.kt` を新規作成:

```kotlin
package com.example.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime

/** 「空き時間です」通知を出してよい時間帯。 */
data class NotificationWindow(val start: LocalTime, val end: LocalTime)

/** 従来ハードコードされていた既定値（8:00〜22:00）。 */
val DEFAULT_NOTIFICATION_WINDOW = NotificationWindow(LocalTime.of(8, 0), LocalTime.of(22, 0))

private const val PREFS_NAME = "notification_window"
private const val KEY_START_MINUTES = "start_minutes"
private const val KEY_END_MINUTES = "end_minutes"

/**
 * 通知有効時間帯の永続化。項目が2つだけの単純な設定なので、DataStore は導入せず
 * [SharedPreferences] をそのまま使う。
 */
class NotificationWindowPreferences(private val prefs: SharedPreferences) {

    fun get(): NotificationWindow {
        val startMinutes = prefs.getInt(KEY_START_MINUTES, DEFAULT_NOTIFICATION_WINDOW.start.toMinutesOfDay())
        val endMinutes = prefs.getInt(KEY_END_MINUTES, DEFAULT_NOTIFICATION_WINDOW.end.toMinutesOfDay())
        return NotificationWindow(
            start = LocalTime.ofSecondOfDay(startMinutes * 60L),
            end = LocalTime.ofSecondOfDay(endMinutes * 60L)
        )
    }

    fun set(window: NotificationWindow) {
        prefs.edit()
            .putInt(KEY_START_MINUTES, window.start.toMinutesOfDay())
            .putInt(KEY_END_MINUTES, window.end.toMinutesOfDay())
            .apply()
    }

    private fun LocalTime.toMinutesOfDay(): Int = hour * 60 + minute

    companion object {
        fun get(context: Context): NotificationWindowPreferences =
            NotificationWindowPreferences(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            )
    }
}
```

- [ ] **Step 4: テストを実行して成功を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.data.NotificationWindowPreferencesTest"`
Expected: PASS

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/myapplication/data/NotificationWindowPreferences.kt \
  app/src/test/java/com/example/myapplication/data/NotificationWindowPreferencesTest.kt
git commit -m "feat: add NotificationWindowPreferences for configurable notification window"
```

---

## Task 4: GoogleCalendarSync — 時刻指定予定とサブタスク列挙対応

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarApi.kt`
- Modify: `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarSync.kt`
- Modify: `app/src/test/java/com/example/myapplication/data/calendar/GoogleCalendarSyncTest.kt`
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`（`FakeCalendarSync` のオーバーライドシグネチャ更新のみ。ここではまだ呼び出し側の配線はしない）

**Interfaces:**
- Consumes: `Task.eventHasTime`（Task 1）, `SubTask`（既存）
- Produces: `GoogleCalendarSync.insertEvent(task, categoryName, subTasks: List<SubTask> = emptyList())`, `GoogleCalendarSync.updateEvent(eventId, task, categoryName, subTasks: List<SubTask> = emptyList())` — 以降のタスクがこの拡張シグネチャを使う

この変更は公開シグネチャを広げるため、依存箇所（`GoogleCalendarSyncTest` の `FakeCalendarApi`、`TaskViewModelCalendarTest` の `FakeCalendarSync`）を直さないとプロジェクト全体がコンパイルできない。そのため「シグネチャ変更 → 依存箇所修正 → 新規テスト追加」の順で進める。

- [ ] **Step 1: `GoogleCalendarApi.kt` の `CalendarEventRequest` を時刻対応にする**

`CalendarEventDate` を削除し、`CalendarEventRequest.start/end` を `CalendarEventDateTime` にする:

```kotlin
/**
 * 予定の作成・更新に送る本文。
 *
 * [start] / [end] は終日予定なら `date`、時刻指定予定なら `dateTime` を持つ
 * [CalendarEventDateTime] を使う（読み取り側と同じ型）。
 *
 * @param summary 予定のタイトル
 * @param description 予定の説明
 */
@Serializable
data class CalendarEventRequest(
    val summary: String,
    val description: String,
    val start: CalendarEventDateTime,
    val end: CalendarEventDateTime
)
```

`CalendarEventDate` のクラス定義（`data class CalendarEventDate(val date: String)` とその KDoc）は削除する。

- [ ] **Step 2: `GoogleCalendarSync.kt` の `toEventRequest` を書き換え、シグネチャを広げる**

import に `com.example.myapplication.data.SubTask` と `java.time.temporal.ChronoUnit` を追加。

`insertEvent` / `updateEvent` を以下に置き換える:

```kotlin
    /**
     * 締切当日の終日予定（または [Task.eventHasTime] が true なら時刻付き予定）を作成し、
     * そのイベント ID を返す。[subTasks] があれば説明欄に列挙する。
     */
    open suspend fun insertEvent(
        task: Task,
        categoryName: String,
        subTasks: List<SubTask> = emptyList()
    ): CalendarResult<String> =
        request("予定の作成") { authorization ->
            api.insertEvent(authorization, task.toEventRequest(categoryName, subTasks))
        }.mapSuccess { body ->
            body?.id?.let { CalendarResult.Success(it) }
                ?: CalendarResult.Failure("予定は作成されましたが ID を取得できませんでした")
        }

    /**
     * 既存の予定をタスクの現在の内容へ更新する。[subTasks] があれば説明欄に列挙する。
     * 予定がユーザーに手動で削除されていた場合は [CalendarResult.NotFound]。
     */
    open suspend fun updateEvent(
        eventId: String,
        task: Task,
        categoryName: String,
        subTasks: List<SubTask> = emptyList()
    ): CalendarResult<Unit> =
        request("予定の更新") { authorization ->
            api.patchEvent(authorization, eventId, task.toEventRequest(categoryName, subTasks))
        }.mapSuccess { CalendarResult.Success(Unit) }
```

`toEventRequest` を以下に置き換える:

```kotlin
    /**
     * タイトルと説明。完了済みのタスクは一目で分かるよう印を付ける。
     * サブタスクがあれば説明欄に列挙し、[Task.eventHasTime] に応じて終日/時刻指定を切り替える。
     */
    private fun Task.toEventRequest(
        categoryName: String,
        subTasks: List<SubTask>
    ): CalendarEventRequest {
        val mark = if (isCompleted) "✓ " else ""
        val description = buildString {
            append("重要度: $importance / 緊急度: $urgency\nカテゴリ: $categoryName")
            val sorted = subTasks.sortedBy { it.sortOrder }
            if (sorted.isNotEmpty()) {
                append("\n\nサブタスク:\n")
                append(sorted.joinToString("\n") { "・${it.title}" })
            }
        }
        val zone = ZoneId.systemDefault()
        val (start, end) = if (eventHasTime) {
            val startInstant = Instant.ofEpochMilli(deadline)
            val endInstant = startInstant.plus(1, ChronoUnit.HOURS)
            CalendarEventDateTime(dateTime = startInstant.atZone(zone).format(DATETIME_FORMATTER)) to
                CalendarEventDateTime(dateTime = endInstant.atZone(zone).format(DATETIME_FORMATTER))
        } else {
            val date = localDateOf(deadline)
            // end.date は排他的なので締切日の「翌日」を入れると締切当日だけの終日予定になる
            CalendarEventDateTime(date = date.format(DATE_FORMATTER)) to
                CalendarEventDateTime(date = date.plusDays(1).format(DATE_FORMATTER))
        }
        return CalendarEventRequest(
            summary = "$mark$title",
            description = description,
            start = start,
            end = end
        )
    }
```

`companion object` の `DATE_FORMATTER` の直後に追加:

```kotlin
        /** 時刻指定予定用のRFC3339フォーマット（オフセット付き）。 */
        private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
```

- [ ] **Step 3: `GoogleCalendarSyncTest.kt` の `FakeCalendarApi` にリクエスト本文の記録を追加し、`createTask` を拡張する**

`FakeCalendarApi` クラスの `apiCallCount` の直後にフィールドを追加し、`insertEvent`/`patchEvent` で記録する:

```kotlin
        val capturedEvents = mutableListOf<CalendarEventRequest>()
```

```kotlin
        override suspend fun insertEvent(
            authorization: String,
            event: CalendarEventRequest
        ): Response<CalendarEventResponse> {
            authorizations.add(authorization)
            capturedEvents.add(event)
            return handler(authorization)
        }
```

`createTask()` ヘルパーを引数付きに変更する（既存の呼び出し `createTask()` は無変更で動く）:

```kotlin
    private fun createTask(
        deadline: Long = 1_700_000_000_000L,
        eventHasTime: Boolean = false
    ): Task = Task(
        title = "テストタスク",
        deadline = deadline,
        importance = 2,
        urgency = 2,
        categoryId = null,
        isCompleted = false,
        eventHasTime = eventHasTime
    )
```

- [ ] **Step 4: `TaskViewModelCalendarTest.kt` の `FakeCalendarSync` オーバーライドをシグネチャに合わせる**

`FakeCalendarSync` の `insertEvent`/`updateEvent` を以下に置き換える（挙動は変えない、シグネチャだけ合わせる）:

```kotlin
        override suspend fun insertEvent(
            task: Task,
            categoryName: String,
            subTasks: List<SubTask>
        ): CalendarResult<String> {
            insertCallCount.incrementAndGet()
            delay(50)
            return CalendarResult.Success("event123")
        }

        override suspend fun updateEvent(
            eventId: String,
            task: Task,
            categoryName: String,
            subTasks: List<SubTask>
        ): CalendarResult<Unit> {
            updateCallCount.incrementAndGet()
            lastUpdatedTask = task
            return CalendarResult.Success(Unit)
        }
```

- [ ] **Step 5: 全体をビルドして緑になることを確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.data.calendar.GoogleCalendarSyncTest" --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: PASS（既存テストは全て通る。新規の振る舞いはまだテストしていない）

- [ ] **Step 6: 新しい振る舞いのテストを追加する（サブタスク列挙・時刻指定）**

`GoogleCalendarSyncTest.kt` に追加:

```kotlin
    @Test
    fun `サブタスクがあれば説明欄に列挙される`() = runBlocking {
        val authManager = TestAuthManager().apply { queuedTokens.add("tokenA") }
        val api = FakeCalendarApi { _ -> Response.success(CalendarEventResponse(id = "event1")) }
        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)
        val subTasks = listOf(
            com.example.myapplication.data.SubTask(id = 1, taskId = 1, title = "下書き", sortOrder = 0),
            com.example.myapplication.data.SubTask(id = 2, taskId = 1, title = "清書", sortOrder = 1)
        )

        sync.insertEvent(createTask(), "仕事", subTasks)

        val request = api.capturedEvents.single()
        assertTrue(request.description.endsWith("サブタスク:\n・下書き\n・清書"))
    }

    @Test
    fun `サブタスクが無ければ説明欄にサブタスクの見出しを出さない`() = runBlocking {
        val authManager = TestAuthManager().apply { queuedTokens.add("tokenA") }
        val api = FakeCalendarApi { _ -> Response.success(CalendarEventResponse(id = "event1")) }
        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)

        sync.insertEvent(createTask(), "仕事")

        val request = api.capturedEvents.single()
        assertFalse(request.description.contains("サブタスク"))
    }

    @Test
    fun `eventHasTimeがfalseなら終日予定のdateを使う`() = runBlocking {
        val authManager = TestAuthManager().apply { queuedTokens.add("tokenA") }
        val api = FakeCalendarApi { _ -> Response.success(CalendarEventResponse(id = "event1")) }
        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)

        sync.insertEvent(createTask(deadline = 1_700_000_000_000L, eventHasTime = false), "仕事")

        val request = api.capturedEvents.single()
        assertEquals(null, request.start.dateTime)
        assertEquals(null, request.end.dateTime)
        assertTrue(request.start.date != null)
    }

    @Test
    fun `eventHasTimeがtrueなら時刻付きのdateTimeを使い1時間後を終了時刻にする`() = runBlocking {
        val authManager = TestAuthManager().apply { queuedTokens.add("tokenA") }
        val api = FakeCalendarApi { _ -> Response.success(CalendarEventResponse(id = "event1")) }
        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)

        sync.insertEvent(createTask(deadline = 1_700_000_000_000L, eventHasTime = true), "仕事")

        val request = api.capturedEvents.single()
        assertEquals(null, request.start.date)
        val start = java.time.OffsetDateTime.parse(request.start.dateTime)
        val end = java.time.OffsetDateTime.parse(request.end.dateTime)
        assertEquals(java.time.Instant.ofEpochMilli(1_700_000_000_000L), start.toInstant())
        assertEquals(start.toInstant().plusSeconds(3600), end.toInstant())
    }
```

- [ ] **Step 7: テストを実行して成功を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.data.calendar.GoogleCalendarSyncTest"`
Expected: PASS（実装は Step 2 で既に入っているので即グリーン）

- [ ] **Step 8: コミット**

```bash
git add app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarApi.kt \
  app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarSync.kt \
  app/src/test/java/com/example/myapplication/data/calendar/GoogleCalendarSyncTest.kt \
  app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt
git commit -m "feat: support timed calendar events and subtask listing in GoogleCalendarSync"
```

---

## Task 5: TaskViewModel — カレンダー呼び出しへのサブタスク配線

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`

**Interfaces:**
- Consumes: `GoogleCalendarSync.insertEvent/updateEvent(..., subTasks)`（Task 4）
- Produces: `addTask` / `setCalendarLinked` / `syncToCalendar` / `undoDelete` が常にサブタスクをカレンダー呼び出しに渡す（挙動変更、公開シグネチャは変えない）

- [ ] **Step 1: 失敗するテストを書く**

`TaskViewModelCalendarTest.kt` の `FakeCalendarSync` に、渡された `subTasks` を記録するフィールドを追加する:

```kotlin
        var lastInsertedSubTasks: List<SubTask>? = null
```

`insertEvent` の中で記録するよう変更:

```kotlin
        override suspend fun insertEvent(
            task: Task,
            categoryName: String,
            subTasks: List<SubTask>
        ): CalendarResult<String> {
            insertCallCount.incrementAndGet()
            lastInsertedSubTasks = subTasks
            delay(50)
            return CalendarResult.Success("event123")
        }
```

`FakeRepository` にサブタスクを差し込めるようにする（`storedTasks` の直後）:

```kotlin
        val subTasksByTaskId = mutableMapOf<Int, List<SubTask>>()

        override suspend fun getSubTasksFor(taskId: Int): List<SubTask> =
            subTasksByTaskId[taskId] ?: emptyList()
```

テストを追加:

```kotlin
    @Test
    fun `新規タスク追加でカレンダー登録するとサブタスクも渡す`() = runTest {
        val repo = FakeRepository()
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.addTask(
            title = "新規タスク",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null,
            subTaskTitles = listOf("下書き", "清書"),
            addToCalendar = true
        )
        advanceUntilIdle()

        assertEquals(listOf("下書き", "清書"), calendar.lastInsertedSubTasks?.map { it.title })
    }

    @Test
    fun `既存タスクのカレンダー連携時に保存済みサブタスクを渡す`() = runTest {
        val task = createTask(id = 1, calendarEventId = null)
        val repo = FakeRepository().apply {
            save(task)
            subTasksByTaskId[1] = listOf(SubTask(id = 1, taskId = 1, title = "下書き", sortOrder = 0))
        }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repository = repo, calendarSync = calendar)

        viewModel.setCalendarLinked(task, true)
        advanceUntilIdle()

        assertEquals(listOf("下書き"), calendar.lastInsertedSubTasks?.map { it.title })
    }
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: FAIL（`lastInsertedSubTasks` が常に `emptyList()` のまま。アサーションで失敗）

- [ ] **Step 3: `TaskViewModel.kt` の呼び出し箇所を修正する**

`addTask` 内、`insertEvent` の呼び出しを次のように変更（`subTasks` 変数はこの呼び出しより前に定義済みなのでそのまま渡す）:

```kotlin
            if (addToCalendar) {
                val saved = task.copy(id = id)
                when (val result = calendarSync.insertEvent(saved, categoryNameOf(saved), subTasks)) {
```

`setCalendarLinked` 内、`if (enabled)` ブロックを次のように変更:

```kotlin
                if (enabled) {
                    if (current.calendarEventId != null) return@withLock
                    val subTasks = repository.getSubTasksFor(current.id)
                    when (val result = calendarSync.insertEvent(current, categoryNameOf(current), subTasks)) {
```

`syncToCalendar` を次のように変更:

```kotlin
    private suspend fun syncToCalendar(task: Task) {
        val eventId = task.calendarEventId ?: return
        val name = categoryNameOf(task)
        val subTasks = repository.getSubTasksFor(task.id)
        when (val result = calendarSync.updateEvent(eventId, task, name, subTasks)) {
            is CalendarResult.Success -> Unit
            is CalendarResult.NotFound -> {
                when (val recreated = calendarSync.insertEvent(task, name, subTasks)) {
```

`undoDelete` 内、最後の `insertEvent` 呼び出しを次のように変更（`deleted.subTasks` は既に取得済み）:

```kotlin
            when (val result = calendarSync.insertEvent(task, categoryNameOf(task), deleted.subTasks)) {
```

- [ ] **Step 4: テストを実行して成功を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: PASS（全件）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/myapplication/TaskViewModel.kt \
  app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt
git commit -m "feat: pass subtasks through to calendar sync calls"
```

---

## Task 6: 手動通知のアラーム基盤（TaskNotificationScheduler / Receiver / BootReceiver）

**Files:**
- Create: `app/src/main/java/com/example/myapplication/work/TaskNotificationScheduler.kt`
- Create: `app/src/main/java/com/example/myapplication/work/TaskNotificationReceiver.kt`
- Create: `app/src/main/java/com/example/myapplication/work/BootReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `TaskRepository.getTasksWithFutureNotification`（Task 2）, `FreeTimeNotifier` / `AndroidFreeTimeNotifier`（既存）
- Produces: `interface TaskNotificationScheduler { fun schedule(task: Task); fun cancel(taskId: Int) }`, `class AndroidTaskNotificationScheduler(context: Context) : TaskNotificationScheduler` — Task 7 がこれを `TaskViewModel` に注入する

このタスクは Android フレームワーク（`AlarmManager`, `BroadcastReceiver`）に直結する薄いグルーコードで構成される。既存の `AndroidFreeTimeNotifier` や `TaskStartActionReceiver` にも自動テストが無い（Robolectric 未導入のため）のと同じ理由で、ここも自動テストは書かず、Task 7 のユニットテストでは `TaskNotificationScheduler` インターフェースをフェイクに差し替える。実際の発火・再起動後の再登録は実機での動作確認が必要（設計書のテスト方針を参照）。

- [ ] **Step 1: `TaskNotificationScheduler.kt` を作成する**

```kotlin
package com.example.myapplication.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.myapplication.data.Task

/**
 * タスクごとの手動通知時刻の予約を抽象化する。
 * 単体テストでは AlarmManager が動かないため、本番用とテスト用を差し替えられるようにする。
 */
interface TaskNotificationScheduler {
    /** [Task.notificationTime] が null なら何もしない。 */
    fun schedule(task: Task)

    fun cancel(taskId: Int)
}

/**
 * 本番用。[AlarmManager] で指定時刻に1回だけ [TaskNotificationReceiver] を起こす。
 *
 * 「正確なアラーム」権限（Android 12+ の SCHEDULE_EXACT_ALARM）が無い端末では
 * [AlarmManager.setAndAllowWhileIdle]（数分の誤差を許容）にフォールバックする。
 * 権限が無いことでクラッシュしたり、無反応になったりはしない。
 */
class AndroidTaskNotificationScheduler(context: Context) : TaskNotificationScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(task: Task) {
        val triggerAtMillis = task.notificationTime ?: return
        val pendingIntent = pendingIntentFor(task.id)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(taskId: Int) {
        alarmManager.cancel(pendingIntentFor(taskId))
    }

    private fun pendingIntentFor(taskId: Int): PendingIntent {
        val intent = Intent(appContext, TaskNotificationReceiver::class.java)
            .putExtra(TaskNotificationReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            appContext,
            taskId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
```

- [ ] **Step 2: `TaskNotificationReceiver.kt` を作成する**

```kotlin
package com.example.myapplication.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 手動通知時刻の AlarmManager から呼ばれる。指定時刻に「始めさせる」通知を1回出す。
 *
 * マニフェストで exported="false" にすること（他アプリから任意のタスクを操作されないため）。
 */
class TaskNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TaskRepository(AppDatabase.getDatabase(appContext).taskDao())
                val task = repository.getTaskById(taskId)
                // 発火までの間に完了・削除された可能性があるので、その場合は何もしない
                if (task != null && !task.isCompleted) {
                    AndroidFreeTimeNotifier(appContext).notifyTaskStart(task)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}
```

- [ ] **Step 3: `BootReceiver.kt` を作成する**

```kotlin
package com.example.myapplication.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myapplication.data.AppDatabase
import com.example.myapplication.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 端末再起動で消える AlarmManager の予約を復元する。
 * マニフェストの intent-filter を BOOT_COMPLETED だけに絞ってあること。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = TaskRepository(AppDatabase.getDatabase(appContext).taskDao())
                val scheduler = AndroidTaskNotificationScheduler(appContext)
                repository.getTasksWithFutureNotification(System.currentTimeMillis())
                    .forEach { task -> scheduler.schedule(task) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 4: `AndroidManifest.xml` に権限・レシーバーを追加する**

`<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` の直後に追加:

```xml
    <!-- 手動通知時刻に確実に発火させるため（Android 12+ はユーザーが設定でON/OFFする特別な権限） -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <!-- 端末再起動後にアラーム予約を復元するため -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

`<receiver android:name=".TaskStartActionReceiver" ... />` の直後に追加:

```xml
        <!--
             手動通知時刻の AlarmManager からのみ呼ばれる内部用レシーバー。
             他アプリから任意のタスクを操作されないよう exported="false" にする。
        -->
        <receiver
            android:name=".work.TaskNotificationReceiver"
            android:exported="false" />

        <!-- 端末再起動後にアラーム予約を復元する -->
        <receiver
            android:name=".work.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 5: ビルドが通ることを確認する**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: コミット**

```bash
git add app/src/main/java/com/example/myapplication/work/TaskNotificationScheduler.kt \
  app/src/main/java/com/example/myapplication/work/TaskNotificationReceiver.kt \
  app/src/main/java/com/example/myapplication/work/BootReceiver.kt \
  app/src/main/AndroidManifest.xml
git commit -m "feat: add AlarmManager-based task notification scheduler and boot rescheduling"
```

---

## Task 7: TaskViewModel — 手動通知の配線と事後編集関数

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`

**Interfaces:**
- Consumes: `TaskNotificationScheduler`（Task 6）, `TaskRepository.updateEventTime/updateNotificationTime`（Task 2）
- Produces: `TaskViewModel.addTask(..., eventHasTime: Boolean = false, notificationTime: Long? = null)`, `TaskViewModel.updateEventTime(task, eventHasTime, newDeadline)`, `TaskViewModel.updateNotificationTime(task, newNotificationTime)` — Task 9/10 の UI から呼ぶ

- [ ] **Step 1: 失敗するテストを書く**

`TaskViewModelCalendarTest.kt` に `TaskNotificationScheduler` のフェイクを追加し、`createViewModel` ヘルパーに注入できるようにする:

```kotlin
    private class FakeTaskNotificationScheduler : TaskNotificationScheduler {
        val scheduled = mutableListOf<Task>()
        val cancelled = mutableListOf<Int>()
        override fun schedule(task: Task) { scheduled.add(task) }
        override fun cancel(taskId: Int) { cancelled.add(taskId) }
    }
```

`createViewModel` を次のように変更する（既存の呼び出し元は無変更で動く）:

```kotlin
    private fun createViewModel(
        repository: FakeRepository,
        calendarSync: FakeCalendarSync,
        notificationScheduler: TaskNotificationScheduler = FakeTaskNotificationScheduler()
    ): TaskViewModel {
        return TaskViewModel(
            application = FakeApplication(),
            repository = repository,
            authManager = FakeAuthManager(),
            calendarSync = calendarSync,
            freeTimeCheckScheduler = FreeTimeCheckScheduler {},
            notificationScheduler = notificationScheduler
        )
    }
```

`FakeRepository` に部分更新の記録を追加する（`titleUpdates` の直後）:

```kotlin
        val eventTimeUpdates = mutableListOf<Triple<Int, Long, Boolean>>()
        val notificationTimeUpdates = mutableListOf<Pair<Int, Long?>>()

        override suspend fun updateEventTime(taskId: Int, deadline: Long, eventHasTime: Boolean) {
            eventTimeUpdates.add(Triple(taskId, deadline, eventHasTime))
            tasks[taskId]?.let { tasks[taskId] = it.copy(deadline = deadline, eventHasTime = eventHasTime) }
        }

        override suspend fun updateNotificationTime(taskId: Int, notificationTime: Long?) {
            notificationTimeUpdates.add(taskId to notificationTime)
            tasks[taskId]?.let { tasks[taskId] = it.copy(notificationTime = notificationTime) }
        }
```

import に `TaskNotificationScheduler`（`com.example.myapplication.work.TaskNotificationScheduler`）を追加。

テストを追加:

```kotlin
    @Test
    fun `notificationTime指定でタスク追加すると通知が予約される`() = runTest {
        val repo = FakeRepository()
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.addTask(
            title = "新規タスク",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null,
            notificationTime = 1_700_000_500_000L
        )
        advanceUntilIdle()

        assertEquals(1, scheduler.scheduled.size)
        assertEquals(1_700_000_500_000L, scheduler.scheduled.single().notificationTime)
    }

    @Test
    fun `notificationTime未指定でタスク追加しても通知は予約しない`() = runTest {
        val repo = FakeRepository()
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.addTask(
            title = "新規タスク",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        advanceUntilIdle()

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `タスク削除で通知予約を解除する`() = runTest {
        val task = createTask(id = 1)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.deleteTask(task)
        advanceUntilIdle()

        assertEquals(listOf(1), scheduler.cancelled)
    }

    @Test
    fun `undoDeleteで通知時刻があれば再予約する`() = runTest {
        val task = createTask(id = 1).copy(notificationTime = 1_700_000_500_000L)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.deleteTask(task)
        advanceUntilIdle()
        scheduler.scheduled.clear()

        viewModel.undoDelete()
        advanceUntilIdle()

        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun `updateNotificationTimeは既存予約を解除してから新しい時刻で予約し直す`() = runTest {
        val task = createTask(id = 1)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.updateNotificationTime(task, 1_700_000_900_000L)
        advanceUntilIdle()

        assertEquals(listOf(1), scheduler.cancelled)
        assertEquals(1_700_000_900_000L, scheduler.scheduled.single().notificationTime)
        assertEquals(1_700_000_900_000L, repo.getTaskById(1)?.notificationTime)
    }

    @Test
    fun `updateNotificationTimeにnullを渡すと予約解除のみ行う`() = runTest {
        val task = createTask(id = 1)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.updateNotificationTime(task, null)
        advanceUntilIdle()

        assertEquals(listOf(1), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
        assertNull(repo.getTaskById(1)?.notificationTime)
    }

    @Test
    fun `updateEventTimeは連携済みタスクのカレンダー予定も更新する`() = runTest {
        val task = createTask(id = 1, calendarEventId = "event_old")
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repo, calendar)

        viewModel.updateEventTime(task, eventHasTime = true, newDeadline = 1_700_050_000_000L)
        advanceUntilIdle()

        assertEquals(1, calendar.updateCallCount.get())
        assertEquals(1_700_050_000_000L, repo.getTaskById(1)?.deadline)
        assertEquals(true, repo.getTaskById(1)?.eventHasTime)
    }

    @Test
    fun `updateEventTimeは未連携タスクならカレンダーAPIを呼ばない`() = runTest {
        val task = createTask(id = 1, calendarEventId = null)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val viewModel = createViewModel(repo, calendar)

        viewModel.updateEventTime(task, eventHasTime = false, newDeadline = 1_700_050_000_000L)
        advanceUntilIdle()

        assertEquals(0, calendar.updateCallCount.get())
    }
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: FAIL（`TaskViewModel` に `notificationScheduler` パラメータや `updateEventTime`/`updateNotificationTime` が無くコンパイルエラー）

- [ ] **Step 3: `TaskViewModel.kt` を実装する**

import に以下を追加:

```kotlin
import com.example.myapplication.work.AndroidTaskNotificationScheduler
import com.example.myapplication.work.TaskNotificationScheduler
```

コンストラクタに `notificationScheduler` パラメータを追加する:

```kotlin
class TaskViewModel(
    application: Application,
    private val repository: TaskRepository = TaskRepository(
        AppDatabase.getDatabase(application).taskDao()
    ),
    private val authManager: GoogleAuthManager = GoogleAuthManager.get(application),
    private val calendarSync: GoogleCalendarSync = GoogleCalendarSync(authManager),
    private val freeTimeCheckScheduler: FreeTimeCheckScheduler =
        WorkManagerFreeTimeCheckScheduler(application),
    private val notificationScheduler: TaskNotificationScheduler =
        AndroidTaskNotificationScheduler(application)
) : AndroidViewModel(application) {
```

`addTask` のシグネチャと本体を変更する:

```kotlin
    fun addTask(
        title: String,
        deadline: Long,
        importance: Int,
        urgency: Int,
        categoryId: Int?,
        subTaskTitles: List<String> = emptyList(),
        addToCalendar: Boolean = false,
        eventHasTime: Boolean = false,
        notificationTime: Long? = null
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val task = Task(
                title = trimmed,
                deadline = deadline,
                importance = importance,
                urgency = urgency,
                categoryId = categoryId,
                eventHasTime = eventHasTime,
                notificationTime = notificationTime
            )
            val id = repository.insert(task)
            val saved = task.copy(id = id)

            val subTasks = subTaskTitles
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, subTitle ->
                    SubTask(taskId = id, title = subTitle, sortOrder = index)
                }
            if (subTasks.isNotEmpty()) repository.insertSubTasks(subTasks)

            if (addToCalendar) {
                when (val result = calendarSync.insertEvent(saved, categoryNameOf(saved), subTasks)) {
                    is CalendarResult.Success ->
                        repository.updateCalendarEventId(saved.id, result.value)
                    else -> notifyCalendarFailure(result)
                }
            }

            if (notificationTime != null) {
                notificationScheduler.schedule(saved)
            }
        }
    }
```

`deleteTask` に予約解除を追加する:

```kotlin
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val subTasks = repository.getSubTasksFor(task.id)
            repository.delete(task)
            notificationScheduler.cancel(task.id)

            val eventDeleted = task.calendarEventId?.let { eventId ->
                val result = calendarSync.deleteEvent(eventId)
                notifyCalendarFailure(result)
                result is CalendarResult.Success
            } ?: false

            lastDeleted = DeletedTask(task, subTasks, eventDeleted)
        }
    }
```

`undoDelete` の先頭付近（サブタスク再挿入の直後）に予約の再登録を追加する:

```kotlin
    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            val task = deleted.task
            repository.insert(task)
            if (deleted.subTasks.isNotEmpty()) repository.insertSubTasks(deleted.subTasks)
            if (task.notificationTime != null) notificationScheduler.schedule(task)

            if (task.calendarEventId == null || !deleted.calendarEventDeleted) return@launch

            when (val result = calendarSync.insertEvent(task, categoryNameOf(task), deleted.subTasks)) {
                is CalendarResult.Success ->
                    repository.updateCalendarEventId(task.id, result.value)
                else -> {
                    repository.updateCalendarEventId(task.id, null)
                    notifyCalendarFailure(result)
                }
            }
        }
    }
```

ファイル末尾（`syncToCalendar` の直後、クラスの閉じ括弧の前）に新しい関数を追加する:

```kotlin
    /**
     * 予定の時刻指定を変更する。連携済み（calendarEventId != null）ならカレンダー側の
     * 予定も合わせて更新する（[syncToCalendar] を再利用）。
     */
    fun updateEventTime(task: Task, eventHasTime: Boolean, newDeadline: Long) {
        viewModelScope.launch {
            repository.updateEventTime(task.id, newDeadline, eventHasTime)
            syncToCalendar(task.copy(deadline = newDeadline, eventHasTime = eventHasTime))
        }
    }

    /**
     * 手動通知時刻を変更する。既存の予約を解除してから、新しい時刻があれば予約し直す。
     * null を渡すと手動通知を解除するだけになる。
     */
    fun updateNotificationTime(task: Task, newNotificationTime: Long?) {
        viewModelScope.launch {
            repository.updateNotificationTime(task.id, newNotificationTime)
            notificationScheduler.cancel(task.id)
            if (newNotificationTime != null) {
                notificationScheduler.schedule(task.copy(notificationTime = newNotificationTime))
            }
        }
    }
```

- [ ] **Step 4: テストを実行して成功を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: PASS（全件）

- [ ] **Step 5: コミット**

```bash
git add app/src/main/java/com/example/myapplication/TaskViewModel.kt \
  app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt
git commit -m "feat: wire manual notification scheduling and add event/notification time editing"
```

---

## Task 8: FreeTimeCheckWorker — 通知有効時間帯を設定から読む

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/work/FreeTimeCheckWorker.kt`

**Interfaces:**
- Consumes: `NotificationWindowPreferences`（Task 3）
- Produces: 変更なし（内部実装のみ変更、`WORK_NAME` は変わらない）

既存の `FreeTimeCheckWorkerTest.kt` はデフォルト値（`NotificationWindowPreferences.get(context)` が返す `DEFAULT_NOTIFICATION_WINDOW` = 8:00〜22:00）で動作するため、変更不要でそのままグリーンになる想定。

- [ ] **Step 1: `FreeTimeCheckWorker.kt` を変更する**

import を変更する。`java.time.LocalTime` を削除し、以下を追加:

```kotlin
import com.example.myapplication.data.NotificationWindowPreferences
```

クラス定義とコンストラクタを変更する:

```kotlin
class FreeTimeCheckWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters,
    private val repository: TaskRepository = TaskRepository(
        AppDatabase.getDatabase(context).taskDao()
    ),
    authManager: GoogleAuthManager = GoogleAuthManager.get(context),
    private val calendarSync: GoogleCalendarSync = GoogleCalendarSync(authManager),
    private val authState: () -> CalendarAuthState = { authManager.authState.value },
    private val clock: Clock = Clock.systemDefaultZone(),
    private val notifier: FreeTimeNotifier = AndroidFreeTimeNotifier(context),
    private val notificationWindow: () -> com.example.myapplication.data.NotificationWindow = {
        NotificationWindowPreferences.get(context).get()
    }
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (authState() !is CalendarAuthState.Authorized) return Result.success()

        val window = notificationWindow()
        val zone = clock.zone
        val now = Instant.now(clock)
        val localTime = now.atZone(zone).toLocalTime()
        if (localTime.isBefore(window.start) || !localTime.isBefore(window.end)) {
            return Result.success()
        }

        val events = when (val result = calendarSync.listTodayEvents(now, zone)) {
            is CalendarResult.Success -> result.value
            else -> return Result.success()
        }

        val windowEnd = now.atZone(zone).toLocalDate().atTime(window.end).atZone(zone).toInstant()
        val freeSlot = findNextFreeSlot(events, now, windowEnd) ?: return Result.success()

        val slotStartMillis = freeSlot.start.toEpochMilli()
        if (repository.isSlotNotified(slotStartMillis)) return Result.success()

        val task = repository.getTopEligibleTaskForNotification() ?: return Result.success()

        notifier.notifyTaskStart(task)

        repository.insertNotifiedSlot(
            NotifiedSlot(startMillis = slotStartMillis, endMillis = freeSlot.end.toEpochMilli())
        )
        repository.deleteNotifiedSlotsOlderThan(now.minus(24, ChronoUnit.HOURS).toEpochMilli())

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "free_time_check"
    }
}
```

（`WINDOW_START` / `WINDOW_END` の `private val` 定義は削除する）

- [ ] **Step 2: 既存テストを実行して変更なくグリーンなことを確認する**

Run: `./gradlew connectedDebugAndroidTest --tests "com.example.myapplication.work.FreeTimeCheckWorkerTest"`
Expected: PASS（デフォルトの通知有効時間帯が従来と同じ8:00〜22:00のため、既存のテストケースは無変更で通る）

- [ ] **Step 3: コミット**

```bash
git add app/src/main/java/com/example/myapplication/work/FreeTimeCheckWorker.kt
git commit -m "feat: read notification window from NotificationWindowPreferences"
```

---

## Task 9: 共通UI部品 OptionalTimePicker

**Files:**
- Create: `app/src/main/java/com/example/myapplication/OptionalTimePicker.kt`

**Interfaces:**
- Produces: `@Composable fun OptionalTimePicker(label, description, enabled, onEnabledChange, hour, minute, onTimeChange)` — Task 10・11 がこれを使う

Compose UI 部品は Compose UI テストの対象だが、このプロジェクトには Compose UI の自動テストが無く（`androidx.compose.ui.test.junit4` は依存関係にあるが既存のテストファイルは無い）、実機での目視確認を採用する。ここでは実装のみ行い、Task 12 のビルド確認と合わせて実機で見た目を確認する。

- [ ] **Step 1: `OptionalTimePicker.kt` を作成する**

```kotlin
package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 「時刻を指定する」トグルと、ON のときだけ出る時刻表示ボタン＋ピッカーをまとめた部品。
 * AddTaskScreen（新規登録）と TaskEditDialog（事後編集）の両方から使う。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionalTimePicker(
    label: String,
    description: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
    if (enabled) {
        TextButton(onClick = { showDialog = true }) {
            Text("時刻: %02d:%02d".format(hour, minute))
        }
    }

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        SimpleTimePickerDialog(
            onDismissRequest = { showDialog = false },
            onConfirm = {
                onTimeChange(timePickerState.hour, timePickerState.minute)
                showDialog = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

/**
 * Material3 には `DatePickerDialog` はあるが `TimePickerDialog` は無いため、
 * `AlertDialog` をベースに最小限のダイアログ枠を用意する。
 */
@Composable
internal fun SimpleTimePickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("キャンセル") }
        },
        text = { content() }
    )
}
```

- [ ] **Step 2: ビルドが通ることを確認する**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add app/src/main/java/com/example/myapplication/OptionalTimePicker.kt
git commit -m "feat: add reusable OptionalTimePicker composable"
```

---

## Task 10: AddTaskScreen — 予定時刻・通知時刻の入力

**Files:**
- Modify: `app/src/main/java/com/example/myapplication/AddTaskScreen.kt`
- Modify: `app/src/main/java/com/example/myapplication/MainActivity.kt`
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`（`addTask` の呼び出し確認テストは既存のまま。新規テストは追加しない。Task 7 で追加済みのテストが該当するため）

**Interfaces:**
- Consumes: `OptionalTimePicker`（Task 9）, `TaskViewModel.addTask(..., eventHasTime, notificationTime)`（Task 7）
- Produces: `NewTaskInput` に `eventHasTime: Boolean` と `notificationTime: Long?` を追加

- [ ] **Step 1: `NewTaskInput` を拡張する**

```kotlin
data class NewTaskInput(
    val title: String,
    val deadline: Long,
    val importance: Int,
    val urgency: Int,
    val categoryId: Int?,
    val subTaskTitles: List<String>,
    val addToCalendar: Boolean,
    val eventHasTime: Boolean,
    val notificationTime: Long?
)
```

- [ ] **Step 2: 日付＋時刻を合成するヘルパーを追加する**

`toLocalEndOfDay` の直後に追加:

```kotlin
/**
 * DatePicker が返す「UTC のその日の 0 時」から、指定した時（ローカル）を合成する。
 * 通知時刻・予定時刻の両方で、選んだ締切日はそのまま使う。
 */
private fun toLocalDateTime(utcMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), hour, minute, 0)
    }.timeInMillis
}
```

- [ ] **Step 3: 状態を追加し `save()` を変更する**

`addToCalendar` の宣言の直後に追加:

```kotlin
    var eventHasTime by rememberSaveable { mutableStateOf(false) }
    var eventHour by rememberSaveable { mutableIntStateOf(9) }
    var eventMinute by rememberSaveable { mutableIntStateOf(0) }
    var notificationEnabled by rememberSaveable { mutableStateOf(false) }
    var notificationHour by rememberSaveable { mutableIntStateOf(9) }
    var notificationMinute by rememberSaveable { mutableIntStateOf(0) }
```

`save()` 関数を次のように変更する:

```kotlin
    fun save() {
        val date = datePickerState.selectedDateMillis ?: return
        if (title.isBlank()) {
            titleTouched = true
            return
        }
        keyboardController?.hide()
        val deadline = if (eventHasTime) {
            toLocalDateTime(date, eventHour, eventMinute)
        } else {
            toLocalEndOfDay(date)
        }
        val notificationTime = if (notificationEnabled) {
            toLocalDateTime(date, notificationHour, notificationMinute)
        } else {
            null
        }
        onTaskAdded(
            NewTaskInput(
                title = title.trim(),
                deadline = deadline,
                importance = importance.toInt(),
                urgency = urgency.toInt(),
                categoryId = selectedCategoryId.takeIf { it != NO_CATEGORY },
                subTaskTitles = subTaskTitles.toList(),
                addToCalendar = addToCalendar,
                eventHasTime = eventHasTime,
                notificationTime = notificationTime
            )
        )
    }
```

- [ ] **Step 4: UI を追加する**

Google カレンダーの `Switch` を含む `Row`（284〜320行目付近）の直後、`Spacer(modifier = Modifier.height(8.dp))` の前に追加:

```kotlin
            HorizontalDivider()

            OptionalTimePicker(
                label = "予定の時刻を指定する",
                description = "指定しない場合は締切日の終日予定になります",
                enabled = eventHasTime,
                onEnabledChange = { eventHasTime = it },
                hour = eventHour,
                minute = eventMinute,
                onTimeChange = { h, m -> eventHour = h; eventMinute = m }
            )

            HorizontalDivider()

            OptionalTimePicker(
                label = "通知時刻を指定する",
                description = "指定した時刻に必ず通知します（締切日と同じ日）",
                enabled = notificationEnabled,
                onEnabledChange = { notificationEnabled = it },
                hour = notificationHour,
                minute = notificationMinute,
                onTimeChange = { h, m -> notificationHour = h; notificationMinute = m }
            )
```

- [ ] **Step 5: `MainActivity.kt` の呼び出しを更新する**

`onTaskAdded` のコールバック内、`viewModel.addTask(...)` の呼び出しに引数を追加する:

```kotlin
                            onTaskAdded = { input: NewTaskInput ->
                                viewModel.addTask(
                                    title = input.title,
                                    deadline = input.deadline,
                                    importance = input.importance,
                                    urgency = input.urgency,
                                    categoryId = input.categoryId,
                                    subTaskTitles = input.subTaskTitles,
                                    addToCalendar = input.addToCalendar,
                                    eventHasTime = input.eventHasTime,
                                    notificationTime = input.notificationTime
                                )
                                navController.popBackStack()
                            },
```

- [ ] **Step 6: ビルドが通ることを確認する**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 実機で確認する**

`./gradlew installDebug` で実機にインストールし、タスク登録画面で「予定の時刻を指定する」「通知時刻を指定する」をONにして時刻ピッカーが開閉し、値が保存されることを目視確認する。

- [ ] **Step 8: コミット**

```bash
git add app/src/main/java/com/example/myapplication/AddTaskScreen.kt \
  app/src/main/java/com/example/myapplication/MainActivity.kt
git commit -m "feat: add event time and notification time inputs to AddTaskScreen"
```

---

## Task 11: TaskEditDialog + TaskListScreen — 既存タスクの事後編集

**Files:**
- Create: `app/src/main/java/com/example/myapplication/TaskEditDialog.kt`
- Modify: `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- Modify: `app/src/main/java/com/example/myapplication/MainActivity.kt`
- Modify: `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`

**Interfaces:**
- Consumes: `OptionalTimePicker`（Task 9）, `TaskViewModel.renameTask/updateEventTime/updateNotificationTime`（既存 + Task 7）
- Produces: `data class TaskEditResult(title, eventHasTime, deadline, notificationTime)`, `TaskViewModel.applyTaskEdit(task, result)`

- [ ] **Step 1: `TaskViewModel.applyTaskEdit` の失敗するテストを書く**

`TaskViewModelCalendarTest.kt` に追加（`TaskEditResult` は `com.example.myapplication.TaskEditResult` を import する）:

```kotlin
    @Test
    fun `applyTaskEditは変更があった項目だけ更新する`() = runTest {
        val task = createTask(id = 1, title = "元のタイトル", calendarEventId = null)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.applyTaskEdit(
            task,
            TaskEditResult(
                title = "新しいタイトル",
                eventHasTime = true,
                deadline = 1_700_050_000_000L,
                notificationTime = 1_700_000_900_000L
            )
        )
        advanceUntilIdle()

        assertEquals(listOf(1 to "新しいタイトル"), repo.titleUpdates)
        assertEquals(listOf(Triple(1, 1_700_050_000_000L, true)), repo.eventTimeUpdates)
        assertEquals(listOf(1 to 1_700_000_900_000L), repo.notificationTimeUpdates)
    }

    @Test
    fun `applyTaskEditはタイトルが同じなら更新しない`() = runTest {
        val task = createTask(id = 1, title = "元のタイトル", calendarEventId = null)
        val repo = FakeRepository().apply { save(task) }
        val calendar = FakeCalendarSync()
        val scheduler = FakeTaskNotificationScheduler()
        val viewModel = createViewModel(repo, calendar, scheduler)

        viewModel.applyTaskEdit(
            task,
            TaskEditResult(
                title = "元のタイトル",
                eventHasTime = task.eventHasTime,
                deadline = task.deadline,
                notificationTime = task.notificationTime
            )
        )
        advanceUntilIdle()

        assertTrue(repo.titleUpdates.isEmpty())
        assertTrue(repo.eventTimeUpdates.isEmpty())
        assertTrue(repo.notificationTimeUpdates.isEmpty())
    }
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: FAIL（`TaskEditResult` も `applyTaskEdit` もまだ存在せずコンパイルエラー）

- [ ] **Step 3: `TaskEditDialog.kt` を作成する（`TaskEditResult` を含む）**

```kotlin
package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.Task
import java.util.Calendar

private const val TASK_TITLE_MAX_LENGTH = 50

/** タスク編集ダイアログでの確定結果。 */
data class TaskEditResult(
    val title: String,
    val eventHasTime: Boolean,
    val deadline: Long,
    val notificationTime: Long?
)

/**
 * タスク名・予定の時刻指定・通知時刻をまとめて編集するダイアログ。
 * 締切の「日付」自体はここでは変更できない（変更したい場合は削除して登録し直す運用とする）。
 */
@Composable
fun TaskEditDialog(
    task: Task,
    onConfirm: (TaskEditResult) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }

    val originalDeadline = task.deadline
    var eventHasTime by rememberSaveable(task.id) { mutableStateOf(task.eventHasTime) }
    val originalEventTime = remember(task.id) { Calendar.getInstance().apply { timeInMillis = originalDeadline } }
    var eventHour by rememberSaveable(task.id) { mutableIntStateOf(originalEventTime.get(Calendar.HOUR_OF_DAY)) }
    var eventMinute by rememberSaveable(task.id) { mutableIntStateOf(originalEventTime.get(Calendar.MINUTE)) }

    var notificationEnabled by rememberSaveable(task.id) { mutableStateOf(task.notificationTime != null) }
    val originalNotificationTime = remember(task.id) {
        Calendar.getInstance().apply { timeInMillis = task.notificationTime ?: task.deadline }
    }
    var notificationHour by rememberSaveable(task.id) {
        mutableIntStateOf(originalNotificationTime.get(Calendar.HOUR_OF_DAY))
    }
    var notificationMinute by rememberSaveable(task.id) {
        mutableIntStateOf(originalNotificationTime.get(Calendar.MINUTE))
    }

    val trimmedTitle = title.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タスクを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= TASK_TITLE_MAX_LENGTH) title = it },
                    label = { Text("タスク名") },
                    singleLine = true,
                    supportingText = { Text("${title.length} / $TASK_TITLE_MAX_LENGTH") }
                )

                OptionalTimePicker(
                    label = "予定の時刻を指定する",
                    description = "カレンダー連携中なら予定の時刻も更新されます",
                    enabled = eventHasTime,
                    onEnabledChange = { eventHasTime = it },
                    hour = eventHour,
                    minute = eventMinute,
                    onTimeChange = { h, m -> eventHour = h; eventMinute = m }
                )

                OptionalTimePicker(
                    label = "通知時刻を指定する",
                    description = "指定した時刻に必ず通知します",
                    enabled = notificationEnabled,
                    onEnabledChange = { notificationEnabled = it },
                    hour = notificationHour,
                    minute = notificationMinute,
                    onTimeChange = { h, m -> notificationHour = h; notificationMinute = m }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val deadline = combineDateAndTime(
                        originalDeadline,
                        if (eventHasTime) eventHour else 23,
                        if (eventHasTime) eventMinute else 59
                    )
                    val notificationTime = if (notificationEnabled) {
                        combineDateAndTime(originalDeadline, notificationHour, notificationMinute)
                    } else {
                        null
                    }
                    onConfirm(
                        TaskEditResult(
                            title = trimmedTitle,
                            eventHasTime = eventHasTime,
                            deadline = deadline,
                            notificationTime = notificationTime
                        )
                    )
                },
                enabled = trimmedTitle.isNotEmpty()
            ) {
                Text("変更")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

/** 元の日付はそのまま保ち、時刻部分だけ差し替える（終日予定は 23:59:59 に正規化する）。 */
private fun combineDateAndTime(baseMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = baseMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, if (hour == 23 && minute == 59) 59 else 0)
    }.timeInMillis
```

`remember` の import が抜けているため、import 一覧に `androidx.compose.runtime.remember` を追加する。

- [ ] **Step 4: `TaskViewModel.applyTaskEdit` を実装する**

`updateNotificationTime` 関数の直後に追加（import に `com.example.myapplication.TaskEditResult` は不要、同一パッケージのため）:

```kotlin
    /**
     * [TaskEditDialog] の確定結果を、変更があった項目だけ適用する。
     */
    fun applyTaskEdit(task: Task, result: TaskEditResult) {
        val trimmedTitle = result.title.trim()
        if (trimmedTitle.isNotEmpty() && trimmedTitle != task.title) {
            renameTask(task, trimmedTitle)
        }
        if (result.eventHasTime != task.eventHasTime || result.deadline != task.deadline) {
            updateEventTime(task, result.eventHasTime, result.deadline)
        }
        if (result.notificationTime != task.notificationTime) {
            updateNotificationTime(task, result.notificationTime)
        }
    }
```

- [ ] **Step 5: テストを実行して成功を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: PASS（全件）

- [ ] **Step 6: `TaskListScreen.kt` を変更し、タイトルタップで `TaskEditDialog` を開く**

`onTaskRename: (Task, String) -> Unit` パラメータを `onTaskEdit: (Task, TaskEditResult) -> Unit` に置き換える。

`taskToRename` 変数の宣言を `taskToEdit` に変更する:

```kotlin
    // 編集対象のタスク。null ならダイアログを出さない
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
```

`onTaskTitleClick = { task -> taskToRename = task }` を次のように変更する:

```kotlin
                    onTaskTitleClick = { task -> taskToEdit = task },
```

ファイル末尾付近の `taskToRename?.let { task -> CategoryNameDialog(...) }` ブロックを次のように置き換える:

```kotlin
    taskToEdit?.let { task ->
        TaskEditDialog(
            task = task,
            onConfirm = { result ->
                onTaskEdit(task, result)
                taskToEdit = null
            },
            onDismiss = { taskToEdit = null }
        )
    }
```

ファイル冒頭の `private const val TASK_TITLE_MAX_LENGTH = 50`（タスク名の文字数上限のコメント付き）は `TaskEditDialog.kt` 側に定義を移したので削除する。

「通知設定」への導線を追加する。`TaskListScreen` に `onManageNotificationSettings: () -> Unit` パラメータを追加し、`import androidx.compose.material.icons.filled.Notifications` を追加した上で、`onManageCategories` の `IconButton`（253〜255行目）の直後に追加する:

```kotlin
                    IconButton(onClick = onManageNotificationSettings) {
                        Icon(Icons.Filled.Notifications, contentDescription = "通知設定")
                    }
```

- [ ] **Step 7: `MainActivity.kt` の呼び出しを更新する**

`onTaskRename = viewModel::renameTask` を `onTaskEdit = viewModel::applyTaskEdit` に置き換える。

`onManageCategories` の直後に `onManageNotificationSettings` を追加する（実際のナビゲーション配線は Task 12 で行うため、ここでは仮に `onManageCategories` と同じ遷移にせず、次のタスクで実装するプレースホルダーではなく、実際のルートを直接ここで追加しても良い。本タスクでは呼び出し元のシグネチャ整合だけを行い、実装は Task 12 でまとめて行う）:

このステップでは `TaskListScreen` 呼び出しに一時的に `onManageNotificationSettings = {}` を渡してビルドを通す（Task 12 で実装を差し替える）:

```kotlin
                            onManageNotificationSettings = {},
```

- [ ] **Step 8: ビルドが通ることを確認する**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: 実機で確認する**

`./gradlew installDebug` でインストールし、タスク一覧でタイトルをタップして編集ダイアログが開くこと、タイトル変更・時刻指定の変更が保存されることを目視確認する。

- [ ] **Step 10: コミット**

```bash
git add app/src/main/java/com/example/myapplication/TaskEditDialog.kt \
  app/src/main/java/com/example/myapplication/TaskListScreen.kt \
  app/src/main/java/com/example/myapplication/MainActivity.kt \
  app/src/main/java/com/example/myapplication/TaskViewModel.kt \
  app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt
git commit -m "feat: add TaskEditDialog for editing existing task's event/notification time"
```

---

## Task 12: NotificationSettingsScreen + MainActivity 配線

**Files:**
- Create: `app/src/main/java/com/example/myapplication/NotificationSettingsScreen.kt`
- Modify: `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- Modify: `app/src/main/java/com/example/myapplication/MainActivity.kt`
- Modify: `app/src/main/java/com/example/myapplication/OptionalTimePicker.kt`（`SimpleTimePickerDialog` を再利用するため可視性はそのまま `internal` でよい）
- Modify: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`

**Interfaces:**
- Consumes: `NotificationWindowPreferences`（Task 3）, `SimpleTimePickerDialog`（Task 9）
- Produces: `TaskViewModel.notificationWindow: StateFlow<NotificationWindow>`, `TaskViewModel.saveNotificationWindow(window)`

- [ ] **Step 1: 失敗するテストを書く**

`TaskViewModelCalendarTest.kt` に、`NotificationWindowPreferences` をフェイクの `SharedPreferences` で注入できるよう `createViewModel` を拡張する:

```kotlin
    private fun createViewModel(
        repository: FakeRepository,
        calendarSync: FakeCalendarSync,
        notificationScheduler: TaskNotificationScheduler = FakeTaskNotificationScheduler(),
        notificationWindowPreferences: NotificationWindowPreferences =
            NotificationWindowPreferences(FakeSharedPreferences())
    ): TaskViewModel {
        return TaskViewModel(
            application = FakeApplication(),
            repository = repository,
            authManager = FakeAuthManager(),
            calendarSync = calendarSync,
            freeTimeCheckScheduler = FreeTimeCheckScheduler {},
            notificationScheduler = notificationScheduler,
            notificationWindowPreferences = notificationWindowPreferences
        )
    }
```

`NotificationWindowPreferencesTest.kt`（Task 3）で作った `FakeSharedPreferences` と同じ内容のクラスをこのファイルにも追加する（テストごとにフェイクを手書きする既存の流儀に合わせる）。クラス末尾（`FakeTaskDao` の直後）に追加:

```kotlin
    /** [com.example.myapplication.data.NotificationWindowPreferencesTest.FakeSharedPreferences] と同内容。 */
    private class FakeSharedPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = Unit
        override fun edit(): SharedPreferences.Editor = FakeEditor()

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { pending[key] = null }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                pending.clear()
            }
        }
    }
```

import に `android.content.SharedPreferences` と `com.example.myapplication.data.NotificationWindow`、`com.example.myapplication.data.NotificationWindowPreferences` を追加する。

テストを追加:

```kotlin
    @Test
    fun `saveNotificationWindowで保存した値がnotificationWindowに反映される`() = runTest {
        val repo = FakeRepository()
        val calendar = FakeCalendarSync()
        val prefs = NotificationWindowPreferences(FakeSharedPreferences())
        val viewModel = createViewModel(repo, calendar, notificationWindowPreferences = prefs)

        viewModel.saveNotificationWindow(
            NotificationWindow(java.time.LocalTime.of(7, 0), java.time.LocalTime.of(21, 0))
        )

        assertEquals(java.time.LocalTime.of(7, 0), viewModel.notificationWindow.value.start)
        assertEquals(java.time.LocalTime.of(21, 0), viewModel.notificationWindow.value.end)
        // SharedPreferences 側にも保存されている
        assertEquals(java.time.LocalTime.of(7, 0), prefs.get().start)
    }
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: FAIL（`TaskViewModel` に `notificationWindowPreferences` パラメータや `notificationWindow`/`saveNotificationWindow` が無くコンパイルエラー）

- [ ] **Step 3: `TaskViewModel.kt` を実装する**

import に以下を追加:

```kotlin
import com.example.myapplication.data.NotificationWindow
import com.example.myapplication.data.NotificationWindowPreferences
import kotlinx.coroutines.flow.asStateFlow
```

コンストラクタに `notificationWindowPreferences` パラメータを追加する:

```kotlin
    private val notificationScheduler: TaskNotificationScheduler =
        AndroidTaskNotificationScheduler(application),
    private val notificationWindowPreferences: NotificationWindowPreferences =
        NotificationWindowPreferences.get(application)
) : AndroidViewModel(application) {
```

`authState` プロパティの直後あたりに追加:

```kotlin
    /** 通知有効時間帯。設定画面の初期値・保存に使う。 */
    private val _notificationWindow = MutableStateFlow(notificationWindowPreferences.get())
    val notificationWindow: StateFlow<NotificationWindow> = _notificationWindow.asStateFlow()

    fun saveNotificationWindow(window: NotificationWindow) {
        notificationWindowPreferences.set(window)
        _notificationWindow.value = window
    }
```

（`MutableStateFlow` の import は既存の `kotlinx.coroutines.flow.*` のうちいくつかは個別importなので、`import kotlinx.coroutines.flow.MutableStateFlow` も追加すること）

- [ ] **Step 4: テストを実行して成功を確認する**

Run: `./gradlew testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest"`
Expected: PASS（全件）

- [ ] **Step 5: `NotificationSettingsScreen.kt` を作成する**

```kotlin
package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.NotificationWindow
import java.time.LocalTime

/**
 * 「空き時間です」通知（始めさせる通知）を出してよい時間帯を変更する画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    initialWindow: NotificationWindow,
    onSave: (NotificationWindow) -> Unit,
    onBack: () -> Unit
) {
    var startHour by rememberSaveable { mutableIntStateOf(initialWindow.start.hour) }
    var startMinute by rememberSaveable { mutableIntStateOf(initialWindow.start.minute) }
    var endHour by rememberSaveable { mutableIntStateOf(initialWindow.end.hour) }
    var endMinute by rememberSaveable { mutableIntStateOf(initialWindow.end.minute) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "「空き時間です」通知を出してよい時間帯を設定します。",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("開始: %02d:%02d".format(startHour, startMinute))
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("終了: %02d:%02d".format(endHour, endMinute))
            }

            Button(
                onClick = {
                    onSave(NotificationWindow(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute)))
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存する")
            }
        }
    }

    if (showStartPicker) {
        val state = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)
        SimpleTimePickerDialog(
            onDismissRequest = { showStartPicker = false },
            onConfirm = {
                startHour = state.hour
                startMinute = state.minute
                showStartPicker = false
            }
        ) { TimePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = true)
        SimpleTimePickerDialog(
            onDismissRequest = { showEndPicker = false },
            onConfirm = {
                endHour = state.hour
                endMinute = state.minute
                showEndPicker = false
            }
        ) { TimePicker(state = state) }
    }
}
```

- [ ] **Step 6: `MainActivity.kt` にルートを追加する**

`ROUTE_CATEGORIES` の直後に追加:

```kotlin
private const val ROUTE_NOTIFICATION_SETTINGS = "notification_settings"
```

`onManageNotificationSettings = {}`（Task 11 のプレースホルダー）を次のように差し替える:

```kotlin
                            onManageNotificationSettings = {
                                navController.navigate(ROUTE_NOTIFICATION_SETTINGS) {
                                    launchSingleTop = true
                                }
                            },
```

`composable(ROUTE_CATEGORIES) { ... }` ブロックの直後に追加する:

```kotlin
                    composable(ROUTE_NOTIFICATION_SETTINGS) {
                        val window by viewModel.notificationWindow.collectAsStateWithLifecycle()
                        NotificationSettingsScreen(
                            initialWindow = window,
                            onSave = viewModel::saveNotificationWindow,
                            onBack = { navController.popBackStack() }
                        )
                    }
```

- [ ] **Step 7: ビルドが通ることを確認する**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 実機で一連の機能を確認する**

`./gradlew installDebug` でインストールし、以下を実機で確認する:
- タスク一覧の通知アイコンから設定画面を開き、開始・終了時刻を変更して保存できること
- 新規タスク登録で「予定の時刻を指定する」「通知時刻を指定する」を設定して保存し、Google カレンダー連携ONなら実際に時刻付き予定が作られること（実機は Google 連携設定済みの前提。未設定なら `SETUP.md` を参照）
- サブタスクを付けたタスクをカレンダーに登録し、予定の説明欄にサブタスクが列挙されていること
- タスク一覧でタイトルをタップし、`TaskEditDialog` で時刻・通知時刻を変更できること
- 通知時刻を指定したタスクを保存し、その時刻に実際に通知が来ること（数分後の時刻で確認）
- 端末を再起動し、通知時刻がまだ先のタスクの通知が再起動後も来ること

- [ ] **Step 9: コミット**

```bash
git add app/src/main/java/com/example/myapplication/NotificationSettingsScreen.kt \
  app/src/main/java/com/example/myapplication/TaskViewModel.kt \
  app/src/main/java/com/example/myapplication/MainActivity.kt \
  app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt
git commit -m "feat: add notification window settings screen"
```

---

## 完了確認（全タスク後）

- [ ] `./gradlew testDebugUnitTest` が全件成功する
- [ ] `./gradlew connectedDebugAndroidTest` が全件成功する（実機/エミュレータ接続時）
- [ ] `./gradlew assembleDebug` が成功する
- [ ] Task 12 Step 8 の実機確認項目を再確認する
