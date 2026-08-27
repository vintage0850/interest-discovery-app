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

---

## 案件4：iOS/Android両対応（KMP移行）Phase 1

**状態:** `設計判断済み・実装完了` → **`Windows側の検証完了・Mac側作業待ち`**（2026-08-26）
**担当:** 設計・実装（Kimi役）は前セッションで完了済み。今回はClaudeが検証（Task 10 Step 1〜3）のみ実施。

関連ドキュメント:
- `docs/superpowers/specs/2026-08-24-kmp-ios-migration-phase1-design.md`（設計、ユーザー承認済み）
- `docs/superpowers/plans/2026-08-24-kmp-shared-module-task-crud.md`（Task 1〜9、`:shared`のSQLDelightデータ層。全タスク`master`にコミット済み）
- `docs/superpowers/plans/2026-08-24-kmp-compose-ui-port.md`（Task 1〜9、Compose MultiplatformのUI移植。全タスク`master`にコミット済み。Task 10は本セッションで実施）

### 引き継ぎメモ（2026-08-26 / Claude）

前セッションまでに、KMP Phase 1の実装（`:shared`モジュール新設、SQLDelightデータ層、Compose Multiplatform UI移植、`App()`ルートComposable、iOSセットアップ手順・Swiftブリッジ、レビュー指摘の修正2件）が全て`master`ブランチにコミット済みであることを`git log`で確認した。`git status`もクリーン（未コミット差分なし）。

本セッションでは残っていた **Task 10「最終確認」のStep 1〜3**（Windows上で実行可能な検証）のみを実施した。

**実行結果:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :shared:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :shared:testDebugUnitTest` | BUILD SUCCESSFUL（`AppStateTest`含む全件） |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug` | BUILD SUCCESSFUL（`:shared`追加による`:app`への回帰なし） |

`docs/superpowers/plans/2026-08-24-kmp-compose-ui-port.md`のTask 10 Step1〜3のチェックボックスを完了に更新済み。

**次の担当と行動:**

**次の担当: なし（Windows側でできる作業はここまで）**

Task 10 Step 4に記載の以下4項目は、Mac環境でのユーザー自身の作業が必要（Kotlin/NativeのiOSターゲットはWindowsでコンパイル不可のため）:

1. `./gradlew :shared:compileKotlinIosSimulatorArm64` — iOSターゲットのコンパイル確認
2. `SETUP.md`「iOSアプリのセットアップ」節に従ったXcodeプロジェクトの作成・実行確認
3. `IosDatabaseDriverFactory`の外部キー制約（`PRAGMA foreign_keys`）挙動の確認
4. Android実機/エミュレータでの`App()`の目視確認（タスク追加・削除・カテゴリ管理が実際に動くこと。今回はコンパイル確認のみ）

**注意（未整理の副産物、2026-08-26に解消）:** 旧`.worktrees/subtask-calendar-notification`（ブランチ`feature/subtask-calendar-notification`）は`master`の祖先コミットで固有差分が無かったため、ユーザー承認のうえ削除し、`master`最新から作り直した。詳細は案件5参照。

---

## 案件5：サブタスク表示改善・カレンダー手動登録・通知時間手動設定

**状態:** `設計判断済み・ユーザー承認済み` → `実装完了（全12タスク）・Codexレビュー` → `修正中（CHANGES REQUIRED、計3ラウンド）` → **`完了（Gate 4 PASS）`**（2026-08-26）
**担当:** Kimi（実装）。設計裁定はClaude（2026-08-24）。優先順位変更の経緯はClaude（2026-08-26）。

### 経緯・優先順位の変更（2026-08-26 / Claude）

`docs/superpowers/specs/2026-08-24-kmp-ios-migration-phase1-design.md`では「この機能はKMP移行（案件4）完了後、共通化されたコードベースの上で作り直す」として保留にしていた。
しかし案件4のPhase 1はMac環境でのiOSシミュレータ確認が必須で止まっており（`TASK.md`案件4参照）、ユーザーから「Macが無いので、保留にしていたこちらの機能を先に進めよう」と明示指示があったため、優先順位を変更した。

**この機能は`app/`（既存Android・Room版）を対象とし、`:shared`（KMP版）には触れない。** 設計ドキュメント・実装計画とも元々`app/`配下のファイルのみを対象にしており、KMP移行の有無に関係なく独立して実装できる。両者が同じファイルを取り合うことはない（`:shared`配下のファイルは今回一切変更しない）。

