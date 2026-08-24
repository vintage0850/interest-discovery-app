# Compose Multiplatform UI移植（KMP Phase 1b） Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:shared`モジュールにCompose MultiplatformでUI（タスク一覧・追加・カテゴリ管理・サブタスクCRUD）を実装し、既存Android版（`app/`）のカレンダー連携・通知関連を除いた縮小版として移植する。`:iosApp`のXcodeセットアップ手順を用意する。

**Architecture:** 現行の `app/src/main/java/com/example/myapplication/{TaskListScreen,AddTaskScreen,CategoryManagerScreen,MainActivity,ui/theme}` をベースに、カレンダー連携（`GoogleAuthManager`/`CalendarAuthState`/アカウントメニュー/連携トグル/サインアウト）・通知（`TaskStatus.IN_PROGRESS`バッジの意味付けは残すが操作導線は無し）を除いた縮小版を `:shared` の commonMain に書き直す。状態管理は `androidx.lifecycle.ViewModel`（Android専用）を使わず、`TaskRepository` を包む素の `AppState` クラス（独自の `CoroutineScope` を持つ）で代替する。画像（キャラクターイラスト）はCompose Multiplatform Resources（`composeResources`）で移植する。ナビゲーションは `org.jetbrains.androidx.navigation:navigation-compose`（マルチプラットフォーム版、Androidの標準ライブラリとは別グループ）を使う。`:app`（既存Android）はこのプランで一切変更しない。

**Tech Stack:** Kotlin Multiplatform 2.2.20, Compose Multiplatform 1.11.1, `org.jetbrains.androidx.navigation:navigation-compose` 2.9.2, SQLDelight 2.3.2（既存）, kotlinx-coroutines 1.10.2（既存）

**Spec:** `docs/superpowers/specs/2026-08-24-kmp-ios-migration-phase1-design.md`

## Global Constraints

- 新規コードのコメントは既存コードの慣習に合わせ日本語
- Red → Green の順でテストを書く（ただしCompose UI自体の自動テストはこのプロジェクトに前例が無く、今回も追加しない。実行確認はすべてMac環境でユーザー自身が行う前提とする — 詳細はTask 10参照）
- 各タスクの最後に必ずコミットする（1タスク=1コミット、テストが通った状態でコミットする）
- `app/` 配下のファイルは一切変更しない
- カレンダー連携・通知関連の機能（`GoogleAuthManager`、`CalendarAuthState`、アカウントメニュー、カレンダー登録トグル、サインアウト、`TaskStatus.IN_PROGRESS`への遷移操作）は一切移植しない。`Task.status`フィールド自体は`:shared`のドメインモデルに既にあるため、表示（進行中バッジ）だけは残すが、常に`TODO`のままなので実質的に表示されない
- このセッション（Windows）では `:shared:assembleDebug`（androidTarget）のコンパイル確認のみを行う。実機/エミュレータでの実行確認・iOSシミュレータでの確認はすべてMac環境でユーザー自身が行う（Task 10参照）。各タスクの検証コマンドはこれに従う
- Kotlin/NativeのiOSターゲット（iosArm64/iosSimulatorArm64/iosX64）はWindowsではコンパイルできない。iOS向けの変更は書くが、コンパイル確認はスコープ外とする

---

## Task 1: `:shared` にCompose Multiplatform・ナビゲーション・Resourcesを追加する

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

**Interfaces:**
- Produces: `:shared`でCompose Multiplatform（`compose.material3`/`compose.foundation`/`compose.ui`/`compose.components.resources`/`compose.materialIconsExtended`）とナビゲーション（`navigation-compose-multiplatform`）が使えるようになる。以降の全タスクがこれに依存する

- [ ] **Step 1: `gradle/libs.versions.toml` にバージョン・プラグイン・ライブラリを追加する**

`[versions]` に追加:

```toml
composeMultiplatform = "1.11.1"
navigationComposeMultiplatform = "2.9.2"
```

`[libraries]` に追加:

```toml
navigation-compose-multiplatform = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "navigationComposeMultiplatform" }
```

`[plugins]` に追加:

```toml
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```

- [ ] **Step 2: `shared/build.gradle.kts` にプラグインと依存関係を追加する**

`plugins { ... }` ブロックに追加:

```kotlin
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
```

（`libs.plugins.kotlin.compose` は `:app` が既に使っているCompose Compilerプラグインのエイリアス。`:shared`にも同じバージョンで適用する）

`kotlin { sourceSets { ... } }` の `commonMain.dependencies { ... }` を次のように変更する（既存の2行の直後に追加）:

```kotlin
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(libs.navigation.compose.multiplatform)
        }
```

`androidMain.dependencies { ... }` の直後（`val androidUnitTest by getting { ... }` の前）に追加:

```kotlin
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(compose.uiTooling)
            implementation(compose.preview)
        }
```

（既存の `androidMain.dependencies { implementation(libs.sqldelight.android.driver) }` を上記で置き換える）

`android { ... }` ブロックの `compileOptions { ... }` の直後に追加（Compose用のビルド機能を有効化）:

```kotlin
    buildFeatures {
        compose = true
    }
```

- [ ] **Step 3: Compose Resourcesのパッケージ名を設定する**

`sqldelight { ... }` ブロックの直後に追加:

```kotlin
compose.resources {
    packageOfResClass = "com.example.myapplication.shared.resources"
}
```

- [ ] **Step 4: androidTargetのビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミット**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "feat: add Compose Multiplatform, navigation, and resources to :shared"
```

---

## Task 2: テーマ（Color / Type / Theme）を移植する

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/theme/Color.kt`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/theme/Type.kt`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/theme/Theme.kt`

**Interfaces:**
- Produces: `@Composable fun SharedAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` — Task 8（App.kt）が使う

現行の `app/src/main/java/com/example/myapplication/ui/theme/{Color,Type,Theme}.kt` を移植する。`dynamicColor`（Android 12+専用のDynamic Color API）はプラットフォーム分岐が必要になるためPhase 1のスコープ外とし、常に固定のカラースキームを使う。

- [ ] **Step 1: `Color.kt` を作成する**

`app/src/main/java/com/example/myapplication/ui/theme/Color.kt` の中身をそのまま `package` 宣言だけ変えてコピーする:

```kotlin
package com.example.myapplication.shared.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

- [ ] **Step 2: `Type.kt` を作成する**

```kotlin
package com.example.myapplication.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: `Theme.kt` を作成する（dynamicColorは除外）**

```kotlin
package com.example.myapplication.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Android版の `MyApplicationTheme` からDynamic Color（Android 12+専用API、
 * プラットフォーム分岐が必要）を除いた版。Phase 1のスコープ外のため常に固定配色を使う。
 */
@Composable
fun SharedAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: ビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/theme
git commit -m "feat: port theme (Color/Type/Theme) to :shared, minus Android dynamic color"
```

