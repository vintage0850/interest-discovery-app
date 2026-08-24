# KMP共有モジュール（:shared）とタスクCRUDデータ層 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:shared` Kotlin Multiplatformモジュールを新設し、SQLDelightによるDBスキーマ・ドメインモデル（Task/SubTask/Category/TaskStatus）・Repository（CRUD）をcommonMainに実装する。iOS実行確認を含まない、Windows上で完結して検証可能な範囲（このプランのスコープ）。

**Architecture:** 既存の `:app`（Android, Room）には一切手を入れない。新規 `:shared` モジュールに、現行 `app/src/main/java/com/example/myapplication/data/` 配下のドメインモデル・Repositoryロジックを、Room依存を除いてSQLDelight版として書き直す。DBドライバは `DatabaseDriverFactory` インターフェースをcommonMainに置き、Android実装（`AndroidSqliteDriver`）とiOS実装（`NativeSqliteDriver`）をそれぞれ用意する。iOS実装はこのプランの中で**書くが、コンパイル確認はできない**（Kotlin/NativeのiOSターゲットはmacOSホストでしかコンパイルできないため）。テストは、ドライバ不要な純粋ロジックは `commonTest`、DBを使うRepositoryロジックは `androidUnitTest`（ホストJVM上、SQLDelightのJDBCドライバ`sqlite-driver`を使用、エミュレータ/実機不要）で書く。

**Tech Stack:** Kotlin Multiplatform 2.2.20, SQLDelight 2.3.2, kotlinx-coroutines 1.10.2, JUnit4（`commonTest`/`androidUnitTest`）

**Spec:** `docs/superpowers/specs/2026-08-24-kmp-ios-migration-phase1-design.md`

**スコープ外（別プランで扱う）:** Compose MultiplatformによるUI移植、`:iosApp` Xcodeプロジェクト、iOSシミュレータでの実行確認。これらはこのプランの成果物（`:shared`のドメイン層）の上に構築する後続プラン。

## Global Constraints

- 新規コードのコメントは既存コードの慣習に合わせ日本語
- Red → Green の順でテストを書く
- 各タスクの最後に必ずコミットする（1タスク=1コミット、テストが通った状態でコミットする）
- iOSターゲット（iosArm64/iosSimulatorArm64/iosX64）のコンパイルはこのセッション（Windows）では実行できない。iosMainのコードは記述するが、`./gradlew :shared:compileKotlinIosArm64` 等の実行・確認はスコープ外とし、各タスクの検証は `androidTarget` 関連タスク（`:shared:testDebugUnitTest` 等）のみで行う
- `:app` は今回のプランで一切変更しない（`app/` 配下のファイルは対象外）
- ルートの `gradle/libs.versions.toml` と `settings.gradle.kts` はモジュール追加に必要な範囲でのみ変更する

---

## Task 1: Kotlin を 2.2.20 に上げ、既存 :app が壊れていないことを確認する

**Files:**
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: Kotlin 2.2.20（以降の全タスクの前提）

Compose Multiplatformの公式ドキュメントは、iOS/webなど変化の速いターゲットを含むプロジェクトでは Kotlin 2.2.20 以降を推奨している（2.2.10のままだと将来 :shared に iOS ターゲットを追加した際に問題が起きうる）。:shared 追加に先立ち、影響範囲を最小に保つため先に上げて :app の回帰が無いことを確認する。

- [ ] **Step 1: `gradle/libs.versions.toml` の `kotlin` を上げる**

`[versions]` の `kotlin = "2.2.10"` を次のように変更:

```toml
kotlin = "2.2.20"
```

- [ ] **Step 2: 既存 :app のビルド・テストが壊れていないことを確認する**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL（Kotlinバージョン変更のみで、他の変更は無いため既存の挙動は変わらない想定）

- [ ] **Step 3: コミット**

```bash
git add gradle/libs.versions.toml
git commit -m "chore: bump Kotlin to 2.2.20 for upcoming KMP shared module"
```

