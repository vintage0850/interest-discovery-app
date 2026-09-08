# 案件27 Lane B（Android/KMP＋ホスト）完了報告

- 実施日: 2026-09-08
- 対象ブランチ: `discovery-backend`
- 対象: Work Unit B-1〜B-8
- 引き継ぎ元: Antigravity のタイムアウト後に残った未コミット変更

## 実施内容

### B-1 共通モデル・契約・Fake実装

- 月次ナラティブの5フィールド、週次／月次の通知種別、`ReportData` の月次表示状態を追加した。
- `DiscoveryRepository` に月次ナラティブ取得とアクティブセッション取得の契約を追加した。
- `FakeDiscoveryRepository` に月次ダミーデータとレポート統合を追加した。

### B-2 実リポジトリ・契約テスト

- `GET /sessions/{session_id}/report/monthly-narrative` の snake_case DTO 復号とUIモデル変換を追加した。
- サマリーを正本に、週次と月次を独立取得・独立フォールバックするようにした。
- 503だけでなく通信・復号例外でも片方の失敗が他方と実績表示を失わせないよう修正した。
- Real/Fakeリポジトリの正常系、503、復号失敗、月次データ統合をテストした。

### B-3 月次カード・設定保存

- Reportタブに `🌱 30日間の気付き` カードを追加した。
- 対象期間、全体の気付き、進み方の波、続けられたペースを表示し、月次単独失敗時の固定文言を追加した。
- セッション×週次／月次の最終通知キーを `SharedPreferences` へ同期保存する実装を追加した。
- Android通知チャンネル名を追加した。

### B-4 UI状態・通知タップ遷移

- cold/warm startの通知IntentをAndroidホストからKMP UIへ渡す経路を追加した。
- 有効な通知はReportタブへ遷移・再取得し、週次／月次カードをフォーカスするようにした。
- 不正種別、セッションなし／不一致、セッション復元例外ではHomeへ戻し、確定Snackbarを表示するようにした。

### B-5 KMP状態テスト

- 通知種別の復号、週次／月次遷移、再取得、フォーカス、不正種別、セッションなし／不一致／復元失敗を検証した。

### B-6 WorkManager・Scheduler

- `NetworkType.CONNECTED` 制約、24時間周期、`ExistingPeriodicWorkPolicy.KEEP` の一意な定期Workerを追加した。
- UTC週キー／月キー、初回・重複・巻戻り抑止、週次／月次の独立実行、成功後のみ同期保存する処理を追加した。
- Workerの2引数コンストラクタをProGuard keep対象に追加した。

### B-7 Notifier・Androidホスト統合

- 固定文言、通知チャンネル、安全な明示的PendingIntent、cold/warm start extrasを追加した。
- 通知IDとrequestCodeをセッションID×レポート種別の偶奇へ符号化し、旧方式の遠隔セッション間衝突を解消した。
- `MainActivity` 起動時に定期レポートSchedulerを登録した。

### B-8 WorkManager・ホスト統合テスト

- UTC週／月境界、初回、重複、長期停止後の現在分のみ通知、巻戻り、固定文言、ID衝突を検証した。
- Workerの設定OFF、権限なし、セッションなし、両方成功、重複、週次／月次片側API失敗、通知発行例外を追加した。
- MainActivityの通常起動、週次cold start、月次warm startの計装テストを追加した。

## TDD Red / Green 記録

引き継ぎコードの既存テストは初回実行で成功したが、仕様照合で次の未検証不具合を特定し、失敗テストを追加してから修正した。

1. 月次／週次レスポンスのJSON復号失敗が独立フォールバックされず、`JsonConvertException` でレポート全体が失敗した。
2. 通知タップ時のセッション復元例外がHome＋Snackbarへフォールバックせず、coroutineが失敗した。
3. `offset + sessionId` 方式で「月次 session 1」と「週次 session 10001」の通知ID／requestCodeが衝突した。

REDでは上記3系統・計4テストの失敗を確認し、例外境界の修正と通知ID符号化後に同じテストのGREENを確認した。

## テスト・ビルド結果

| コマンド | 結果 |
|---|---|
| `.\gradlew.bat :shared:testDebugUnitTest :app:testDebugUnitTest --no-daemon --rerun-tasks` | PASS。300件、failure 0、error 0、skip 0。58/58タスク実行。 |
| `.\gradlew.bat :app:compileDebugAndroidTestSources :app:assembleDebug --no-daemon --rerun-tasks` | PASS。83/83タスク実行。AndroidTestを含むソースコンパイルとdebug APK生成に成功。 |

既存プロジェクト由来のAGP/Kotlin API非推奨警告は出力されたが、エラーはなかった。

### 計装テストについて

Androidエミュレータ／実機がこの作業環境にないため、`connectedDebugAndroidTest` は実行していない。`DiscoveryPeriodicReportWorkerTest` と `MainActivityLaunchTest` は `compileDebugAndroidTestSources --rerun-tasks` でコンパイル成功まで確認した。通知表示、権限許可／拒否、offline後の再試行、cold/warm tap時の実画面到達は、エミュレータまたは実機での後続確認が必要である。

## コミット一覧

| Work Unit | Commit | 内容 |
|---|---|---|
| B-1 | `04f78d2` | 月次レポート共通契約 |
| B-2 | `70b4034` | 月次取得・独立フォールバック・リポジトリテスト |
| B-3 | `d2c8788` | 月次カード・通知キー保存 |
| B-4 | `a5d958b` | 通知遷移・カードフォーカス |
| B-5 | `7dff1e3` | KMP通知遷移テスト |
| B-6 | `aaed5f9` | 定期レポートWorker・Scheduler |
| B-7 | `dec52e0` | Notifier・MainActivity統合 |
| B-8 | `e2fcea1` | WorkManagerルール・計装テスト |

## 作業ツリー上の既存変更

`TASK.md` には引き継ぎ前から案件27の経緯追記が未コミットで存在する。本対応ではユーザー指示に従って `TASK.md` を編集・ステージ・コミットしていない。
