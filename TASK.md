# TASK — MyApplication カレンダー連携

このファイルは `C:\Users\vinta\Claude_Test\AGENTS.md`（共通運用規則）に基づく作業台帳。
着手前に必ず本ファイルの「対象ファイル」を確認し、担当が解除されていないファイルは編集しない。

## 案件

Google カレンダー連携（CalendarContract → Calendar REST API v3 + OAuth 2.0 への移行）の
実機検証前バグ修正。移行そのものは実装完了・ビルド通過済み。

関連ノート:
- `app-studio/Obsidian_Comapany/開発・実装/ADR-001 Googleカレンダー連携をREST API+OAuthへ移行.md`
- `app-studio/Obsidian_Comapany/開発・実装/Android OAuth・Calendar API の落とし穴.md`
- `app-studio/Obsidian_Comapany/Projects/MyApplication カレンダー連携 移行プロジェクト.md`

## 状態

`実装中` → `レビュー中` → **`完了`**（2026-08-24、Codex役としてClaudeがGate 4判定：`PASS`）

| 単位 | 内容 | 担当 | 状態 |
| --- | --- | --- | --- |
| WU-A | 401 時のアクセストークン無効化 | Kimi | **完了** |
| WU-B | `CancellationException` の再 throw | Kimi | **完了** |
| WU-C | カレンダー連携操作のタスク単位排他制御と部分更新 | Kimi | **完了**（2026-08-21 確認） |
| WU-D | insert タイムアウト時の重複予定対策 | — | **保留**（下記「設計判断」参照） |

## 対象ファイル（担当宣言）

WU-A / WU-B / WU-C は Kimi が担当する。担当解除まで他の AI は編集しない。

- `app/src/main/java/com/example/myapplication/data/calendar/GoogleAuthManager.kt`
- `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarSync.kt`
- `app/src/main/java/com/example/myapplication/TaskViewModel.kt`
- `app/src/main/java/com/example/myapplication/data/TaskDao.kt`
- `app/src/test/**`（新規テスト）

**編集してはいけないファイル:**

- `app/schemas/*.json` — スキーマ変更は発生しない。変わったら設計判断のやり直し
- `local.properties` — OAuth クライアント ID 設定済み。触らない
- `SETUP.md` — 2026-08-19 に更新済み

## 受入条件

### WU-A: 401 時のアクセストークン無効化

- `GoogleCalendarSync.request()` が HTTP 401 を受けたとき、`GoogleAuthManager` のキャッシュ済みトークンを破棄する。
- **破棄するのは「今回の通信に使ったトークンと一致する場合だけ」**。
  並行処理が先に新しいトークンを取得済みのとき、後から返ってきた古い 401 で新トークンを消してはならない。
- 401 → 再取得 → 再試行を自動で行う場合、**再試行は1回まで**。2回目の 401 は `CalendarResult.Unauthorized` を返す。
- テスト: 401 応答後にキャッシュが破棄されること／別トークンが渡された 401 ではキャッシュが残ること。

### WU-B: `CancellationException` の再 throw

- `GoogleCalendarSync.kt` と `GoogleAuthManager.kt` の `catch (e: Exception)` および
  `runCatching` が `CancellationException` を握り潰さないようにする。
- 画面を閉じるなどで処理が中断されたとき、ユーザーにエラーメッセージが出ないこと。
- テスト: キャンセル時に `notifyCalendarFailure` 相当が呼ばれないこと。

### WU-C: 排他制御と部分更新

- 同じタスクに対するカレンダー操作が同時に走らないようにする（タスク ID 単位の排他）。
- 連携トグルを素早く2回操作しても、**予定が2件作られない**こと。
- カレンダー連携の結果を保存する際、`Task` 全列を上書きせず **`calendarEventId` だけを更新**する。
  `TaskDao` に対象列だけを更新する `@Query` を追加してよい（**スキーマ変更は不可**）。
- テスト: 連続実行で `insertEvent` が1回しか呼ばれないこと／他の列が巻き戻らないこと。

### 全単位共通

- `./gradlew :app:assembleDebug` と `./gradlew :app:testDebugUnitTest` が成功する。
- アクセストークンがログに出ない（`HttpLoggingInterceptor` の `redactHeader("Authorization")` を維持）。
- 重要な処理には非エンジニアにも読める日本語コメントを付ける。
- ADR-001 の「同期失敗時のポリシー」を変更しない。変更が必要と判断したら実装せず差し戻す。

## 設計判断

### 2026-08-19 / Claude

Codex（CTO・品質保証）の独立レビューで挙がった優先度「高」4件について裁定した。
指摘の実在は Claude が該当コードを開いて確認済み。

**WU-A（401 でトークンが残る）— 採用**

`GoogleCalendarSync.kt:150` が 401 を `Unauthorized` に変換する際、
`clearCachedToken()` を呼んでいない。キャッシュは `TOKEN_CACHE_MILLIS = 5分`
（`GoogleAuthManager.kt:334`）保持されるため、Google 側で権限が取り消された場合に
最大5分間 401 を繰り返す。ADR-001 が定めた「期限切れなら `authorize()` を呼び直す」
という設計が、この経路では機能していない。**設計の欠落であり、実装漏れの修正にあたる。**