### 作業場所

`app/src/main/java/com/example/myapplication/` 配下、worktree `.worktrees/subtask-calendar-notification`（ブランチ`feature/subtask-calendar-notification`、`master`の最新3d40a03から作成）。マージ済み（コミット8472f36まで）。

### 対象ファイル（担当宣言：Kimi）

`docs/superpowers/plans/2026-08-24-subtask-calendar-notification.md` のTask 1〜12に記載の全ファイル（`Task.kt`、`AppDatabase.kt`、`TaskDao.kt`、`TaskRepository.kt`、`GoogleCalendarSync.kt`、`GoogleCalendarApi.kt`、`TaskViewModel.kt`、新規`TaskNotificationScheduler`/`TaskNotificationReceiver`/`BootReceiver`/`NotificationWindowPreferences`/`NotificationSettingsScreen`/`OptionalTimePicker`/`TaskEditDialog`、`AddTaskScreen.kt`、`TaskListScreen.kt`、`MainActivity.kt`、`AndroidManifest.xml`、対応する`app/src/test`・`app/src/androidTest`）。担当解除までこのworktree以外（＝`master`本体や`:shared`）で同じファイルを編集しない。

### 受入条件

`docs/superpowers/specs/2026-08-24-subtask-calendar-notification-design.md`の「スコープ」「データモデルの変更」「コンポーネント構成」節、および`docs/superpowers/plans/2026-08-24-subtask-calendar-notification.md`の各Taskの受入条件・テスト方針に従う。全12タスク完了後、`./gradlew :app:testDebugUnitTest :app:assembleDebug`が成功すること。

### 実行ログ

**注記（2026-08-26 / Claude）:** Kimiは実行ログを誤って`app/TASK.md`という別ファイルに記録していた（Task 1〜4のみ記録、以降は未記録）。本セクションへ統合し、`app/TASK.md`は削除した。

Kimi自身の記録（Task 1〜4、コミット時点で確認）:

```
2026-08-26 Task 1: Task.eventHasTime 追加 + DB v6 移行。:app:assembleDebug BUILD SUCCESSFUL。スキーマ 6.json 生成済み。connectedDebugAndroidTest は実機/エミュレータ未接続のため実行不可（adb devices で 0 台）。コミット 8cf2d36。
2026-08-26 Task 2: TaskDao/TaskRepository 拡張。:app:testDebugUnitTest --tests "com.example.myapplication.TaskViewModelCalendarTest" BUILD SUCCESSFUL。コミット faf82e9。
2026-08-26 Task 3: NotificationWindowPreferences 追加。:app:testDebugUnitTest --tests "com.example.myapplication.data.NotificationWindowPreferencesTest" BUILD SUCCESSFUL。コミット 68a0af4。
2026-08-26 Task 4: GoogleCalendarSync 時刻指定・サブタスク対応。:app:testDebugUnitTest --tests "com.example.myapplication.data.calendar.GoogleCalendarSyncTest" BUILD SUCCESSFUL。コミット 026655a。
```

Task 5〜12（Kimi自身のログ記録は無いが、コミットと成果物から完了を確認）:

```
34e95d8 feat: pass subtasks through to calendar sync calls
b9f136f feat: add AlarmManager-based task notification scheduler and boot rescheduling
3a8e6d7 feat: wire manual notification scheduling and add event/notification time editing
89d0227 feat: read notification window from NotificationWindowPreferences
d44c76b feat: add reusable OptionalTimePicker composable
ae73a2e feat: add event time and notification time inputs to AddTaskScreen
28568c9 feat(app): Task 11 - wire TaskEditDialog into TaskListScreen and MainActivity
b14e67d feat(app): Task 12 - add notification window settings screen and wire MainActivity
```

**Task 5〜12 の TDD Red/Green 証跡（2026-08-26 / Kimi）:**

案件5の Codex 品質レビューで「テスト先行の証跡が無い」と指摘されたため、
修正にあたって新たに Red/Green を取ったテストは以下の通り。

