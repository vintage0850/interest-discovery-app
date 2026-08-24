# サブタスク表示改善・カレンダー手動登録・通知時間手動設定 — 設計

- 日付: 2026-08-24
- ステータス: 設計判断済み（2026-08-24 / Claude）。ユーザー承認済み。

## 背景・目的

既存のタスク管理アプリには以下の土台がすでにある。

- Google カレンダーへの手動登録トグル（`setCalendarLinked`、`AddTaskScreen` のチェックボックス）
- サブタスク機能（`SubTask` エンティティ、`TaskWithSubTasks`）
- 空き時間検知による「始めさせる」通知（`FreeTimeCheckWorker`、8:00〜22:00固定）
- 未使用のまま残っている `Task.notificationTime` カラム

これらを拡張し、以下3点を実現する。

1. カレンダーに登録される予定に、サブタスクの内容を「メインタスク - サブタスク」的に分かるよう反映する
2. カレンダー予定の日時（現在は締切日の終日予定固定）をユーザーが手動指定できるようにする
3. タスクごとに指定した時刻に、空き時間検知を待たずに確実に通知を出す「手動通知時刻」機能と、その土台となるアプリ全体の通知有効時間帯の設定画面を追加する
4. 上記②③は新規タスク作成時だけでなく、既存タスクに対しても事後編集できるようにする

## スコープ

含む:
- カレンダー予定の説明欄にサブタスクを列挙する
- カレンダー予定の開始時刻（任意）をタスク追加時に指定できるようにし、指定時は時刻付き予定、未指定時は従来通り終日予定にする
- タスクごとの手動通知時刻（`notificationTime`）を `AddTaskScreen` から設定できるようにし、`AlarmManager` で指定時刻に確実に通知する
- 端末再起動後もアラームが失われないよう再登録する仕組み
- アプリ全体の通知有効時間帯（現在ハードコードされている8:00〜22:00）を変更できる設定画面

含まない（YAGNI、将来検討）:
- サブタスクを個別のカレンダー予定として分割登録すること（1つの予定にまとめる）
- 手動通知の繰り返し・スヌーズ
- カレンダー予定の終了時刻のカスタマイズ（時刻指定時は開始時刻から1時間固定）
- サブタスクの内容自体の事後編集（今回のスコープは②③の事後編集のみ）

## データモデルの変更

### `Task` テーブル

新規カラムを追加する（Room migration）。

```kotlin
val eventHasTime: Boolean = false
```

- `deadline`（epoch millis）は既存のまま流用する。`eventHasTime == true` の場合、`deadline` の時刻部分をそのままカレンダー予定の開始時刻として使う
- `eventHasTime == false` の場合は従来通り、`deadline` の暦日で終日予定を作る
- `notificationTime: Long?` は既存カラムをそのまま使う（epoch millis、null なら手動通知なし）

### 通知有効時間帯の永続化

Room ではなく `SharedPreferences` を使う（単純なキー2つのみのため DataStore 導入は過剰）。

```kotlin
object NotificationWindowPreferences {
    fun get(context: Context): NotificationWindow // start: LocalTime, end: LocalTime
    fun set(context: Context, window: NotificationWindow)
}
```

- デフォルト値は現行通り 8:00〜22:00
- `FreeTimeCheckWorker` はハードコードされた `WINDOW_START`/`WINDOW_END` の代わりにここから読む

## コンポーネント構成

### ① サブタスクのカレンダー表示改善

`GoogleCalendarSync.toEventRequest()` を拡張する。呼び出し元（`TaskViewModel`）から `TaskWithSubTasks`（もしくは `List<SubTask>`）を渡せるようシグネチャを変更する。

```kotlin
private fun Task.toEventRequest(
    categoryName: String,
    subTasks: List<SubTask> = emptyList()
): CalendarEventRequest {
    val mark = if (isCompleted) "✓ " else ""
    val subTaskLines = subTasks.takeIf { it.isNotEmpty() }
        ?.sortedBy { it.sortOrder }
        ?.joinToString("\n") { "・${it.title}" }
    val description = buildString {
        append("重要度: $importance / 緊急度: $urgency\nカテゴリ: $categoryName")
        if (subTaskLines != null) append("\n\nサブタスク:\n$subTaskLines")
    }
    ...
}
```

- タイトルは変更しない（メインタスク名のまま）。カレンダー月表示で長くなりすぎるのを避けるため、サブタスク列挙は説明欄に留める
- `insertEvent` / `updateEvent` を呼ぶ全箇所（`addTask`、`setCalendarLinked`、`syncToCalendar` など）で、対象タスクのサブタスク一覧を `repository.getSubTasksFor(taskId)` 等で取得して渡すよう変更する

### ② カレンダー予定の日時手動指定

`GoogleCalendarApi.kt` の `CalendarEventRequest.start/end` を、読み取り側と同じ `CalendarEventDateTime` 型（`date` か `dateTime` のどちらかを持つ）に統一する。専用の `CalendarEventDate` は削除する。

```kotlin
private fun Task.toEventRequest(...): CalendarEventRequest {
    val zone = ZoneId.systemDefault()
    val (start, end) = if (eventHasTime) {
        val startInstant = Instant.ofEpochMilli(deadline)
        CalendarEventDateTime(dateTime = startInstant.atZone(zone).format(DATETIME_FORMATTER)) to
            CalendarEventDateTime(dateTime = startInstant.plus(1, ChronoUnit.HOURS).atZone(zone).format(DATETIME_FORMATTER))
    } else {
        val date = localDateOf(deadline)
        CalendarEventDateTime(date = date.format(DATE_FORMATTER)) to
            CalendarEventDateTime(date = date.plusDays(1).format(DATE_FORMATTER))
    }
    return CalendarEventRequest(summary = ..., description = ..., start = start, end = end)
}
```