「使用したトークンと一致する場合だけ破棄」という条件は必須。無条件破棄にすると
並行処理が取得した新トークンを巻き添えで消し、認可のやり直しが増える。

**WU-B（キャンセルの握り潰し）— 採用**

プロジェクト全体を検索したが `CancellationException` を再 throw している箇所が1つもない。
コルーチンの標準的な誤りで、修正は小さく副作用がない。

**WU-C（再入による予定重複）— 採用。ただし DB 変更は列単位の更新のみ許可**

`TaskViewModel.kt` に `Mutex` / `synchronized` が一切存在しない。
`setCalendarLinked` は UI から渡された古い `Task` の `calendarEventId` を見て
二重登録を防ごうとしているが、素早い連続操作で両方が `null` を見て予定を2件作る。
**ADR-001 が最も回避したかった「孤児予定」の新たな発生経路であり、修正必須。**

`TaskDao` への `@Query` 追加は承認する。**テーブル定義は変更しない**ため
Room のスキーマバージョンは v4 のまま、マイグレーション追加も不要。
`app/schemas/4.json` に差分が出たら設計判断のやり直しとする。

**WU-D（insert タイムアウトで重複作成）— 保留。今は実装しない**

「クライアント側で安定したイベント ID を生成して `events.insert` の `id` に渡し、
409 Conflict を『作成済み』として扱う」という提案は Calendar API 上は正当な手法だが、
冪等キーの設計、409 の分岐、既存データとの整合という**アーキテクチャ変更**にあたる。

**保留の理由:** この実装はまだ実機・エミュレータで一度も動作確認されていない。
実際の失敗の出方を見ないまま冪等化を入れると、想定に基づく設計を重ねることになる。
実機検証（同意フロー、オフライン削除→取り消し、終日予定の日付）を終えてから再判断する。

**ただし、誤解を招く UI 文言だけは先に直す価値がある**（作成できたかどうか不明な状態を
「ネットワークに接続できませんでした」と断定している）。これは WU-C 完了後に
別単位として切り出す。今回のスコープには含めない。

### 却下済み事項（再提案禁止 / ADR-001 由来）

以下は判断済み。蒸し返さないこと。

1. CalendarContract 版のフォールバックは残さない
2. サインアウト時に `calendarEventId` をクリアしない
3. メール取得のための Credential Manager 追加は見送り
4. `google-api-services-calendar` は不採用（Retrofit を採用）
5. Android クライアントでリフレッシュトークンは扱わない

## テスト結果

（実行した担当者が追記する。コマンドと結果をそのまま書くこと）

