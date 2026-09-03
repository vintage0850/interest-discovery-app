# 品質レビュー: Mikke設計書ギャップ対応 P0

- 判定: **CHANGES REQUIRED**
- レビュー担当: Codex（規約適合・仕様適合の独立レビューを併用）
- レビュー日: 2026-09-04
- 対象TASK: `TASK.md` の「案件15：Mikke設計書ギャップ対応（P0）」
- 比較範囲: `HEAD`（`195dc2cb23b6bae60d1bd3130dced0b4958126ad`）に対する未コミット差分のうち、案件15で列挙されたバックエンド・shared実装およびバックエンドテスト

## Gate 1 仕様

- [x] 1-1 P0の対象、対象外、閾値、ユーザー反応、Criterion昇格条件、no-insight時の動作がTASK.mdに明記されている。
  - 1-1 未適用の理由:
- [ ] 1-2 `POST /sessions/{id}/hypothesis/update` のnullable化について、既存API利用者向けの互換方針が定義されている。
  - 1-2 未適用の理由: 従来の `HypothesisResponse` オブジェクトが低確信度時にJSON `null`へ変わる破壊的変更だが、API versioning、移行期間、安定した結果wrapper等の方針がない。リポジトリ内Androidはこの更新エンドポイントを呼んでいないため現行コードは直接壊れないものの、旧・外部クライアントの後方互換性は保証されない。

## Gate 2 設計

- [x] 2-1 `HypothesisFeedbackResult` は `feedback`、`updated_hypothesis`、`new_criterion` をまとめて返し、クライアントが1回のPOST結果で状態を更新できるレスポンス設計になっている。
  - 2-1 未適用の理由:
- [x] 2-2 confidence delta、Criterion昇格閾値、no-insight閾値は、バックエンドの名前付きモジュール定数へ置かれている。
  - 2-2 未適用の理由:
- [ ] 2-3 Android実装が `HypothesisFeedbackResult` の1往復設計を利用し、送信結果を原子的にUIへ反映する。
  - 2-3 未適用の理由: `RealDiscoveryRepository.sendHypothesisFeedback()` はPOST本文を読み取らず、成功直後に `getDiscovery()` でsummaryを追加GETする。実際は2往復であり、POST成功後にGETが失敗すると、サーバーではconfidence更新済みなのにUIは失敗を表示する。ユーザーが再送すると同じ反応が重複加算され得る。

## Gate 3 実装

- [x] 3-1 バックエンドは同感 `+0.15`、わからない `0`、違う `-0.15`、`[0,1]` clampを実装している。
  - 3-1 未適用の理由:
- [x] 3-2 同感後のconfidenceが `0.6`以上ならCriterionへ昇格し、同じsource hypothesisでは重複作成せずconfidenceと更新日時を更新する。
  - 3-2 未適用の理由:
- [x] 3-3 Gemini結果のconfidenceが `0.3`未満なら仮説を保存せず、`null`を返すno-insightガードが実装されている。
  - 3-3 未適用の理由:
- [x] 3-4 Python/Kotlinの反応値3種に対応漏れがない。
  - 3-4 未適用の理由: Pythonは `agree` / `unsure` / `disagree`、Kotlinは `AGREE` / `UNSURE` / `DISAGREE` を定義し、送信時に `reaction.name.lowercase()` で期待するwire値へ変換している。
- [ ] 3-5 フィードバック送信の多重操作で、意図しないconfidence累積を防止する。
  - 3-5 未適用の理由: UIに送信中状態やボタン無効化がなく、`actionMutex` は連打を破棄せず順番にすべて送信する。同感の偶発連打だけでconfidence上昇・Criterion昇格が起こり得る。

## Gate 4 レビュー

- [x] 4-1 バックエンド全pytestがCodex環境で成功する。
  - 4-1 未適用の理由:
- [x] 4-2 バックエンドの主要なP0ロジックに回帰テストがある。
  - 4-2 未適用の理由: ±0.15/0、上下clamp、昇格、重複防止、一覧順、404、invalid reaction、低confidence未保存を確認している。
- [ ] 4-3 閾値ちょうどの境界挙動がテストで固定されている。
  - 4-3 未適用の理由: `confidence == 0.3` は保存されること、および反応後 `confidence == 0.6` はCriterionへ昇格することを直接固定するテストがない。
- [ ] 4-4 新設したKotlinのfeedback/criterion経路が自動テストで固定されている。
  - 4-4 未適用の理由: 今回の未コミット差分にKotlinテスト変更がない。wire値、POST path/body、POST結果反映、no-insight文言、confidenceラベル境界、Criterion追加通知、三ボタン配線が直接保証されていない。TASK.mdにはClaudeによる既存Discoveryテスト42件成功が追記されているが、新規契約の回帰テストが差分にない問題は解消しない。
- [x] 4-5 対象差分に空白エラーがない。
  - 4-5 未適用の理由:

