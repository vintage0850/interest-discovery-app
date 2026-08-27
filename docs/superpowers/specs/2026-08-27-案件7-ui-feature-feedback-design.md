# 案件7 UI/機能フィードバック — 仕様

- 日付: 2026-08-27
- ステータス: 仕様確定
- 対象: Android版 `app/`（Compose + Room）
- 実装担当: Kimi

## 1. 目的とスコープ

ユーザーから受けたUI/機能フィードバックのうち、次の5項目を既存の設計に沿って改善する。

1. 設定導線の統合
2. カテゴリ削除後にカテゴリ欄へ空白が残る不具合の修正
3. メインタスク編集のタップ判定拡大
5. サブタスクのリネーム
7. 優先順位・時間によるタスクの並び替え

画像アセットが必要な項目4（画面内の猫画像差し替え）と項目6（画像付き通知）は、ユーザー判断により本仕様の対象外とする。

## 2. 現状調査

| 調査対象 | 現状と設計上の意味 |
| --- | --- |
| `TaskListScreen.kt` | トップバーにGoogleアカウント、カテゴリ管理、通知設定の3導線が並ぶ。メインタスクはタイトル文字だけが編集タップ対象。サブタスクは完了切替のみ。カテゴリタブは削除済みIDを表示上は先頭へフォールバックするが、`selectedFilter` 自体には無効なIDが残る。DAOから来た一覧をカテゴリで絞るだけで、並び順の切替はない。 |
| `AddTaskScreen.kt` | カテゴリ一覧の変化に対して選択IDを補正済み。サブタスクは新規作成時だけ入力できる。今回の既存タスクのリネームでは変更不要。 |
| `TaskEditDialog.kt` | メインタスクのタイトル・予定時刻・通知時刻をまとめて編集する。サブタスク編集UIはない。 |
| `CategoryManagerScreen.kt` | カテゴリ削除前に移動件数を表示し、削除後はタスクを「未分類」に移す前提のUIになっている。空行を生成する処理はない。 |
| `MainActivity.kt` | カテゴリ管理と通知設定が別ルートで、一覧画面からそれぞれ直接遷移する。設定ハブのルートはない。 |
| `TaskViewModel.kt` | メインタスク編集はタスクID単位の `Mutex` と列単位更新を使う。サブタスクは完了切替のみで、現在はエンティティ全体を `updateSubTask` する。 |
| `Task.kt` / `TaskDao.kt` | `priorityScore = importance * 3 + urgency` が既に定義済み。全件取得は「未完了、優先度降順、締切昇順」の固定順。締切は `deadline` に保存済み。 |
| `SubTask.kt` / `TaskDao.kt` / `TaskRepository.kt` | サブタスクには既に `id`、`taskId`、`title` があり、更新経路も存在する。タイトル列追加やマイグレーションは不要。 |
| `GoogleCalendarSync.kt` | カレンダー予定の説明欄へ最新のサブタスク名を `sortOrder` 順で列挙できる。既存予定の更新APIも実装済み。 |
| ADR-001 | 更新失敗時に予定を重複作成しない、削除失敗時にリンクを失わない、サインアウトで `calendarEventId` を消さない、という同期ポリシーを維持する必要がある。 |

### 項目2のコード上の再発要因

カテゴリ削除後、現在の `TaskListScreen` は `selectedIndex` と `currentTab` だけを「すべて」へフォールバックする。`rememberSaveable` の `selectedFilter` には削除済みカテゴリIDが残り、タブも位置ベースで再利用される。この不整合を残さず、カテゴリ一覧更新時に選択値とComposableの識別を明示的に正規化する。

## 3. 全体設計判断

### 3.1 設定導線

検討した案は、トップバーのオーバーフローメニュー、ボトムシート、独立した設定ハブ画面の3つ。独立画面を採用する。

- トップバーのアクションは歯車アイコン1個だけにする。
- 歯車から新しい「設定」画面へ遷移する。
- 設定画面に「カテゴリ管理」「通知設定」「Googleカレンダー連携」を一覧表示する。
- カテゴリ管理と通知設定は既存画面へ遷移する。
- Googleカレンダー連携は、接続状態とメールアドレスを表示し、未接続時は接続、接続済み時はサインアウトを提供する。
- OAuth同意処理、未設定・拒否・通信失敗のメッセージは既存の `rememberCalendarAuthorization` と `CalendarAuthorizationOutcome` を再利用する。

独立画面は、設定項目が増えてもトップバーを再び増殖させず、戻る操作とTalkBackの読み上げ順も自然になる。並び替えは一覧の一時的な表示操作なので設定ハブには入れない。

### 3.2 並び替え

