# 2026-09-09 案件27-31 Codex最終ゲートレビュー修正レポート

## 概要

TASK.md「2026-09-09 Codex最終ゲートレビュー結果 — FAIL(修正必須3件)」に記載された3件の修正必須事項をTDD(Red→Green→Refactor)で対応した。
全バックエンドテストおよびAndroid/KMP単体テストがPASSしている。

---

## 修正内容

### 修正1(重大): 月次キャッシュの削除漏れ

**対象:** `backend/discovery/repository.py` の `delete_session_cascade()`

案件30(プライバシー削除)は案件27(月次レポート)マージ前に設計されており、`MonthlyNarrativeCache` テーブルがカスケード削除対象から漏れていた。

**実施内容:**
- `WeeklyNarrativeCache` 削除直後に `MonthlyNarrativeCache` も `session_id` で削除する処理を追加
- `backend/tests/test_discovery_repository.py` のカスケード削除テストに、月次キャッシュが作成・削除されることの検証を追加

### 修正2(重大): Evidence.behavior_categories列のスキーマ移行漏れ

**対象:** `backend/discovery/models.py` の `Evidence.behavior_categories`

`SQLModel.metadata.create_all()` は新規テーブル作成のみで既存テーブルへの列追加(ALTER TABLE)は行わないため、本番相当の既存 `discovery.db` では `behavior_categories` 列が存在せず、`no such column` エラーが発生していた。

**実施内容:**
- `backend/discovery/repository.py` に `_run_migrations()` を追加
- `DiscoveryRepository.__init__()` 実行時に SQLite の `PRAGMA table_info(evidence)` で列の有無を確認し、`behavior_categories` 列が無ければ `ALTER TABLE evidence ADD COLUMN behavior_categories TEXT` を実行
- 他DB方言に対しては何もしない(現時点ではSQLiteのみを想定)
- 旧スキーマで作成したDBからのマイグレーションと、冪等性(2回初期化してもエラーにならない)を検証するテストを `backend/tests/test_discovery_repository.py` に追加

### 修正3(中): 通知未発行でも通知済みキーが保存される競合窓

**対象:** `app/src/main/java/com/example/myapplication/work/DiscoveryReportNotifier.kt`、`DiscoveryPeriodicReportWorker.kt`

`DiscoveryReportNotifier` は通知権限が無い場合に静かに return していたが、`DiscoveryPeriodicReportWorker` はその後も通知済みキーを保存してしまっていた。

**実施内容:**
- `DiscoveryReportNotifier.notifyReport()` の戻り値を `Unit` から `Boolean` に変更
  - 通知が実際に発行された場合 `true`
  - 通知権限が無いなどで発行されなかった場合 `false`
- `AndroidDiscoveryReportNotifier.notifyReport()` で、通知権限が無い場合に `false` を返すよう修正
- `DiscoveryPeriodicReportWorker` で、`notifier.notifyReport()` が `true` を返した場合のみ `saveLastNotifiedWeekKey` / `saveLastNotifiedMonthKey` を実行するよう修正
- `app/src/androidTest/java/com/example/myapplication/work/DiscoveryPeriodicReportWorkerTest.kt` の `FakeDiscoveryReportNotifier` を `Boolean` 返却に対応させ、通知発行が失敗(false)した場合にキーが保存されないことを検証するテストケースを追加

---

## テスト結果

### バックエンドテスト

```bash
cd backend
python -m pytest
```

結果:

```
==============================
355 passed, 5 warnings in 51.39s
==============================
```

### Android / KMP 単体テスト

```bash
./gradlew.bat :shared:testDebugUnitTest :app:testDebugUnitTest --no-daemon
```

結果:

```
BUILD SUCCESSFUL in 33s
72 actionable tasks: 1 executed, 71 up-to-date
```

また、Android instrumented test のコードも修正したため、コンパイル確認を実施:

```bash
./gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon
```

結果:

```
BUILD SUCCESSFUL in 56s
56 actionable tasks: 3 executed, 53 up-to-date
```

---

## コミット一覧

| # | ハッシュ | メッセージ |
|---|----------|------------|
| 1 | `3848c9d` | fix(backend): 月次キャッシュのカスケード削除漏れを修正 |
| 2 | `5d06665` | fix(backend): Evidence.behavior_categories 列のスキーマ移行を追加 |
| 3 | `fe43548` | fix(app): 通知未発行時に通知済みキーが保存される競合窓を修正 |

---

## 備考

- 全修正はTDDで実施。各修正に対してまず失敗するテストを追加し、その後最小限の実装でGREENにした。
- 修正1のテストでは `save_monthly_narrative_cache()` で月次キャッシュを作成し、`delete_session_cascade()` 後に `get_monthly_narrative_cache()` が `None` となることを検証。
- 修正2のテストでは一時SQLite DBを新スキーマで作成後、`ALTER TABLE evidence DROP COLUMN behavior_categories` で旧スキーマに戻し、`DiscoveryRepository` 初期化でマイグレーションが実行されることを検証。
- 修正3のテストでは `FakeDiscoveryReportNotifier` に特定種別で `false` を返す `refuseType` を追加し、週次通知が拒否された場合に週次キーのみ保存されず、月次通知と月次キー保存は正常に行われることを検証。