| 日付 | 担当 | コマンド | 結果 |
| --- | --- | --- | --- |
| 2026-08-18 | Claude | `./gradlew :app:assembleDebug --rerun-tasks` | BUILD SUCCESSFUL。`BuildConfig.GOOGLE_OAUTH_CLIENT_ID` に値が入ることを確認 |
| 2026-08-18 | Claude | `./gradlew connectedDebugAndroidTest` | **失敗**。`dl.google.com` に到達できず依存解決不能（一時的な DNS 断）。5時間ハング後に失敗。ネットワークは復旧済み、未再実行 |
| 2026-08-19 | Kimi | `./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.data.calendar.GoogleCalendarSyncTest"` | BUILD SUCCESSFUL。3 テストすべて合格 |
| 2026-08-19 | Kimi | `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL。既存テストも含め全テスト合格 |
| 2026-08-19 | Kimi | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL。APK 生成完了 |
| 2026-08-19 | Kimi | `./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.data.calendar.GoogleCalendarSyncTest"` | BUILD SUCCESSFUL。7 テストすべて合格（キャンセル関連 4 テストを含む） |
| 2026-08-19 | Kimi | `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL。全テスト合格 |
| 2026-08-19 | Kimi | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL。APK 生成完了 |
| 2026-08-21 | Kimi | `./gradlew :app:testDebugUnitTest :app:assembleDebug` | BUILD SUCCESSFUL。`TaskViewModelCalendarTest` の4テストを含め全テスト合格 |
| 2026-08-21 | Claude | compileSdk/targetSdk 36 化後 `./gradlew :app:assembleDebug :app:testDebugUnitTest` | BUILD SUCCESSFUL |
| 2026-08-21 | Claude | `./gradlew :app:assembleRelease`（署名+minify+shrinkResources 有効化後、初回） | BUILD SUCCESSFUL。R8/lintVital 通過 |
| 2026-08-21 | Claude | release APK をエミュレータ `Pixel_8a` に実機インストールし起動 | **クラッシュ**（`NoSuchMethodException: TaskViewModel.<init> [Application]`）。案件2の「監査で見つかった重大な不具合」参照 |
| 2026-08-21 | Claude | debug APK でも同一クラッシュを再現確認（release/minify 起因ではなく既存バグと断定） | 再現。「My Application keeps stopping」ダイアログを screencap で確認 |
| 2026-08-21 | Claude | `MainActivityLaunchTest` 新規作成、修正前の `MainActivity.kt` に対し `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.MainActivityLaunchTest` | **RED**: 同一の `NoSuchMethodException` でテスト失敗（想定通りの失敗） |
| 2026-08-21 | Claude | `MainActivity.kt` に `ViewModelProvider.Factory` を明示する修正を適用後、同テストを再実行 | **GREEN**: BUILD SUCCESSFUL |
| 2026-08-21 | Claude | `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`（修正後、全テスト） | BUILD SUCCESSFUL。インストゥルメンテーション5件・ユニットテスト全て合格（0 failed） |
| 2026-08-21 | Claude | `./gradlew :app:assembleRelease`（修正後、再ビルド） | BUILD SUCCESSFUL |
| 2026-08-21 | Claude | 修正後の release APK をエミュレータに実機インストール・起動、screencap で確認 | クラッシュなし。タスク一覧画面（空状態）が正常表示、logcat に `FATAL EXCEPTION` なし |

## 引き継ぎメモ

### 完了事項（2026-08-21 / Claude・案件2）

- Google Play ストア公開レベルへの監査・実装を実施（詳細は「案件2」参照）
- **起動即クラッシュの重大バグを発見・TDDで修正**（`MainActivity.kt` の ViewModel 生成方法）。回帰テスト `MainActivityLaunchTest` を追加
- リリース署名設定を追加し、開発用キーストアを生成（`SETUP.md`「リリース署名」参照）
- release ビルドの `minifyEnabled` / `shrinkResources` を有効化し、必要な ProGuard keep ルールを追加。実機起動確認済み
- `compileSdk` / `targetSdk` を 36 に更新（Google Play の 2026-08-31 期限対応）
- `PRIVACY_POLICY.md` のドラフトを新規作成
- ユーザー対応が必要な項目（アプリ名・パッケージ名の決定、プライバシーポリシーの公開、キーストアのバックアップ、Play Console でのストア公開作業）を「案件2」に整理して記録。外部公開行為そのものは AGENTS.md の禁止事項により実行していない

### 完了事項（2026-08-21 / Kimi）

- WU-C「カレンダー連携操作のタスク単位排他制御と部分更新」を確認・記録
  - 作業ツリーには既に実装済みだった（未コミット）。本ファイルの状態表記が古かっただけと判断し、内容を検証した
  - `TaskViewModel.kt`: タスク ID ごとの `Mutex`（`calendarMutexes`）を追加済み。`setCalendarLinked` はロック取得後に最新の `Task` を再取得してから ON/OFF を判定するため、素早い連続トグルでも予定が2件作られない
  - `TaskDao.kt`: `updateCalendarEventId(taskId, calendarEventId)` の部分更新クエリが実装済み。`TaskViewModel` のカレンダー登録・解除・再作成の全経路がこれを使い、`Task` 全列上書き（`updateTask`）を使っていない
  - テーブル定義・スキーマは変更なし（`app/schemas/` に差分なし。Room スキーマ v4 のまま）
  - テスト: `app/src/test/java/com/example/myapplication/TaskViewModelCalendarTest.kt` が新規作成済みで、受入条件に対応する4ケースが揃っている
    - 連続トグルで `insertEvent` が1回だけ呼ばれる
    - 部分更新で他列（タイトル等）が巻き戻らない
    - 連携解除時も部分更新のみ
    - 新規タスク追加時の連携登録も部分更新のみ
  - `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL（全テスト合格、警告のみ・エラーなし）
  - 受入条件（TASK.md 記載分）はすべて満たしていることを確認した

### 完了事項（2026-08-19 / Kimi）

- WU-A「401 時のアクセストークン無効化」を実装完了
  - `GoogleAuthManager` に `invalidateTokenIfMatches(token)` を追加。今回の通信に使ったトークンとキャッシュが一致する場合だけキャッシュを破棄する
  - `GoogleCalendarSync.request()` で 401 を受けたら上記を呼び出し、トークンを再取得して 1 回だけ再試行する。2 回目の 401 は `CalendarResult.Unauthorized` を返す
  - 並行処理が先に新しいトークンを取得済みの場合、古いトークンの 401 で新トークンを巻き添えで消さない
  - テスト: `app/src/test/java/com/example/myapplication/data/calendar/GoogleCalendarSyncTest.kt` を新規作成。3 ケースを TDD で実装・合格
- WU-B「`CancellationException` の再 throw」を実装完了
  - `GoogleCalendarSync.kt` の `runCatching` と `catch (e: Exception)` を、コルーチンのキャンセル例外を握り潰さない形に修正
    - トークン取得中・API 通信中・401 後のトークン無効化・401 後のトークン再取得、いずれの経路でも `CancellationException` をそのまま再 throw する
  - `GoogleAuthManager.kt` 内の `runCatching` も同様にキャンセル例外を貫通させる
  - テスト: `GoogleCalendarSyncTest.kt` に追加済みのキャンセル関連 4 テストが合格
- `./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug` ともに成功

### 完了事項（2026-08-19 / Claude）

- GCP セットアップ完了。OAuth クライアント ID を `local.properties` に設定済み、`BuildConfig` 反映確認済み
- `SETUP.md` を現行の Google Auth Platform 画面に合わせて更新
- Codex による独立レビューを実施し、優先度「高」4件について設計裁定を記録（上記）

### Gate 4 レビュー（2026-08-24 / Claude、ユーザー指示によりCodex役として代行）

**判定: `PASS`**