---

## Task 2: `:shared` モジュールの雛形を作成する

**Files:**
- Create: `shared/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/Placeholder.kt`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: `:shared` モジュール（androidTarget + iosArm64/iosSimulatorArm64/iosX64、空のcommonMain）。以降の全タスクがこの上に実装を追加する

- [ ] **Step 1: `gradle/libs.versions.toml` にKMP関連のバージョン・プラグイン・ライブラリを追加する**

`[versions]` に追加:

```toml
sqldelight = "2.3.2"
kotlinxCoroutinesCore = "1.10.2"
```

`[libraries]` に追加:

```toml
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutinesCore" }
sqldelight-coroutines-extensions = { group = "app.cash.sqldelight", name = "coroutines-extensions", version.ref = "sqldelight" }
sqldelight-android-driver = { group = "app.cash.sqldelight", name = "android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { group = "app.cash.sqldelight", name = "native-driver", version.ref = "sqldelight" }
# androidUnitTest（ホストJVM）でDBを使ったテストを実機/エミュレータ無しで動かすためのJDBCドライバ
sqldelight-sqlite-driver = { group = "app.cash.sqldelight", name = "sqlite-driver", version.ref = "sqldelight" }
```

`[plugins]` に追加:

```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 2: `settings.gradle.kts` に `:shared` を登録する**

`include(":app")` の直後に追加:

```kotlin
include(":shared")
```

- [ ] **Step 3: `shared/build.gradle.kts` を作成する**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.coroutines.extensions)
        }
        commonTest.dependencies {
            implementation(libs.junit)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.example.myapplication.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("SharedDatabase") {
            packageName.set("com.example.myapplication.shared.db")
        }
    }
}
```

- [ ] **Step 4: 空のcommonMainファイルを作成する（sourceSetを成立させるため）**

`shared/src/commonMain/kotlin/com/example/myapplication/shared/Placeholder.kt`:

```kotlin
package com.example.myapplication.shared

/**
 * :shared モジュールの雛形確認用。Task 4 でドメインモデルを追加したら削除する。
 */
internal const val SHARED_MODULE_PLACEHOLDER = true
```

- [ ] **Step 5: androidTargetのビルドが通ることを確認する（iOSターゲットはこのセッションでは検証できない）**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL（androidTargetのコンパイルが通ることを確認。iosArm64等のタスクはこのコマンドには含まれない）

- [ ] **Step 6: コミット**

```bash
git add gradle/libs.versions.toml settings.gradle.kts shared/build.gradle.kts \
  shared/src/commonMain/kotlin/com/example/myapplication/shared/Placeholder.kt
git commit -m "feat: scaffold :shared KMP module (androidTarget + iOS targets, empty commonMain)"
```

---

## Task 3: SQLDelightスキーマ（Category / Task / SubTask）

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/example/myapplication/shared/db/Category.sq`
- Create: `shared/src/commonMain/sqldelight/com/example/myapplication/shared/db/Task.sq`
- Create: `shared/src/commonMain/sqldelight/com/example/myapplication/shared/db/SubTask.sq`

**Interfaces:**
- Consumes: なし
- Produces: SQLDelightが生成する `SharedDatabase`（パッケージ `com.example.myapplication.shared.db`）、`CategoryQueries`/`TaskQueries`/`SubTaskQueries` — Task 5・6 が使う

現行Room v5スキーマ（`app/src/main/java/com/example/myapplication/data/AppDatabase.kt`）を踏襲する。`notificationTime`・`calendarEventId`・`status` 列は仕様通り残すが、Phase 1のRepositoryでは使わない。

- [ ] **Step 1: `Category.sq` を作成する**

```sql
CREATE TABLE Category (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    sortOrder INTEGER NOT NULL
);

selectAll:
SELECT * FROM Category ORDER BY sortOrder ASC, id ASC;

selectByName:
SELECT * FROM Category WHERE name = ?;

