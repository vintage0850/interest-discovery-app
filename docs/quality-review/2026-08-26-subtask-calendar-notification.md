# 品質レビュー: サブタスク表示改善・カレンダー手動登録・通知時間手動設定

- 判定: CHANGES REQUIRED
- レビュー担当: Codex
- レビュー日: 2026-08-26
- 対象TASK: `TASK.md` の「案件5」
- 比較範囲: `master` (`3d40a03`) ... `HEAD` (`b14e67d`)、12コミット

## Gate 1 仕様

- [x] 1-1 目的、利用者、対象外、受入条件が明確である。
  - 1-1 未適用の理由:
- [x] 1-2 不明点が実装者の推測に委ねられていない。
  - 1-2 未適用の理由:

## Gate 2 設計

- [x] 2-1 必要な構成、データ、画面、エラー対応が決まっている。
  - 2-1 未適用の理由:
- [x] 2-2 DB、認証、課金、公開API、アーキテクチャ、大規模変更はClaudeが承認している。
  - 2-2 未適用の理由:

## Gate 3 実装

- [ ] 3-1 テストを先に作り、失敗を確認してから実装している。
  - 3-1 未適用の理由:
- [x] 3-2 宣言された担当範囲だけを変更している。
  - 3-2 未適用の理由:
- [x] 3-3 重要な処理には非エンジニアにも理解できる日本語の説明がある。
  - 3-3 未適用の理由:

## Gate 4 レビュー

- [x] 4-1 関連テスト、型チェック、ビルドが成功する。
  - 4-1 未適用の理由:
- [x] 4-2 秘密情報、危険な権限、入力値、エラー処理を確認している。
  - 4-2 未適用の理由:
- [ ] 4-3 納品後に別担当者が保守できる構造になっている。
  - 4-3 未適用の理由:

## Gate 5 納品

- [ ] 5-1 README、環境構築手順、操作方法、既知の制約が揃っている。
  - 5-1 未適用の理由:
- [x] 5-2 引き継ぎノートと再利用可能な知見が更新されている。
  - 5-2 未適用の理由:
- [ ] 5-3 外部公開、課金、データ削除を含む場合は、実行前のユーザー承認を得ている。
  - 5-3 未適用の理由: 今回はローカルのAndroid機能実装とレビューのみで、外部公開、課金、データ削除を行わないため。

## 実行した確認

- コマンド: `git rev-parse master`; `git rev-parse HEAD`; `git log master..HEAD --oneline`; `git diff master...HEAD`; `git diff --check master...HEAD`
- 結果: 固定点 `3d40a03`、レビュー対象HEAD `b14e67d`、対象12コミット・26ファイルを確認。全差分を設計・実装計画・案件5と照合した。`git diff --check` は成功。レビュー中に追加された文書整理コミット `6b62fc0` は実装差分の対象外とした。
- コマンド: `.\gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`
- 結果: 未完了。既定の `C:\.gradle` にlockを作成できず失敗。
- コマンド: `GRADLE_USER_HOME=.gradle-review` 相当で同じ3タスクを再実行
- 結果: Gradle 9.3.1配布物のダウンロードがサンドボックスのネットワーク制限で拒否され、失敗。
- コマンド: 既存の `C:\Users\vinta\.gradle` とローカルGradle 9.3.1を `--offline --no-daemon` で使用して同じ3タスクを再実行
- 結果: `native-platform.dll.lock` を読み取り専用キャッシュへ作成できず、Gradle起動前に失敗。
- 既存の独立実行記録: `TASK.md` 案件5に、2026-08-26 / Claudeによる `:app:testDebugUnitTest`（47件・失敗0）、`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin` の `BUILD SUCCESSFUL` が記録されている。このためGate 4-1の3コマンドは成功済みと判定した。
- 確認できなかった範囲: このCodexサンドボックス内での3コマンド再実行、`MigrationTest` の実行、AlarmManager発火と端末再起動後の再登録の実機確認。`connectedDebugAndroidTest` は端末未接続のため未実施と記録されている。
- 静的確認: v5→v6 migrationは `eventHasTime INTEGER NOT NULL DEFAULT 0`、DB version 6、migration登録、schema 6、既存データを使う `MigrationTest` が揃っている。DAOは部分更新を使用し、既存のカレンダー連携トグルはタスクID単位Mutex、Google Calendar通信は `CancellationException` を再throwする。
- セキュリティ確認: `SCHEDULE_EXACT_ALARM` と `RECEIVE_BOOT_COMPLETED` の用途は設計内。両receiverは `exported=false`、通知PendingIntentはタスクIDをrequest codeに使い `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`。exact alarm不可時は `setAndAllowWhileIdle` へフォールバックする。Authorizationヘッダーはデバッグログでもredactされ、リリースではHTTPログを有効化しない。新たな秘密情報のログ出力は見つからない。
- スコープ確認: サブタスクを説明欄へ列挙、時刻付き予定、手動通知、boot復元、通知窓設定、既存タスク編集を実装し、繰り返し・スヌーズ・終了時刻編集・サブタスク事後編集などの対象外機能は追加していない。