ユーザーから「Codex役として私がGate 4レビューを代行」との明示指示を受けて実施した。
本来Codexが担当する工程をClaudeが代行する例外対応であり、AGENTS.mdの通常運用（Codexが独立して品質ゲートを判定する）とは異なる点に注意。
自動レビュー（`code-review`スキルのバックグラウンドフォーク）も併用したが、返ってきた指摘が本プロジェクトと無関係な別コードベース（`com.karaoke.growth`パッケージ）を指しており明らかな誤動作だったため**破棄**し、手動レビューのみで判定した。

**確認したこと（`git diff`で全21ファイルの変更を通読）:**
- テスト・型・ビルド: `testDebugUnitTest`／`assembleDebug`／`assembleRelease`（R8込み）／`connectedDebugAndroidTest`（エミュレータ`Pixel_8a`、11件）すべて成功
- 機密情報: `local.properties`・`*.jks`は`.gitignore`済み。ログの`Authorization`ヘッダは`redactHeader`維持。SETUP.mdにクライアントID等の実値は無く例示のみ
- 危険な権限: 追加された`POST_NOTIFICATIONS`は今回の機能に必要な最小権限。拒否時も機能を隠さず静かにスキップする設計（`NotificationManagerCompat.areNotificationsEnabled()`チェック）を確認
- 入力値・エラー処理: `CancellationException`の再throwパターンが新規コードにも一貫して適用されている。401リトライ・トークン無効化のmutex保護も確認。Room移行（v3→v4、v4→v5）はスキーマJSON書き出しとマイグレーションテストで検証済み
- 保守性: 部分更新（`update*By`系クエリ）パターンが新規追加分にも一貫。テスト用の依存注入（`CalendarLogger`、`FreeTimeCheckScheduler`、`authState`関数注入）が既存の設計方針に沿っている

**指摘（低優先度・ブロッキングではない。次回改善候補としてメモ）:**
1. `FreeTimeCheckWorker.doWork()`で`notifier.notifyTaskStart(task)`の後に`repository.insertNotifiedSlot(...)`を呼んでいるため、この間にプロセスが強制終了すると次回Worker実行で同じ空き時間帯に再通知される可能性がある（`work/FreeTimeCheckWorker.kt:70-74`）。実害は稀な重複通知程度で、データ破損は無い
2. 空き時間検知が予定の`transparency`（予定として表示/表示しない）や参加者の「欠席」応答を考慮していないため、「予定はあるが実際には空いている」時間帯を誤って埋まっていると判定しうる（`data/calendar/CalendarEventListParsing.kt`）。設計ドキュメントのスコープ外（YAGNI）だが、実運用で気になれば次回検討
3. `TaskViewModel.calendarMutexes`（案件1 WU-C由来）はタスクごとの`Mutex`を保持し続け、削除されたタスク分もエントリが残る。実害はメモリ数十バイト程度で軽微

**再確認方法:** 上記1・2は実機で1時間おきの実行を待つ実地検証、3はメモリプロファイラでの長時間運用確認が必要。いずれも次回セッションでのフォローアップ候補とする。

### 次の担当と行動

**次の担当: なし（Gate 4 PASSにより完了）**

AGENTS.mdの通常フロー「通常の変更はPASSになった時点で完了とし、ユーザーの最終承認を要求しない」に基づき、案件1は完了とする。
未コミットの差分（`git status`）はユーザー判断でコミットすること。

**並行して残っている作業（担当: ユーザー）**

エミュレータ `Pixel_8a` は起動済み、アプリもインストール済み。
Google アカウント（`vintage0850@gmail.com`）でのログインはユーザー本人の操作が必要。
確認項目は ADR-001 の「実機で最初に確認すべきこと」4点。

### 未解決・注意事項

- **同意画面へのスコープ `calendar.events` 登録が未完了。** Console の「データアクセス」画面が
  繰り返しフリーズして操作できない。テスト運用では必須ではないが、
  `403 insufficient_permissions` が出たらまずここを疑う
- **`gcloud` が壊れている。** SDK 580.0.0 の `lib/third_party` が丸ごと欠落しており
  Python 経由の実コマンドが全滅する。`gcloud auth list` だけは通るので誤認しやすい
- ~~**`connectedDebugAndroidTest` 未実行。**~~ 2026-08-21 に Kimi がエミュレータ `Pixel_8a` で実行し、5件全て合格を確認済み（下記「案件2」参照）。本番 DB 名 `task_database` を使うため、実行はエミュレータ限定・普段使いの端末では実行しないことは引き続き有効

---

## 案件3：空き時間検知による「始めさせる」通知機能

**状態:** `依頼` → `設計判断済み・実装待ち` → `実装完了・レビュー中` → **`完了`**（2026-08-24、Codex役としてClaudeがGate 4判定：`PASS`）
**担当:** Claude（設計レビュー・裁定 → ユーザー指示によりKimi役として実装 → ユーザー指示によりCodex役としてGate 4レビューも実施）

### 実装完了報告（2026-08-23 / Claude、Kimi役として）

ユーザーから「案件3をKimi担当でやらせて」との明示指示を受け、設計裁定を行ったClaudeがそのまま実装まで担当した（本来の担当割り当てはKimiだが、今回はユーザー指示による例外）。