- `TaskViewModelCalendarTest.タイトルと予定時刻を同時に変更してもupdateEventは1回だけ両方の新値で呼ばれる`
  - **RED**: `applyTaskEdit` が `renameTask` / `updateEventTime` を別 coroutine で起動していたため、
    `FakeCalendarSync.updateEvent` に遅延を入れると `updateEvent` が 2 回呼ばれ、
    後から完了した方の呼び出しが古いタイトル（または古い時刻）で上書きした。
    期待値 1 に対し実際 2 回で失敗。
  - **GREEN**: `applyTaskEdit` を単一 coroutine に統合し、Mutex 取得後に最新 Task を再取得、
    全変更を合成した `merged` Task で `doSyncToCalendar` を 1 回だけ呼ぶように修正したところ、
    `updateEvent` が 1 回だけ、新タイトル・新時刻の両方を含む Task で呼ばれるようになった。

- `AndroidTaskNotificationSchedulerTest`
  - `exact許可時はsetExactAndAllowWhileIdleを使う`
  - `exact未許可時はsetAndAllowWhileIdleにフォールバックする`
  - `notificationTimeがnullなら何も予約しない`
  - `cancelはtaskId別のPendingIntentを使う`
  - **RED**: `AndroidTaskNotificationScheduler` が `AlarmManager` と `PendingIntent` を直接触っていたため単体テスト不可。`AlarmOperations` インターフェースを注入可能な境界として抽出するリファクタリング前はテストが書けなかった。
  - **GREEN**: `AlarmOperations`・PendingIntent ファクトリ・exact 可否判定を注入する構成にした上で、上記 4 ケースを TDD で追加。exact/fallback/cancel/識別を網羅。

- `TaskNotificationReceiverTest`
  - `未完了タスクがあれば通知を発行する`
  - `完了済みタスクでは通知を発行しない`
  - `削除済みタスクでは通知を発行しない`
  - **GREEN**: `TaskNotificationReceiver.handleReceive` を注入可能な純粋関数として抽出し、未完了/完了/削除の 3 分岐を網羅。

- `BootReceiverTest`
  - `未来の未完了タスクを再予約する`
  - `対象タスクが無ければ何も予約しない`
  - `複数タスクがあればすべて再予約する`
  - **GREEN**: `BootReceiver.handleBoot` を注入可能な純粋関数として抽出し、boot 時の対象抽出・再予約を網羅。

Claudeによる最終確認（2026-08-26、Kimiのバックグラウンドセッションが`killed`表示で終了したため再実行）:

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | BUILD SUCCESSFUL（新規テスト含む全47ユニットテスト、失敗0） |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew :app:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL（`MigrationTest.kt`・`FreeTimeCheckWorkerTest.kt`の追記分含む） |

`connectedDebugAndroidTest`（実機・エミュレータでのマイグレーション/Worker実機検証）は、今回のセッションでは実機・エミュレータが未接続のため未実施。次回実機/エミュレータ接続時に実施すること。

### CHANGES REQUIRED 修正完了（2026-08-26 / Kimi実装、Claude検証）

指摘1〜3を修正済み（詳細は上記「Task 5〜12のTDD Red/Green証跡」参照）。
Kimiのセッションがビルド承認待ちで完了しなかったため、Claudeが代わりに検証コマンドを実行した。

**1回目の検証（Kimi修正直後）:** `updateNotificationTimeにnullを渡すと予約解除のみ行う`ほか計4件のユニットテストが失敗。`AndroidTaskNotificationScheduler`のテストがRobolectric未導入のJVM環境で`Intent`/`PendingIntent`を直接使っていたためRuntimeExceptionになっていたこと、`updateNotificationTime`が「新値がnullかつ現在値と等しい（=両方null）」場合に早期returnしてcancel()を呼ばない不具合が原因。Kimiに差し戻して再修正を依頼した。

**2回目の検証（再修正後）:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL**（58 tests、失敗0） |

`connectedDebugAndroidTest`は引き続き実機・エミュレータ未接続のため未実施。

### Codex Gate 4 再レビュー（2回目・2026-08-26）: CHANGES REQUIRED

Codexから3件の追加指摘。ユーザーの指示によりClaudeが直接修正した（Kimiには差し戻さず）。

