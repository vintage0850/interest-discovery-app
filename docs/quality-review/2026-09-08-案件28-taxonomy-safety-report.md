# 案件28 設計仕様書との差分是正（Action Taxonomy / AI Safety）完了報告

## 実施内容

### 28-A. AI Safety 強化

- `backend/discovery/gemini_prompts.py` の3つの system instruction に、センシティブ属性に関する禁止事項を追加した。
  - 対象: `_EXPERIMENT_SYSTEM_INSTRUCTION` / `_HYPOTHESIS_SYSTEM_INSTRUCTION` / `_WEEKLY_NARRATIVE_SYSTEM_INSTRUCTION`
  - 追加文言:
    ```
    - 精神疾患、発達障害、IQ、性的指向、政治思想、宗教、医療状態について、推論・言及・示唆をしない
    ```
  - `_EXPERIMENT_SYSTEM_INSTRUCTION` には「絶対にやらないこと」セクションがなかったため、新規に追加した。
- `backend/tests/test_discovery_gemini_prompts.py` に、各 instruction が上記文言（または「精神疾患」「医療状態」などのキーワード）を含むことを検証するテストを追加した。

### 28-B. Action Taxonomy 追加

1. **モデル拡張**
   - `backend/discovery/models.py` に `BehaviorCategory` 列挙型を追加。
   - 許可値: `EXPLORE`, `COMPARE`, `ANALYZE`, `CREATE`, `IMPROVE`, `ORGANIZE`, `PRACTICE`, `COMMUNICATE`, `DECIDE`, `REFLECT`
   - `Evidence` モデルに `behavior_categories: dict[str, float] | None`（JSON型）を追加。
   - SQLAlchemy `@validates` で分類名の正当性・強度（0.0〜1.0）を検証。
   - `EvidenceResponse` も同フィールドを返すように更新。

2. **Gemini 分類クライアント追加**
   - `DiscoveryGeminiClient` に `classify_behavior_categories(evidences, experiments, results)` を追加。
   - `update_hypothesis` とは別の Gemini 呼び出しとし、新規 system instruction (`_BEHAVIOR_CATEGORY_SYSTEM_INSTRUCTION`) で 10 分類のみを許可する JSON 出力を指示。
   - 戻り値は `{evidence_id: {category: score}}` のマッピング。
   - 不正な分類名・強度範囲外は Pydantic `field_validator` で棄却（`_validate_domain` と同じパターン）。

3. **ルーター統合**
   - `backend/discovery/router.py` の `update_hypothesis` エンドポイントで、`repo.build_evidence()` 後に `classify_behavior_categories()` を呼び出し、各 `Evidence` の `behavior_categories` を更新・保存する処理を追加。
   - Gemini 呼び出し失敗時は既存パターンと同様に HTTP 503 を返す。
   - 分類結果が `dict` 形式でない場合は安全のため保存をスキップするガードを入れ、既存テストのモック互換性も維持。

4. **テスト追加**
   - 新規ファイル `backend/tests/test_discovery_behavior_categories.py` を作成。
   - カバー項目:
     - 10 分類 enum の存在確認
     - `Evidence` モデルの正常系 / 不正分類名 / 強度範囲外 / None 許容
     - `classify_behavior_categories` の正常系・不正分類棄却・範囲外棄却・ malformed JSON 棄却・SDK エラーラップ・プロンプト内容
     - ルーター統合: hypothesis 更新時に分類結果が保存されること
     - ルーター統合: 分類失敗時に 503 が返り、保存されないこと

## テスト結果

```
$ python -m pytest -v
====================== 300 passed, 5 warnings in 50.95s =======================
```

- 全 300 テストがパス。
- 警告は FastAPI `on_event` 非推奨および `starlette.testclient` 関連の既存のものであり、本変更とは無関係。

## 変更ファイル

- `backend/discovery/gemini_prompts.py`
- `backend/discovery/models.py`
- `backend/discovery/repository.py`
- `backend/discovery/router.py`
- `backend/tests/test_discovery_gemini_prompts.py`
- `backend/tests/test_discovery_behavior_categories.py`
- `docs/quality-review/2026-09-08-案件28-taxonomy-safety-report.md`

## コミット一覧

- `a3532ee` feat(discovery): 案件28 Action Taxonomy追加とAI Safety強化
  - 6 files changed, 568 insertions(+), 2 deletions(-)
- docs(quality-review): 案件28 Action Taxonomy / AI Safety 完了報告を追加（本ファイル）
  - 1 file changed, 75 insertions(+)

## 備考

- フロントエンド/UI 表示は今回のスコープ外とし、バックエンド API のみ実装済み。
- `TASK.md` は編集していない。
