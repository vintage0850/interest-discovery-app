# 案件33 シグナル蓄積トリガー・レポート(マイルストーンナラティブ) 完了報告

- 実施日: 2026-09-09
- 対象ブランチ: `feature/signal-milestone-report`
- 引き継ぎ元: 前セッション(Kimi)の `[Tool use interrupted]` による中断タスク

## 実施内容

### 1. バックエンド実装

前セッションで完了済みの `MilestoneNarrativeCache`・`MilestoneNarrativeResponse` を起点に、以下をTDDで追加した。

- `discovery/repository.py`:
  - `count_signals(session_id)` ヘルパー
  - `get_milestone_narrative_cache(session_id, milestone)` / `save_milestone_narrative_cache(...)`
  - 同時挿入時の `IntegrityError` ハンドリング(勝者を取得・敗者は無視)
  - `delete_session_cascade` に `MilestoneNarrativeCache` を含める
- `discovery/gemini_prompts.py`:
  - `MilestoneNarrativeCandidate` Pydantic モデル(`insight_text` バリデーション付き)
  - `generate_milestone_narrative(signal_count, milestone, top_domain)`
  - システムインストラクション(差別的・敏感な属性を含まない制約)
- `discovery/router.py`:
  - `GET /sessions/{session_id}/report/milestone-narrative`
  - milestone = 総シグナル件数 // 10、milestone=0 の場合 404
  - キャッシュ優先、未生成時のみ Gemini 呼び出し、生成後はキャッシュ保存

### 2. Android/KMP 実装

- `DiscoveryModels.kt`:
  - `MilestoneNarrative` データクラス(`milestone`, `insightText`)
  - `ReportType.MILESTONE("milestone")`
  - `ReportData` に `milestoneNarrative` / `milestoneErrorMessage` を追加
- `DiscoveryRepository.kt`:
  - `getMilestoneNarrative(): MilestoneNarrative` 契約を追加
- `RealDiscoveryRepository.kt`:
  - `MilestoneNarrativeResponseDto` と snake_case → camelCase 変換
  - `getMilestoneNarrative()` 実装
  - `getReportData()` にマイルストーン取得を追加し、週次/月次/マイルストーンを独立してフォールバック
- `FakeDiscoveryRepository.kt`:
  - `getMilestoneNarrative()` ダミー実装(milestone=2)
  - `getReportData()` にマイルストーンを統合
- `DiscoveryState.kt`:
  - `onReportNotificationTapped()` が `ReportType.MILESTONE` も汎用的に処理するよう既存実装を活用(追加変更なしで対応可能だった)
- `DiscoverySettingsStorage` / `InMemoryDiscoverySettingsStorage`:
  - `getLastNotifiedMilestone(sessionId)` / `saveLastNotifiedMilestone(sessionId, milestone)` を追加
- `DiscoverySettingsStorage.android.kt`:
  - SharedPreferences への `last_notified_milestone_$sessionId` 永続化を追加
- `DiscoveryPeriodicReportWorker.kt`:
  - 週次/月次に加え、マイルストーン到達判定を追加
  - 到達済みマイルストーンが未通知の場合のみ通知・キー更新
  - API 失敗時はキー保存せず次回再試行
- `DiscoveryReportNotifier.kt`:
  - マイルストーン固定通知文言を追加
  - 通知ID/requestCode の符号化を3種別・セッション間で衝突しない `30_000 + sessionId * 3 + reportType.ordinal` に変更

### 3. テスト追加

- バックエンド:
  - `test_discovery_repository.py`: `TestMilestoneNarrativeCacheRepository`(保存/取得/上書き/分離/件数/カスケード削除/同時挿入)
  - `test_discovery_gemini_prompts.py`: `TestGenerateMilestoneNarrative`(成功/長文/空・空白・複数行/JSON破損/SDKエラー/プロンプト内容)
  - `test_discovery_router.py`: `TestMilestoneNarrativeEndpoints`(200/404/503/API key欠如/クライアント引数/キャッシュ再利用/キャッシュ分離)
- Android/KMP:
  - `RealDiscoveryRepositoryTest`: マイルストーン取得・エラー・`ReportData` 統合・フォールバック系テスト
  - `PeriodicReportNotificationTest`: `ReportType.MILESTONE` 復号・通知タップ遷移・フォーカス
  - `DiscoveryPeriodicReportRulesTest`: マイルストーン固定文言・3種別通知ID/requestCode衝突防止
  - `DiscoveryPeriodicReportWorkerTest`: マイルストーン通知成功・重複抑制・到達済みスキップ・API失敗時の継続

## TDD Red / Green 記録

今回追加したすべてのテストは、実装前に失敗(Red)を確認してから最小実装で通過(Green)させた。

1. `DiscoveryReportNotifier` の `buildReportNotificationContent(ReportType.MILESTONE)` が `when` 式の網羅性エラーでコンパイル失敗 → `MILESTONE` 分岐追加で Green。
2. `reportIdentityFor` が2種別前提だったため3種別衝突テストが失敗 → `sessionId * 3 + ordinal` に変更で Green。
3. `InMemoryDiscoverySettingsStorage` にマイルストーンキーが未実装のため Worker テストが失敗 → インターフェース・メモリ実装・Android実装を追加で Green。
4. `RealDiscoveryRepository.getMilestoneNarrative()` が `NotImplementedError` を投げるためテストが失敗 → DTO・メソッド実装で Green。
5. `getReportData()` のマイルストーン失敗フォールバックテストが失敗 → try/catch で `milestoneErrorMessage` を設定するよう変更で Green。

## テスト・ビルド結果