`AddTaskScreen` に「時刻を指定する」トグル＋時刻ピッカーを追加する（日付ピッカーは既存の締切日ピッカーをそのまま使う）。トグルOFF時は `eventHasTime = false`、ONなら締切の日付＋選んだ時刻を `deadline` に合成して保存する。

### ③ 手動通知時刻

**スケジューラ:** 新規 `TaskNotificationScheduler`（`AlarmManager` ラッパー）。

```kotlin
class TaskNotificationScheduler(context: Context) {
    fun schedule(task: Task) // notificationTime が null なら何もしない
    fun cancel(taskId: Int)
}
```

- `AlarmManager.canScheduleExactAlarms()` が true なら `setExactAndAllowWhileIdle`、false なら `setAndAllowWhileIdle`（誤差許容）にフォールバックする
- PendingIntent の宛先は新規 `TaskNotificationReceiver`（`BroadcastReceiver`）。受信したら既存の `FreeTimeNotifier.notifyTaskStart(task)` を再利用して通知を出す
- リクエストコード / PendingIntent の識別は `TaskStartActionReceiver` と同様、タスクIDを使う

**登録タイミング:** `TaskViewModel.addTask()` で `notificationTime` が設定されていれば `scheduler.schedule(task)` を呼ぶ。タスク削除時（`deleteTask`）は `scheduler.cancel(task.id)` を呼ぶ。`undoDelete()` では再度 `schedule` する。

**端末再起動対応:** 新規 `BootReceiver`（`BOOT_COMPLETED` を受信）。`repository` から `notificationTime != null && notificationTime > now && isCompleted == false` のタスクを全件取得し、`scheduler.schedule()` で再登録する。`AndroidManifest.xml` に `RECEIVE_BOOT_COMPLETED` 権限と `<receiver>` 登録を追加する。

**権限:** `SCHEDULE_EXACT_ALARM`（Android 12+、ユーザーが設定画面でON/OFFする特別な権限）を `AndroidManifest.xml` に追加。`canScheduleExactAlarms()` が false の場合は上記フォールバックで動作するため、権限が無い状態でもクラッシュはしない。

**UI:** `AddTaskScreen` に「通知時刻」ピッカー（任意項目）を追加。指定した場合のみ `notificationTime` を保存し、`addTask()` から `TaskViewModel` に渡す。

### 通知有効時間帯の設定画面

新規 `NotificationSettingsScreen`。`TaskListScreen` の既存メニュー（Google連携メニューと同様の位置）に導線を追加する。

- 開始時刻・終了時刻の2つの時刻ピッカー
- 保存時に `NotificationWindowPreferences.set()` を呼ぶ
- `FreeTimeCheckWorker` は `doWork()` の冒頭で `NotificationWindowPreferences.get(context)` を読み、`WINDOW_START`/`WINDOW_END` の代わりに使う

### ④ 既存タスクの事後編集

現在のタイトル変更ダイアログ（タイトルタップ → `CategoryNameDialog` で `renameTask` を呼ぶ導線）を拡張し、1つのダイアログで以下3項目をまとめて編集できるようにする。別画面は作らない。

- タスク名（既存のまま）
- 予定の時刻指定（②と同じ「時刻を指定する」トグル＋時刻ピッカー部品を再利用）
- 通知時刻（③と同じトグル＋時刻ピッカー部品を再利用）

**`TaskViewModel` の追加関数:**

```kotlin
fun updateEventTime(task: Task, eventHasTime: Boolean, newDeadline: Long) {
    // DB更新（deadline, eventHasTime）
    // calendarEventId != null なら syncToCalendar 相当で予定を時刻付き/終日に更新
}

fun updateNotificationTime(task: Task, newNotificationTime: Long?) {
    // DB更新（notificationTime）
    // scheduler.cancel(task.id) の後、newNotificationTime != null なら scheduler.schedule(updated)
}
```

- ダイアログの「変更」確定時に、変更があった項目だけ対応する関数を呼ぶ（タイトルのみ変更なら従来通り `renameTask` のみ呼ぶ）
- `syncToCalendar` は現在 private のため、予定更新ロジックを再利用できるよう `internal` 化するか、`updateEventTime` 内から直接 `calendarSync.updateEvent` を呼ぶ形に整理する（実装時に既存コードとの重複を見て判断）

## テスト方針

- `GoogleCalendarSync.toEventRequest()` 相当のロジック: サブタスクあり/なし、時刻指定あり/なしの組み合わせをユニットテストで検証（既存の `GoogleCalendarSyncTest` 相当があれば拡張）
- `TaskNotificationScheduler`: `AlarmManager` は Robolectric shadow を使うか、インターフェース抽出してモック化する
- `NotificationWindowPreferences`: 読み書きの単体テスト
- 手動での実機確認: 実際に `AlarmManager` の発火・端末再起動後の再登録はエミュレータ/実機での動作確認が必要（ユニットテストでは再起動シナリオを完全には再現できないため）
- `updateEventTime` / `updateNotificationTime`: DB更新・カレンダー再同期・アラームの再予約/解除がそれぞれ正しく呼ばれるかをユニットテストで検証

## 未決事項（実装時に確認）

- `eventHasTime` の Room migration 番号（既存の migration 履歴を確認して次番号を採番する）
- `SCHEDULE_EXACT_ALARM` 権限使用の Play ストアポリシー適合（個人利用中心のアプリであれば問題ない想定だが、公開する場合は要確認）