Roomのクエリ追加、複数Flow、設定値の永続化は行わない。`TaskListScreen` が受け取った一覧をカテゴリで絞り込んだ後、選択中のモードで表示上だけ安定ソートする。

- 初期値: `優先順位順`
- 選択肢1: `優先順位順`
  - 未完了を先頭
  - `priorityScore` 降順
  - 同点は `deadline` 昇順
  - さらに同点なら `createdAt` 昇順、`id` 昇順
- 選択肢2: `締切が近い順`
  - 未完了を先頭
  - `deadline` 昇順
  - 同時刻は `priorityScore` 降順
  - さらに同点なら `createdAt` 昇順、`id` 昇順

ここで「時間」は既存データモデル上の締切日時 `deadline` を意味する。時刻指定のない終日タスクは既存仕様どおり締切日の23:59:59として比較する。選択は `rememberSaveable` で画面回転後も維持するが、アプリ再起動後は初期値へ戻す。

## 4. 項目別仕様

### 項目1: 設定ボタンを1つに統合

#### 目的

一覧画面の設定関連アイコンを1か所に集約し、それぞれの意味と到達先を迷わず理解できるようにする。

#### 対象外

- 個別タスクのカレンダー登録ボタン
- 新規タスク画面の「Googleカレンダーに登録」スイッチ
- 設定値の新規追加や保存方式の変更
- OAuth、認証スコープ、Google Calendar APIの変更
- ドロワーやボトムナビゲーションの導入

#### 受入条件

1. 一覧画面トップバーに設定関連のアクションが歯車アイコン1個だけ表示される。
2. 歯車を1回タップすると、タイトルが「設定」の画面へ遷移する。
3. 設定画面から「カテゴリ管理」と「通知設定」の既存画面へ1回のタップで遷移できる。
4. 設定画面にGoogleカレンダーの状態が「未設定」「未接続」「接続済み」のいずれかで表示される。接続済みでメールアドレスが取得できる場合は併記する。
5. 未接続時は接続、接続済み時はサインアウトを実行できる。ADR-001に従い、サインアウトしても `calendarEventId` は消さない。
6. OAuthクライアント未設定時は接続操作を無効にし、既存の設定案内を表示する。
7. 各行と操作には内容が分かる日本語ラベルまたは `contentDescription` があり、タップ領域は48dp以上である。
8. 戻る操作で、設定の子画面から設定画面、設定画面からタスク一覧へ戻る。

#### 担当ファイル

- 新規: `app/src/main/java/com/example/myapplication/SettingsScreen.kt`
- 変更: `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- 変更: `app/src/main/java/com/example/myapplication/MainActivity.kt`
- 新規または変更: `app/src/androidTest/java/com/example/myapplication/SettingsScreenTest.kt`
- 変更不要: `NotificationSettingsScreen.kt`、`CategoryManagerScreen.kt`、`GoogleCalendarSync.kt`

### 項目2: カテゴリ削除後の空白を解消

#### 目的

カテゴリ削除後に、一覧上部のカテゴリ欄へ削除済みカテゴリの空白・ゴーストタブ・不正な選択状態を残さない。

#### 対象外

- カテゴリ削除時に所属タスクも削除すること
- カテゴリの並び替え
- Roomの外部キー、`ON DELETE SET NULL`、カテゴリテーブルの変更
- 「未分類」カテゴリをDBレコードとして作成すること

#### 受入条件

1. カテゴリFlowから削除対象が消えた同じ更新で、そのカテゴリのタブと占有していた空白が消える。
2. 削除したカテゴリを選択中だった場合、`selectedFilter` 自体を `すべて` に更新し、「すべて」タブを選択状態にする。
3. タブのComposableはカテゴリIDを安定キーとして扱い、前後のタブの表示状態を引き継がない。
4. 削除したカテゴリに属していたタスクは消えず、行のカテゴリ名と必要なタブが明示的に「未分類」と表示される。
5. 最後のユーザーカテゴリを削除しても、空のタブは表示されない。「すべて」と、未分類タスクが存在する場合だけ「未分類」を表示する。
6. 削除後に画面回転しても削除済みカテゴリIDが選択状態として復元されない。
7. `CategoryManagerScreen` の削除確認件数と削除完了メッセージは現行仕様を維持する。

#### 担当ファイル

- 変更: `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- 新規または変更: `app/src/androidTest/java/com/example/myapplication/TaskListScreenTest.kt`
- 調査のみ・変更不要: `app/src/main/java/com/example/myapplication/CategoryManagerScreen.kt`
- 変更不要: `Task.kt`、`TaskDao.kt`、`TaskRepository.kt`、`AppDatabase.kt`、`app/schemas/*.json`

### 項目3: メインタスク編集のタップ判定を拡大