-- リネーム先の名前が「自分以外の」カテゴリと衝突していないか調べるためのクエリ
selectConflictingName:
SELECT * FROM Category WHERE name = :name AND id != :excludingId;

selectMaxSortOrder:
SELECT MAX(sortOrder) FROM Category;

insert:
INSERT INTO Category(name, sortOrder) VALUES (?, ?);

updateName:
UPDATE Category SET name = ? WHERE id = ?;

delete:
DELETE FROM Category WHERE id = ?;
```

- [ ] **Step 2: `Task.sq` を作成する**

```sql
CREATE TABLE Task (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    deadline INTEGER NOT NULL,
    importance INTEGER NOT NULL,
    urgency INTEGER NOT NULL,
    categoryId INTEGER REFERENCES Category(id) ON DELETE SET NULL,
    isCompleted INTEGER AS Boolean NOT NULL DEFAULT 0,
    progress INTEGER NOT NULL DEFAULT 0,
    notificationTime INTEGER,
    calendarEventId TEXT,
    createdAt INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'TODO'
);

selectAll:
SELECT * FROM Task ORDER BY isCompleted ASC, (importance * 3 + urgency) DESC, deadline ASC;

selectById:
SELECT * FROM Task WHERE id = ?;

insert:
INSERT INTO Task(title, deadline, importance, urgency, categoryId, isCompleted, progress, notificationTime, calendarEventId, createdAt, status)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

lastInsertRowId:
SELECT last_insert_rowid();

update:
UPDATE Task
SET title = :title, deadline = :deadline, importance = :importance, urgency = :urgency,
    categoryId = :categoryId, isCompleted = :isCompleted, progress = :progress
WHERE id = :id;

delete:
DELETE FROM Task WHERE id = ?;

