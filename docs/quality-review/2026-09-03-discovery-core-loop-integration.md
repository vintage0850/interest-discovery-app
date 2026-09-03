# 品質レビュー: 案件12 興味発見（Discovery）中核ループ実データ化

- 判定: **CHANGES REQUIRED**
- レビュー担当: Codex（規約適合・仕様適合の独立レビューを併用）
- レビュー日: 2026-09-03
- 対象TASK: `TASK.md` の「案件12」Task 1〜6および実機確認中の不具合修正
- 対象コミット: `94aa4a3`, `7d7d097`, `49113e1`, `d0597f0`, `c3fe9f6`, `02cab61`, `081090b`, `f16e23c`, `2f2ceaf`
- 比較範囲: `ec2516a` ... `2f2ceaf`
- 対象仕様: `docs/superpowers/specs/2026-09-02-discovery-real-api-integration-design.md`
- 実装計画: `docs/superpowers/plans/2026-09-02-discovery-real-api-integration.md`

> 注: 指定された `templates/QUALITY-REVIEW.template.md` は作業ツリーとGit履歴に存在しなかったため、`docs/quality-review/` の既存レビューと同じGateチェック形式で作成した。

## Gate 1 仕様

- [x] 1-1 中核ループの目的、対象メソッド、非目標、データ変換、受入条件が明確である。
  - 1-1 未適用の理由:
- [x] 1-2 実機確認で判明したホーム取得・debug HTTP接続の不足について、原因と対応が `TASK.md` に記録されている。
  - 1-2 未適用の理由:

## Gate 2 設計

- [x] 2-1 Ktor client、セッション、候補キャッシュ、confidence算出、FakeからRealへのDI切替が設計されている。
  - 2-1 未適用の理由:
- [ ] 2-2 UI上で選択した実験IDを詳細・開始まで一貫して引き継ぎ、API状態遷移を直列化する設計になっている。
  - 2-2 未適用の理由: クリックした候補を保持せず、`select`成功を待たずに画面遷移・`start`へ進む。

## Gate 3 実装

- [x] 3-1 Task 1〜5はRed/Greenの履歴があり、主要Repositoryメソッドが実装されている。
  - 3-1 未適用の理由:
- [ ] 3-2 選択→開始→完了の対象と順序が、バックエンドの状態遷移制約どおり保証される。
  - 3-2 未適用の理由: 代替候補では選択IDと開始IDが食い違い、`select`と`start`は別coroutineで順序保証がない。
- [ ] 3-3 実機で発見した不具合修正がTDDで固定されている。
  - 3-3 未適用の理由: `081090b` の `getHomeState()` 実データ化に回帰テストがない。

## Gate 4 レビュー

- [x] 4-1 指定されたbackend全pytestが成功する。
  - 4-1 未適用の理由:
- [ ] 4-2 エラー処理と機密性が納品可能な水準である。
  - 4-2 未適用の理由: `select`/`start`失敗を握り潰してUIだけ進行し、既定clientの `LogLevel.ALL` がリリースでも本文を出力し得る。
- [ ] 4-3 主要な失敗経路が自動テストで固定されている。
  - 4-3 未適用の理由: 設計で要求された4xx/5xx例外伝播テスト、選択対象の一貫性、select/start順序、`getHomeState()`の回帰テストがない。

## Gate 5 納品

- [x] 5-1 設計書、実装計画、Task別履歴、実機確認結果が揃っている。
  - 5-1 未適用の理由:
- [x] 5-2 ユーザーが実機で実験選択→開始→タイマー→振り返り→完了の正常系を確認済みである。
  - 5-2 未適用の理由:
- [ ] 5-3 接続先と通信ログがビルド種別・プラットフォームに応じて安全に構成されている。
  - 5-3 未適用の理由: 共通 `App.kt` に開発用 `http://localhost:8000` が固定され、HTTP全量ログも共通実装で常時有効である。

## 実行した確認

- `cd backend && python -m pytest -q`: **125 passed, 6 warnings in 32.75s**、終了コード0。
- 警告内訳: Starlette `TestClient` の非推奨1件、FastAPI `on_event` の非推奨4件、サンドボックス権限による `.pytest_cache` 作成失敗1件。テスト失敗はない。
- `git diff --check ec2516a49cc4122750afc7b1e97b1f84384db811 2f2ceaf27472aec2d1359869ba6af73777d1e24f`: 成功。
- `git diff ec2516a...2f2ceaf` ではなく、指定された直列コミットの内容を漏れなく含む `git diff ec2516a 2f2ceaf` を使用した。
- 現在の未コミット変更は案件14等を含むためレビュー対象から除外し、`2f2ceaf` のコミット済みスナップショットだけを確認した。
- `TASK.md` 末尾のユーザー実機確認済み記録と、`POST /sessions`、実験生成、表示成功の先行記録を確認した。