## 指摘事項

### 指摘1（重大）

- 対象箇所: `app/src/main/java/com/example/myapplication/TaskViewModel.kt:320`, `:411`, `:435`（`renameTask`、`updateEventTime`、`applyTaskEdit`）
- 問題: タイトルと予定時刻を同時変更すると、`applyTaskEdit` が2本の独立したcoroutineを開始し、それぞれ元の `task` から「新タイトル＋旧時刻」「旧タイトル＋新時刻」を作ってGoogle予定を更新する。`syncToCalendar` は既存のタスクID単位Mutexで保護されておらず、API完了順で片方の変更が巻き戻る。
- 影響: DBには両方の変更が残っても、Googleカレンダー側はタイトルまたは予定時刻が古い状態になり、アプリ表示と外部予定が不整合になる。
- 必要な修正: 編集確定を単一coroutineへ統合し、必要なDB部分更新後に全変更を合成した最新Taskを使ってカレンダー同期を1回だけ行う。同じタスクへの他のカレンダー操作ともタスクID単位Mutexで直列化する。
- 再確認方法: 連携済みタスクのタイトルと予定時刻を同時変更するユニットテストを追加し、API遅延順を入れ替えても `updateEvent` が1回だけ、両方の新値を含むTaskで呼ばれることを確認する。実機でも同時編集後のGoogle予定を確認する。

### 指摘2（重大）

- 対象箇所: `app/src/main/java/com/example/myapplication/work/TaskNotificationScheduler.kt`、`TaskNotificationReceiver.kt`、`BootReceiver.kt` と対応テスト
- 問題: `TaskNotificationScheduler` のインターフェース抽出によりViewModelはテスト可能だが、本番 `AndroidTaskNotificationScheduler` のexact/fallback分岐、PendingIntent識別、receiverの完了・削除判定、boot時の未来・未完了タスク再予約を固定する自動テストがない。現状のテストはFake schedulerへの呼出し確認に留まる。
- 影響: Android API条件、manifest連携、PendingIntent生成、再起動復元の回帰がCIで検出できず、機能の中心である指定時刻通知が端末上で動かないまま納品される可能性がある。
- 必要な修正: AlarmManager/PendingIntent生成・repository・schedulerを注入可能な境界へ整理して単体テストするか、instrumentationテストを追加する。少なくとも exact許可時、未許可時fallback、taskId別cancel、発火時の完了/削除、boot時の対象抽出と再予約を覆う。
- 再確認方法: 追加テストを含む `:app:testDebugUnitTest` と `:app:compileDebugAndroidTestKotlin` を成功させ、接続端末でmigration、指定時刻発火、再起動後再登録を実行して結果を `TASK.md` に記録する。

### 指摘3