**実装したもの:**
- `data/Task.kt`: `TaskStatus` enum（TODO/IN_PROGRESS）、`Task.status` 列追加
- `data/AppDatabase.kt`: `MIGRATION_4_5`（tasks.status 列追加 + notified_slots テーブル新設）。schemaバージョンv5、`app/schemas/.../5.json` 書き出し確認済み
- `data/NotifiedSlot.kt`: 新規エンティティ
- `data/TaskDao.kt` / `TaskRepository.kt`: `updateTaskStatus`、`getTopEligibleTaskForNotification`、`isSlotNotified`、`insertNotifiedSlot`、`deleteNotifiedSlotsOlderThan` を部分更新パターンで追加
- `TaskViewModel.kt`: `toggleCompleted` で完了時に `status` を強制的に TODO へ戻す処理を追加。WorkManager定期登録は `FreeTimeCheckScheduler` インターフェースで抽象化し単体テスト時に実際のWorkManagerを呼ばないようにした
- `data/calendar/FreeSlotFinder.kt`: `findNextFreeSlot` 純粋関数（空きギャップ計算）
- `data/calendar/GoogleCalendarApi.kt` / `GoogleCalendarSync.kt`: `listEvents` / `listTodayEvents` 追加
- `data/calendar/CalendarEventListParsing.kt`: 終日予定（date）・時刻指定予定（dateTime）を統一的に変換する `toEventSlotOrNull`
- `work/FreeTimeCheckWorker.kt`: 1時間おきの空き時間チェックWorker。認可状態は`GoogleAuthManager.authState`が`open`でないため、テスト容易性のため関数注入（`authState: () -> CalendarAuthState`）で抽象化した
- `work/FreeTimeNotifier.kt`: 通知発行（`NotificationManagerCompat.areNotificationsEnabled()`で権限チェック、無ければ静かにスキップ）
- `TaskStartActionReceiver.kt`: 通知の「始める」ボタンから`status`のみ部分更新。`exported="false"`、`PendingIntent.FLAG_IMMUTABLE`設定済み
- `TaskListScreen.kt`: `IN_PROGRESS`時に「進行中」`AssistChip`バッジ表示
- `MainActivity.kt`: 初回起動時にPOST_NOTIFICATIONS権限をリクエスト（API 33+）
- `AndroidManifest.xml`: `POST_NOTIFICATIONS`権限、`TaskStartActionReceiver`登録（exported=false）
- `proguard-rules.pro`: `FreeTimeCheckWorker`の2引数コンストラクタを明示的に温存（WorkManagerのリフレクション生成対策）

**設計ドキュメントからの逸脱・追加判断:**
- `GoogleCalendarSync.kt`・`GoogleAuthManager.kt`は案件1でKimiが担当宣言中のため、`GoogleAuthManager`は一切変更していない。`FreeTimeCheckWorker`の認可チェックは`authManager.authState`を直接読まず、関数注入で抽象化することでテスト容易性を確保した（`GoogleAuthManager.authState`が`open`でなくサブクラス化できないため）
- `FreeTimeCheckWorker`のコンストラクタに`@JvmOverloads`を付与。Kotlinのデフォルト引数だけではWorkManagerの既定WorkerFactoryが期待する2引数コンストラクタ（`Context, WorkerParameters`）がリフレクションから見えず実機で生成に失敗するため（release buildで発覚しやすい典型的な罠）
- `TaskViewModel`のWorkManager定期登録呼び出しを`FreeTimeCheckScheduler`インターフェースで抽象化。`WorkManager.getInstance()`は初期化されていないと例外を投げるため、単体テストで`TaskViewModel`を生成するたびに落ちるのを防いだ
- `MainActivityLaunchTest`に`GrantPermissionRule`を追加（`androidx.test:rules`を新規依存追加）。POST_NOTIFICATIONS権限ダイアログがActivity起動中に出ると`RESUMED`に到達できず既存の回帰テストが誤って失敗するため

**テスト結果:**

