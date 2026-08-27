# My Application 仕様書（SPEC）

## 1. プロジェクト概要・目的

本プロジェクトは、**Android／iOS 両対応を目指すタスク管理アプリ**です。既存の Android アプリ（`app` モジュール）に加え、Kotlin Multiplatform（KMP）による共有モジュール（`shared`）と iOS ホストアプリ（`iosApp`）を新設し、UI とドメインロジックを段階的に共通化しています。

アプリの目的は「やるべきことを優先度順に整理し、締切を見失わないこと」です。各タスクには重要度・緊急度を設定でき、優先度スコアに基づいて自動で並び替えられます。Android 版ではさらに、Google Calendar への締切連携、空き時間検知による「始めさせる」通知など、作業を促す機能を備えています。

## 2. 技術スタック

### 2.1 言語・プラットフォーム

| 項目 | 技術 |
|------|------|
| プログラミング言語 | Kotlin（Android / shared）、Swift（iOS ラッパー） |
| Android フレームワーク | Jetpack Compose、AndroidX |
| マルチプラットフォーム UI | Compose Multiplatform 1.11.1 |
| ビルドシステム | Gradle（Kotlin DSL）|
| Kotlin バージョン | 2.2.20 |
| Android Gradle Plugin | 9.1.1 |
| minSdk / targetSdk | 26 / 36 |

### 2.2 主要ライブラリ

| 用途 | ライブラリ | バージョン（代表的） |
|------|-----------|---------------------|
| Android UI | Compose BOM | 2024.11.00 |
| ナビゲーション（Android） | androidx.navigation:navigation-compose | 2.8.4 |
| ナビゲーション（KMP） | org.jetbrains.androidx.navigation:navigation-compose | 2.9.2 |
| Android ローカル DB | Room | 2.7.0-alpha11 |
| 共有ローカル DB | SQLDelight | 2.3.2 |
| 非同期処理 | kotlinx-coroutines | 1.10.2 |
| 日時処理（KMP） | kotlinx-datetime | 0.7.1 |
| バックグラウンド処理 | WorkManager | 2.10.0 |
| Google OAuth | Play Services Auth | 21.6.0 |
| HTTP / REST | Retrofit + OkHttp + kotlinx.serialization | 2.12.0 / 4.12.0 / 1.9.0 |
| 画像リソース（KMP） | Compose Multiplatform Resources | 1.11.1 |

## 3. アーキテクチャ構成

プロジェクトは 3 つのモジュールで構成されています。

```
MyApplication/
├── app/                 # 既存の Android アプリ
├── shared/              # KMP 共有モジュール（Android/iOS 両対応を目指す）
└── iosApp/              # iOS ホストアプリ（Swift の薄いラッパー）
```

### 3.1 `app` モジュール

- **役割**: Android 専用アプリとして現在も動作しているモジュール。
- **責務**:
  - `MainActivity` による画面構成と権限取得
  - `TaskViewModel` による Android 版の状態管理
  - Jetpack Compose による画面描画
  - Room を使ったローカル DB
  - Google Calendar REST API 連携（OAuth、予定の作成・更新・削除）
  - WorkManager による「空き時間です」通知の定期チェック
  - Android 通知の発行と「始める」アクション受信
- **注意**: 現時点では `shared` モジュールには依存していません。KMP 移行は段階的に進めており、`app` は既存の Room 実装のまま並行稼働しています。

### 3.2 `shared` モジュール

- **役割**: Kotlin Multiplatform ライブラリ。Android と iOS の両方をターゲットに、共通のドメインロジックと UI を提供します。
- **ソースセット構成**:
  - `commonMain`: タスク・サブタスク・カテゴリのデータモデル、SQLDelight スキーマ、Repository、Compose Multiplatform による画面（TaskList / AddTask / CategoryManager）、ナビゲーション、テーマ、画像リソース
  - `androidMain`: `AndroidDatabaseDriverFactory`（SQLDelight の Android ドライバ）
  - `iosMain`: `IosDatabaseDriverFactory`、iOS 向け `MainViewController` ブリッジ
  - `commonTest` / `androidUnitTest`: ドメインモデル・Repository・AppState の単体テスト
- **責務**:
  - プラットフォーム非依存のタスク CRUD
  - Compose Multiplatform による UI 共通化
  - SQLDelight による SQLite アクセス

### 3.3 `iosApp` モジュール

- **役割**: Xcode プロジェクト用の最小限の Swift ラッパー。
- **責務**:
  - `ComposeUIViewController` をホストし、`shared` モジュールの `App(driverFactory:)` を呼び出す
  - `ContentView.swift` / `iOSApp.swift` のみを提供
- **注意**: `.xcodeproj` は Windows 環境では生成できないため、実際の Xcode プロジェクト作成・シミュレータ実行は macOS 側で行う前提です（`SETUP.md` 参照）。

## 4. 主な機能一覧

### 4.1 タスク一覧画面（TaskListScreen）

