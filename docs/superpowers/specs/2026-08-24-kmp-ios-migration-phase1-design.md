# iOS/Android両対応（KMP移行）Phase 1 — 設計

- 日付: 2026-08-24
- ステータス: 設計判断済み（2026-08-24 / Claude）。ユーザー承認済み。

## 背景・目的

現在のアプリは Kotlin/Jetpack Compose によるAndroid専用アプリだが、iOSでもリリースしたいという要望がある。
Kotlin Multiplatform (KMP) + Compose Multiplatform への移行を通じて、既存のドメインロジック（タスク管理、優先度計算、サブタスク進捗）とUIの大部分を両OSで共有する。

移行は一度に全部やらず、段階的に進める。本設計は最初の到達点（Phase 1）を対象とする。

**Phase 1のゴール:** iOSシミュレータでタスク一覧・CRUDが動くこと。カレンダー連携・通知は含めない。

**シーケンス上の決定事項:** 直前に設計した「サブタスク表示改善・カレンダー手動登録・通知時間手動設定」（`docs/superpowers/plans/2026-08-24-subtask-calendar-notification.md`）は保留する。KMP Phase 1を先に進め、これら3機能は共通化後のコードベースの上で作り直す。

## スコープ

含む:
- `:shared` KMPモジュール新設（commonMain / androidMain / iosMain、androidTarget + iosArm64/iosSimulatorArm64/iosX64）
- ドメインモデル（`Task`, `SubTask`, `Category`, `TaskStatus`）とRepositoryロジックのcommonMain移植
- SQLDelightによるDB定義（現行Room v5相当のスキーマ: tasks, subtasks, categories）
- Compose MultiplatformによるUI共通化（タスク一覧・追加・カテゴリ管理・サブタスクのCRUD）
- 新規 `:iosApp`（Xcodeプロジェクト、`ComposeUIViewController` をホストする最小限のSwiftラッパー）
- iOSシミュレータで上記CRUDが一通り動くこと

含まない（YAGNI、将来Phaseで対応）:
- Googleカレンダー連携（Ktor移行・iOS版OAuth含む）
- 通知（空き時間検知・手動通知時刻）
- `notified_slots` テーブル（通知専用なので今回のスキーマには含めない）
- 既存 `:app`（Android）の変更 — 今回は一切触らない。Room実装のまま並行して動き続ける
- 保留中の3機能（サブタスク表示改善・カレンダー手動登録・通知時間手動設定）
- Xcode実機配信・App Store提出（Apple Developer未登録のため、シミュレータ確認まで）

## モジュール構成

```
MyApplication/
  app/                 既存Androidアプリ（今回無変更）
  shared/              新規 KMPモジュール
    src/
      commonMain/      Task, SubTask, Category, TaskRepository, SQLDelightスキーマ, Compose UI
      androidMain/     SQLDelightドライバのAndroid実装
      iosMain/         SQLDelightドライバのiOS(Native)実装
  iosApp/              新規 Xcodeプロジェクト（Swiftの薄いラッパーのみ）
```

`:app` は今回のPhaseでは `:shared` に依存させない（依存させると即座にAndroid版の動作リスクが生まれるため）。Phase 2で `:app` を `:shared` に依存させ、既存Room実装を退役させる（Room/SQLDelightは両方ともプレーンなSQLiteファイルを使うため、スキーマを合わせれば既存ユーザーのDBファイルをそのまま読める見込みだが、詳細はPhase 2で検証する）。

## データ層（SQLDelight）

現行Room v5スキーマを踏襲する `.sq` ファイルを `shared/src/commonMain/sqldelight/` に作成する。

```sql
CREATE TABLE Category (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    sortOrder INTEGER NOT NULL
);

CREATE TABLE Task (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    deadline INTEGER NOT NULL,
    importance INTEGER NOT NULL,
    urgency INTEGER NOT NULL,
    categoryId INTEGER REFERENCES Category(id) ON DELETE SET NULL,
    isCompleted INTEGER NOT NULL DEFAULT 0,
    progress INTEGER NOT NULL DEFAULT 0,
    notificationTime INTEGER,
    calendarEventId TEXT,
    createdAt INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'TODO'
);

CREATE TABLE SubTask (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    taskId INTEGER NOT NULL REFERENCES Task(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    isCompleted INTEGER NOT NULL DEFAULT 0,
    sortOrder INTEGER NOT NULL DEFAULT 0
);
```

`notificationTime` / `calendarEventId` / `status` は列として残す（将来Phaseでの再マイグレーションを避けるため）が、Phase 1のUI・ロジックでは使わない（常にデフォルト値のまま）。

`TaskRepository` は現行の `app/src/main/java/com/example/myapplication/data/TaskRepository.kt` に近いシグネチャでcommonMainに書き直す。`Flow` ベースのクエリはSQLDelightの `.asFlow().mapToList()` 拡張（`app-cash/sqldelight` の coroutines-extensions）で対応する。

## UI（Compose Multiplatform）

`TaskListScreen` / `AddTaskScreen` / `CategoryManagerScreen` / `CategoryNameDialog` は、カレンダー連携・通知関連のパラメータを除いた縮小版をcommonMainに書き直す。Material3 + Compose Foundationのみで構成されている部分は移植の難度が低い。ナビゲーションは `androidx.navigation.compose` のMultiplatform版を使い、現行の `ROUTE_LIST` / `ROUTE_ADD` / `ROUTE_CATEGORIES` 構造を踏襲する。

`iosApp` 側は `ComposeUIViewController { App() }` を呼ぶだけの最小限のSwiftコードのみとする。

## テスト方針

- ドメインロジック（Repository、優先度計算 `Task.priorityScore`、`TaskWithSubTasks.progress` など）はcommonTestでJUnitベースのテストを書く（Kotlin/Nativeターゲットでも実行される）
- SQLDelightのクエリはin-memoryドライバ（`JdbcSqliteDriver`のin-memory、またはNative側は `NativeSqliteDriver`）でcommonTest実行
- UIはiOSシミュレータでの目視確認とする。このPhaseではCompose UIテストは書かない（既存Androidアプリの慣習に合わせる。既存にもCompose UIテストは無い）

## 未決事項（実装時に確認）

- Compose Multiplatform / SQLDelight の正確なバージョン選定（Kotlin 2.2.10, AGP 9.1.1 との組み合わせ確認）
- `androidx.navigation.compose` のMultiplatform対応バージョンの安定性確認。問題があれば簡易な自前ナビゲーション（sealed classによる画面状態管理）にフォールバックする
- Xcodeプロジェクトの具体的なセットアップ手順（Kotlin/Native フレームワークのXcodeへの組み込み方法。CocoaPods連携 or 直接フレームワーク参照）