---

## Task 3: キャラクターイラストをCompose Resourcesとして移植する

**Files:**
- Create: `shared/src/commonMain/composeResources/drawable/cat_happy.xml`
- Create: `shared/src/commonMain/composeResources/drawable/cat_sad.xml`
- Create: `shared/src/commonMain/composeResources/drawable/cat_flustered.xml`

**Interfaces:**
- Produces: Compose Multiplatform Resourcesが自動生成する `Res.drawable.cat_happy` / `Res.drawable.cat_sad` / `Res.drawable.cat_flustered` — Task 7（TaskListScreen）が使う

現行の `app/src/main/res/drawable/cat_{happy,sad,flustered}.xml` は、Android固有のリソース参照を含まない素の `<vector>` XMLのため、Compose Multiplatform Resourcesがサポートするベクター画像としてそのままコピーできる。

- [ ] **Step 1: 3つのXMLファイルをそのままコピーする**

```bash
cp app/src/main/res/drawable/cat_happy.xml shared/src/commonMain/composeResources/drawable/cat_happy.xml
cp app/src/main/res/drawable/cat_sad.xml shared/src/commonMain/composeResources/drawable/cat_sad.xml
cp app/src/main/res/drawable/cat_flustered.xml shared/src/commonMain/composeResources/drawable/cat_flustered.xml
```

コピー後、各ファイルの中身が `<vector xmlns:android="http://schemas.android.com/apk/res/android" ...>` で始まり、`android:src`等の他リソースへの参照を含んでいないことを確認する（含んでいた場合はCompose Multiplatform Resourcesが処理できないため、この場で報告して停止する）。

- [ ] **Step 2: `Res`オブジェクトが生成されビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL。`shared/build/generated/compose/resourceGenerator/` 配下に `Res.kt` 等が生成され、`Res.drawable.cat_happy` 等が参照可能になっていること（生成先の正確なパスはSQLDelightと同様ビルドが通れば確認不要。もし生成タスク名が必要なら `./gradlew :shared:tasks --all | grep -i resource` で確認してよい）

- [ ] **Step 3: コミット**

```bash
git add shared/src/commonMain/composeResources
git commit -m "feat: port character illustrations as Compose Multiplatform resources"
```

---

## Task 4: AppState（ViewModel相当の状態管理）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/AppState.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/example/myapplication/shared/ui/AppStateTest.kt`

**Interfaces:**
- Consumes: `TaskRepository`（既存）, `Task`/`SubTask`/`Category`/`TaskWithSubTasks`（既存）
- Produces: `class AppState(repository: TaskRepository, coroutineScope: CoroutineScope)` with `val allTasks: StateFlow<List<TaskWithSubTasks>>`, `val categories: StateFlow<List<Category>>`, `val messages: SharedFlow<String>`, `fun addTask(title, deadline, importance, urgency, categoryId, subTaskTitles = emptyList())`, `fun toggleCompleted(task: Task)`, `fun toggleSubTaskCompleted(subTask: SubTask)`, `fun renameTask(task: Task, newTitle: String)`, `fun deleteTask(task: Task)`, `fun undoDelete()`, `fun addCategory(name: String)`, `fun renameCategory(category: Category, newName: String)`, `fun deleteCategory(category: Category)`, `suspend fun countTasksInCategory(categoryId: Int): Int` — Task 6・7・8 のUIがこれを使う

現行の `app/src/main/java/com/example/myapplication/TaskViewModel.kt` から、カレンダー連携・通知関連（`authState`、`setCalendarLinked`、`signOut`、`syncToCalendar`、`notifyCalendarFailure`、Mutex、`FreeTimeCheckScheduler`）を除いたCRUD部分だけを、`androidx.lifecycle.ViewModel`（Android専用）を使わない素のクラスとして書き直す。`viewModelScope` の代わりにコンストラクタで受け取る `CoroutineScope` を使う（テスト時は `TestScope` を注入できる）。

**設計上の既知の違い（Room版との差分。実装漏れではない）:** Room版の `undoDelete` は `OnConflictStrategy.REPLACE` により削除前と同じ `id` でタスクを復元できるが、`:shared` の `TaskRepository.insert` は常に新しい `id` を自動採番する（Task 3で定義したSQLDelightの `insert` クエリが `id` 列を指定しないため）。そのため今回の `undoDelete` は「同じ内容のタスクを新しい `id` で作り直す」動作になる。実用上の違いはない（一覧に同じ内容で復元される）。

- [ ] **Step 1: 失敗するテストを書く**

`AppState` はDB（`TaskRepository`）に依存するため、プラットフォーム固有のドライバが無い
`commonTest` では実行できない。`TaskRepositoryTest`（前プランのTask 6）と同じ方式で、
ホストJVM上で動くJDBCドライバを使い `androidUnitTest` にテストを書く。

`shared/src/androidUnitTest/kotlin/com/example/myapplication/shared/ui/AppStateTest.kt` を作成する:

```kotlin
package com.example.myapplication.shared.ui

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.myapplication.shared.Category
import com.example.myapplication.shared.SubTask
import com.example.myapplication.shared.Task
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.db.SharedDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStateTest {

    private class InMemoryDriverFactory : DatabaseDriverFactory {
        override fun createDriver(): SqlDriver {
            val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            SharedDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
            return driver
        }
    }

    private fun newAppState(scope: kotlinx.coroutines.CoroutineScope): AppState =
        AppState(TaskRepository(InMemoryDriverFactory()), scope)

    @Test
    fun `addTaskで前後の空白を取り除いたタイトルが保存される`() = runTest {
        val state = newAppState(this)

        state.addTask(
            title = "  買い物  ",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        advanceUntilIdle()

        assertEquals("買い物", state.allTasks.first().single().task.title)
    }

    @Test
    fun `addTaskは空白のみのタイトルなら何もしない`() = runTest {
        val state = newAppState(this)

        state.addTask(
            title = "   ",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        advanceUntilIdle()

        assertTrue(state.allTasks.first().isEmpty())
    }

    @Test
    fun `addTaskはサブタスクの前後の空白を取り除き空文字は無視する`() = runTest {
        val state = newAppState(this)

        state.addTask(
            title = "レポート",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null,
            subTaskTitles = listOf("  下書き  ", "", "   ", "清書")
        )
        advanceUntilIdle()

        val subTasks = state.allTasks.first().single().subTasks
        assertEquals(listOf("下書き", "清書"), subTasks.map { it.title })
    }

    @Test
    fun `deleteTaskで削除しundoDeleteで同じ内容が復元される`() = runTest {
        val state = newAppState(this)
        state.addTask(
            title = "タスクA",
            deadline = 1_700_000_000_000L,
            importance = 3,
            urgency = 1,
            categoryId = null,
            subTaskTitles = listOf("サブ1")
        )
        advanceUntilIdle()
        val task = state.allTasks.first().single().task

        state.deleteTask(task)
        advanceUntilIdle()
        assertTrue(state.allTasks.first().isEmpty())

        state.undoDelete()
        advanceUntilIdle()

        val restored = state.allTasks.first().single()
        assertEquals("タスクA", restored.task.title)
        assertEquals(listOf("サブ1"), restored.subTasks.map { it.title })
    }

    @Test
    fun `undoDeleteは削除直後以外は何もしない`() = runTest {
        val state = newAppState(this)

        state.undoDelete()
        advanceUntilIdle()

        assertTrue(state.allTasks.first().isEmpty())
    }

    @Test
    fun `renameTaskは空白のみなら変更しない`() = runTest {
        val state = newAppState(this)
        state.addTask(
            title = "元の名前",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        advanceUntilIdle()
        val task = state.allTasks.first().single().task

        state.renameTask(task, "   ")
        advanceUntilIdle()

        assertEquals("元の名前", state.allTasks.first().single().task.title)
    }

    @Test
    fun `toggleCompletedで完了状態が反転する`() = runTest {
        val state = newAppState(this)
        state.addTask(
            title = "タスクB",
            deadline = 1_700_000_000_000L,
            importance = 2,
            urgency = 2,
            categoryId = null
        )
        advanceUntilIdle()
        val task = state.allTasks.first().single().task

        state.toggleCompleted(task)
        advanceUntilIdle()
        assertTrue(state.allTasks.first().single().task.isCompleted)

        state.toggleCompleted(state.allTasks.first().single().task)
        advanceUntilIdle()
        assertTrue(!state.allTasks.first().single().task.isCompleted)
    }

    @Test
    fun `addCategoryで重複した名前ならmessagesに失敗を知らせる`() = runTest {
        val state = newAppState(this)
        state.addCategory("仕事")
        advanceUntilIdle()

        val messages = mutableListOf<String>()
        val job = kotlinx.coroutines.launch { state.messages.collect { messages.add(it) } }
        state.addCategory("仕事")
        advanceUntilIdle()
        job.cancel()

        assertTrue(messages.any { it.contains("すでにあります") })
    }
}
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.ui.AppStateTest"`
Expected: FAIL（`AppState` が存在せずコンパイルエラー）

- [ ] **Step 3: `AppState.kt` を実装する**

```kotlin
package com.example.myapplication.shared.ui

import com.example.myapplication.shared.Category
import com.example.myapplication.shared.SubTask
import com.example.myapplication.shared.Task
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.TaskWithSubTasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `androidx.lifecycle.ViewModel`（Android専用）を使わない、Compose Multiplatform共通の状態管理。
 * Android版 `TaskViewModel` からカレンダー連携・通知関連を除いたCRUD部分の移植。
 * ライフサイクル管理（`coroutineScope`のキャンセル）は呼び出し側（Android/iOSそれぞれのホスト）の責務。
 */
class AppState(
    private val repository: TaskRepository,
    private val coroutineScope: CoroutineScope
) {
    val allTasks: StateFlow<List<TaskWithSubTasks>> = repository.allTasks.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val categories: StateFlow<List<Category>> = repository.categories.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** 直前に削除したタスク。取り消し（[undoDelete]）のために保持する。 */
    private var lastDeleted: DeletedTask? = null

    private data class DeletedTask(val task: Task, val subTasks: List<SubTask>)

    // ---- カテゴリ ----

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        coroutineScope.launch {
            if (repository.addCategory(trimmed)) {
                _messages.tryEmit("「$trimmed」を追加しました")
            } else {
                _messages.tryEmit("「$trimmed」はすでにあります")
            }
        }
    }

    fun renameCategory(category: Category, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == category.name) return
        coroutineScope.launch {
            if (repository.renameCategory(category, trimmed)) {
                _messages.tryEmit("「$trimmed」に変更しました")
            } else {
                _messages.tryEmit("「$trimmed」はすでにあります")
            }
        }
    }

    /**
     * カテゴリを削除する。中のタスクは消えず「未分類」に移る
     * （tasks.categoryId の外部キーが ON DELETE SET NULL のため DB 側で処理される）。
     */
    fun deleteCategory(category: Category) {
        coroutineScope.launch {
            val moved = repository.countTasksInCategory(category.id)
            repository.deleteCategory(category)
            _messages.tryEmit(
                if (moved > 0) {
                    "「${category.name}」を削除しました（${moved}件を未分類に移動）"
                } else {
                    "「${category.name}」を削除しました"
                }
            )
        }
    }

    /** 削除の確認ダイアログに件数を出すために使う。 */
    suspend fun countTasksInCategory(categoryId: Int): Int =
        repository.countTasksInCategory(categoryId)

    // ---- タスク ----

    fun addTask(
        title: String,
        deadline: Long,
        importance: Int,
        urgency: Int,
        categoryId: Int?,
        subTaskTitles: List<String> = emptyList()
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return

        coroutineScope.launch {
            val task = Task(
                title = trimmed,
                deadline = deadline,
                importance = importance,
                urgency = urgency,
                categoryId = categoryId
            )
            val id = repository.insert(task)

            val subTasks = subTaskTitles
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, subTitle ->
                    SubTask(taskId = id, title = subTitle, sortOrder = index)
                }
            if (subTasks.isNotEmpty()) repository.insertSubTasks(subTasks)
        }
    }

    fun toggleCompleted(task: Task) {
        coroutineScope.launch {
            repository.update(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun renameTask(task: Task, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty() || trimmed == task.title) return
        coroutineScope.launch {
            repository.update(task.copy(title = trimmed))
        }
    }

    fun toggleSubTaskCompleted(subTask: SubTask) {
        coroutineScope.launch {
            repository.updateSubTask(subTask.copy(isCompleted = !subTask.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        coroutineScope.launch {
            // 取り消しに備えて、削除前にサブタスクも読み出しておく
            val subTasks = repository.getSubTasksFor(task.id)
            repository.delete(task)
            lastDeleted = DeletedTask(task, subTasks)
        }
    }

    /**
     * 直前の削除を取り消す。`:shared` の insert は常に新しい id を採番するため、
     * 元と同じ id では復元されない（内容は同じ）。
     */
    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        coroutineScope.launch {
            val id = repository.insert(deleted.task)
            if (deleted.subTasks.isNotEmpty()) {
                repository.insertSubTasks(deleted.subTasks.map { it.copy(id = 0, taskId = id) })
            }
        }
    }
}
```

- [ ] **Step 4: テストを実行して成功を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.ui.AppStateTest"`
Expected: PASS（全件）

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/AppState.kt \
  shared/src/androidUnitTest/kotlin/com/example/myapplication/shared/ui/AppStateTest.kt
git commit -m "feat: add AppState (ViewModel-less state holder) for shared UI"
```

---

## Task 5: CategoryManagerScreen + CategoryNameDialog の移植

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/CategoryManagerScreen.kt`