- **対象**: Android 版 / 共有 UI 版の両方
- **機能**:
  - タスクを「すべて」、カテゴリ別、「未分類」でタブ絞り込み（横スクロールタブ）
  - 未完了タスクを優先度スコア（重要度×3＋緊急度）の高い順に表示
  - タスクの完了チェック ON/OFF
  - タスク名の長押し／タップによるリネーム
  - 右へのスワイプで削除（取り消しスナックバー付き）
  - サブタスク進捗のプログレスバー表示
  - キャラクターイラストとメッセージによる空き具合のフィードバック
  - 右上の歯車アイコンからカテゴリ管理画面へ
  - 右下の FAB からタスク登録画面へ
  - **Android 版限定**: Google カレンダー連携アイコンとアカウントメニュー

### 4.2 タスク登録画面（AddTaskScreen）

- **対象**: Android 版 / 共有 UI 版の両方
- **機能**:
  - タイトル入力（最大 50 文字）
  - 締切日の DatePicker 選択
  - 重要度・緊急度のスライダー（1〜3）
  - カテゴリ選択（FilterChip）、新規カテゴリ追加
  - サブタスクの追加・削除（最大 10 件）
  - **Android 版限定**: Google カレンダーに締切日を終日予定として登録する ON/OFF

### 4.3 カテゴリ管理画面（CategoryManagerScreen）

- **対象**: Android 版 / 共有 UI 版の両方
- **機能**:
  - カテゴリの追加
  - カテゴリ名の変更
  - カテゴリの削除（所属タスクは「未分類」に移動）
  - 削除前に「何件のタスクが未分類に移るか」を確認ダイアログで表示

### 4.4 Google カレンダー連携（Android 版のみ）

- **対象**: Android 版のみ（`shared` には未移植）
- **機能**:
  - Play Services Authorization Client による OAuth 認可
  - Google Calendar REST API v3 経由で締切日を終日予定として作成
  - タスク変更時の予定更新、削除時の予定削除
  - カレンダー側で予定が消されていた場合の再作成
  - アカウントメニューによる連携状況確認とサインアウト

### 4.5 「空き時間です」通知（Android 版のみ）

- **対象**: Android 版のみ
- **機能**:
  - WorkManager による 1 時間おきのバックグラウンドチェック
  - Google Calendar の当日予定を取得し、30 分以上の空き時間を検知
  - 空き時間を見つけたら、最優先の未完了タスクを対象に通知を発行
  - 通知の「始める」ボタンでタスクを進行中（IN_PROGRESS）に変更
  - 同じ空き時間帯への重複通知を防止

### 4.6 iOS アプリホスト

- **対象**: `iosApp`
- **機能**:
  - SwiftUI の `UIViewControllerRepresentable` 経由で `shared` の Compose UI を表示
  - iOS 側からは `IosDatabaseDriverFactory` を渡すだけ

## 5. データモデル・DB 構造

### 5.1 ドメインモデル（共通）

```
Task
├── id: Int
├── title: String
├── deadline: Long          # エポックミリ秒
├── importance: Int         # 1〜3
├── urgency: Int            # 1〜3
├── categoryId: Int?        # null = 未分類
├── isCompleted: Boolean
├── progress: Int           # 0〜100
├── notificationTime: Long? # 将来用（現在未使用）
├── calendarEventId: String?# 将来用 / Android 版で使用
├── createdAt: Long
└── status: TaskStatus      # TODO / IN_PROGRESS

SubTask
├── id: Int
├── taskId: Int
├── title: String
├── isCompleted: Boolean
└── sortOrder: Int

Category
├── id: Int
├── name: String            # 一意
└── sortOrder: Int
```

- **優先度スコア**: `importance * 3 + urgency`（4〜12）
- **タスク進捗**: サブタスクがあればサブタスク完了率、なければ `progress` / 完了済みなら 100

### 5.2 Android 版 DB（Room）

- **DB 名**: `task_database`
- **バージョン**: 5
- **テーブル**:
  - `tasks`
  - `subtasks`
  - `categories`
  - `notified_slots`（空き時間通知の重複防止用）
- **外部キー**:
  - `tasks.categoryId` → `categories.id`（ON DELETE SET NULL）
  - `subtasks.taskId` → `tasks.id`（ON DELETE CASCADE）
- **移行履歴**:
  - v1→v2: カレンダー ID 列・サブタスクテーブル追加
  - v2→v3: 固定 enum カテゴリから `categories` テーブルへ移行
  - v3→v4: `calendarEventId` を INTEGER から TEXT へ変更
  - v4→v5: `status` 列と `notified_slots` テーブル追加

### 5.3 共有モジュール DB（SQLDelight）

- **DB 名**: `SharedDatabase`
- **パッケージ**: `com.example.myapplication.shared.db`
- **テーブル**: `Task` / `SubTask` / `Category`
- **特徴**:
  - SQLDelight は `.sq` ファイルから型付きクエリを生成
  - `commonMain` にスキーマ定義を配置し、Android/iOS それぞれのドライバで動作
  - 外部キー制約（`ON DELETE SET NULL` / `ON DELETE CASCADE`）を利用
  - Android 実装では `PRAGMA foreign_keys=ON` を明示的に実行