countByCategoryId:
SELECT COUNT(*) FROM Task WHERE categoryId = ?;
```

- [ ] **Step 3: `SubTask.sq` を作成する**

```sql
CREATE TABLE SubTask (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    taskId INTEGER NOT NULL REFERENCES Task(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    isCompleted INTEGER AS Boolean NOT NULL DEFAULT 0,
    sortOrder INTEGER NOT NULL DEFAULT 0
);

selectForTask:
SELECT * FROM SubTask WHERE taskId = ? ORDER BY sortOrder ASC;

insert:
INSERT INTO SubTask(taskId, title, isCompleted, sortOrder) VALUES (?, ?, ?, ?);

update:
UPDATE SubTask SET title = :title, isCompleted = :isCompleted, sortOrder = :sortOrder WHERE id = :id;

delete:
DELETE FROM SubTask WHERE id = ?;
```

- [ ] **Step 4: 生成コードのコンパイルを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL（SQLDelightのコード生成タスクが自動的に走り、`CategoryQueries`/`TaskQueries`/`SubTaskQueries` と `SharedDatabase` インターフェースが生成された上でandroidTargetのコンパイルが通る。生成タスク名が不明な場合は `./gradlew :shared:tasks --all | grep -i sqldelight` で確認してよい）

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/sqldelight
git commit -m "feat: add SQLDelight schema for Category/Task/SubTask"
```

---

## Task 4: ドメインモデル（Task / SubTask / Category / TaskStatus）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/TaskStatus.kt`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/Task.kt`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/SubTask.kt`
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/Category.kt`
- Create: `shared/src/commonTest/kotlin/com/example/myapplication/shared/TaskTest.kt`
- Create: `shared/src/commonTest/kotlin/com/example/myapplication/shared/TaskWithSubTasksTest.kt`
- Delete: `shared/src/commonMain/kotlin/com/example/myapplication/shared/Placeholder.kt`

**Interfaces:**
- Consumes: なし
- Produces: `Task`, `SubTask`, `Category`, `TaskWithSubTasks`, `TaskStatus`（DB非依存のプレーンな値クラス）— Task 6 の Repository が使う

現行 `app/src/main/java/com/example/myapplication/data/Task.kt` / `SubTask.kt` / `Category.kt` からRoomアノテーションを除いた版。ロジック（`priorityScore`・`isOverdue`・`progress`）はそのまま踏襲する。

- [ ] **Step 1: 失敗するテストを書く（`Task.priorityScore` / `isOverdue`）**

`shared/src/commonTest/kotlin/com/example/myapplication/shared/TaskTest.kt`:

```kotlin
package com.example.myapplication.shared

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
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.TaskTest"`
Expected: FAIL（`Task` クラスが存在せずコンパイルエラー）

- [ ] **Step 3: `TaskStatus.kt` を作成する**

```kotlin
package com.example.myapplication.shared

/** タスクの進行状態。「完了したまま進行中」という状態は持たせない。 */
enum class TaskStatus { TODO, IN_PROGRESS }
```

- [ ] **Step 4: `Task.kt` を作成する**

```kotlin
package com.example.myapplication.shared

/**
 * Room版（`app/src/main/java/com/example/myapplication/data/Task.kt`）からDB依存を除いた
 * プレーンな値クラス。DBとの変換は Task 6 の Repository が担う。
 */
data class Task(
    val id: Int = 0,
    val title: String,
    val deadline: Long,
    val importance: Int, // 1-3
    val urgency: Int,    // 1-3
    /** 所属カテゴリ。null は「未分類」（カテゴリが削除されたタスク）。 */
    val categoryId: Int? = null,
    val isCompleted: Boolean = false,
    val progress: Int = 0, // 0-100
    val createdAt: Long = 0L,
    val status: TaskStatus = TaskStatus.TODO
) {
    /** 並び順・色分けに使う優先度スコア。4（低）〜12（高）。 */
    val priorityScore: Int
        get() = importance * 3 + urgency

    /** 未完了かつ締切を過ぎているか。 */
    fun isOverdue(now: Long): Boolean = !isCompleted && deadline < now
}
```

- [ ] **Step 5: テストを実行して成功を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.TaskTest"`
Expected: PASS（全件）

- [ ] **Step 6: 失敗するテストを書く（`TaskWithSubTasks.progress`）**

`shared/src/commonTest/kotlin/com/example/myapplication/shared/TaskWithSubTasksTest.kt`:

```kotlin
package com.example.myapplication.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskWithSubTasksTest {

    private fun task(isCompleted: Boolean = false, progress: Int = 0) = Task(
        title = "テストタスク",
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        isCompleted = isCompleted,
        progress = progress
    )

    private fun subTask(isCompleted: Boolean) =
        SubTask(taskId = 1, title = "サブ", isCompleted = isCompleted)

    @Test
    fun `サブタスクがあればその完了率を返す`() {
        val result = TaskWithSubTasks(
            task = task(),
            subTasks = listOf(subTask(true), subTask(true), subTask(false), subTask(false))
        )
        assertEquals(50, result.progress)
    }

    @Test
    fun `サブタスクが無く完了済みなら100`() {
        val result = TaskWithSubTasks(task = task(isCompleted = true), subTasks = emptyList())
        assertEquals(100, result.progress)
    }

    @Test
    fun `サブタスクが無く未完了ならタスク自身のprogress`() {
        val result = TaskWithSubTasks(task = task(progress = 30), subTasks = emptyList())
        assertEquals(30, result.progress)
    }
}
```

- [ ] **Step 7: テストを実行して失敗を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.TaskWithSubTasksTest"`
Expected: FAIL（`SubTask`/`TaskWithSubTasks` が存在せずコンパイルエラー）

- [ ] **Step 8: `SubTask.kt` を作成する**

```kotlin
package com.example.myapplication.shared

data class SubTask(
    val id: Int = 0,
    val taskId: Int,
    val title: String,
    val isCompleted: Boolean = false,
    /** 表示順。追加した順に 0, 1, 2... を振る。 */
    val sortOrder: Int = 0
)

/** タスクと、それにぶら下がるサブタスクをまとめて扱うための組。 */
data class TaskWithSubTasks(
    val task: Task,
    val subTasks: List<SubTask> = emptyList()
) {
    /** サブタスクがあればその完了率、なければタスク自身の進捗を返す。 */
    val progress: Int
        get() = when {
            subTasks.isNotEmpty() -> subTasks.count { it.isCompleted } * 100 / subTasks.size
            task.isCompleted -> 100
            else -> task.progress
        }
}
```

- [ ] **Step 9: `Category.kt` を作成する**

```kotlin
package com.example.myapplication.shared

/**
 * ユーザーが自由に作れるタスクの分類枠。
 * 同じ名前が並ぶと選び分けられないので name には一意制約を張っている（DB側）。
 */
data class Category(
    val id: Int = 0,
    val name: String,
    /** タブや選択肢に並ぶ順。追加した順に 0, 1, 2... を振る。 */
    val sortOrder: Int = 0
) {
    companion object {
        /** カテゴリ名の上限。タブに収まる程度に抑える。 */
        const val MAX_NAME_LENGTH = 12

        /** 初回起動時に用意しておく枠。ユーザーは自由に消して構わない。 */
        val DEFAULTS = listOf("スキル", "提出", "やりたい")

        /** カテゴリが消されたタスク（categoryId が null）の表示名。 */
        const val UNCATEGORIZED_LABEL = "未分類"
    }
}
```

- [ ] **Step 10: `Placeholder.kt` を削除する**

```bash
git rm shared/src/commonMain/kotlin/com/example/myapplication/shared/Placeholder.kt
```

- [ ] **Step 11: テストを実行して成功を確認する**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS（全件）

- [ ] **Step 12: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared \
  shared/src/commonTest/kotlin/com/example/myapplication/shared
git commit -m "feat: add Task/SubTask/Category/TaskStatus domain models to :shared"
```

---

## Task 5: DatabaseDriverFactory（Android実装 + iOS実装）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/db/DatabaseDriverFactory.kt`
- Create: `shared/src/androidMain/kotlin/com/example/myapplication/shared/db/DatabaseDriverFactory.android.kt`
- Create: `shared/src/iosMain/kotlin/com/example/myapplication/shared/db/DatabaseDriverFactory.ios.kt`

**Interfaces:**
- Consumes: SQLDelightが生成する `SharedDatabase.Schema`（Task 3）
- Produces: `interface DatabaseDriverFactory { fun createDriver(): SqlDriver }`, `AndroidDatabaseDriverFactory(context: Context)`, `IosDatabaseDriverFactory()` — Task 6 のRepositoryテスト、および将来のAndroid/iOSアプリ側DI(このプランのスコープ外)が使う

`expect`/`actual` はAndroid実装が `Context` を要求しシグネチャが揃わないため使わず、インターフェース＋プラットフォーム別実装クラスの方式を取る（KMPでの一般的な回避パターン）。

- [ ] **Step 1: `commonMain` にインターフェースを定義する**

```kotlin
package com.example.myapplication.shared.db

import app.cash.sqldelight.db.SqlDriver

/**
 * プラットフォームごとのSQLiteドライバ生成を隠蔽する。
 * Android実装は Context を要求しシグネチャが揃わないため、expect/actual ではなく
 * インターフェース + プラットフォーム別実装クラスの方式を取る。
 */
interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

- [ ] **Step 2: `androidMain` に実装する**

```kotlin
package com.example.myapplication.shared.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = SharedDatabase.Schema,
            context = context,
            name = "shared.db",
            callback = object : AndroidSqliteDriver.Callback(SharedDatabase.Schema) {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // ON DELETE CASCADE / SET NULL を機能させるために必須
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }
            }
        )
}
```

- [ ] **Step 3: `iosMain` に実装する（このセッションではコンパイル確認できない）**

```kotlin
package com.example.myapplication.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun createDriver(): SqlDriver =
        NativeSqliteDriver(SharedDatabase.Schema, "shared.db")
}
```

（`NativeSqliteDriver` はデフォルトで外部キー制約を有効化する設定を持つため、Android実装のような明示的な `PRAGMA foreign_keys=ON` は不要。Mac環境での実装時に `app.cash.sqldelight:native-driver` のドキュメントで要確認。）

- [ ] **Step 4: androidTargetのビルドが通ることを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/db \
  shared/src/androidMain/kotlin/com/example/myapplication/shared/db \
  shared/src/iosMain/kotlin/com/example/myapplication/shared/db
git commit -m "feat: add DatabaseDriverFactory (Android + iOS implementations)"
```