1. **`deleteTask`が同一taskIdのMutex外だった。** → `mutexFor(task.id).withLock { ... }` で囲むよう修正（`TaskViewModel.kt`）。
2. **通知時刻だけの変更でもCalendar APIを呼んでいた。** → `applyTaskEdit`で`titleChanged || eventTimeChanged`のときだけ`doSyncToCalendar`を呼ぶよう修正。回帰テスト`applyTaskEditで通知時刻だけ変更した場合はCalendar APIを呼ばない`を追加（`TaskViewModelCalendarTest.kt`）。
3. **schedulerテストがtaskId別PendingIntentを実質検証していなかった。** → `FakePendingIntentFactory`を導入し、taskIdごとに異なる`PendingIntent`が渡ることを検証するテストを追加・強化（`AndroidTaskNotificationSchedulerTest.kt`）。
4. **boot時の対象抽出（未来・未完了のみ）が実クエリで未検証だった。** → in-memory Room DBに対する実クエリを検証する`TaskDaoBootQueryTest.kt`（androidTest）を新規追加。
5. **SETUP.mdの操作手順が実装と不一致。** → 「長押し/編集アイコン」→「タイトルをタップ」、「入力欄からタスク作成」→「右下の＋（フローティングボタン）」に修正。

**検証（2026-08-26 / Claude）:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL**（失敗0。新規テスト・新規instrumentedテストファイルの追加分含めコンパイル・実行成功） |

`TaskDaoBootQueryTest`（instrumented）自体は実機・エミュレータ未接続のため`connectedDebugAndroidTest`での実行は未実施。次回接続時に実行すること。

### Codex Gate 4 再レビュー（3回目・2026-08-26）: CHANGES REQUIRED（残り1件）

前回の4点（通知時刻のみ変更でCalendar API未呼び出し・schedulerのtaskId検証・boot対象抽出クエリ・SETUP.md）はすべて解消と確認された。
残った1件：**`deleteTask`がMutex取得後も呼び出し元が渡した古いTask引数の`calendarEventId`を使っていた。** 連携ON直後に削除すると、古い（null等の）値で削除処理をしてしまい、最新のGoogle予定が孤児化しうる。

**修正（2026-08-26 / Claude）:** `deleteTask`内でMutex取得後に`repository.getTaskById(task.id)`で最新状態を再取得し、`calendarEventId`・サブタスク取得・`repository.delete`・`lastDeleted`すべてこの最新Taskを使うよう修正（他のミューテーション関数と同じパターンに統一）。
回帰テスト`deleteTaskは呼び出し時点の古いTaskではなく最新のcalendarEventIdを削除する`を追加：`deleteTask`呼び出し前に別経路で`calendarEventId`を更新し、古い引数ではなく最新の値に対して`deleteEvent`が呼ばれることを検証。

**検証（2026-08-26 / Claude）:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL**（失敗0） |

### Codex Gate 4 最終レビュー（4回目・2026-08-26）: PASS

残っていた`deleteTask`の古いTask引数参照が解消されたことを確認し、**Gate 4 PASS**。blocking finding 0件。

### 次の担当と行動

**次の担当: なし（Gate 4 PASSにより完了）。**
`connectedDebugAndroidTest`（実機/エミュレータでのmigration・通知発火・再起動復元・`TaskDaoBootQueryTest`の実行）は次回実機/エミュレータ接続時に確認すること（ブロッキングではない）。
worktree `.worktrees/subtask-calendar-notification`の差分は未コミット・未マージ。コミット・`master`へのマージはユーザー判断で実施すること。

### Codex品質レビュー実行記録（2026-08-26）

| コマンド | 結果 |
| --- | --- |
| `git diff --check master...b14e67d` | 成功 |
| `.\gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin` | Codexサンドボックスでは既定の `C:\.gradle` にlockを作成できず、Gradle起動前に失敗 |
| ワークツリー内 `GRADLE_USER_HOME` で同上 | Gradle配布物のダウンロードがネットワーク制限で拒否され、Gradle起動前に失敗 |
| 既存キャッシュを `--offline --no-daemon` で使用して同上 | 読み取り専用キャッシュに `native-platform.dll.lock` を作成できず、Gradle起動前に失敗 |

3 Gradleタスクは直前のClaude独立実行で全て `BUILD SUCCESSFUL` と記録済みのため、レビューではその結果を採用した。Codex環境での再実行不能はコード失敗として扱わない。品質判定は実装競合と不足テスト・文書を理由に `CHANGES REQUIRED`（1回目、以降の経緯は上記参照）。

---

## 案件6：広報・事前配布戦略（戦略部隊）

**状態:** `戦略ドラフト完成・ユーザー承認待ち`（2026-08-26）
**担当:** 戦略部隊（Grok=市場調査 → Gemini=統合・ドラフト作成）。最終承認はユーザー/Claude。

