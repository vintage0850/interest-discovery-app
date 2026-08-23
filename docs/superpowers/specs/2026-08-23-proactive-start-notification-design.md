# 空き時間検知による「始めさせる」通知機能 — 設計

- 日付: 2026-08-23
- ステータス: 承認待ち(ユーザーレビュー前)

## 背景・目的

現状のアプリは「登録済みタスクを消化するTodoリスト」だが、これを「予定を終わらせる」のではなく「予定（タスク）を始めさせる」方向に拡張する。
具体的には、Googleカレンダーの予定から空き時間を検出し、ユーザーが行動を起こしやすいタイミングでプッシュ通知を送る。通知には「始める」アクションボタンを付け、タップ一つでタスクを「進行中」状態にできるようにする。

## スコープ

含む:
- カレンダーの空き時間（予定と予定の間の隙間）を動的に検出する
- 1時間おきのバックグラウンドチェックで、条件を満たす空き時間があれば通知する
- 通知に「始める」ボタンを付け、アプリを開かずにタスクを「進行中」にできる
- タスクに「進行中」状態を追加し、一覧画面に表示する

含まない（YAGNI、将来検討）:
- ポモドーロ的な作業時間の計測・記録
- 空き時間の長さに応じたタスクの目安所要時間マッチング（タスクに所要時間フィールドを追加する変更は今回見送り）
- 通知のスヌーズ・「後で」アクション
- 通知タイミングのユーザー設定画面（時間帯は8:00〜22:00で固定値としてハードコードし、将来設定可能にする）

## データモデルの変更

### `Task` テーブル

`isCompleted: Boolean` は変更しない。新たに以下を追加する。

```kotlin
enum class TaskStatus { TODO, IN_PROGRESS }
```

- `Task.status: TaskStatus`（デフォルト `TODO`）を新規カラムとして追加（Room migration）
- 完了操作（`toggleCompleted`でチェックON）が行われたタイミングで、`status` は強制的に `TODO` に戻す。「進行中のまま完了」という状態は持たせない
- 通知の「始める」操作の対象になり得るのは `isCompleted == false && status == TODO` のタスクのみ

### 新規テーブル: `NotifiedSlot`

同じ空き時間帯中に複数回通知しないための重複防止に使う。

```kotlin
@Entity(tableName = "notified_slots")
data class NotifiedSlot(
    @PrimaryKey val startMillis: Long,
    val endMillis: Long
)
```

- `FreeTimeCheckWorker` が空き区間を検出するたびに、`startMillis` が一致するレコードが既に存在するかを確認する。あれば通知済みとしてスキップ
- 古いレコードは肥大化を避けるため、Worker実行のたびに「24時間より前のレコード」を削除する

## コンポーネント構成

### `GoogleCalendarApi` の拡張

既存は `insertEvent` / `patchEvent` / `deleteEvent` のみ。予定一覧取得用に以下を追加する。

```kotlin
@GET("calendars/primary/events")
suspend fun listEvents(
    @Header("Authorization") authorization: String,
    @Query("timeMin") timeMin: String,   // RFC3339
    @Query("timeMax") timeMax: String,
    @Query("singleEvents") singleEvents: Boolean = true,
    @Query("orderBy") orderBy: String = "startTime"
): Response<CalendarEventListResponse>
```

スコープは既存の `calendar.events`（読み書き両方を含む）のままで追加変更は不要。

`GoogleCalendarSync` に `listTodayEvents(): CalendarResult<List<CalendarEventSlot>>` を追加し、既存の `request()` 共通処理（401リトライ等）に乗せる。`CalendarEventSlot(start: Instant, end: Instant)` は終日予定・時刻指定予定の両方を統一的な区間として扱うための軽量データクラス。

### 空きギャップ計算（純粋関数）

```kotlin
fun findNextFreeSlot(
    events: List<CalendarEventSlot>,
    now: Instant,
    windowEnd: Instant,       // その日の通知許可終了時刻（22:00）
    minDurationMinutes: Int = 30
): FreeSlot?
```

- `now` 以降の予定を開始時刻順に見て、`now`（または直前の予定終了時刻）から次の予定開始までの間隔を計算する
- 間隔が `minDurationMinutes` 以上ならその区間を候補として返す。最初に見つかった区間を採用する（＝直近の空き時間を優先）
- 予定が尽きた場合は `windowEnd` までを1区間とみなす
- 副作用なしのプレーンな関数として `data/calendar` パッケージ内に置き、Worker本体から分離してユニットテストしやすくする

### `FreeTimeCheckWorker`（WorkManager, `CoroutineWorker`）