## ブロッキング指摘

### 1. 選択した候補と詳細・開始対象が一致しない

- `shared/src/commonMain/kotlin/com/example/myapplication/shared/ui/App.kt` は、クリックされた `experiment.id` を `selectExperiment` へ渡すが、選択した `Experiment` 自体を状態またはnavigation引数に保持しない。
- 詳細画面は常に `featuredExperiment`、無ければ候補リスト先頭を使う。このため代替候補を選ぶと、バックエンドで選択したIDとは別の実験を表示して `startExperiment` へ渡す。
- バックエンドの `generated → selected → started` 制約により開始が失敗し得る。選択したIDを詳細・開始・振り返りまで一貫して保持する修正と、先頭以外の候補を選ぶ回帰テストが必要である。

### 2. `select` と `start` の順序・失敗がUIフローに反映されない

- `DiscoveryState.selectExperiment()` と `startExperiment()` は別々のcoroutineを起動し、例外を握り潰す。画面側も `select` の完了を待たず詳細へ遷移し、`start` の完了を待たずタイマーを開始する。
- 通信順によって `start` が `select` より先に到着すると状態遷移に失敗するが、UIはタイマー→振り返りまで進み、最終的な完了送信で初めて破綻し得る。
- 設計の「呼び出し元で捕捉してエラー状態に落とす」とTask 6の一連操作要件を満たすには、API成功を待ってから遷移・タイマー開始し、失敗時は進行を止めて利用者に通知する必要がある。

### 3. 既定HTTPクライアントがリリースでも本文を全量ログ出力し得る

- `RealDiscoveryRepository.kt` の既定clientは `LogLevel.ALL` を無条件に有効化している。
- セッション識別子、生成実験、選択メモ、評価値などのリクエスト・レスポンス本文がLogcatへ出得る。本番配線の `App.kt` もこの既定clientを利用する。
- HTTP本文ログをdebugビルド限定にするか、本番では無効化する構成へ分離し、リリース相当設定で本文が記録されないことを確認する必要がある。

## テスト網羅性と非ブロッキング事項

- 設計のテスト方針にある「4xx/5xx時に例外が伝播する」テストがない。`select`/`start`/`skip`のテストも主にパス確認で、HTTP method・body・失敗応答を十分に固定していない。
- 実機で初めて発覚した `getHomeState()` の静的データ問題に対する回帰テストがなく、AGENTS.mdのTDD規則を満たさない。
- 共通 `App.kt` の `localhost` 固定はAndroid debug＋`adb reverse`では動くが、Android releaseやiOS実機では同じ意味にならない。base URLをplatform/build設定から注入するのが望ましい。
- debug cleartext許可リストに、現在使っていない旧LAN IP `10.47.192.172` が残っている。今回の判定を単独ではブロックしないが削除を推奨する。
- `getDiscovery()`、分野、レポート、設定の空値/no-opは案件12で明示された非目標のため、今回のブロッキング指摘にはしない。

## 実機確認記録との整合

- ユーザーが実機で正常系の一連操作を完了した記録は有効であり、基本経路が少なくとも一度動作した証拠として採用した。
- ただし記録には、先頭以外の候補を選んだか、低速・失敗通信で `select` と `start` の順序が維持されたか、リリースログに本文が残らないかの所見がない。そのため上記指摘を否定する証拠にはならない。

## Standards / Spec 集計

- Standards軸: high 1件、medium 2件、low 2件。最大はHTTP本文の全量ログ。
- Spec軸: high 2件、medium 1件。最大は選択対象の不一致とAPI状態遷移の競合・失敗握り潰し。

## 次の行動

- 差し戻し先: Discovery UI/state/repository実装担当（原則Kimi。設計判断が必要な場合はClaude）。
- 選択した `Experiment` を一貫して保持し、`select`成功後に詳細へ、`start`成功後にタイマーへ進むよう修正する。失敗時は画面遷移を止め、既存エラーUIへ通知する。
- HTTP本文ログをdebug限定または無効化し、base URLをplatform/build構成から注入する。
- 先頭以外の候補、select/startの遅延順序と失敗、4xx/5xx、`getHomeState()`を覆う回帰テストをTDDで追加する。
- 修正後にDiscoveryのGradleテスト、app debug/releaseビルド、`cd backend && python -m pytest -q`、`git diff --check`を実行し、先頭以外の候補を含む実機フローを再確認してGate 4再レビューを依頼する。

---

## Gate 4再レビュー（Codex / 2026-09-03）