- 対象箇所: `TASK.md` 案件5のTask 5〜12実行ログ、利用者向け操作・既知制約の文書
- 問題: 最終3 Gradleタスクの成功記録はあるが、Task 5〜12についてテストを先に失敗させたRedの記録がなく、通知設定・手動通知・再起動復元の操作方法と実機未確認という既知制約が利用者向け文書に整理されていない。
- 影響: Gate 3-1のTDD実施とGate 5-1の納品文書を第三者が確認できない。
- 必要な修正: Kimiが残っているRed/Green証跡を追記し、利用者向け文書へ新機能の操作方法、exact alarm権限が無い場合の誤差許容fallback、実機で未確認の項目を記載する。
- 再確認方法: `TASK.md` と利用者向け文書を再読し、Task 5〜12のテスト先行証跡と利用者が必要とする操作・制約が一意に分かることを確認する。

## 次の行動

- 差し戻し先（原則としてKimi）: Kimi
- 期限: 未指定
- エスカレーション先（必要な場合のみ。設計はClaude、予算・納期・仕様はユーザー）: `SCHEDULE_EXACT_ALARM` を使った状態でPlayストア公開する場合のみ、公開前にClaudeへポリシー適合判断を依頼する。

---

## Gate 4 再レビュー追記（2026-08-26 / Codex）

- **判定: CHANGES REQUIRED**
- 再レビュー範囲: `git diff master...HEAD`（`master=3d40a03`, `HEAD=6b62fc0`）と、指摘1〜3の修正が含まれる未コミット差分 `git diff HEAD`
- 対象: `TaskViewModel.kt`、`work/TaskNotificationScheduler.kt`、`TaskNotificationReceiver.kt`、`BootReceiver.kt`、対応テスト、`TASK.md`、`SETUP.md`、テスト依存追加

### Standards

- 明文化されたコード標準の hard violation は確認しなかった。日本語コメントとテスト用境界の意図は読み取れる。
- 判断ヒューリスティック（Mysterious Name / テスト名と検証の不一致）: `AndroidTaskNotificationSchedulerTest.kt:102-110` の「taskId別」テストは cancel 回数しか検証せず、factory が受け取った `42`, `7` を assert していない。テスト名が保証する回帰防止になっていない。
- 判断ヒューリスティック（Duplicated Code、非ブロッキング）: `TaskViewModelCalendarTest.kt` の `FakeSharedPreferences` は `NotificationWindowPreferencesTest.kt` と実質同一で、将来のテスト fixture 共通化候補である。

### Spec

#### 指摘1: 部分解消

- 直接原因だった `applyTaskEdit` の2 coroutine競合は解消している。`TaskViewModel.kt:465-500` で単一 coroutine、Mutex取得後の最新Task再取得、部分更新、`merged` Taskによる1回のカレンダー同期が実装され、`TaskViewModelCalendarTest.kt:421-447` が新タイトル・新時刻・1回呼び出しを固定している。
- ただし必要な修正にあった「同じタスクへの他のカレンダー操作ともタスクID単位Mutexで直列化」は完遂していない。`TaskViewModel.kt:343-355` の `deleteTask` は `calendarSync.deleteEvent` をMutex外で呼ぶため、同時の編集や連携切替と競合し、削除後の予定再作成や古い内容の反映が起こり得る。削除経路も同じMutexに含めた競合テストが必要。
- また `applyTaskEdit` は通知時刻だけの変更でも `doSyncToCalendar(merged)` を呼ぶ。カレンダー項目が変わった場合だけ同期する条件に絞るべきである。

#### 指摘2: 部分解消

- exact許可時 / fallback、`notificationTime=null`、receiverの未完了 / 完了 / 削除済み分岐、boot時に取得済みタスクを再予約する処理の単体テストは追加された。
- `AndroidTaskNotificationSchedulerTest.kt:102-110` は `pendingIntentFactory` に渡されたtaskIdを検証しないため、必須の「taskId別cancel / PendingIntent識別」は未カバー。
- `BootReceiverTest.kt:17-61` は `getTasks` がすでに抽出したリストをscheduleすることだけを検証する。`TaskDao.kt:71-75` の実クエリに対し、過去・完了・`notificationTime=null`を除外して未完了の未来通知だけを返すことを固定するDAO/Roomまたは統合テストがない。「boot時の対象抽出と再予約」は部分カバーに留まる。
- `BootReceiver` の `android:exported="false"` は未解消事項として扱わない。Android公式の `<receiver>` 仕様では `false` でもsystemからのメッセージは受信可能とされているため、この設定はセキュリティ面からも維持でよい。