#### 目的

タイトル文字の形・長さに依存せず、1回のタップでタスク編集ダイアログを開けるようにする。

#### 対象外

- ダブルタップ、長押し、インライン編集の追加
- カード全体を編集ボタンにすること
- カレンダー登録ボタン、完了チェックボックス、スワイプ削除の挙動変更
- `TaskEditDialog` の編集項目変更

#### 受入条件

1. タスク行の左側情報領域（タイトル、締切、カテゴリ、状態表示を含む）を1回タップすると `TaskEditDialog` が開く。
2. 左側情報領域のタップ高さは最低48dp、幅はカレンダーアイコン直前まで確保する。
3. 短い1文字タイトル、取り消し線付きの完了タスクでも同じタップ領域を持つ。
4. カレンダーアイコンはカレンダー操作だけ、チェックボックスは完了切替だけを行い、編集ダイアログを開かない。
5. 横スワイプによる削除は従来どおり動作し、スワイプを編集タップとして誤検出しない。
6. 編集領域はTalkBackから「タスクを編集する」操作として認識できる。

#### 担当ファイル

- 変更: `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- 新規または変更: `app/src/androidTest/java/com/example/myapplication/TaskListScreenTest.kt`
- 調査のみ・変更不要: `app/src/main/java/com/example/myapplication/TaskEditDialog.kt`
- 変更不要: `TaskViewModel.kt`、データ層、Google Calendar関連ファイル

### 項目5: サブタスクをリネーム可能にする

#### 目的

登録済みサブタスクの名前を、完了状態や表示順を失わずに変更できるようにする。Googleカレンダー連携済みの親タスクでは、予定の説明欄も最新の名前へ同期する。

#### 対象外

- サブタスクの追加、削除、並び替え
- サブタスクごとの締切・優先度・通知・カレンダー予定
- メインタスクの編集ダイアログへ全サブタスク編集機能を統合すること
- `subtasks` テーブルの列追加、Roomバージョン更新、マイグレーション

#### 受入条件

1. サブタスク行のチェックボックス以外のテキスト領域を1回タップすると、現在名を初期値にした「サブタスク名を変更」ダイアログが開く。
2. 編集タップ領域は最低48dpの高さを持つ。チェックボックスのタップは完了切替だけを行う。
3. 前後の空白を除いた名前が空なら確定できない。変更がなければDB更新もCalendar API呼び出しも行わない。
4. 確定時は `title` 列だけを更新し、`isCompleted`、`sortOrder`、`taskId` を変更しない。
5. 完了切替も `isCompleted` 列だけの部分更新へ変更し、リネームとの競合で名前を巻き戻さない。
6. 親タスクがカレンダー未連携ならローカル更新だけで完了する。
7. 親タスクがカレンダー連携済みなら、親タスクIDの既存 `Mutex` 内で最新タスクを取得し、更新後のサブタスク一覧を使って既存予定を1回更新する。
8. カレンダー更新の404/410、ネットワークエラー、認証エラーは既存の `doSyncToCalendar` とADR-001のポリシーに従う。ネットワークエラーで予定を作り直さない。
9. カレンダー同期に失敗してもローカルのリネームは保持し、既存のスナックバーメッセージで失敗を知らせる。
10. 既存のサブタスク名には新しい文字数制限を遡及適用しない。新規作成UIの仕様も変更しない。

#### 担当ファイル

- 変更: `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- 変更: `app/src/main/java/com/example/myapplication/TaskEditDialog.kt`（サブタスク名専用ダイアログを追加）
- 変更: `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- 変更: `app/src/main/java/com/example/myapplication/data/TaskDao.kt`
- 変更: `app/src/main/java/com/example/myapplication/data/TaskRepository.kt`
- 変更: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt`
- 新規または変更: `app/src/androidTest/java/com/example/myapplication/TaskListScreenTest.kt`
- 新規: `app/src/androidTest/java/com/example/myapplication/data/TaskDaoSubTaskTest.kt`
- 調査のみ・変更不要: `app/src/main/java/com/example/myapplication/data/SubTask.kt`
- 変更不要: `AppDatabase.kt`、`app/schemas/*.json`、`GoogleCalendarSync.kt`、`GoogleCalendarApi.kt`

### 項目7: 優先順位・時間でタスクをソート

#### 目的

ユーザーが、その場の判断軸に応じて「重要なもの」または「締切が近いもの」を先に確認できるようにする。

#### 対象外

- 昇順・降順の個別切替
- 名前順、作成順、カテゴリ順、手動ドラッグ順
- サブタスクの並び替え
- 並び順のアプリ再起動をまたぐ永続化
- DAOの動的クエリ、Roomスキーマ、ViewModelのFlow構成変更