---

## Task 6: TaskRepository（CRUD）

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/myapplication/shared/TaskRepository.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/example/myapplication/shared/TaskRepositoryTest.kt`

**Interfaces:**
- Consumes: `SharedDatabase`/`TaskQueries`/`SubTaskQueries`/`CategoryQueries`（Task 3）, `Task`/`SubTask`/`Category`/`TaskStatus`（Task 4）, `DatabaseDriverFactory`（Task 5）
- Produces: `class TaskRepository(driverFactory: DatabaseDriverFactory)` with `allTasks: Flow<List<TaskWithSubTasks>>`, `categories: Flow<List<Category>>`, `suspend fun insert(task: Task): Int`, `suspend fun update(task: Task)`, `suspend fun delete(task: Task)`, `suspend fun getTaskById(id: Int): Task?`, `suspend fun insertSubTasks(subTasks: List<SubTask>)`, `suspend fun updateSubTask(subTask: SubTask)`, `suspend fun deleteSubTask(subTask: SubTask)`, `suspend fun getSubTasksFor(taskId: Int): List<SubTask>`, `suspend fun addCategory(name: String): Boolean`, `suspend fun renameCategory(category: Category, newName: String): Boolean`, `suspend fun deleteCategory(category: Category)`, `suspend fun countTasksInCategory(categoryId: Int): Int` — 後続プラン（UI移植）が使う

現行 `app/src/main/java/com/example/myapplication/data/TaskRepository.kt` のうち、カレンダー連携・通知関連（`updateCalendarEventId`/`updateStatus`/`getTopEligibleTaskForNotification`/`NotifiedSlot`関連）はPhase 1のスコープ外のため移植しない。

androidUnitTest（ホストJVM）でテストするため、テスト用に `app.cash.sqldelight:sqlite-driver`（JDBC、インメモリ）で `SqlDriver` を作る小さなヘルパーをテストファイル内に用意する。

- [ ] **Step 1: 失敗するテストを書く（Task CRUD）**

`shared/src/androidUnitTest/kotlin/com/example/myapplication/shared/TaskRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Step 2: テストを実行して失敗を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.TaskRepositoryTest"`
Expected: FAIL（`TaskRepository` が存在せずコンパイルエラー）

