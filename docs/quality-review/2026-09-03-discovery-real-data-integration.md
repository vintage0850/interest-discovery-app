# 品質レビュー: Discovery追加画面の実データ統合

- 判定: **CHANGES REQUIRED**
- レビュー担当: Codex（規約適合・仕様適合の独立レビューを併用）
- レビュー日: 2026-09-03
- 対象TASK: `TASK.md` の「案件14」内「追加実装（Claude / 別セッション、日付未記録）— 「対象外」節のgetDiscovery等を実データ化」以降
- 比較範囲: 指定7ファイルの未コミット `git diff`（Android設定ストレージは未追跡ファイル本文を確認）

> 注: 指定された `templates/QUALITY-REVIEW.template.md` は作業ツリー、Git管理対象、利用可能なSpartan配置に存在しなかったため、`docs/quality-review/` の既存レビューと同じGateチェック形式で作成した。

## Gate 1 仕様

- [x] 1-1 実データ化するメソッドと対象ファイルがTASK.mdに列挙されている。
  - 1-1 未適用の理由:
- [ ] 1-2 レポート各項目と設定内の保存件数について、データ源・空状態・集計規則が明確である。
  - 1-2 未適用の理由: `weeklyInsights`、`changeFromPast`、`signalDistribution`、`topSignal`、`savedSignalCount` の意味とデータ源が明文化されていない。

## Gate 2 設計

- [x] 2-1 セッションサマリーAPI、共通ストレージ抽象、Android永続化の構成が決まっている。
  - 2-1 未適用の理由:
- [ ] 2-2 表示モデルの全フィールドを実データまたは明示的な空状態へ変換する設計になっている。
  - 2-2 未適用の理由: `ReportData` と `MyDataSettings` の既定サンプル値が本番経路へ漏れる。

## Gate 3 実装

- [x] 3-1 バックエンドに合計時間・分野別生成数・分野別完了数の集計とテストが追加されている。
  - 3-1 未適用の理由:
- [x] 3-2 `getDiscovery()`、`getDomainFields()`、通知設定・時刻・データ共有設定の永続化が実装されている。
  - 3-2 未適用の理由:
- [ ] 3-3 `getReportData()` と `getSettings()` が固定サンプル値を返さない。
  - 3-3 未適用の理由: レポート3フィールドと保存済みシグナル数が固定既定値のままである。

## Gate 4 レビュー

- [x] 4-1 バックエンド全pytestが成功する。
  - 4-1 未適用の理由:
- [ ] 4-2 指定されたDiscovery GradleテストをCodex環境で完走できる。
  - 4-2 未適用の理由: ローカルキャッシュを用いたオフライン再実行でもFoojayプラグイン依存がキャッシュに無く、ネットワーク制限で解決できなかった。Claudeの同一コマンド成功記録はある。
- [ ] 4-3 実データ化した各画面の意味論と永続化契約が自動テストで固定されている。
  - 4-3 未適用の理由: レポートの固定値混入、`topSignal`の集計元、`savedSignalCount`を検証する回帰テストがない。

## Gate 5 納品

- [x] 5-1 変更概要と先行検証結果がTASK.mdに記録されている。
  - 5-1 未適用の理由:
- [ ] 5-2 変更ファイル一覧と実際の作業ツリー差分が一致する。
  - 5-2 未適用の理由: Kotlin側テストの `RealDiscoveryRepositoryTest.kt` が変更済みだが、案件14の変更ファイル一覧と今回指定されたレビュー対象に含まれていない。コミット漏れを防ぐためClaudeが確認する必要がある。

## 実行した確認

- `cd backend && python -m pytest -q`: **125 passed, 6 warnings**、終了コード0（36.60秒）。5件の既存deprecation warningに加え、サンドボックスで `.pytest_cache` を作成できないwarningが1件発生した。
- `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"`: Gradle起動前に既定の `C:\.gradle` へlock親ディレクトリを作成できず失敗。
- ワークスペース内の `GRADLE_USER_HOME` と既存Gradle 9.3.1配布物を使い、同じテストを `--offline` で再実行: Foojay resolver plugin 1.0.0がローカルキャッシュに無く、ネットワーク制限下で解決できず **BUILD FAILED**。したがってCodexによるGradleテスト成功は確認できていない。
- `TASK.md` にはClaudeによる同一Gradleコマンドの **BUILD SUCCESSFUL** が記録されている。
- 追跡済み対象6ファイルの `git diff --check` は空白エラーなし。未追跡のAndroid設定ストレージも `git diff --no-index --check` で空白エラーなし。
- 対象7ファイル、`DiscoveryModels.kt` の既定値、`TASK.md`案件14を静的確認した。

## ブロッキング指摘

### 1. レポート画面に固定サンプルの洞察と分布が残る

- `RealDiscoveryRepository.kt:218-225` の `getReportData()` は `totalCompletedCount`、`totalMinutesSpent`、`topSignal` だけを指定している。
- `DiscoveryModels.kt:107-113` の `weeklyInsights`、`changeFromPast`、`signalDistribution` には固定のデモ文言と件数が既定値として設定されているため、本番の実ユーザーにも架空の傾向と分布が表示される。
- TASK.md:2979 の「`getReportData()`を実データ化」を満たすには、各項目をバックエンド実値へ配線するか、未提供項目には固定サンプルではなく明示的な空状態を返す必要がある。
- 実データあり・空データ双方で固定文言や固定件数が混入しないテストを追加すること。