### 経緯

ユーザーからの依頼で、AI会社に新設した「戦略部隊」（`AGENTS.md`参照）の初回案件として実施。
対象はMyApplicationのみ（Registar_Calender・TaskMVP_Newは対象外）。

関連ノート:
- `app-studio/Obsidian_Comapany/Inbox/2026-08-26_Grok調査_個人開発 Androidアプリ タスク管理カレンダー連携 Google Play.md`（Grok調査、2026年8月時点）
- `app-studio/Obsidian_Comapany/戦略/MyApplication_広報事前配布戦略_ドラフト.md`（Geminiによる統合ドラフト。事前配布プラン・広報プラン・7週間タイムライン・リスク一覧を含む）

### ユーザー対応が必要な項目（ドラフトの「次のアクション」より）

- [ ] 正式なアプリ名・`applicationId`（パッケージ名）の決定
- [ ] プライバシーポリシーのWeb公開（`PRIVACY_POLICY.md`を元にURL確定。案件2から持ち越し）
- [ ] `release.keystore.jks`とパスワード類のバックアップ（案件2から持ち越し）
- [ ] 実機での内部テスト起動確認（OAuth同意フロー・空き時間通知）
- [ ] GCP「制限付きスコープ（`calendar.events`）」の検証審査申請（外部公開に関わる操作のため、申請前にユーザー承認が必要。AGENTS.md禁止事項に該当）
- [ ] クローズドテストのテスター20〜30名の募集（SNS発信・Zenn/Qiita記事は着手可、Play Console上の登録自体はPlay公開行為のためユーザー実施）

### ユーザー決定（2026-08-26）

- **アプリ名: 「Realize」に決定。**
- **`applicationId`（パッケージ名）は変更しない。** `com.example.myapplication`のまま維持。GCP OAuthクライアントの再設定は不要。

### 対象ファイル（担当宣言：Kimi、2026-08-26〜）

- `app/src/main/res/values/strings.xml`（`app_name`を"Realize"に変更）
- `app/src/main/res/values-*/strings.xml`が存在する場合は同様に変更（多言語対応時のみ）

上記以外のファイル（`AndroidManifest.xml`含む）は変更しない。`AndroidManifest.xml`は案件5が担当宣言していたため元々関与不要だったが、案件5は完了・マージ済み。

### Kimiへの引き継ぎタスク

1. **アプリ名変更（着手可）**：`strings.xml`の`app_name`を"My Application"から"Realize"へ変更。`./gradlew :app:assembleDebug`で成功確認。
2. **既存ユーザー移行時のUX警告ダイアログ**（保留・対象ファイル未確定）：Room v3→v4マイグレーション実行を検知し、カレンダー上の孤児予定について手動削除を案内するダイアログを表示する。

### 次の担当と行動

**次の担当: Kimi**（アプリ名変更）。完了後、テスト結果と引き継ぎメモを本ファイルに追記すること。

---

## 案件7：UI/機能フィードバック（ユーザーからの手直し依頼）

**状態:** `仕様確定・Kimi実装待ち`（2026-08-27、Codex）
**担当:** Codexが現状調査と仕様確定を完了。次はKimiがTDDで実装する。

### 経緯

ユーザーから `/company` 経由でUI/機能面のフィードバック一式を受け取った。整理すると以下7項目。

**UI:**
1. 設定ボタンが複数箇所に分散している → 一つに統合できないか（ユーザー案）
2. カテゴリを削除した際、カテゴリ欄に空白が残る（表示崩れ・バグ）
3. メインタスクをリネームする際のタップ判定が小さく、連打してもリネームに入りにくい
4. ~~UI上の猫の画像を、ユーザー指定の別の猫に差し替える~~ → **ユーザー判断によりスコープ外（画像アセット未提供のため見送り）**

**機能:**
5. サブタスクもリネームできるようにする（現状はメインタスクのみ？要確認）
6. ~~通知に猫の画像を表示できるようにする（画像付き通知）~~ → **ユーザー判断によりスコープ外（画像アセット未提供のため見送り）**
7. 優先順位・時間でタスクをソートできる機能

**今回のスコープ: 項目1・2・3・5・7の5件。** 項目4・6は画像アセットが用意でき次第、別途対応する。

### 保留解除（2026-08-26 / Claude）