**Interfaces:**
- Consumes: `Category`（既存）
- Produces: `@Composable fun CategoryManagerScreen(categories, snackbarHostState, onAdd, onRename, onDelete, countTasksIn, onBack)`, `@Composable fun CategoryNameDialog(title, initialName, confirmLabel, onConfirm, onDismiss, label = "カテゴリ名", maxLength = Category.MAX_NAME_LENGTH)` — Task 6・7 が使う

現行の `app/src/main/java/com/example/myapplication/CategoryManagerScreen.kt` はカレンダー連携に依存していないため、`import`のパッケージだけ変えてほぼそのまま移植する。

- [ ] **Step 1: `CategoryManagerScreen.kt` を作成する**

```kotlin
package com.example.myapplication.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.Category

/**
 * カテゴリ（タスクの枠）の追加・リネーム・削除をする画面。
 *
 * 削除してもタスク自体は消えず「未分類」に移るので、
 * 何件動くのかを確認ダイアログで先に見せる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    categories: List<Category>,
    snackbarHostState: SnackbarHostState,
    onAdd: (String) -> Unit,
    onRename: (Category, String) -> Unit,
    onDelete: (Category) -> Unit,
    countTasksIn: suspend (Int) -> Int,
    onBack: () -> Unit
) {
    // 追加ダイアログ / 編集ダイアログ（編集中のカテゴリを保持）
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    // 削除確認に出す「未分類に移る件数」。数え終わるまでは null
    var deletingTaskCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(deleting) {
        val target = deleting
        deletingTaskCount = if (target == null) null else countTasksIn(target.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("カテゴリの管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("カテゴリを追加") }
            )
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "カテゴリがありません。\n「カテゴリを追加」から作ってください。",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = category }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "「${category.name}」の名前を変更"
                                    )
                                }
                                IconButton(onClick = { deleting = category }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "「${category.name}」を削除",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryNameDialog(
            title = "カテゴリを追加",
            initialName = "",
            confirmLabel = "追加",
            onConfirm = {
                onAdd(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editing?.let { category ->
        CategoryNameDialog(
            title = "名前を変更",
            initialName = category.name,
            confirmLabel = "変更",
            onConfirm = {
                onRename(category, it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { category ->
        val count = deletingTaskCount
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("「${category.name}」を削除しますか?") },
            text = {
                Text(
                    when {
                        count == null -> "確認しています…"
                        count > 0 -> "このカテゴリの${count}件のタスクは削除されず、" +
                            "「${Category.UNCATEGORIZED_LABEL}」に移動します。"
                        else -> "このカテゴリにタスクはありません。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    // 件数を数え終わるまでは押せないようにして、確認なしの削除を防ぐ
                    enabled = count != null,
                    onClick = {
                        onDelete(category)
                        deleting = null
                    }
                ) {
                    Text("削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("キャンセル") }
            }
        )
    }
}

/**
 * 追加・リネームで共通の入力ダイアログ。
 * カテゴリ名だけでなくタスク名のリネームでも使うため、ラベルと文字数上限は呼び出し側から渡す。
 */
@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "カテゴリ名",
    maxLength: Int = Category.MAX_NAME_LENGTH
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= maxLength) name = it
                },
                label = { Text(label) },
                singleLine = true,
                supportingText = { Text("${name.length} / $maxLength") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
```

- [ ] **Step 2: ビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/CategoryManagerScreen.kt
git commit -m "feat: port CategoryManagerScreen and CategoryNameDialog to :shared"
```

---

## Task 6: AddTaskScreen の移植（カレンダー連携を除く）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/AddTaskScreen.kt`

**Interfaces:**
- Consumes: `Category`（既存）
- Produces: `data class NewTaskInput(title, deadline, importance, urgency, categoryId, subTaskTitles)`, `@Composable fun AddTaskScreen(categories, onAddCategory, onTaskAdded, onBack)` — Task 8 が使う

現行の `app/src/main/java/com/example/myapplication/AddTaskScreen.kt` から、Googleカレンダー連携のUI（`Row`＋`Switch`のブロック、`GoogleAuthManager`/`CalendarAuthState`関連の状態・import）と `addToCalendar` フィールドを除いた版。

- [ ] **Step 1: `AddTaskScreen.kt` を作成する**