### 2. `topSignal` が実際の行動シグナルではなく完了分野から推測される

- `RealDiscoveryRepository.kt:220-224` は最多の `domainCompletedCounts` を `domainToBehaviorSignal` で変換している。
- バックエンドの `BehaviorSummary` には実測の `action_type_counts` が既に存在する。分野と行動シグナルは別概念であり、例えばtech分野でCREATE行動が最多でもANALYZEと表示され得る。
- DTOへ `action_type_counts` を取り込み、実測値の最多項目から `topSignal` と分布を構築すること。同数時と空データ時の規則もテストで固定すること。

### 3. `savedSignalCount` が常に固定値10になる

- `DiscoverySettingsStorage.android.kt:14-21` は `savedSignalCount` を保存・復元せず、`MyDataSettings()` の既定値10を毎回返す。
- これはユーザーの保存済みシグナル実績と無関係な件数を設定画面へ表示する。設定値でないなら設定ストレージから分離してサマリーの `total_signals` から取得し、設定値なら保存対象へ含める必要がある。
- 新しいストレージインスタンスでの復元と、バックエンド実績との整合を回帰テストで固定すること。

## 非ブロッキング指摘

- `RealDiscoveryRepository.kt` にAPI DTO、表示用分野メタデータ、設定ストレージ抽象・既定実装まで集約されており、責務が肥大化している。設定ストレージは共通の別ファイルへ分離する余地がある。
- `getDomainFields()` と `getReportData()` は同じサマリーを個別取得するため、連続した画面操作で重複通信やスナップショット不整合が起こり得る。必要なら短期キャッシュまたは共有ロードを検討する。
- `roundedTo1Decimal()` は表示フォーマットをRepositoryで行う。現状の表示要件ではブロックしないが、表示層のformatterへ寄せる余地がある。

## Standards / Spec 集計

- Standards軸: blocking 3件、judgement call 3件。最大は固定サンプル値を実ユーザーのレポートへ表示すること。
- Spec軸: blocking 2件、中程度1件、台帳不整合1件。最大は `getReportData()` の実データ化が未完了であること。

## 次の行動

- 差し戻し先: **Claude（追加実装担当）**。
- `ReportData` の全表示項目、実測 `action_type_counts` による `topSignal` / 分布、実際の `savedSignalCount` を実装し、対応するKotlinテストを追加する。
- 案件14の変更ファイル一覧へ `RealDiscoveryRepositoryTest.kt` を含めるべきか確認し、必要な差分をコミット対象から漏らさない。
- 修正後に `cd backend && python -m pytest -q` と `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"` を再実行し、Gate 4再レビューを依頼する。

---

## Gate 4 再レビュー（2026-09-03 / Codex）

- 判定: **PASS**
- 対象コミット: `79d66e9b3669f2022ac44b9787f1d70d513a9fc9`
- 作業履歴訂正コミット: `eb40cb9faed1a613e7d455cf95cb2b3782177182`

### 前回指摘1〜3の確認

1. **解消 — 固定サンプル値の混入**
   - `getReportData()`が`weeklyInsights`と`changeFromPast`へ明示的に空文字列を設定し、`signalDistribution`を実集計値から構築するようになった。
   - 実データなしの場合も空Mapを返す回帰テスト、および固定デモ文言を返さない回帰テストが追加されている。
2. **解消 — `topSignal`の集計元**
   - `BehaviorSummaryDto`へ`actionTypeCounts`を追加し、解決可能な実測キーがある場合はそれを`topSignal`と分布へ優先利用する。
   - 解決可能な実測キーがない場合は、設計判断どおり`domainCompletedCounts`を実績ベースのフォールバックとして集計する。実測値優先、分野フォールバック、データなしの各ケースがテストされている。
3. **解消 — `savedSignalCount`の固定値**
   - `getSettings()`が保存済み設定を読み込んだ後、サマリーの実測`totalSignals`で`savedSignalCount`を上書きする。
   - 保存値10に対してAPI値7を返す回帰テストが追加され、設定値と実績値の責務が分離されている。

### 独立検証

- `cd backend && python -m pytest -q`: **125 passed, 6 warnings**、終了コード0（31.95秒）。5件の既存deprecation warningに加え、権限制限で`.pytest_cache`を作成できないwarningが1件発生した。
- `.\gradlew.bat :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"`: Gradle起動前に`C:\.gradle\wrapper\dists\...\gradle-9.3.1-bin.zip.lck`の親ディレクトリを作成できず終了したため、Codex環境では完走できなかった。
- GradleについてはKimiの同一コマンド **BUILD SUCCESSFUL（34 tests passed）** の記録を参照した。今回の失敗はテスト実行前の環境権限制限であり、品質判定を妨げる実装上の失敗ではない。
- `git show --check 79d66e9`は空白エラーなし。コミットには実装と対応回帰テストの両方が含まれる。

### 結論

前回のブロッキング指摘1〜3は、確定済みの設計判断に沿ってすべて解消されている。新たなブロッキング指摘はなく、Gate 4を**PASS**とする。前回の非ブロッキング指摘（Repositoryの責務分割、サマリー取得の共有、表示フォーマット配置）は将来改善事項であり、今回の承認を妨げない。