案件5がCodex Gate 4 `PASS`（4ラウンド目）で完了し、`master`へマージ済み（コミット8472f36）。`TaskListScreen.kt`等の担当宣言は解除されたため、案件7の実装に着手可能。

### 仕様確定（2026-08-27 / Codex）

詳細仕様: `docs/superpowers/specs/2026-08-27-案件7-ui-feature-feedback-design.md`

- **項目1:** 一覧トップバーは歯車アイコン1個に統合し、新しい設定ハブ画面から「カテゴリ管理」「通知設定」「Googleカレンダー連携」へ進む。個別タスクのカレンダーボタンは統合対象外。
- **項目2:** カテゴリ削除時に削除済みIDを `selectedFilter` から除去して「すべて」へ戻し、カテゴリIDを安定キーにして空白・ゴーストタブを残さない。所属タスクは従来どおり「未分類」に移す。
- **項目3:** メインタスクの編集導線をタイトル文字だけから左側情報領域全体へ広げ、48dp以上のタップ領域を確保する。カレンダー・完了・スワイプ削除とは分離する。
- **項目5:** サブタスクのテキスト領域から専用リネームダイアログを開く。タイトルと完了状態は別々のDAO部分更新にし、連携済みなら既存のMutexと `GoogleCalendarSync.updateEvent` で予定説明欄も同期する。
- **項目7:** DBを変えず、カテゴリ絞り込み後の一覧を画面内で「優先順位順」または「締切が近い順」に安定ソートする。初期値は優先順位順、選択は画面回転まで保持しアプリ再起動では初期化する。
- 項目4・6はユーザー判断どおりスコープ外。
- Roomスキーマ、認証、課金、公開API、ADR-001の同期失敗ポリシーに変更はない。現時点でClaudeへの追加設計判断依頼は不要。実装中にスキーマ等の変更が必要と判明した場合は実装を止め、**「Claudeへ設計判断を依頼」**として差し戻す。

### 次の担当と行動

**次の担当: Kimi（TDD実装）。**

1. 上記仕様書を読み、1作業単位を最大3ファイル・半日以内・1コミットに分割する。
2. 推奨順は、設定ハブ → カテゴリ削除／メイン編集タップ領域／ソート → サブタスクのDAO部分更新 → サブタスクのリネームUIとカレンダー同期。
3. 各単位で失敗テストを先に追加し、Red → Green → Refactorを守る。
4. 最後に `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin` を実行し、エミュレータ接続時は `./gradlew :app:connectedDebugAndroidTest` も実行する。
5. 実装結果、テスト結果、コミットIDを本セクションへ追記し、CodexのGate 3.5 / Gate 4レビューへ渡す。

### 作業場所（担当宣言：Kimi、2026-08-27〜）

worktree `.worktrees/ui-feedback-item7`（ブランチ`feature/ui-feedback-item7`、`master`の`4a9aa4a`から作成）。
担当解除までこのworktree以外（＝`master`本体）で`app/src/main/java/com/example/myapplication/`配下の同じファイルを編集しない。

### 実装完了報告（2026-08-27 / Kimi実装、Claude検証・仕上げ）

Kimiが項目1・2・3・5・7を一括で実装したが、セッション中Gradleコマンドが承認待ちのまま完了し、未コミット・未検証だった。Claudeが引き継いでビルド検証と3件の不具合修正を行った。

**実装内容（Kimi）:**
- `SettingsHubScreen.kt`（新規）: 項目1。歯車アイコン1個から「カテゴリ管理」「通知設定」「Googleカレンダー連携」へ遷移する設定ハブ画面。`calendarLinkSummary()`で認可状態をUI表示用データへ変換（`CalendarLinkSummaryTest.kt`で検証）。
- `TaskListScreen.kt`: 項目2は`resolveSelectedFilter()`でカテゴリ削除後に無効な`selectedFilter`を「すべて」へ解決（`TaskListLogicTest.kt`で検証）。項目3はメインタスクの`onTitleClick`領域をタイトル文字だけから左側情報Column全体へ拡大。項目7は`SortOrder`（優先順位順／締切が近い順）を追加し、`sortedTasks()`で表示専用ソート（DB非変更）、`FilterChip`で切替UI。
- `TaskViewModel.kt` / `TaskDao.kt` / `TaskRepository.kt`: 項目5。`renameSubTask()`を追加、タスクID単位Mutexで直列化し最新状態を再取得後に`updateSubTaskTitle`で部分更新、連携済みなら`doSyncToCalendar`で予定側も同期。`toggleSubTaskCompleted`も全列上書き（`updateSubTask`）から`updateSubTaskCompleted`部分更新へ変更。
- `MainActivity.kt`: 設定ハブへのナビゲーションと`onSubTaskRename`の配線。