```kotlin
package com.example.myapplication.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.Category
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val MAX_TITLE_LENGTH = 50
private const val MAX_SUBTASKS = 10

/** カテゴリ未選択（＝未分類）を rememberSaveable の Int で表すための番兵。 */
private const val NO_CATEGORY = -1

/** 保存時に画面から受け取る入力一式。引数が増えすぎたのでまとめた。 */
data class NewTaskInput(
    val title: String,
    val deadline: Long,
    val importance: Int,
    val urgency: Int,
    /** 所属カテゴリ。null は未分類。 */
    val categoryId: Int?,
    val subTaskTitles: List<String>
)

/**
 * DatePicker が返すのは「UTC のその日の 0 時」。
 * そのままだと端末のタイムゾーン次第で前日/翌日にずれるため、
 * ローカルタイムのその日の 23:59:59 に正規化して締切として保存する。
 */
private fun toLocalEndOfDay(utcMillis: Long): Long {
    val utcDate = Instant.fromEpochMilliseconds(utcMillis)
        .toLocalDateTime(TimeZone.UTC)
        .date
    val localMidnight = utcDate.atTime(23, 59, 59)
    return localMidnight.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

/** 選択した日付を画面表示用に整形する（"2026年8月24日" 形式）。 */
private fun formatDate(utcMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(utcMillis).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}年${date.monthNumber}月${date.dayOfMonth}日"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskScreen(
    categories: List<Category>,
    onAddCategory: (String) -> Unit,
    onTaskAdded: (NewTaskInput) -> Unit,
    onBack: () -> Unit
) {
    // 画面回転で入力が消えないよう rememberSaveable を使う
    var title by rememberSaveable { mutableStateOf("") }
    var importance by rememberSaveable { mutableFloatStateOf(2f) }
    var urgency by rememberSaveable { mutableFloatStateOf(2f) }
    var selectedCategoryId by rememberSaveable { mutableIntStateOf(NO_CATEGORY) }
    // 新しいカテゴリをこの画面から作れるようにする（作成直後はそれを選択状態にする）
    var showNewCategoryDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCategoryName by rememberSaveable { mutableStateOf<String?>(null) }

    // 初回表示時と、この画面から作ったカテゴリが一覧に流れてきた時の選択合わせ
    LaunchedEffect(categories) {
        val requested = pendingCategoryName
        val created = requested?.let { name -> categories.firstOrNull { it.name == name } }
        when {
            created != null -> {
                selectedCategoryId = created.id
                pendingCategoryName = null
            }
            // 未選択なら先頭のカテゴリを既定にする
            selectedCategoryId == NO_CATEGORY -> {
                categories.firstOrNull()?.let { selectedCategoryId = it.id }
            }
            // 選択中のカテゴリが他画面で消された場合の取りこぼしを防ぐ
            categories.none { it.id == selectedCategoryId } -> {
                selectedCategoryId = categories.firstOrNull()?.id ?: NO_CATEGORY
            }
        }
    }
    // 一度でも入力に触れたか。触れる前からエラーを出さないためのフラグ
    var titleTouched by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val subTaskTitles = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf<String>() }

    val datePickerState = rememberDatePickerState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val isTitleEmpty = title.isBlank()
    val selectedDate = datePickerState.selectedDateMillis
    val canSave = !isTitleEmpty && selectedDate != null

    fun save() {
        val date = datePickerState.selectedDateMillis ?: return
        if (title.isBlank()) {
            titleTouched = true
            return
        }
        keyboardController?.hide()
        onTaskAdded(
            NewTaskInput(
                title = title.trim(),
                deadline = toLocalEndOfDay(date),
                importance = importance.toInt(),
                urgency = urgency.toInt(),
                categoryId = selectedCategoryId.takeIf { it != NO_CATEGORY },
                subTaskTitles = subTaskTitles.toList()
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タスク登録") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // 小さい画面やキーボード表示時に下の保存ボタンへ届くようにする
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= MAX_TITLE_LENGTH) title = it
                    titleTouched = true
                },
                label = { Text("タイトル") },
                placeholder = { Text("例: プログラミングの学習") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                isError = titleTouched && isTitleEmpty,
                supportingText = {
                    if (titleTouched && isTitleEmpty) {
                        Text("タイトルを入力してください")
                    } else {
                        Text("${title.length} / $MAX_TITLE_LENGTH")
                    }
                }
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val dateText = selectedDate?.let { formatDate(it) } ?: "日付を選択"
                Text(text = "締め切り: $dateText")
            }

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("確定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("キャンセル")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            LabeledSlider(
                label = "重要度: ${importance.toInt()} (1:低 3:高)",
                value = importance,
                onValueChange = { importance = it }
            )

            LabeledSlider(
                label = "緊急度: ${urgency.toInt()} (1:低 3:高)",
                value = urgency,
                onValueChange = { urgency = it }
            )

            Text("カテゴリ", style = MaterialTheme.typography.titleSmall)
            // カテゴリは何個でも作れるので、はみ出したら折り返す
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name) },
                        leadingIcon = if (selectedCategoryId == category.id) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                }
                AssistChip(
                    onClick = { showNewCategoryDialog = true },
                    label = { Text("新しいカテゴリ") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) }
                )
            }
            if (categories.isEmpty()) {
                Text(
                    text = "カテゴリがありません。このタスクは「${Category.UNCATEGORIZED_LABEL}」になります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            SubTaskEditor(titles = subTaskTitles)

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Text("タスクを保存する")
            }
        }
    }

    if (showNewCategoryDialog) {
        CategoryNameDialog(
            title = "カテゴリを追加",
            initialName = "",
            confirmLabel = "追加",
            onConfirm = { name ->
                // 作成は非同期なので、名前を控えておいて一覧に現れたら選択する
                pendingCategoryName = name
                onAddCategory(name)
                showNewCategoryDialog = false
            },
            onDismiss = { showNewCategoryDialog = false }
        )
    }
}

@Composable
private fun SubTaskEditor(titles: SnapshotStateList<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("サブタスク（任意）", style = MaterialTheme.typography.titleSmall)

        titles.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { titles[index] = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("${index + 1} つ目") },
                    singleLine = true
                )
                IconButton(onClick = { titles.removeAt(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "このサブタスクを削除")
                }
            }
        }

        TextButton(
            onClick = { titles.add("") },
            enabled = titles.size < MAX_SUBTASKS
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (titles.size < MAX_SUBTASKS) {
                    "サブタスクを追加"
                } else {
                    "サブタスクは $MAX_SUBTASKS 件までです"
                }
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..3f,
            steps = 1
        )
    }
}
```

**注記:** Android版は `java.text.DateFormat`/`java.util.Calendar` を使っていたが、これらはcommonMainで使えない（JVM専用）。代わりにKotlinx-datetimeを使う。`shared/build.gradle.kts` の `commonMain.dependencies` に追加が必要:

```kotlin
            implementation(libs.kotlinx.datetime)
```

`gradle/libs.versions.toml` にも追加する:

```toml
kotlinxDatetime = "0.7.1"
```
```toml
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }
```

- [ ] **Step 2: kotlinx-datetimeの依存を追加する**

上記の3ファイル（`gradle/libs.versions.toml` 2箇所、`shared/build.gradle.kts` 1箇所）を編集する。

- [ ] **Step 3: ビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts \
  shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/AddTaskScreen.kt
git commit -m "feat: port AddTaskScreen to :shared, minus calendar integration"
```

---

## Task 7: TaskListScreen の移植（カレンダー連携を除く）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/TaskListScreen.kt`

**Interfaces:**
- Consumes: `Task`/`SubTask`/`Category`/`TaskWithSubTasks`/`TaskStatus`（既存）, `Res.drawable.{cat_happy,cat_sad,cat_flustered}`（Task 3）
- Produces: `@Composable fun TaskListScreen(tasks, categories, snackbarHostState, onAddTask, onManageCategories, onTaskToggle, onSubTaskToggle, onTaskDelete, onUndoDelete, onTaskRename)` — Task 8 が使う

現行の `app/src/main/java/com/example/myapplication/TaskListScreen.kt` から、カレンダー連携部分（`CalendarAccountMenu`、`rememberCalendarAuthorization`、`CalendarAuthorizationOutcome`、アカウントメニューの`IconButton`、`onCalendarLinkChange`/`onSignOut`/`authState`パラメータ、`TaskItem`のカレンダーアイコン）を除いた版。日付フォーマットは Task 6 と同様 `kotlinx-datetime` を使う。画像は `painterResource(Res.drawable.xxx)`（Compose Multiplatform Resources）を使う。

- [ ] **Step 1: `TaskListScreen.kt` を作成する**