#### 指摘3: 部分解消

- `SETUP.md:400-444` に通知時刻、通知ウィンドウ、exact alarm fallback、再起動後復元、実機未確認範囲が整理された点は解消。
- 利用手順に実装との不一致がある。`SETUP.md:415` は「タスクを長押し（または編集アイコン）」とするが、実装は `TaskListScreen.kt:557-563` のタイトルの通常タップであり、長押しも編集アイコンもない。`SETUP.md:408` の「入力欄からタスクを作成」も該当する一覧画面の入力欄がない。
- `TASK.md:493-525` は Task 5〜12 のTDD証跡と題するが、receiver / bootはGREENのみでRED失敗の記録がない。schedulerのREDも「単体テスト不可」という説明で、失敗したテスト名・実行結果を示さない。テスト先行を実施していない範囲は後追いテストと正確に記録し、Gate 3-1を充足したと扱わないこと。

### 検証記録

- `git diff --check master -- TASK.md SETUP.md app/build.gradle.kts gradle/libs.versions.toml app/src/main/java/com/example/myapplication/TaskViewModel.kt app/src/main/java/com/example/myapplication/work app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt app/src/test/java/com/example/myapplication/work`: 成功。
- Gradleはユーザー指定に従い再実行せず、`TASK.md:544-550` のClaude検証記録 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` = **BUILD SUCCESSFUL（58 tests、失敗0）** を採用した。この成功はテストの未カバー範囲を解消するものではない。
- 実機・エミュレータの `connectedDebugAndroidTest`、指定時刻発火、端末再起動後の再登録は引き続き未実施。これ自体は今回のブロッキング理由にしない。

### 次の修正と再確認

1. `deleteTask` のカレンダー削除も同一taskIdのMutex下で行い、編集/連携切替との競合テストを追加する。通知時刻だけの編集ではGoogle Calendar APIを呼ばないことも固定する。
2. schedulerテストでfactoryの受信taskIdとcancelされた個別PendingIntentをassertする。Room/DAOまたは統合テストでboot対象から過去・完了・null通知を除外することを固定する。
3. `SETUP.md` を「一覧画面右下の＋」と「タスクタイトルをタップ」に合わせる。`TASK.md` は実在するRED実行証跡を追記し、後追いテストはその旨を正確に記録する。
4. 修正後に同じ3 Gradleタスクと `git diff --check` を再実行し、本文書に再判定を追記する。

集計: Standards軸は hard finding 0件・judgement call 2件（最大はテスト名と検証の不一致）、Spec軸は未解消/部分対応 3指摘（最大は指摘1の同一タスク操作の競合が残ること）。

---

## Gate 4 再々レビュー追記（2026-08-26 / Codex）

- **判定: CHANGES REQUIRED**
- 固定点: `master=3d40a03`、`HEAD=6b62fc0`
- レビュー範囲: `git diff master...HEAD` に加え、今回の修正は未コミット・未追跡のため `git diff HEAD` と新規ファイル本文を確認した。
- 対象: `TaskViewModel.kt`、`TaskViewModelCalendarTest.kt`、`work/AndroidTaskNotificationSchedulerTest.kt`、`SETUP.md`、新規 `TaskDaoBootQueryTest.kt`。関連する `TaskNotificationScheduler.kt`、`TaskDao.kt`、テスト依存差分も照合した。

### Standards

- 明文化されたリポジトリ標準の hard violation は確認しなかった。
- 判断ヒューリスティック（Duplicated Code、非ブロッキング）: `TaskViewModel.kt:448-456` と `:500-506` に、通知時刻更新後の `cancel` → 条件付き `schedule` が重複している。今後条件が増える場合は private helper への集約余地がある。
- 判断ヒューリスティック（Duplicated Code、非ブロッキング）: `TaskViewModelCalendarTest.kt` の `FakeSharedPreferences` は `NotificationWindowPreferencesTest.kt` と実質同内容であり、共通テストfixture化の余地がある。

Standards軸の集計は hard finding 0件、judgement call 2件。最大は通知再予約ロジックの重複だが、今回のGate 4を単独でブロックする重大度ではない。

### Spec

#### 削除処理のMutex対応: 未解消

- `TaskViewModel.kt:343-359` で `deleteTask` 自体を `mutexFor(task.id).withLock` に入れた点は確認した。
- ただし、Mutex取得後に `repository.getTaskById(task.id)` で最新Taskを再取得せず、呼び出し時の古い `task` を `repository.delete(task)` と `task.calendarEventId` の判定に使っている。`setCalendarLinked` は同じMutex内で最新Taskへ新しいイベントIDを保存するため、未連携のTaskに対して「カレンダー連携ON → 直後にタスク削除」がこの順で直列実行されると、削除側は古い `calendarEventId=null` を見て新規作成済みのGoogle予定を削除しない。`doSyncToCalendar` が NotFound 後に予定を再作成してIDを貼り替え、その後に削除が待っていた場合も同様に古いIDを使う。
- 結果として、前回要求した「同じtaskIdの他操作との競合で孤児予定を残さない」は満たしていない。`TaskViewModelCalendarTest.kt` にも、削除と連携ONまたはイベントID貼替の競合を固定するテストは追加されていない。削除系の既存テストは通知予約解除とundo再予約のみである。
- 必要な修正: Mutex取得後の最新Taskを削除・外部予定削除・`lastDeleted`保存に使う。少なくとも「連携ONで新ID保存後に古いTask引数から削除しても、その新IDの予定が削除される」競合テストを追加する。

#### 通知時刻だけの変更: 解消

- `TaskViewModel.kt:494-498` は `titleChanged || eventTimeChanged` の場合だけ `doSyncToCalendar(merged)` を呼ぶ。
- `TaskViewModelCalendarTest.kt:450-475` が、通知時刻だけの変更でCalendar APIを呼ばずDB更新することを固定している。

#### schedulerのtaskId/PendingIntent検証: 解消

- `AndroidTaskNotificationSchedulerTest.kt:117-149` は、factoryが受け取ったtaskIdを `listOf(42, 7)` と照合し、taskIdごとの異なるPendingIntentが `cancel` と `schedule` に渡ることを検証している。前回の「回数しか見ていない」状態は解消した。

#### boot対象抽出の実クエリ検証: 解消（実機実行は保留）

- `TaskDaoBootQueryTest.kt:53-68` は in-memory Room DB に未来・過去・完了済み・通知時刻nullの4件を保存し、`TaskDao.getTasksWithFutureNotification(now)` が未来かつ未完了かつ通知時刻ありの1件だけを返すことを検証する。
- `connectedDebugAndroidTest` は端末未接続のため未実施であり、このテストはコンパイル確認までで実行未確認。ユーザー指定どおり、これ自体はブロッキング理由にしない。

#### SETUP.mdの操作手順: 解消

- `SETUP.md:408` はタスク追加を「一覧画面右下の＋（フローティングボタン）」、`:415` は既存タスク編集を「タイトルをタップ」と記載しており、`TaskListScreen.kt:268-274`、`:560-563` の実装と一致する。

Spec軸の集計は blocking finding 1件（削除処理が最新Taskを使わず、外部予定を残し得る）。その他4修正は解消済みで、スコープクリープは確認しなかった。

### 検証記録

- `git diff --check HEAD`: 成功（追跡済みの今回修正に空白エラーなし）。未追跡の `AndroidTaskNotificationSchedulerTest.kt` と `TaskDaoBootQueryTest.kt` も `git diff --no-index --check NUL <file>` の出力に空白エラーなし（終了コード1はNULとの差分が存在するため）。
- Gradleはユーザー指定に従い、`TASK.md` のClaude検証記録 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` = **BUILD SUCCESSFUL（失敗0）** を採用した。これは今回の未解消競合を対象とする回帰テストが存在することを意味しない。
- `connectedDebugAndroidTest`、実機での通知発火・再起動復元・`TaskDaoBootQueryTest`実行は未実施。ユーザー指定どおり非ブロッキングとした。