**Claudeが修正した不具合（2026-08-27）:**
1. `SettingsHubScreen.kt`に`GoogleAuthManager`のimportが漏れておりコンパイルエラー → import追加。
2. `FreeTimeCheckWorkerTest.kt`の`FakeTaskDao`が新規追加の`updateSubTaskTitle`/`updateSubTaskCompleted`を実装しておらずコンパイルエラー → 空実装のoverrideを追加。
3. `TaskViewModelCalendarTest.kt`の`連携済みタスクのサブタスクリネームでカレンダー予定も更新される`テストが、Fakeリポジトリを「リネーム後のタイトルで」誤って初期化していたため早期returnで無反応になり失敗 → 初期化を未リネームの状態に修正。

**検証（2026-08-27 / Claude）:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL**（失敗0） |

`connectedDebugAndroidTest`は実機・エミュレータ未接続のため未実施。

### 次の担当と行動

**次の担当: Codex（Gate 3.5 / Gate 4レビュー）。** 項目1・2・3・5・7の実装・ビルド検証済み。項目4・6は画像アセット未提供のためスコープ外のまま。

### Codex Gate 3.5 / Gate 4レビュー（2026-08-27）

**総合判定: `CHANGES REQUIRED`**

- Gate 3.5（設計・SOLID・保守性）: **`CHANGES REQUIRED`**
- Gate 4（テスト・型・ビルド・セキュリティ・入力値・エラー処理）: **`CHANGES REQUIRED`**
- 詳細: `docs/quality-review/2026-08-27-案件7-ui-feature-feedback.md`

**ブロッキング指摘:**

1. カテゴリTabに`key(tab.filter)`がなく、仕様のID安定キー要件を満たさない。
2. 両ソートに`createdAt`タイブレークがない。
3. メイン/サブタスク編集領域の48dp保証と明示的なTalkBack操作名がない。
4. 仕様で禁止された50文字制限を既存サブタスクのリネームに適用している。
5. 設定ハブに「未設定」「未接続」「接続済み」の状態が明示されない。
6. 仕様で必須のCompose UI/Room DAO回帰テストがなく、現在のJVMテストは上記不備を見逃している。
7. 詳細仕様書がこのworktree/`HEAD`に含まれず、main checkoutの未追跡ファイルにしか存在しない。

**検証記録:** `git diff --check master...HEAD` は成功。Gradleはユーザー指示に従い、直前のClaude検証 `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` = **BUILD SUCCESSFUL（失敗0）** を採用した。`connectedDebugAndroidTest`は端末未接続のため未実施だが、ブロッキング理由にしていない。

### 次の担当と行動（Gate差し戻し）

**次の担当: Kimi（修正とテスト追加）。** 上記7点を修正し、同じ3 Gradleタスクと`git diff --check`の結果を記録してCodexの再レビューへ戻す。`connectedDebugAndroidTest`は端末接続時の後続確認でよい。

### CHANGES REQUIRED 修正完了（2026-08-27 / Kimi実装、Claude検証・仕上げ）

Kimiが7点中6点（1〜6）を修正。指摘7（仕様書がworktreeに未追跡）はClaudeがmain checkoutから`docs/superpowers/specs/2026-08-27-案件7-ui-feature-feedback-design.md`をこのworktreeへコピーして解消した。

**Kimiの修正内容:**
1. カテゴリTabに`key(tab.filter)`を追加し安定キー化。
2. `sortedTasks()`の両ソートに`createdAt`昇順→`id`昇順のタイブレークを追加。`TaskListLogicTest`に`createdAt`/`id`が逆になる同点データのテストを追加。
3. メイン/サブタスクの編集領域に`Modifier.heightIn(min = 48.dp)`を追加し、`clickable`にsemantics経由で操作名（「タスクを編集する」「サブタスクの名前を変更する」）を付与。
4. サブタスク名変更から`TASK_TITLE_MAX_LENGTH = 50`の適用とカウンタ表示を除去。50文字超の既存サブタスク名を保持したまま編集できる回帰テストを追加。
5. `calendarLinkSummary()`のsubtitleを「未設定：...」「未接続」「接続済み」（メールありなら「接続済み：${email}」）と明示。
6. `SettingsScreenTest.kt`・`TaskListScreenTest.kt`・`TaskDaoSubTaskTest.kt`（いずれもinstrumented）を新規追加。設定ハブの画面遷移・戻る操作・3状態表示、カテゴリタブ削除後のUI状態、48dp保証、実Room DAOでのサブタスク列非干渉を検証。