```kotlin
package com.example.myapplication.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.myapplication.shared.Category
import com.example.myapplication.shared.SubTask
import com.example.myapplication.shared.Task
import com.example.myapplication.shared.TaskStatus
import com.example.myapplication.shared.TaskWithSubTasks
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.cat_flustered
import myapplication.shared.generated.resources.cat_happy
import myapplication.shared.generated.resources.cat_sad

/**
 * 選択中のタブ。カテゴリは増減するので、位置ではなく「何で絞り込むか」を持たせる。
 * rememberSaveable にそのまま入れられるよう Int で表す。
 */
private const val FILTER_ALL = -1
private const val FILTER_UNCATEGORIZED = 0

/** タブ 1 つ分。filter は上の定数、またはカテゴリの id。 */
private data class TaskTab(val filter: Int, val title: String)

/** タスク名の文字数上限。AddTaskScreen の新規登録時と揃える。 */
private const val TASK_TITLE_MAX_LENGTH = 50

private fun formatDate(epochMillis: Long): String {
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date
    return "${date.year}年${date.monthNumber}月${date.dayOfMonth}日"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    tasks: List<TaskWithSubTasks>,
    categories: List<Category>,
    snackbarHostState: SnackbarHostState,
    onAddTask: () -> Unit,
    onManageCategories: () -> Unit,
    onTaskToggle: (Task) -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onTaskDelete: (Task) -> Unit,
    onUndoDelete: () -> Unit,
    onTaskRename: (Task, String) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableIntStateOf(FILTER_ALL) }
    val scope = rememberCoroutineScope()

    // リネーム対象のタスク。null ならダイアログを出さない
    var taskToRename by remember { mutableStateOf<Task?>(null) }

    val categoryNames = remember(categories) { categories.associate { it.id to it.name } }

    val tabs = remember(categories, tasks) {
        buildList {
            add(TaskTab(FILTER_ALL, "すべて"))
            categories.forEach { add(TaskTab(it.id, it.name)) }
            // カテゴリを消されたタスクの行き先。該当が無いときはタブも出さない
            if (tasks.any { it.task.categoryId == null }) {
                add(TaskTab(FILTER_UNCATEGORIZED, Category.UNCATEGORIZED_LABEL))
            }
        }
    }

    // 選択中のカテゴリが削除されたら「すべて」に戻す
    val selectedIndex = tabs.indexOfFirst { it.filter == selectedFilter }.takeIf { it >= 0 } ?: 0
    val currentTab = tabs[selectedIndex]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("今日のタスク") },
                actions = {
                    IconButton(onClick = onManageCategories) {
                        Icon(Icons.Filled.Settings, contentDescription = "カテゴリの管理")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTask,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = "タスクを追加")
            }
        }
    ) { padding ->
        val filteredTasks = remember(tasks, currentTab) {
            when (currentTab.filter) {
                FILTER_ALL -> tasks
                FILTER_UNCATEGORIZED -> tasks.filter { it.task.categoryId == null }
                else -> tasks.filter { it.task.categoryId == currentTab.filter }
            }
        }
        val uncompletedCount = filteredTasks.count { !it.task.isCompleted }

        Column(modifier = Modifier.padding(padding)) {
            // カテゴリは何個でも増えるので、固定タブではなく横スクロールにする
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = { selectedFilter = tab.filter },
                        text = { Text(tab.title, maxLines = 1) }
                    )
                }
            }

            CharacterStatusHeader(
                uncompletedCount = uncompletedCount,
                hasTasks = filteredTasks.isNotEmpty()
            )

            if (filteredTasks.isEmpty()) {
                EmptyStateView(
                    modifier = Modifier.fillMaxSize(),
                    message = when {
                        tasks.isEmpty() -> "タスクがありません。\n右下の「＋」から追加してください。"
                        currentTab.filter == FILTER_ALL ->
                            "タスクがありません。\n右下の「＋」から追加してください。"
                        else -> "「${currentTab.title}」のタスクはまだありません。"
                    }
                )
            } else {
                TaskList(
                    tasks = filteredTasks,
                    categoryNames = categoryNames,
                    onTaskToggle = onTaskToggle,
                    onSubTaskToggle = onSubTaskToggle,
                    onTaskTitleClick = { task -> taskToRename = task },
                    onTaskDelete = { task ->
                        onTaskDelete(task)
                        // 直前のスナックバーは畳んで、常に最新の削除に対する取り消しを出す
                        snackbarHostState.currentSnackbarData?.dismiss()
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "「${task.title}」を削除しました",
                                actionLabel = "元に戻す",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) onUndoDelete()
                        }
                    }
                )
            }
        }
    }

    taskToRename?.let { task ->
        CategoryNameDialog(
            title = "タスク名を変更",
            initialName = task.title,
            confirmLabel = "変更",
            label = "タスク名",
            maxLength = TASK_TITLE_MAX_LENGTH,
            onConfirm = { newTitle ->
                onTaskRename(task, newTitle)
                taskToRename = null
            },
            onDismiss = { taskToRename = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskList(
    tasks: List<TaskWithSubTasks>,
    categoryNames: Map<Int, String>,
    onTaskToggle: (Task) -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onTaskTitleClick: (Task) -> Unit,
    onTaskDelete: (Task) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp) // FAB に隠れないよう余白を確保
    ) {
        // key を渡すことで、並び替え・削除時に行の状態が混ざらないようにする
        items(tasks, key = { it.task.id }) { item ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart) {
                        onTaskDelete(item.task)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = { SwipeToDeleteBackground() }
            ) {
                TaskItem(
                    item = item,
                    categoryName = item.task.categoryId?.let { categoryNames[it] }
                        ?: Category.UNCATEGORIZED_LABEL,
                    onToggle = { onTaskToggle(item.task) },
                    onSubTaskToggle = onSubTaskToggle,
                    onTitleClick = { onTaskTitleClick(item.task) }
                )
            }
        }
    }
}

@Composable
private fun SwipeToDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "削除",
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun CharacterStatusHeader(uncompletedCount: Int, hasTasks: Boolean) {
    val (imageRes, message) = when {
        !hasTasks -> Res.drawable.cat_happy to "のんびり待ってるよ〜"
        uncompletedCount == 0 -> Res.drawable.cat_happy to "お見事！全部完了です！"
        uncompletedCount >= 5 -> Res.drawable.cat_flustered to "残り${uncompletedCount}件…慌ててます！"
        else -> Res.drawable.cat_sad to "あと${uncompletedCount}件、がんばろう…"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun EmptyStateView(modifier: Modifier, message: String) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
fun TaskItem(
    item: TaskWithSubTasks,
    categoryName: String,
    onToggle: () -> Unit,
    onSubTaskToggle: (SubTask) -> Unit,
    onTitleClick: () -> Unit = {}
) {
    val task = item.task
    // 優先度スコアは 4〜12。テーマ由来の色を使い、ダークテーマでも読めるようにする
    val containerColor by animateColorAsState(
        targetValue = when {
            task.isCompleted -> MaterialTheme.colorScheme.surfaceVariant
            task.priorityScore >= 10 -> MaterialTheme.colorScheme.errorContainer
            task.priorityScore >= 7 -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "cardColor"
    )

    val dateText = remember(task.deadline) { formatDate(task.deadline) }
    val overdue = remember(task.deadline, task.isCompleted) {
        !task.isCompleted && task.deadline < currentTimeMillis()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    // カード全体のタップ操作（スワイプ削除など）と競合しないよう、タイトルだけをタップ対象にする
                    modifier = Modifier.clickable(onClick = onTitleClick)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "締切: $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                        fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal
                    )
                    if (overdue) {
                        Text(
                            text = "期限切れ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.status == TaskStatus.IN_PROGRESS) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("進行中", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() }
            )
        }

        if (item.subTasks.isNotEmpty()) {
            SubTaskSection(item = item, onSubTaskToggle = onSubTaskToggle)
        }
    }
}

@Composable
private fun SubTaskSection(
    item: TaskWithSubTasks,
    onSubTaskToggle: (SubTask) -> Unit
) {
    val doneCount = item.subTasks.count { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        LinearProgressIndicator(
            progress = { item.progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "サブタスク $doneCount / ${item.subTasks.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        item.subTasks.forEach { subTask ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = subTask.isCompleted,
                    onCheckedChange = { onSubTaskToggle(subTask) }
                )
                Text(
                    text = subTask.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null
                )
            }
        }
    }
}
```