1時間おきの `PeriodicWorkRequest` として `MainActivity`（または `Application` クラス新設）の初期化時に `enqueueUniquePeriodicWork(ExistingPeriodicWorkPolicy.KEEP)` で登録する。

処理フロー:
1. `GoogleAuthManager.authState` が `Authorized` でなければ終了
2. 現在時刻が8:00〜22:00の範囲外なら終了
3. `calendarSync.listTodayEvents()` を呼ぶ。失敗（401・ネットワークエラー等）なら静かに終了（ユーザー通知なし。次回Workerに委ねる）
4. `findNextFreeSlot(...)` で空き区間を計算。見つからなければ終了
5. `NotifiedSlot` に同じ `startMillis` のレコードがあれば終了
6. `repository` から `isCompleted == false && status == TODO` のタスクを取得し、`priorityScore` 最大の1件を選ぶ。該当なしなら終了
7. 通知を発行（後述）
8. `NotifiedSlot(start, end)` を保存。24時間より前の古いレコードを削除

### 通知の内容とアクション

- タイトル: 「空き時間です」
- 本文: タスクのタイトル（例:「プログラミングの学習 を始めませんか？」）
- アクションボタン: 「始める」（`NotificationCompat.Action`）
- 通知タップ（本体）: アプリを起動しタスク一覧を開く（通常のランチャーIntent）
- 「始める」ボタン: `TaskStartActionReceiver`（`BroadcastReceiver`）を直接呼び出し、UIを起動せずにDBの対象タスクを `status = IN_PROGRESS` に更新して通知を閉じる

### `TaskStartActionReceiver`

```kotlin
class TaskStartActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
        if (taskId == -1) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // repository 経由で status だけを更新する narrow update
                TaskRepository(AppDatabase.getDatabase(context).taskDao())
                    .updateStatus(taskId, TaskStatus.IN_PROGRESS)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

`TaskDao` に `updateTaskStatus`（`@Query("UPDATE tasks SET status = :status WHERE id = :taskId")`）を追加し、既存の `updateCalendarEventId` / `updateTaskTitle` と同じ「部分更新」パターンに揃える。

## 権限

- `POST_NOTIFICATIONS`（Android 13+, API 33+）: 実行時権限。カレンダー連携（OAuth同意）が完了したタイミング、またはアプリ初回起動時にリクエストする
- `WorkManager` 自体に追加の危険権限は不要（`androidx.work:work-runtime-ktx` 依存を追加）

## エラーハンドリング

- カレンダーAPI呼び出し失敗（401・ネットワークエラー・その他）は、今回のWorkerサイクルを静かに終了する。バックグラウンド処理でスナックバー等のフィードバックは出さない設計とする（既存の `_messages` はUIスコープのSharedFlowで、Workerからは購読されないため技術的にも出せない）
- 401が発生してもトークンキャッシュの無効化は既存の `GoogleCalendarSync`/`GoogleAuthManager` の仕組みに委譲する（Worker側で特別なリトライは行わない。次の1時間後のWorkerで再試行される）

## UI変更

- `TaskItem` に「進行中」バッジ（`AssistChip` 等の小さなラベル）を、`status == IN_PROGRESS` のときだけ表示する
- 一覧のフィルタ・ソート順は変更しない（進行中かどうかは表示上のヒントのみ）

## テスト方針

- `findNextFreeSlot` の純粋関数に対するユニットテスト:
  - 予定が1件もない
  - 現在時刻から次の予定まで30分ちょうど（境界値）
  - 29分（対象外）
  - 複数の予定が連続していて隙間がない
  - 予定がすべて過去（今日はもう空いている）
  - `windowEnd`（22:00）を超える最後の空き区間の扱い
- `NotifiedSlot` 重複防止ロジックのユニットテスト
- `FreeTimeCheckWorker` は `TestListenableWorkerBuilder` を使い、以下の分岐をテスト:
  - 未連携ならスキップ
  - 時間帯外ならスキップ
  - 空き時間なしならスキップ
  - 通知済みスロットならスキップ
  - 正常系で通知が発行され `NotifiedSlot` が保存される
- `TaskStartActionReceiver` は Robolectric 等でのユニットテスト、または `TaskRepository.updateStatus` のユニットテストで代替可能か実装時に判断する

## 実装時の未確定事項（実装計画フェーズで決定）

- `PeriodicWorkRequest` の登録場所（`Application` クラスを新設するか、`MainActivity.onCreate` に置くか）— 現状 `Application` サブクラスが存在しないため、新設するかどうかは実装計画側で判断する
- 通知チャンネル（`NotificationChannel`）の定義場所と重要度レベル