| 日付 | コマンド | 結果 |
| --- | --- | --- |
| 2026-08-23 | `./gradlew :app:testDebugUnitTest --tests "com.example.myapplication.data.calendar.FreeSlotFinderTest"` | BUILD SUCCESSFUL。8ケース全て合格 |
| 2026-08-23 | `./gradlew :app:testDebugUnitTest`（既存分含む全体） | BUILD SUCCESSFUL。全テスト合格 |
| 2026-08-23 | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| 2026-08-23 | `./gradlew :app:assembleRelease`（proguard-rules.pro追記後） | BUILD SUCCESSFUL。R8/lintVital通過 |
| 2026-08-23 | `./gradlew :app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL |
| 2026-08-23 | エミュレータ`Pixel_8a`（データwipe後）で`./gradlew :app:connectedDebugAndroidTest` | BUILD SUCCESSFUL。11件全て合格（MigrationTestのv4→v5ケース、FreeTimeCheckWorkerTestの5分岐、MainActivityLaunchTest含む） |
| 2026-08-23 | debug APKを実機インストール・起動、POST_NOTIFICATIONS権限ダイアログの表示・許可・その後の画面遷移をscreencapで確認 | クラッシュなし。権限ダイアログ表示→許可→タスク一覧（空状態）へ正常遷移を確認 |

**未検証（実機・実データでの確認が必要）:**
- 実際にGoogleカレンダーと連携した状態でのWorker実行（本物のカレンダー予定に対する空き時間検知）は、テスト用のフェイクでの分岐網羅のみ。実際の空き時間で通知が届くかは1時間おきの実行を待つ実地検証が必要
- 通知の「始める」ボタンをタップした際の実際の`TaskStartActionReceiver`起動（androidTestでは`TaskRepository.updateStatus`相当のロジックをWorker側でのみ検証。Receiver自体の実機起動は未検証）

### 対象ファイル（担当宣言：Kimi）※実際はClaudeがKimi役として実施

設計ドキュメント: `docs/superpowers/specs/2026-08-23-proactive-start-notification-design.md`
（元は「承認待ち(ユーザーレビュー前)」。ユーザーの指示により、実装はKimiに委ね、Claudeは設計裁定のみ行った。）

### 設計判断（2026-08-23 / Claude）

設計ドキュメントを読み、既存コード（`Task.kt`／`AppDatabase.kt`／`GoogleAuthManager.kt`／`AndroidManifest.xml`／`app/build.gradle.kts`）と突き合わせて事実確認した。

**採用。全体方針は妥当。** `authState`（`CalendarAuthState.Authorized`）、`Task.priorityScore`（computed property）など、設計が前提とする既存要素は実際に存在することを確認した。`WorkManager`依存（`androidx.work:work-runtime-ktx`）は既にHEADの`app/build.gradle.kts`に存在しており追加変更不要。Room スキーマは現在v4のため、`Task.status`列と`NotifiedSlot`テーブル追加は v4→v5 の新規マイグレーションになる（MIGRATION_3_4と同様の作り直しパターンを踏襲すること）。

**ただし、以下4点は受入条件として明示的に追加する（設計ドキュメントに欠落あり、Gate 4で必須確認）:**

1. **通知権限が無い場合のクラッシュ防止。** `POST_NOTIFICATIONS`（API 33+）が拒否されている状態で`NotificationManagerCompat.notify()`を呼ぶと`SecurityException`でWorkerがクラッシュしうる。通知発行前に`NotificationManagerCompat.areNotificationsEnabled()`相当のチェックを入れ、権限が無ければ静かにスキップすること。
2. **終日予定のパース。** Google Calendar APIのレスポンスは終日予定だと`start.date`（時刻無し）、時刻指定予定だと`start.dateTime`になる。`CalendarEventSlot`へ変換する処理で両方を明示的に扱い、終日予定は端末のデフォルトタイムゾーンで日付境界（0:00〜24:00）として区間化すること。この変換のユニットテストを追加する（設計ドキュメントのテスト方針に無かった項目）。
3. **`TaskStartActionReceiver`のセキュリティ。** マニフェストで`android:exported="false"`にする。`PendingIntent`生成時は`PendingIntent.FLAG_IMMUTABLE`を付ける（API 31+で必須）。
4. **`FreeTimeCheckWorker`の実行間隔は目安であり保証されない旨をコメントに明記。** `PeriodicWorkRequest`の最小間隔は15分だが、Doze等でOSが実際の実行を遅延させることがある。「1時間おき」という設計上の期待とズレても不具合ではないことをコード上のコメントとテストの前提に反映すること。

**却下・変更なし:** スコープ（YAGNI項目）、データモデル方針（`status`列・`NotifiedSlot`テーブル）、コンポーネント構成、`Application`クラス新設の要否判断はいずれも設計ドキュメントの通りでよい。ADR-001の同期失敗ポリシーとは独立した機能であり抵触しない。

### 対象ファイル（担当宣言：Kimi）

新規:
- `app/src/main/java/com/example/myapplication/data/NotifiedSlot.kt`
- `app/src/main/java/com/example/myapplication/work/FreeTimeCheckWorker.kt`（パッケージ名は実装時に判断可）
- `app/src/main/java/com/example/myapplication/TaskStartActionReceiver.kt`
- 空きギャップ計算の純粋関数ファイル（`data/calendar`配下）
- `app/src/test/**`（新規テスト、上記の終日予定パーステストを含む）

変更:
- `app/src/main/java/com/example/myapplication/data/Task.kt`（`status`列追加）
- `app/src/main/java/com/example/myapplication/data/AppDatabase.kt`（v4→v5マイグレーション）
- `app/src/main/java/com/example/myapplication/data/TaskDao.kt`（`updateTaskStatus`部分更新クエリ追加）
- `app/src/main/java/com/example/myapplication/data/TaskRepository.kt`
- `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarApi.kt`（`listEvents`追加）
- `app/src/main/java/com/example/myapplication/data/calendar/GoogleCalendarSync.kt`（`listTodayEvents`追加）
- `app/src/main/java/com/example/myapplication/TaskListScreen.kt`（「進行中」バッジ）
- `app/src/main/AndroidManifest.xml`（`POST_NOTIFICATIONS`権限、レシーバー登録）
- `app/build.gradle.kts`（依存追加が必要になった場合のみ）

**注意:** `TaskViewModel.kt`・`TaskDao.kt`・`GoogleCalendarSync.kt`は現在「案件1（WU-A〜C）」でKimiが担当宣言中・Codexのレビュー待ち（未コミット）。同じ担当者が続けて着手する場合でも、案件1の差分と案件3の差分が混ざって隔離不能にならないよう、**案件1をコミット（またはCodexのGate 4 PASSを取得）してから案件3に着手すること**を強く推奨する。

### 受入条件

上記「設計判断」の4点に加え、設計ドキュメントの「テスト方針」章に記載の全項目を満たすこと。
`./gradlew :app:assembleDebug`と`./gradlew :app:testDebugUnitTest`が成功すること。

### Gate 4 レビュー（2026-08-24 / Claude、ユーザー指示によりCodex役として代行）

**判定: `PASS`**

案件1と合わせて同一差分をレビューした（詳細な確認項目・自動レビュー破棄の経緯は案件1のGate 4レビュー記載を参照）。
案件3固有の確認事項は以下の通り。

**設計判断で追加した4つの受入条件の充足確認:**
1. 通知権限が無い場合のクラッシュ防止 → `work/FreeTimeNotifier.kt`で`NotificationManagerCompat.areNotificationsEnabled()`を確認し、無効なら静かにスキップ。確認済み
2. 終日予定のパース＋ユニットテスト → `data/calendar/CalendarEventListParsing.kt`で`date`/`dateTime`を明示的に分岐。`CalendarEventListParsingTest`で3ケース（時刻指定、終日、start/end欠落）を確認済み
3. `TaskStartActionReceiver`のセキュリティ → `AndroidManifest.xml`で`exported="false"`、`PendingIntent`生成時に`FLAG_IMMUTABLE`付与を確認済み
4. `FreeTimeCheckWorker`の実行間隔が目安である旨のコメント → クラスKDocに明記済み

**指摘（低優先度・ブロッキングではない）:** 案件1のGate 4レビューに記載した指摘1・2（通知発行とNotifiedSlot保存の非原子性、`transparency`/欠席応答未考慮）は案件3固有の内容としてもそのまま該当する。次回改善候補としてメモ済み。

**再確認方法:** 案件1のGate 4レビュー記載を参照。

### 次の担当と行動

**次の担当: なし（Gate 4 PASSにより完了）**

未コミットの差分（案件1・2・3すべて含む）はユーザー判断でコミットすること。
「未検証」節に記載の実機・実地検証（1時間おきの実行を待つ通知の実地確認等）は、次回ユーザーがエミュレータ/実機で確認するまで保留。

---

## 案件2：Google Play ストア公開レベルへの調整

**状態:** `実装中` → `レビュー中` → **`コード部分Gate 4 PASS・ユーザー対応待ち`**（2026-08-24）
**担当:** Claude（監査・設計判断）→ Kimi 相当（実装、同セッション内で兼任）→ Codex役としてClaudeがGate 4判定

ユーザー依頼: 「アプリを Google Play ストアで公開しても大丈夫なレベルになるまで調整してほしい。監査して、ユーザーの命令無しで実装まで行ってよい」との明示指示を受け、Claude が監査・実装を一括で実施した。

### 監査で見つかった重大な不具合（最優先で修正済み）

**アプリが起動即クラッシュする状態だった。** Play Store 提出以前の問題として、debug / release 両ビルドとも `MainActivity` 起動直後に `RuntimeException: Cannot create an instance of class TaskViewModel` で落ちることをエミュレータ実機確認で発見した。

- 原因: `TaskViewModel` のコンストラクタが WU-A〜C のテスト容易性向上のため `repository` / `authManager` / `calendarSync` にデフォルト引数を持つ4引数構成になっていたが、`MainActivity` 側は `by viewModels()`（標準の `AndroidViewModelFactory`）のままだった。標準ファクトリは `Application` 型1引数のみのコンストラクタをリフレクションで探すため、該当する1引数コンストラクタが存在せず `NoSuchMethodException` になる。
- 影響範囲: このアプリを実機・エミュレータで一度でも起動すれば必ず再現する（TASK.md にあった「エミュレータでの実機検証はこれから」という記述の通り、リファクタ後に一度も起動確認されていなかった）。
- 修正: `MainActivity.kt` の `viewModel` 委譲を、`Application` を渡す `ViewModelProvider.Factory` を明示する形に変更（`app/src/main/java/com/example/myapplication/MainActivity.kt`）。
- 回帰テスト: `app/src/androidTest/java/com/example/myapplication/MainActivityLaunchTest.kt` を新規作成。`ActivityScenario.launch(MainActivity::class.java)` で `RESUMED` まで到達することを確認する。修正前に一度実行して同一の `NoSuchMethodException` で失敗する（RED）ことをエミュレータで確認したうえで、修正を戻して合格（GREEN）を確認済み（TDD）。
- 検証: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest` → BUILD SUCCESSFUL、5件のインストゥルメンテーションテスト全て合格。加えて release APK（署名・minify 済み）をエミュレータに実機インストールし、起動〜タスク一覧表示までクラッシュなしを screencap で確認済み。

### Play Store 提出要件の監査結果

| 項目 | 判定 | 対応 |
|---|---|---|
| リリース署名 (`signingConfig`) | 未設定だった | **対応済み**。`local.properties` 経由で署名情報を読み込む `signingConfigs.release` を追加し、開発用キーストア `release.keystore.jks` を生成（`.gitignore` 済み）。詳細は `SETUP.md`「リリース署名」を参照 |
| `isMinifyEnabled` / `isShrinkResources` | 無効だった | **対応済み**。有効化し、Retrofit/kotlinx.serialization 用の keep ルールを `proguard-rules.pro` に追加。release ビルドを実機起動確認済み |
| `compileSdk` / `targetSdk` | 35 だった | **対応済み**。36 に更新（Google Play は 2026-08-31 以降、新規/更新アプリに Android 16 (API 36) ターゲットを要求。参照: [Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk)） |
| パーミッション | `INTERNET` のみ | 問題なし。過剰権限なし |
| `allowBackup` / バックアップ除外設定 | デフォルトのまま | 問題なし。トークン類は永続化されずメモリ上のみのため、バックアップに含まれても機密性の問題は無い |
| ログへの機密情報出力 | 無し | 問題なし。`Authorization` ヘッダーは `redactHeader` 済み、デバッグログは `BuildConfig.DEBUG` でリリースビルドから除外済み（既存実装） |
| シークレットのコミット | 無し | 問題なし。`local.properties` / `*.jks` とも `.gitignore` 済みで確認 |
| プライバシーポリシー | 無かった | **ドラフト作成済み**（`PRIVACY_POLICY.md`）。ただし Play Console には Web 上の公開 URL 登録が必須のため、内容を確認のうえホスティングするのはユーザー対応が必要（下記） |
| アプリ名 (`strings.xml` の `app_name`) | `"My Application"` のまま | **未対応（ユーザー判断待ち）**。ストア掲載名としてふさわしくないため、正式名称が決まり次第変更が必要 |
| `applicationId` (`com.example.myapplication`) | プレースホルダーのまま | **未対応（ユーザー判断待ち）**。変更すると Google Cloud Console 側の OAuth クライアント再登録（新しい SHA-1 + パッケージ名での再作成）が必要になるため、Claude の判断だけでは変更しなかった。下記「設計判断」参照 |
| バージョニング (`versionCode` / `versionName`) | `1` / `"1.0"` | 問題なし。初回リリースとして妥当 |

### 設計判断（2026-08-21 / Claude）

**`applicationId` と `app_name` は変更しない。** 理由: どちらもストア公開前の「ブランディング」に関わる意思決定であり、実装の妥当性の問題ではない。特に `applicationId` の変更は Google Cloud Console に登録済みの OAuth クライアント（`SETUP.md` に記載の設定済みクライアント）の再登録を要する外部システム変更であり、ユーザーの意思決定なしに実行すると既存のカレンダー連携が壊れる。ADR-001 の対象外の大規模変更として、ユーザー確認を待つ。

### Gate 4 レビュー（2026-08-24 / Claude、ユーザー指示によりCodex役として代行）

**判定: `PASS`（コード部分のみ。案件全体としては下記ユーザー対応が未完了のため引き続き未完了）**

案件1・3と合わせて同一差分をレビューした（詳細な確認項目・自動レビュー破棄の経緯は案件1のGate 4レビュー記載を参照）。
案件2固有の確認事項は以下の通り。

- リリース署名: `local.properties`経由の読み込みを確認。実値はコミットされていない
- minify/shrinkResources: 有効化を確認。R8実行後の`assembleRelease`成功、`MainActivityLaunchTest`含む実機起動確認済み
- compileSdk/targetSdk 36化: `assembleRelease`成功で確認済み
- 起動即クラッシュ修正: `MainActivityLaunchTest`で回帰テストとして固定化されていることを確認（今回、POST_NOTIFICATIONS権限ダイアログとの干渉が新たに発生したため`GrantPermissionRule`で対処し、再度GREENを確認済み。詳細は案件3の実装報告参照）

**指摘:** 無し（コード品質観点では問題なし）。ただし「次の担当と行動」に記載の6項目（アプリ名・applicationId・プライバシーポリシー公開・キーストアバックアップ・Play Console提出・同意画面スコープ登録）はいずれもユーザー対応が必須であり、Gate 4のコード品質判定とは独立して未完了のまま。

### 次の担当と行動

**ユーザー対応が必要な項目（Claude/Kimi では実行できない、または実行すべきでない）:**

1. **正式なアプリ名を決め、`app/src/main/res/values/strings.xml` の `app_name` を変更する。**
2. **`applicationId` を変更するかどうかを決める。** 変更する場合は Google Cloud Console で新しい OAuth クライアントの再作成が必要（`SETUP.md` 手順4〜6を再実施）。変更しない場合はこのままで良い。
3. **`PRIVACY_POLICY.md` の内容を確認・加筆し、Web 上に公開する（URL化）。** Play Console の「アプリのコンテンツ → プライバシー ポリシー」にその URL を登録する。
4. **`release.keystore.jks` と `local.properties` 内のパスワード類をリポジトリ外にバックアップする。** `SETUP.md`「リリース署名」参照。
5. **Play Console でのアプリ登録、ストア掲載情報（スクリーンショット・説明文・アイコン等）、データセーフティフォームの入力、Play App Signing の有効化。** これらは Play への外部公開行為そのものであり、AGENTS.md の禁止事項（外部公開はユーザー事前承認が必須）に該当するため Claude/Kimi では行わない。
6. **同意画面のスコープ `calendar.events` 登録**（案件1から持ち越し。本番公開には Google の審査が必要になる制限付きスコープのため、テスト運用のみで公開する場合は不要）。