**注記1:** `currentTimeMillis()` はcommonMainに標準では無いため、同ファイル末尾に追加する:

```kotlin
private fun currentTimeMillis(): Long =
    kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
```

（`kotlinx-datetime` に `Clock.System` があるため、Task 6で追加した依存で足りる）

**注記2:** `import myapplication.shared.generated.resources.Res` 等のパッケージ名は、Task 1 Step 3 で設定した `packageOfResClass` の値から Compose Multiplatform Resourcesプラグインが自動生成する。もし実際の生成パッケージ名が異なる場合（ビルドエラーで判明する）、生成された実際のパッケージ名に置き換えること（この修正は許可された最小限の逸脱であり、`CharacterStatusHeader`のシグネチャ自体は変えないこと）。

- [ ] **Step 2: `currentTimeMillis()` ヘルパーを追加する**

上記注記1の内容をファイル末尾に追加する。

- [ ] **Step 3: ビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL（`Res.drawable.*` のimportパスが実際の生成パッケージと違う場合は注記2に従い修正すること）

- [ ] **Step 4: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/TaskListScreen.kt
git commit -m "feat: port TaskListScreen to :shared, minus calendar integration"
```

---

## Task 8: App.kt（ルートComposable・ナビゲーション配線）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/App.kt`

**Interfaces:**
- Consumes: `AppState`（Task 4）, `TaskListScreen`（Task 7）, `AddTaskScreen`（Task 6）, `CategoryManagerScreen`（Task 5）, `SharedAppTheme`（Task 2）, `DatabaseDriverFactory`（既存）, `TaskRepository`（既存）
- Produces: `@Composable fun App(driverFactory: DatabaseDriverFactory)` — Android側（Task 9で軽く触れるのみ、配線自体はこのプランのスコープ外）・iOS側（Task 9でXcodeから呼ぶ）のホストアプリがこれを呼ぶ

現行の `MainActivity.kt` の `NavHost` 配線部分（カレンダー連携・通知権限リクエストを除く）を移植する。`AppState` の生成・スコープ管理はこの `App` コンポーザブル内で行う（`rememberCoroutineScope` を使い、Composeのライフサイクルに追従させる）。

- [ ] **Step 1: `App.kt` を作成する**

```kotlin
package com.example.myapplication.shared.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.shared.TaskRepository
import com.example.myapplication.shared.db.DatabaseDriverFactory

private const val ROUTE_LIST = "list"
private const val ROUTE_ADD = "add"
private const val ROUTE_CATEGORIES = "categories"

/**
 * Compose Multiplatformアプリのルート。Android/iOS双方のホストから呼ばれる。
 * Android版 `MainActivity` の `NavHost` 配線から、通知権限リクエスト（Android専用）と
 * カレンダー連携関連のパラメータを除いた版。
 */
@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    SharedAppTheme {
        val scope = rememberCoroutineScope()
        val appState = remember(driverFactory) {
            AppState(TaskRepository(driverFactory), scope)
        }

        val navController = rememberNavController()
        val tasks by appState.allTasks.collectAsState()
        val categories by appState.categories.collectAsState()

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(Unit) {
            appState.messages.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }

        NavHost(navController = navController, startDestination = ROUTE_LIST) {
            composable(ROUTE_LIST) {
                TaskListScreen(
                    tasks = tasks,
                    categories = categories,
                    snackbarHostState = snackbarHostState,
                    onAddTask = {
                        // 連打で "add" が積み重なるのを防ぐ
                        navController.navigate(ROUTE_ADD) { launchSingleTop = true }
                    },
                    onManageCategories = {
                        navController.navigate(ROUTE_CATEGORIES) { launchSingleTop = true }
                    },
                    onTaskToggle = appState::toggleCompleted,
                    onSubTaskToggle = appState::toggleSubTaskCompleted,
                    onTaskDelete = appState::deleteTask,
                    onUndoDelete = appState::undoDelete,
                    onTaskRename = appState::renameTask
                )
            }
            composable(ROUTE_ADD) {
                AddTaskScreen(
                    categories = categories,
                    onAddCategory = appState::addCategory,
                    onTaskAdded = { input: NewTaskInput ->
                        appState.addTask(
                            title = input.title,
                            deadline = input.deadline,
                            importance = input.importance,
                            urgency = input.urgency,
                            categoryId = input.categoryId,
                            subTaskTitles = input.subTaskTitles
                        )
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CATEGORIES) {
                CategoryManagerScreen(
                    categories = categories,
                    snackbarHostState = snackbarHostState,
                    onAdd = appState::addCategory,
                    onRename = appState::renameCategory,
                    onDelete = appState::deleteCategory,
                    countTasksIn = appState::countTasksInCategory,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
```

`androidx.compose.runtime.collectAsState`（`StateFlow`をComposeの`State`に変換する拡張関数）はTask 1で追加済みの`compose.runtime`依存に含まれているはずだが、解決できない場合は`androidx.lifecycle:lifecycle-runtime-compose`のマルチプラットフォーム版の追加を検討し、この場で報告すること。

**importパスの注意:** `androidx.navigation.compose.*`（`NavHost`/`composable`/`rememberNavController`）は、Maven座標が`org.jetbrains.androidx.navigation:navigation-compose`であるにもかかわらず、パッケージ名自体は既存のAndroidX Composeと同じ`androidx.navigation.compose`である想定（JetBrainsがAndroidXのソースをマルチプラットフォーム向けにミラーする際の一般的な方式に基づく）。もしビルドエラーでこれが誤りだと判明した場合（例: `org.jetbrains.androidx.navigation.compose`が正しいパッケージだった等）、実際に解決できるimportパスに置き換えること。これは許可された最小限の逸脱であり、`App`関数のシグネチャ自体は変えないこと。