| コマンド | 結果 |
|---|---|
| `python -m pytest` (backend) | **390 passed**, 5 warnings in 83.55s |
| `./gradlew testDebugUnitTest` (Android/KMP unit) | **BUILD SUCCESSFUL**, failure 0, error 0 |
| `./gradlew :app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL**, Android instrumented test ソースコンパイル成功 |

既存プロジェクト由来の AGP/Kotlin API 非推奨警告は出力されたが、エラーはなかった。

### 計装テストについて

Android エミュレータ/実機がこの作業環境にないため、`connectedDebugAndroidTest` は実行していない。追加した `DiscoveryPeriodicReportWorkerTest` のマイルストーン系テストは `:app:compileDebugAndroidTestKotlin` でコンパイル成功まで確認した。

## 変更ファイル一覧

### バックエンド

- `backend/discovery/repository.py`
- `backend/discovery/gemini_prompts.py`
- `backend/discovery/router.py`
- `backend/tests/test_discovery_repository.py`
- `backend/tests/test_discovery_gemini_prompts.py`
- `backend/tests/test_discovery_router.py`

### Android/KMP

- `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryModels.kt`
- `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryRepository.kt`
- `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt`
- `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/FakeDiscoveryRepository.kt`
- `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/DiscoveryState.kt`(変更なし、既存実装で対応)
- `shared/src/androidMain/kotlin/com/example/myapplication/shared/discovery/DiscoverySettingsStorage.android.kt`
- `app/src/main/java/com/example/myapplication/work/DiscoveryPeriodicReportWorker.kt`
- `app/src/main/java/com/example/myapplication/work/DiscoveryReportNotifier.kt`
- `shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepositoryTest.kt`
- `shared/src/commonTest/kotlin/com/example/myapplication/shared/discovery/PeriodicReportNotificationTest.kt`
- `app/src/test/java/com/example/myapplication/work/DiscoveryPeriodicReportRulesTest.kt`
- `app/src/androidTest/java/com/example/myapplication/work/DiscoveryPeriodicReportWorkerTest.kt`

### ドキュメント

- `docs/quality-review/2026-09-09-案件33-milestone-narrative-report.md`(本ファイル)
- `TASK.md` 案件33セクションに「実装結果」を追記

---

## ゲートレビュー指摘への対応 (2026-09-09)

Codex から `CHANGES REQUIRED` とされた指摘1〜4を修正した。

### 指摘1: 並行リクエストでのGemini重複呼び出し防止

- `DiscoveryRepository.get_or_reserve_milestone_narrative_cache()` を新設。`session_id × milestone` 単位でDB予約レコードを挿入し、挿入に成功したリクエストだけがGemini生成の「勝者」となる。
- 敗者は `wait_for_milestone_narrative_cache()` で勝者の生成結果をポーリング待機し、Geminiを呼ばずに同一キャッシュを返す。
- `discovery/router.py` のエンドポイントは予約→生成(勝者)または待機(敗者)→応答の流れに変更。待機中は503ではなく最大10秒待機後にキャッシュを返す。
- `test_discovery_router.py` に `httpx.AsyncClient(transport=httpx.ASGITransport(app=app))` を使った並行テスト `test_get_milestone_narrative_concurrent_requests_call_gemini_once` を追加。2リクエストが同一内容を返し、`generate_milestone_narrative()` の呼び出しが1回であることを検証。

### 指摘2: 中間マイルストーンの欠落防止と通知文言修正

- `GET /sessions/{session_id}/report/milestone-narrative` に `?milestone={milestone}` クエリパラメータを追加(未指定時は従来通り最新milestone)。
- `RealDiscoveryRepository` に `getMilestoneNarrative(milestone: Int)` を追加し、明示的milestoneをURLに反映。
- `DiscoveryPeriodicReportWorker` を `(lastNotifiedMilestone ?: 0) + 1` から `latestMilestone` までループさせ、未通知の中間マイルストーンを欠落なく生成・通知。
- `DiscoveryReportNotifier` の通知文言構築に `reachedSignalCount` を導入。マイルストーン通知タイトルを「シグナルが{milestone×10}件溜まりました」と表示。
- `DiscoveryPeriodicReportWorkerTest` に中間マイルストーン(1→3で2も通知)ケース、`reachedSignalCount` 検証ケースを追加。

### 指摘3: 通知ID/requestCodeの後方互換性維持

- `DiscoveryReportNotifier.reportIdentityFor()` で週次/月次の既存計算式 `30_000 + sessionId * 2 + (MONTHLY ? 1 : 0)` を維持。
- マイルストーンのみ別領域 `1_000_000 + sessionId` に明示的に割り当て。`ReportType.ordinal` には依存しない。
- `DiscoveryPeriodicReportRulesTest` に変更前の週次/月次ID固定値回帰テスト、複数セッション・3種別の衝突防止テストを追加。

### 指摘4: 無関係テストの整理とドキュメント更新

- `backend/tests/test_discovery_repository.py` から重複していた `TestSchemaMigrationRepository` の一方を削除。残す方は `test_migration_adds_behavior_categories_column_to_existing_db` と `test_migration_is_idempotent` を含む1クラス。
- `TASK.md` 案件33セクションに「テスト結果」と「引き継ぎメモ」を追記。
- 本ファイルに「ゲートレビュー指摘への対応」を追記。

## 修正後のテスト・ビルド結果

| コマンド | 結果 |
|---|---|
| `python -m pytest` (backend) | **398 passed**, 5 warnings in 79.52s |
| `./gradlew testDebugUnitTest` (Android/KMP unit) | **BUILD SUCCESSFUL** (0 failed) |
| `./gradlew :app:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL** |

既存プロジェクト由来の AGP/Kotlin API 非推奨警告は出力されたが、エラーはなかった。