## Gate 5 納品

- [x] 5-1 実装概要、対象外、Claudeの検証結果がTASK.mdに記録されている。
  - 5-1 未適用の理由:
- [ ] 5-2 Gate 2〜4のブロッキング指摘が解消され、コミット可能な状態である。
  - 5-2 未適用の理由: Androidの2往復実装、nullable APIの互換方針、新規Kotlin回帰テストが未解決である。

## 実行した確認

- `cd backend && python -m pytest -q`: **139 passed, 6 warnings**、終了コード0（39.43秒）。合格数はClaude報告と一致した。警告数がClaude報告より1件多いのは、Codex環境で `.pytest_cache` を作成できない `PytestCacheWarning` が加わったためで、テスト失敗ではない。
- `git diff --check`: 空白エラーなし。
- `git diff --name-only`、案件15の対象一覧、全変更hunk、既存呼び出し箇所を静的確認した。
- Kotlinテストについて、レビュー依頼時点では「Claude報告の結果は別途確認中」とされたが、確認時点のTASK.mdには `:shared:testDebugUnitTest --tests "com.example.myapplication.shared.discovery.*" --no-daemon` の **BUILD SUCCESSFUL（42件、失敗0）** が追記されていた。本レビューではClaude報告として参照し、Codexによる独立再実行は行っていない。

## ブロッキング指摘

### 1. Androidが1往復用レスポンスを捨て、二重反応の失敗モードを作っている

- `backend/discovery/models.py` の `HypothesisFeedbackResult` 自体は、feedback、更新後仮説、新規Criterionを1回で返す妥当な設計である。
- しかし `shared/src/commonMain/kotlin/com/example/myapplication/shared/discovery/RealDiscoveryRepository.kt` の `sendHypothesisFeedback()` はPOSTレスポンスをdeserializeせず、直後にsummaryをGETする。
- POST成功・GET失敗時、サーバー状態だけが更新されてクライアントは失敗扱いになる。再試行に冪等性キーがないため、confidenceを再度増減させる。
- POST結果を利用して1往復で状態更新するか、再同期を必須とするならAPIを冪等化し、その契約と失敗時挙動を仕様化すること。

### 2. 新設Kotlin経路に回帰テスト差分がない

- バックエンドテストは追加されているが、Kotlin側の変更対象に対応するテストファイルは差分に含まれない。
- 少なくとも `AGREE/UNSURE/DISAGREE` のwire値、feedback POST契約、結果反映、no-insight表示、Criterionラベル境界、Stateの新規追加通知をテストで固定すること。
- 既存42件の成功は回帰がないことの参考にはなるが、新規ロジックの正しさを直接証明しない。AGENTS.mdのTDD必須規約にも適合しない。

### 3. nullable API変更に後方互換方針がない

- `POST /sessions/{session_id}/hypothesis/update` は、低confidence時に従来のオブジェクトではなくJSON `null`をHTTP 201で返す。
- リポジトリ内Androidには同エンドポイントの呼び出しがなく、summary DTOはnullable対応済みなので、確認できる現行クライアントには直接の破損はない。
- ただし既存API利用者が常に `HypothesisResponse` をdeserializeする契約は壊れる。安定した結果wrapper、versioned endpoint、互換期間等から移行方針を決め、契約テストを追加すること。

## 非ブロッキング指摘

- `0.15`、`0.6`、`0.85`の判定がバックエンド、Fake、UI変換へ分散している。バックエンドのP0閾値は名前付き定数で変更しやすいが、Fakeとのドリフトは検知できない。Kotlin共通ポリシー化または契約テストを検討すること。
- UIは送信中の反応ボタンを無効化しない。少なくとも同一操作の多重送信防止を入れることが望ましい。
- `new_criterion` は既存Criterion更新時にも非nullになるため、フィールド名が「新規作成」を厳密には表さない。クライアントが新規追加通知を正確に出すなら `criterion_created` 等の明示情報を返す方が安全である。

## Standards / Spec 集計

- Standards軸: hard violation 1件、high judgement call 1件、medium judgement call 2件。最大は新設Kotlin経路に回帰テスト差分がないこと。
- Spec軸: 重大2件、中程度2件。最大は1往復レスポンス設計をAndroid実装が利用せず、部分成功時に反応を二重適用し得ること。

## 次の行動

- 差し戻し先: **Claude（実装担当）**。
- Androidで `HypothesisFeedbackResult` を利用する状態更新、またはfeedback APIの冪等化を実装し、部分成功時の再送を安全にする。
- 新規Kotlin経路と閾値境界の回帰テストを追加する。
- hypothesis updateのnullable化について互換方針を決め、API契約へ反映する。
- 修正後にバックエンド全pytest、Discovery Kotlinテスト、Kotlin compileを再実行してGate 4再レビューを依頼する。

## Gate 4 再レビュー