- [ ] **Step 3: `TaskRepository.kt` を実装する**

```kotlin
package com.example.myapplication.shared

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.db.SharedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room版（`app/.../data/TaskRepository.kt`）のうち、カレンダー連携・通知関連を除いた
 * CRUD部分をSQLDelightで書き直したもの。Phase 1のUI（後続プラン）が使う。
 */
class TaskRepository(driverFactory: DatabaseDriverFactory) {
    private val database = SharedDatabase(driverFactory.createDriver())
    private val taskQueries = database.taskQueries
    private val subTaskQueries = database.subTaskQueries
    private val categoryQueries = database.categoryQueries

    val allTasks: Flow<List<TaskWithSubTasks>> =
        taskQueries.selectAll(::toTask)
            .asFlow()
            .mapToList(Dispatchers.Default)
            // サブタスクは1件ずつ引くとN+1になるため、タスク一覧の変化のたびに
            // まとめて引き直す。件数が多くない前提（Room版も同様の設計）。
            .map { tasks ->
                tasks.map { task ->
                    TaskWithSubTasks(
                        task = task,
                        subTasks = subTaskQueries.selectForTask(task.id.toLong(), ::toSubTask)
                            .executeAsList()
                    )
                }
            }

    val categories: Flow<List<Category>> =
        categoryQueries.selectAll(::toCategory).asFlow().mapToList(Dispatchers.Default)

    suspend fun insert(task: Task): Int = withContext(Dispatchers.Default) {
        taskQueries.transactionWithResult {
            taskQueries.insert(
                title = task.title,
                deadline = task.deadline,
                importance = task.importance.toLong(),
                urgency = task.urgency.toLong(),
                categoryId = task.categoryId?.toLong(),
                isCompleted = task.isCompleted,
                progress = task.progress.toLong(),
                notificationTime = null,
                calendarEventId = null,
                createdAt = task.createdAt,
                status = task.status.name
            )
            taskQueries.lastInsertRowId().executeAsOne().toInt()
        }
    }

    suspend fun update(task: Task) = withContext(Dispatchers.Default) {
        taskQueries.update(
            title = task.title,
            deadline = task.deadline,
            importance = task.importance.toLong(),
            urgency = task.urgency.toLong(),
            categoryId = task.categoryId?.toLong(),
            isCompleted = task.isCompleted,
            progress = task.progress.toLong(),
            id = task.id.toLong()
        )
    }

    suspend fun delete(task: Task) = withContext(Dispatchers.Default) {
        taskQueries.delete(task.id.toLong())
    }

    suspend fun getTaskById(id: Int): Task? = withContext(Dispatchers.Default) {
        taskQueries.selectById(id.toLong(), ::toTask).executeAsOneOrNull()
    }

    suspend fun insertSubTasks(subTasks: List<SubTask>) = withContext(Dispatchers.Default) {
        subTaskQueries.transaction {
            subTasks.forEach { subTask ->
                subTaskQueries.insert(
                    taskId = subTask.taskId.toLong(),
                    title = subTask.title,
                    isCompleted = subTask.isCompleted,
                    sortOrder = subTask.sortOrder.toLong()
                )
            }
        }
    }

    suspend fun updateSubTask(subTask: SubTask) = withContext(Dispatchers.Default) {
        subTaskQueries.update(
            title = subTask.title,
            isCompleted = subTask.isCompleted,
            sortOrder = subTask.sortOrder.toLong(),
            id = subTask.id.toLong()
        )
    }

    suspend fun deleteSubTask(subTask: SubTask) = withContext(Dispatchers.Default) {
        subTaskQueries.delete(subTask.id.toLong())
    }

    suspend fun getSubTasksFor(taskId: Int): List<SubTask> = withContext(Dispatchers.Default) {
        subTaskQueries.selectForTask(taskId.toLong(), ::toSubTask).executeAsList()
    }

    /**
     * 同名のカテゴリがすでにある場合は追加せず false を返す。
     * `selectMaxSortOrder` はSQLDelightの仕様上、単一カラムの集約結果は
     * ラッパー型ではなく `Long?`（テーブルが空なら null）としてそのまま返る。
     */
    suspend fun addCategory(name: String): Boolean = withContext(Dispatchers.Default) {
        categoryQueries.transactionWithResult {
            if (categoryQueries.selectByName(name).executeAsOneOrNull() != null) {
                false
            } else {
                val nextOrder = (categoryQueries.selectMaxSortOrder().executeAsOne() ?: -1L) + 1
                categoryQueries.insert(name, nextOrder)
                true
            }
        }
    }

    /** リネーム。同名のカテゴリが既にある場合は変更せず false を返す。 */
    suspend fun renameCategory(category: Category, newName: String): Boolean =
        withContext(Dispatchers.Default) {
            categoryQueries.transactionWithResult {
                val conflict = categoryQueries
                    .selectConflictingName(name = newName, excludingId = category.id.toLong())
                    .executeAsOneOrNull()
                if (conflict != null) {
                    false
                } else {
                    categoryQueries.updateName(newName, category.id.toLong())
                    true
                }
            }
        }

    /** 削除。所属していたタスクは消えず「未分類」になる（DB外部キーの ON DELETE SET NULL）。 */
    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.Default) {
        categoryQueries.delete(category.id.toLong())
    }

    suspend fun countTasksInCategory(categoryId: Int): Int = withContext(Dispatchers.Default) {
        taskQueries.countByCategoryId(categoryId.toLong()).executeAsOne().toInt()
    }

    private fun toTask(
        id: Long,
        title: String,
        deadline: Long,
        importance: Long,
        urgency: Long,
        categoryId: Long?,
        isCompleted: Boolean,
        progress: Long,
        notificationTime: Long?,
        calendarEventId: String?,
        createdAt: Long,
        status: String
    ) = Task(
        id = id.toInt(),
        title = title,
        deadline = deadline,
        importance = importance.toInt(),
        urgency = urgency.toInt(),
        categoryId = categoryId?.toInt(),
        isCompleted = isCompleted,
        progress = progress.toInt(),
        createdAt = createdAt,
        status = TaskStatus.valueOf(status)
    )

    private fun toSubTask(
        id: Long,
        taskId: Long,
        title: String,
        isCompleted: Boolean,
        sortOrder: Long
    ) = SubTask(
        id = id.toInt(),
        taskId = taskId.toInt(),
        title = title,
        isCompleted = isCompleted,
        sortOrder = sortOrder.toInt()
    )

    private fun toCategory(id: Long, name: String, sortOrder: Long) =
        Category(id = id.toInt(), name = name, sortOrder = sortOrder.toInt())
}
```