- [ ] **Step 2: ビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/App.kt
git commit -m "feat: add App() root composable wiring navigation for shared UI"
```

---

## Task 9: `:iosApp` セットアップ手順とSwiftラッパー

**Files:**
- Create: `iosApp/iosApp/iOSApp.swift`
- Create: `iosApp/iosApp/ContentView.swift`
- Modify: `SETUP.md`

**Interfaces:**
- Consumes: `App(driverFactory)`（Task 8）, `IosDatabaseDriverFactory`（既存）

Xcodeプロジェクト自体（`.xcodeproj/project.pbxproj`）はバイナリに近いplist形式で、手書きでの生成・検証はできない。そのため、Xcodeプロジェクトの作成手順は `SETUP.md` にドキュメントとして追加し、実際の作成・ビルド・シミュレータ実行はユーザーがMac環境で行う。Swiftのラッパーコード自体は素のテキストファイルなので用意する。

- [ ] **Step 1: `SETUP.md` に「iOSアプリのセットアップ」節を追加する**

`SETUP.md` の末尾に追加:

```markdown

## iOSアプリのセットアップ（Mac環境で実施）

`:shared` モジュールをiOSシミュレータで動かすための `iosApp` Xcodeプロジェクトは、
Windows環境では作成・ビルドできない（Kotlin/NativeのiOSターゲットも同様）。
以下の手順はMacで実施すること。

### 前提

- Xcode（最新の安定版）
- このリポジトリを `git pull` 済みであること（`iosApp/iosApp/iOSApp.swift` と
  `iosApp/iosApp/ContentView.swift` が含まれている）

### 手順

1. `./gradlew :shared:compileKotlinIosSimulatorArm64` を実行し、`:shared` がiOSシミュレータ向けに
   コンパイルできることを確認する（Windowsでは実行できなかった検証）。エラーが出た場合はここで解消する。
2. Xcodeで「Create a new Xcode project」→「iOS」→「App」を選択する。
   - Product Name: `iosApp`
   - Interface: SwiftUI
   - Language: Swift
   - 保存先: このリポジトリの `iosApp/` 直下（既存の `iosApp/iosApp/*.swift` を上書きしないよう、
     プロジェクト作成後に生成された `ContentView.swift`/`iOSApp.swift`（またはApp名と同名のファイル）を
     このリポジトリのファイルで置き換える）
3. `:shared` が生成するフレームワークをXcodeプロジェクトにリンクする。
   Kotlin Multiplatformの公式ドキュメント（"Connect the framework to your iOS project"）に従い、
   ビルドフェーズに `:shared` のGradleタスクを呼ぶRun Scriptを追加する方法が最も簡単
   （`kotlinlang.org/docs/multiplatform/multiplatform-integrate-in-existing-app.html` 等を参照）。
4. Xcodeでシミュレータを選択してビルド・実行し、タスク一覧画面が表示されることを確認する。
5. 確認できたら `git add iosApp/ && git commit` でXcodeプロジェクトファイル一式をコミットする
   （`.xcodeproj/project.pbxproj` を含む。これはWindows側では生成できないためMacでのコミットが必須）。

### 既知の制約

- `IosDatabaseDriverFactory`（`shared/src/iosMain/.../DatabaseDriverFactory.ios.kt`）が
  `PRAGMA foreign_keys=ON` を明示していない点はKMP Phase 1 Aの最終レビューで指摘済み。
  `NativeSqliteDriver` のデフォルト挙動を確認し、外部キー制約（`ON DELETE SET NULL`/`CASCADE`）が
  期待通り効くか確認すること。効いていない場合は `androidMain` 版と同様に明示的な
  `PRAGMA foreign_keys=ON;` の実行が必要。
```

- [ ] **Step 2: `iosApp/iosApp/iOSApp.swift` を作成する**

```swift
import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

- [ ] **Step 3: `iosApp/iosApp/ContentView.swift` を作成する**

```swift
import UIKit
import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // App(driverFactory:) はKotlin側の @Composable。
        // IosDatabaseDriverFactory は shared/src/iosMain の実装。
        MainViewControllerKt.MainViewController(driverFactory: IosDatabaseDriverFactory())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
    }
}
```

`MainViewControllerKt.MainViewController(driverFactory:)` は、`App(driverFactory)` をiOS側から
`UIViewController` として呼び出すためのブリッジ関数。次のStepで `shared` 側に用意する。

- [ ] **Step 4: iOS向けブリッジ関数を `:shared` に追加する**

`shared/src/iosMain/kotlin/com/example/myapplication/shared/ui/MainViewController.kt` を作成する:

```kotlin
package com.example.myapplication.shared.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.example.myapplication.shared.db.DatabaseDriverFactory
import platform.UIKit.UIViewController

/** iOS側（Swift）から `App(driverFactory)` を呼び出すためのブリッジ関数。 */
fun MainViewController(driverFactory: DatabaseDriverFactory): UIViewController =
    ComposeUIViewController { App(driverFactory) }
```

このファイルはiosMainに属するため、Windowsではコンパイル確認できない（Global Constraints参照）。
構文レベルの誤りが無いよう慎重に記述すること。

- [ ] **Step 5: コミット**

```bash
git add SETUP.md iosApp/iosApp/iOSApp.swift iosApp/iosApp/ContentView.swift \
  shared/src/iosMain/kotlin/com/example/myapplication/shared/ui/MainViewController.kt
git commit -m "docs: add iOS app setup instructions and Swift/Kotlin bridge for shared UI"
```

---

## Task 10: 最終確認（コンパイルのみ・Mac側作業の引き継ぎ）

**Files:** なし（確認のみ）

- [ ] **Step 1: `:shared` のAndroidターゲットのビルドを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: `:shared` の全テストを実行する**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS（全件。Task 4で追加した `AppStateTest` を含む）

- [ ] **Step 3: 既存 `:app` に回帰が無いことを確認する**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL（`:app` は `:shared` に依存していないため無回帰のはず）

- [ ] **Step 4: Mac側で必要な作業を記録する**

このプランの完了時点で、以下はMac環境でのユーザー自身の作業が必要（Windowsでは実行不可能なため）:

1. `./gradlew :shared:compileKotlinIosSimulatorArm64` — iOSターゲットのコンパイル確認（Task 7の `Res.drawable.*` importパスがずれている可能性を含め、初めて確認できる）
2. `SETUP.md`「iOSアプリのセットアップ」節に従ったXcodeプロジェクトの作成・実行確認
3. `IosDatabaseDriverFactory` の外部キー制約（`PRAGMA foreign_keys`）挙動の確認
4. Android実機/エミュレータでの `App()` の目視確認（このプランではコンパイル確認のみで、実際にタスク追加・削除・カテゴリ管理が動くことは未確認）

## 完了確認（全タスク後）

- [ ] `./gradlew :shared:testDebugUnitTest` が全件成功する
- [ ] `./gradlew :shared:assembleDebug` が成功する
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` が成功する（無回帰）
- [ ] Task 10 Step 4に記載のMac側作業を、ユーザーが次回Mac環境で実施する