### 次の修正と再確認

1. `deleteTask` のMutex取得後に最新Taskを再取得し、そのTaskの `calendarEventId` を削除対象に使う。削除・`lastDeleted`にも同じ最新Taskを使う。
2. 連携ONまたはNotFound再作成でイベントIDが貼り替わった後に、古いTask引数で削除しても最新イベントが削除される競合テストを追加する。
3. 修正後、同じ3 Gradleタスクと `git diff --check` を再実行して再判定する。`connectedDebugAndroidTest` は端末接続時の後続確認でよい。

---

## Gate 4 再々々レビュー追記（2026-08-26 / Codex）

- **判定: PASS**
- 固定点: `master=3d40a03`、`HEAD=6b62fc0`
- レビュー範囲: `git diff master...HEAD`。前回の残り1件に対する修正は未コミットの作業ツリー差分にあるため、`git diff HEAD -- app/src/main/java/com/example/myapplication/TaskViewModel.kt app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt` も併せて確認した。
- 対象: `TaskViewModel.kt` の `deleteTask` と、`TaskViewModelCalendarTest.kt` の新規回帰テスト。

### Standards

- 明文化されたリポジトリ標準の hard violation は確認しなかった。`TaskViewModel.kt:343-363` はタスクID単位Mutex取得後に最新Taskを再取得する理由を日本語コメントで説明し、処理も読み取りやすい。
- 判断ヒューリスティック（Duplicated Code、非ブロッキング）: `TaskViewModel.kt` の通知再予約処理と、テスト内の `FakeSharedPreferences` には既存の重複がある。いずれも今回の `deleteTask` 修正とは無関係で、Gate 4をブロックする重大度ではない。