#### 受入条件

1. カテゴリタブとタスク一覧の間に、現在値が分かる「並び順」操作を表示する。
2. 操作を1回タップすると「優先順位順」「締切が近い順」を選択できる。
3. 初回表示は「優先順位順」で、現行DAO順と同じ意味を保つ。
4. 並び替えは選択中カテゴリで絞り込んだ後の一覧に適用する。
5. 両モードで未完了タスクを完了タスクより先に表示する。
6. 同順位の規則は「3.2 並び替え」に定義したとおりで、入力順や再コンポーズにより順番が揺れない。
7. モード切替後も各行のComposeキーはタスクIDのままで、チェック状態やスワイプ状態が別タスクへ移らない。
8. 選択モードは画面回転後も維持され、アプリを終了して再起動すると「優先順位順」へ戻る。
9. 並び替え操作には日本語ラベルがあり、TalkBackで現在の並び順と選択肢を認識できる。

#### 担当ファイル

- 変更: `app/src/main/java/com/example/myapplication/TaskListScreen.kt`
- 新規: `app/src/test/java/com/example/myapplication/TaskListOrderingTest.kt`
- 新規または変更: `app/src/androidTest/java/com/example/myapplication/TaskListScreenTest.kt`
- 調査のみ・変更不要: `app/src/main/java/com/example/myapplication/data/Task.kt`、`TaskDao.kt`、`TaskViewModel.kt`
- 変更不要: `TaskRepository.kt`、`AppDatabase.kt`、`app/schemas/*.json`

## 5. DB・アーキテクチャ・既存設計との整合

### DB変更

Roomのテーブル、列、インデックス、バージョンは変更しない。サブタスクのリネームには既存 `title` 列を使い、DAOへ列単位の `UPDATE` を追加するだけとする。`app/schemas/*.json` に差分が出た場合は実装を止め、**「Claudeへ設計判断を依頼」**として差し戻す。

### ADR-001 / GoogleCalendarSync

- Google Calendar REST API + OAuthを維持し、CalendarContractを再導入しない。
- サブタスク名変更は既存予定の `patchEvent` を使う。
- 404/410以外で予定を作り直さない。
- サインアウト時に `calendarEventId` を消さない。
- `Authorization` ヘッダーのログ秘匿を変更しない。
- 認証、課金、公開API、外部公開操作は変更しない。

### Claudeへの設計判断依頼

現時点では不要。上記5項目は既存のNavigation Compose、Room部分更新、`TaskViewModel` のタスクID単位Mutex、`GoogleCalendarSync.updateEvent` の範囲で実現できる。

ただし、実装中に次のいずれかが必要と判明した場合は実装せず、TASK.mdへ **「Claudeへ設計判断を依頼」** と記載して止める。

- Roomスキーマ、DBバージョン、マイグレーションの変更
- 並び順の永続化方式を新設する判断
- OAuth、Google Calendar APIのリクエスト形式、同期失敗ポリシーの変更
- 公開API、認証、課金、外部サービス設定の変更

## 6. 検証方針

実装はTDDで、失敗テストを確認してから最小実装を行う。

- JVMテスト
  - 2つの並び順と全タイブレーク条件
  - サブタスク名のtrim、空入力、変更なし、部分更新
  - カレンダー未連携／連携済み／同期失敗時のリネーム
  - リネームと完了切替が互いの列を巻き戻さないこと
- Compose instrumented test
  - 一覧トップバーの設定導線が1個であること
  - 設定ハブから各画面へ遷移できること
  - カテゴリ削除相当のstate更新でゴーストタブが残らないこと
  - メインタスクの左情報領域1回タップで編集ダイアログが開くこと
  - サブタスクのテキスト領域1回タップでリネームダイアログが開くこと
  - カレンダー／完了チェック操作が編集を開かないこと
  - 並び順の切替で表示順が変わること
- DAO instrumented test
  - サブタスク名更新で完了状態・表示順を保持すること
  - 完了状態更新でタイトル・表示順を保持すること
- 共通コマンド
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`
  - エミュレータ接続時は `./gradlew :app:connectedDebugAndroidTest`

## 7. Kimiへの実装制約

- 実装対象は `app/` のみ。`:shared` のKMP版へ同時移植しない。
- 1作業単位は最大3ファイル、半日以内、テスト合格後に1コミットとする。
- `TaskListScreen.kt` を複数作業単位で触る場合は、設定導線 → カテゴリ／タップ領域／ソート → サブタスクUIの順に直列実行する。
- 既存の未コミット差分を戻したり、案件7と無関係なリファクタリングを混ぜたりしない。
- 各作業単位で Red → Green → Refactor を記録し、最後に全体コマンドを実行する。