- 判定: **CHANGES REQUIRED**
- 対象: 未コミットworking treeの `DiscoveryState.kt`、`RealDiscoveryRepository.kt`、`DiscoveryStateTest.kt`、`RealDiscoveryRepositoryTest.kt`（統合確認として `App.kt`、`MainActivity.kt`、iOS `MainViewController.kt` の呼び出し・ビルド種別配線も確認）

### 前回指摘4点の確認

1. **選択IDと詳細・開始対象の不一致: 解消。** `selectedExperiment` が選択成功後に保持され、詳細表示と開始処理はいずれも同じ `Experiment` を参照する。先頭以外の `exp-2` を対象にした回帰テストも追加されている。
2. **select→start順序保証と失敗のUI反映: 実装は解消、回帰テストは一部不足。** `selectExperiment` 成功後のcallbackでのみ詳細へ遷移し、`startExperiment` 成功後にのみタイマー開始・実行画面遷移する。両失敗時は遷移せず `messages` に通知するテストがある。一方、repositoryの `select` を意図的にsuspendさせた状態で `start` が先行しないこと、または呼び出し履歴が厳密に `select` → `start` となることを固定するテストはない。
3. **リリースでのHTTP本文ログ: 解消。** 既定値 `enableHttpLogging = false` ではKtor `Logging` pluginを導入せず、Androidは `BuildConfig.DEBUG`、iOSは `false` を渡す。plugin有無のテストも追加されている。
4. **回帰テスト不足: 部分解消。** 先頭以外の候補、select/start失敗時のUI停止・通知、selectの4xx、startの5xx、`getHomeState()` の実データ取得、logging既定無効は追加済み。ただし前回明示したselect/startの競合・順序回帰を直接検証するテストが不足しているため、全件解消とは判定できない。

### 独立検証

- `.\gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"`: **BUILD SUCCESSFUL**（25 actionable tasks: 2 executed, 23 up-to-date、終了コード0）。初回はsandboxの `C:\.gradle` ロック権限で失敗したため、既存の `C:\Users\vinta\.gradle` を明示して再実行した。
- `cd backend && python -m pytest -q`: **125 passed, 6 warnings in 22.87s**（終了コード0）。警告はStarlette/FastAPIの非推奨と `.pytest_cache` 作成権限で、テスト失敗ではない。
- `git diff --check -- <対象4ファイル>`: 成功。改行変換予告以外のエラーなし。

### 必須修正

- `DiscoveryStateTest.kt` に、遅延を制御できる記録用repositoryを用いた回帰テストを追加する。少なくとも、select完了前には詳細遷移callbackが呼ばれずstart操作へ到達できないこと、および正常フローのrepository呼び出し順が `select:<id>` → `start:<same-id>` であることを明示的に検証する。
- 修正後、同じDiscovery Gradleテストを再実行し、CodexへGate 4再々レビューを依頼する。production codeへの追加修正は現時点では要求しない。

---

## Gate 4再々レビュー（Codex / 2026-09-04）

- 判定: **PASS**
- 対象: `DiscoveryStateTest.kt` に追加された `selectThenStart_callsRepositoryInOrderForSameExperiment`、`startExperiment_doesNotRunAheadOfAnInFlightSelect`、および記録・遅延制御用 `RecordingRepository`

### 前回ブロッキング指摘の確認

1. **同一IDでのselect→start順序: 解消。** `selectThenStart_callsRepositoryInOrderForSameExperiment` は先頭以外の `exp-2` を選択・開始し、repository呼び出し履歴を `listOf("select:exp-2", "start:exp-2")` と完全一致で比較する。順序とID同一性の双方を直接検証できている。
2. **select完了前のstart先行防止: 解消。** `startExperiment_doesNotRunAheadOfAnInFlightSelect` は `selectExperiment` に1,000msの仮想遅延を注入し、selectがsuspend中の状態でstart操作を呼ぶ。その時点でstart履歴が存在せず、`runningState.isRunning` もfalseであることを確認するため、select完了前にrepository startまたはタイマー開始が先行しないことを検証できている。select完了後の再操作では履歴が `select:exp-2` → `start:exp-2` となり、running状態へ移ることも確認している。

この2件により、前回Gate 4再レビューで残った順序回帰テスト不足は解消した。前回確認済みの選択対象一貫性、失敗時UI停止・通知、4xx/5xx、HTTPログ既定無効化、`getHomeState()` 回帰と合わせ、追加のブロッキング指摘はない。

### 独立検証

- `./gradlew :shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*"`: **BUILD SUCCESSFUL in 46s**（25 actionable tasks: 2 executed, 23 up-to-date、終了コード0）。
- 初回はGradle既定領域 `C:\.gradle` のロックファイル作成権限で終了コード1となったため、既存の `C:\Users\vinta\.gradle` を `GRADLE_USER_HOME` に指定して同一タスク・同一テストフィルタを再実行した。テスト失敗ではない。