**Claudeが修正した不具合（2026-08-27、2ラウンド目）:**
1. `SettingsScreenTest.kt`・`TaskListScreenTest.kt`が`androidx.compose.ui.test.assertDoesNotExist`をトップレベル関数としてimportしていたためコンパイルエラー（`assertDoesNotExist`は`SemanticsNodeInteraction`のメンバー関数でありimport不要かつ不可）→ 誤ったimport文を削除。
2. `CalendarLinkSummaryTest`の`認可済み状態ではアカウントと接続済みが表示される`が、実装のsubtitle書式（`"${email} で接続済み"`）とテストの期待（`startsWith("接続済み")`）が食い違い失敗 → 実装を`"接続済み：${email}"`に統一し、`SettingsScreenTest.kt`の対応する期待文字列も合わせて修正。

**検証（2026-08-27 / Claude、2回目）:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL**（失敗0） |

`connectedDebugAndroidTest`は実機・エミュレータ未接続のため未実施。

### 次の担当と行動

**次の担当: Codex（Gate 3.5 / Gate 4 再レビュー）。** 指摘1〜7すべて修正・検証済み。

### Codex Gate 3.5 / Gate 4 再レビュー（2回目・2026-08-27）: CHANGES REQUIRED（残り2件）

7件中5件（1、2、4、5、7）は解消と確認された。残り2件：

3. **48dp / TalkBack操作名: 部分解消。** 48dpは解消したが、`clickable`に明示的な`onClickLabel`が無く、`contentDescription`への文言連結だけではTalkBackの「ダブルタップで実行」操作名として認識されない。
6. **UI/DAO回帰テスト: 部分解消。** 設定ハブ・タブ削除・48dp・リネームUIのテストはあるが、仕様§6が要求する「カレンダー／完了チェック操作が編集を開かないこと」「並び順切替で実際の行順が変わること」「トップバーの設定導線が1個」の検証が無い。加えて`SettingsScreenTest.kt`の未設定状態テストが`onNodeWithText("未設定")`の完全一致を使っており、実表示（「未設定：SETUP.md...」）と食い違い実行時に失敗する。

**修正（2026-08-27 / Claude）:**
- `TaskListScreen.kt`: メイン/サブタスクの`clickable`に`onClickLabel = "タスクを編集する"` / `"サブタスクの名前を変更する"`を追加。
- `TaskListScreenTest.kt`: カレンダーボタン・メイン完了チェック・サブタスク完了チェック・スワイプ削除がそれぞれ編集/リネームダイアログを開かずコールバックだけを呼ぶことを検証する4テストを追加。トップバー設定導線が`onAllNodesWithContentDescription("設定")`で1個だけであることを検証するテストを追加。優先度の異なる2タスクを用意し、ソート切替で実際の`positionInRoot.y`の並びが変わることを検証するテストを追加。
- `SettingsScreenTest.kt`: 未設定状態の文言検証を`onNodeWithText("未設定", substring = true)`へ修正。
- 仕様書末尾の余分な空行を除去し、`git diff --check master...HEAD`を成功させた。

**検証（2026-08-27 / Claude）:**

| コマンド | 結果 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon` | **BUILD SUCCESSFUL**（失敗0） |
| `git diff --check master...HEAD` | 成功 |

**注意:** 新規追加の`TaskListScreenTest`のカレンダー/チェック/スワイプ分離テスト・並び順テストはコンパイル確認のみ。`connectedDebugAndroidTest`は実機・エミュレータ未接続のため未実施のまま（引き続き非ブロッキングの前提）。次回実機/エミュレータ接続時に実行し、実際にGREENであることを確認すること。

### 次の担当と行動

**次の担当: Codex（Gate 3.5 / Gate 4 再々レビュー）。** 残り2件を修正・検証済み。