### 5.4 主要な状態管理

| 層 | Android 版 | 共有 UI 版 |
|---|---|---|
| 状態ホルダー | `TaskViewModel`（AndroidViewModel） | `AppState`（CoroutineScope 注入） |
| DB アクセス | Room DAO + `TaskRepository` | SQLDelight + `TaskRepository` |
| UI 更新 | `StateFlow` + `collectAsStateWithLifecycle` | `StateFlow` + `collectAsState` |

## 6. ディレクトリ構造

```
MyApplication/
├── app/                                   # Android アプリモジュール
│   ├── src/main/
│   │   ├── java/com/example/myapplication/
│   │   │   ├── MainActivity.kt            # ルート Activity、NavHost 配線
│   │   │   ├── TaskViewModel.kt           # Android 版状態管理、カレンダー/通知連携
│   │   │   ├── TaskListScreen.kt          # タスク一覧画面
│   │   │   ├── AddTaskScreen.kt           # タスク登録画面
│   │   │   ├── CategoryManagerScreen.kt   # カテゴリ管理画面
│   │   │   ├── TaskStartActionReceiver.kt # 通知「始める」アクション受信
│   │   │   ├── data/
│   │   │   │   ├── Task.kt / SubTask.kt / Category.kt / NotifiedSlot.kt
│   │   │   │   ├── AppDatabase.kt         # Room DB 定義・マイグレーション
│   │   │   │   ├── TaskDao.kt             # Room DAO
│   │   │   │   └── TaskRepository.kt      # Room ベース Repository
│   │   │   ├── calendar/
│   │   │   │   ├── GoogleAuthManager.kt   # OAuth 状態管理
│   │   │   │   ├── GoogleCalendarSync.kt  # REST API クライアント
│   │   │   │   ├── GoogleCalendarApi.kt   # Retrofit インターフェース
│   │   │   │   └── FreeSlotFinder.kt      # 空き時間計算
│   │   │   └── work/
│   │   │       ├── FreeTimeCheckWorker.kt # 定期空き時間チェック
│   │   │       └── FreeTimeNotifier.kt    # 通知発行
│   │   ├── res/                           # Android リソース（画像、文字列、テーマ）
│   │   └── AndroidManifest.xml            # パーミッション・レシーバー宣言
│   └── build.gradle.kts                   # Android アプリ用ビルド設定
│
├── shared/                                # KMP 共有モジュール
│   ├── src/
│   │   ├── commonMain/
│   │   │   ├── kotlin/com/example/myapplication/shared/
│   │   │   │   ├── Task.kt / SubTask.kt / Category.kt / TaskStatus.kt
│   │   │   │   ├── TaskRepository.kt      # SQLDelight ベース Repository
│   │   │   │   └── ui/
│   │   │   │       ├── App.kt             # ルート Composable + NavHost
│   │   │   │       ├── AppState.kt        # 共有 UI 状態管理
│   │   │   │       ├── TaskListScreen.kt
│   │   │   │       ├── AddTaskScreen.kt
│   │   │   │       ├── CategoryManagerScreen.kt
│   │   │   │       └── theme/             # Color / Type / Theme
│   │   │   ├── sqldelight/com/example/myapplication/shared/db/
│   │   │   │   ├── Task.sq
│   │   │   │   ├── SubTask.sq
│   │   │   │   └── Category.sq
│   │   │   └── composeResources/drawable/ # キャラクター画像（KMP Resources）
│   │   ├── androidMain/
│   │   │   └── db/DatabaseDriverFactory.android.kt
│   │   ├── iosMain/
│   │   │   ├── db/DatabaseDriverFactory.ios.kt
│   │   │   └── ui/MainViewController.kt
│   │   ├── commonTest/
│   │   └── androidUnitTest/
│   └── build.gradle.kts
│
├── iosApp/                                # iOS ホストアプリ
│   └── iosApp/
│       ├── iOSApp.swift                   # SwiftUI App エントリ
│       └── ContentView.swift              # ComposeView ホスト
│
├── docs/superpowers/                      # 設計書・実装計画書
│   ├── specs/                             # ADR / 設計ドキュメント
│   └── plans/                             # 実装プラン
├── gradle/libs.versions.toml              # 依存関係バージョン管理
├── settings.gradle.kts                    # プロジェクト設定
└── build.gradle.kts                       # ルートビルド設定
```

## 補足：現時点のスコープと制約

- **Android 版**は既存の Room 実装のまま完全に動作しており、Google カレンダー連携と通知機能を持っています。
- **共有モジュール（`shared`）**は、タスク・カテゴリ・サブタスクの CRUD と Compose Multiplatform UI を実装済みですが、カレンダー連携・通知機能は Phase 1 のスコープ外として未移植です。
- **iOS 版**は Swift ラッパーと Kotlin/Native ブリッジコードを用意していますが、Xcode プロジェクトファイル（`.xcodeproj`）の作成とシミュレータ実行は macOS 環境で実施する必要があります。