- 判定: **CHANGES REQUIRED**
- 再レビュー担当: Codex（規約適合・仕様適合の独立レビューを併用）
- 再レビュー日: 2026-09-04
- 対象: 前回Gate 4判定後の未コミット修正を含む案件15差分

### 前回ブロッキング指摘の確認

- [x] Androidのfeedback更新は1往復になった。
  - `RealDiscoveryRepository.sendHypothesisFeedback()` は `POST /hypotheses/{id}/feedback` のレスポンスを `HypothesisFeedbackResultDto` としてdeserializeし、`HypothesisFeedbackOutcome`を返す。関数内に追加GETはない。
  - `DiscoveryState.sendHypothesisFeedback()` はPOST結果を現在の`DiscoveryData`へローカルマージし、Criterionはid突き合わせでupsertする。
  - `sendHypothesisFeedback_postsOnceAndReturnsOutcomeWithoutExtraGet` は記録されたpathがfeedback POSTの1件だけであることを検証している。
- [x] 新設Kotlin経路に回帰テスト差分が追加された。
  - Repository側にPOST結果、Criterionなし、4xxの3テストがある。
  - State側にCriterion追加・通知、unsure、仮説未ロード、送信中ガード、失敗時通知・フラグ復帰のテストがある。
- [x] nullable API変更の後方互換方針がTASK.mdに記録された。
  - リポジトリ内の唯一の呼び出し元はnullable対応済みで、アプリ未リリースのためversioned endpointを設けない判断が明記されている。

### 解消を確認した追加項目

- `confidence == 0.3`で仮説を保存する境界テストが追加されている。
- 同感後`confidence == 0.6`でCriterionへ昇格する境界テストが追加されている。
- confidence delta、昇格閾値、ラベル閾値はKotlin側の`HypothesisFeedbackPolicy`へ集約され、Real/Fake実装が利用している。
- UIの3ボタンは`isSubmittingFeedback`中に無効化される。
- `git diff --check`は空白エラーなし。

### 残存ブロッキング指摘

#### 多重送信防止にコルーチン起動前の競合窓がある

- `DiscoveryState.sendHypothesisFeedback()` は入口で`isSubmittingFeedback`を確認するが、`true`への更新は`scope.launch`後、さらに`actionMutex.withLock`内で行う。
- 1回目のコルーチンが実行される前に同一dispatcher tickで連続呼び出しされると、すべての呼び出しが`false`を観測して複数コルーチンをenqueueする。`actionMutex`はそれらを破棄せず直列化するため、全POSTが順番に実行され、confidenceが重複加算され得る。
- 追加テスト`sendHypothesisFeedback_whileSubmitting_ignoresDuplicateTaps`は、1回目の呼び出し後に`runCurrent()`を実行してフラグが`true`になったことを確認してから連打している。このため、実装に残るコルーチン起動前の競合窓を検証していない。
- フラグをコルーチン起動前に同期的に設定する、またはmutex内で再チェックして後続処理を破棄するなど、入口から送信開始までを一意に予約すること。回帰テストは`runCurrent()`を挟まず即時に複数回呼び出し、Repository呼び出しが1回だけであることを固定すること。

### テスト内容に関する非ブロッキング指摘

- 1往復テストはpathと結果を検証しているが、HTTP methodとrequest bodyの`agree` wire値までは直接検証していない。
- Stateテストは新規Criterion追加を検証しているが、同じidの既存Criterionを置換して重複させないupsert経路を直接固定していない。
- KotlinとPythonの閾値は言語境界をまたいで別定義のため、手動同期によるドリフト余地は残る。ただし今回のGateを止める指摘とはしない。

### 独立検証結果

- `cd backend && python -m pytest -q`: **142 passed, 6 warnings**、終了コード0（47.38秒）。合格数はClaude報告の142件と一致した。警告が報告より1件多いのは、Codex環境で`.pytest_cache`を作成できない`PytestCacheWarning`が加わったためで、テスト失敗ではない。
- `git diff --check`: 空白エラーなし。
- 未コミット差分、POSTレスポンスのdeserialize経路、Stateのローカルマージ、追加されたKotlinテストのassertionを静的確認した。
- Kotlinテストは今回Codexによる再実行対象には含めず、Claude報告の50件成功を参照した。

### Standards / Spec 集計

- Standards軸: ブロッキング候補1件、非ブロッキング判断事項1件。最大は多重送信防止の競合窓と、それを捕捉しないテスト。
- Spec軸: ブロッキング1件、軽微なテスト不足2件。前回の主要3指摘は解消したが、即時連打時の重複POSTが残る。

### 次の行動

- 差し戻し先: **Claude（実装担当）**。
- `isSubmittingFeedback`の予約をコルーチン起動前から有効にし、即時連続呼び出しでもPOSTが1回になるよう修正する。
- `runCurrent()`前の連続呼び出しを使う回帰テストを追加し、Discovery Kotlinテストを再実行してから再レビューを依頼する。