`shared/build.gradle.kts` の `androidUnitTest` に、テストで使う `JdbcSqliteDriver` 用の依存が既に Task 2 で `sqldelight-sqlite-driver` として入っている（`app.cash.sqldelight:sqlite-driver` は `app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver` を提供する）。

- [ ] **Step 4: テストを実行して成功を確認する**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.TaskRepositoryTest"`
Expected: PASS（全件）

- [ ] **Step 5: 全体テストを実行する**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS（`TaskTest`・`TaskWithSubTasksTest`・`TaskRepositoryTest` すべて）

- [ ] **Step 6: コミット**

```bash
git add shared/src/commonMain/kotlin/com/example/myapplication/shared/TaskRepository.kt \
  shared/src/androidUnitTest/kotlin/com/example/myapplication/shared/TaskRepositoryTest.kt
git commit -m "feat: implement TaskRepository CRUD backed by SQLDelight"
```

---

## Task 7: 最終確認（全体テスト・既存 :app の無回帰確認）

**Files:** なし（確認のみ）

- [ ] **Step 1: `:shared` の全テストを実行する**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS（全件）

- [ ] **Step 2: `:shared` のAndroidビルドを確認する**

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 既存 `:app` に回帰が無いことを確認する**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL（`:app` は `:shared` に依存していないため、Kotlinバージョン変更以外の影響は無い想定）

- [ ] **Step 4: iOSターゲットについての既知の制約を記録する**

このタスクの完了時点で、`shared/src/iosMain/` のコードは記述済みだがコンパイル未確認（Windows環境のため）。次のいずれかのタイミングでMac環境での確認が必要:
- `./gradlew :shared:compileKotlinIosSimulatorArm64` が通ること
- SQLDelightの `native-driver` が期待通り `NativeSqliteDriver` を提供すること（Mac側でのバージョン差異が無いか）

これは後続プラン（Compose Multiplatform UI移植 + `:iosApp` Xcodeセットアップ）の前提条件としてそちらのプランに引き継ぐ。

## 完了確認（全タスク後）

- [ ] `./gradlew :shared:testDebugUnitTest` が全件成功する
- [ ] `./gradlew :shared:assembleDebug` が成功する
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug` が成功する（無回帰）
- [ ] Mac環境で `shared/src/iosMain/` のコードがコンパイルできることを確認する（次プラン着手前、ユーザー自身の作業）