Standards軸の集計は hard finding 0件、judgement call 2件（いずれも非ブロッキング）。

### Spec

#### 削除処理で最新Taskを使う対応: 解消

- `TaskViewModel.kt:343-363` で `deleteTask` は `mutexFor(task.id).withLock` の取得後に `repository.getTaskById(task.id)` を呼び、最新状態を `current` として再取得している。
- サブタスク取得、`repository.delete`、通知予約解除、`current.calendarEventId` に対するGoogle予定削除、`lastDeleted` 保存のすべてが同じ `current` を使う。呼び出し元の古い `task.calendarEventId` を参照する経路は残っていない。
- `TaskViewModelCalendarTest.kt:294-311` の `deleteTaskは呼び出し時点の古いTaskではなく最新のcalendarEventIdを削除する` は、呼び出し元のTaskを `calendarEventId=null` のまま保持し、リポジトリ側だけを `event_new` へ更新してから `deleteTask` を呼ぶ。`deleteEvent("event_new")` が呼ばれたことを検証するため、前回指摘した古い引数による孤児予定リスクを直接固定している。
- 未実装・部分実装、今回の修正に起因するスコープクリープ、実装済みに見える誤りは確認しなかった。

Spec軸の集計は blocking finding 0件。前回までに解消済みとした他の4項目も再び未解消となる差分はなく、前回唯一残った指摘は解消した。

### 検証記録

- `git diff --check master` と `git diff --check HEAD`: 成功（終了コード0）。
- Codex環境で `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` の再実行を試みたが、Gradle起動前に `C:\.gradle\wrapper\dists\...\gradle-9.3.1-bin.zip.lck` の親ディレクトリを作成できず失敗した。
- ユーザー指定に従い、`TASK.md:578-582` のClaude検証記録（同一コマンドが **BUILD SUCCESSFUL、失敗0**）を採用した。
- `connectedDebugAndroidTest`、実機での通知発火・再起動復元・`TaskDaoBootQueryTest`実行は端末未接続のため未実施のまま。ユーザー指定および前回判定どおり、Gate 4のブロッキング理由にはしない。

### 最終判定

前回唯一残った `deleteTask` の古い `Task` 引数参照は、実装と回帰テストの両方で解消されている。**Gate 4はPASS** とする。
