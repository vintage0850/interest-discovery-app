# 案件31 矛盾検出結果のUI可視化（Discoverタブ）完了報告

- 実施日: 2026-09-08
- 対象ブランチ: `feature/contradiction-handling`
- 対象: Discoverタブの「気になる発見」カード表示
- ユーザー指示: `TASK.md` は編集しない / バックエンド・`generate_experiments` ロジックは変更しない

## 実施内容

### 1. モデル・DTO拡張

- `DiscoveryData` に `discrepancies: List<DiscrepancyUiModel>` を追加した。
- `DiscrepancyUiModel` は `type`, `domain`, `experimentId`, `message` を持つ。
- `BehaviorSummaryDto` / `DiscrepancyDto` を追加し、`RealDiscoveryRepository.getDiscovery()` でDTOからUIモデルへマッピングした。

### 2. UIカード実装

- `DiscoverTabScreen.kt` に「気になる発見」カードを追加した。
- カードは `data.discrepancies.isNotEmpty()` の場合のみ表示される。
- 各 `discrepancy.message` をリスト表示する。
- 固定CTA文言「もう一度試してみますか？」を表示する。

### 3. UIテスト

- Robolectric + `createAndroidComposeRule<TestActivity>` で `DiscoverTabScreenTest` を追加した。
- テストケース:
  - `discrepancies` が空 → カード・CTAともに存在しない
  - `discrepancies` が1件 → カードタイトル・message・CTAが表示される
  - `discrepancies` が複数件 → それぞれのmessageが表示される

## TDD Red / Green 記録

1. **RED**: テスト先行で3件のテストを作成。未実装時点でカードが存在しないため2件が失敗した。
2. **GREEN実装後の調整**:
   - タイトルに絵文字を含めていたため、`onNodeWithText` の完全一致で検出できず失敗。タイトルを「気になる発見」のみに変更。
   - 複数件テストでCTAが画面外に出ていたため、`performScrollTo()` でスクロール後に表示確認するよう修正。
3. **GREEN**: 最終的に3件すべてがパスした。

## テスト・ビルド結果

| コマンド | 結果 |
|---|---|
| `.\gradlew.bat :shared:testDebugUnitTest --tests "*DiscoverTabScreenTest*" --no-daemon` | PASS。3/3。 |
| `.\gradlew.bat :shared:testDebugUnitTest --no-daemon` | PASS。195 tests、failures 0、errors 0、skipped 0。 |
| `.\gradlew.bat :shared:assembleDebug --no-daemon` | PASS。debug AAR生成成功。 |

既存プロジェクト由来のAGP/Kotlin API非推奨警告は出力されたが、エラーはなかった。

## コミット

| Commit | 内容 |
|---|---|
| `cf126bf` | feat(discovery): 矛盾検出結果のUIカード表示とテスト |

## 備考

- `TASK.md` はユーザー指示に従い編集・コミットしていない。
- バックエンドの `_detect_discrepancies()` および `generate_experiments` ロジックは変更していない。
- 新規追加の `local.properties` は `.gitignore` 対象でありコミットに含まれていない。
