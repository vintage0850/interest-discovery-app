# Reverse FAQ Backend

賃貸契約書と本人条件を受け取り、Gemini API で「契約前に確認すべき質問」を生成する FastAPI バックエンド。

## セットアップ

```bash
cd backend
pip install -r requirements.txt
```

`.env.example` を `.env` にコピーし、`GEMINI_API_KEY` に Google AI Studio の API キーを設定してください。

```bash
cp .env.example .env
# .env を編集して GEMINI_API_KEY=... を記入
```

## 起動

```bash
cd backend
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

## エンドポイント

- `GET /health` — ヘルスチェック
- `POST /cases/analyze` — 契約書テキストと本人条件から質問を生成

### 例

```bash
curl -X POST http://localhost:8000/cases/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "case_id": 1,
    "document_text": "退去時には所定のクリーニング費用がかかるものとする。更新料は2年ごとに1ヶ月分の賃料とする。",
    "user_context": {
      "student": true,
      "first_time_renting": true,
      "planned_years": 2
    }
  }'
```

## 注意

- `.env` は `.gitignore` に含まれています。API キーを絶対にコミットしないでください。
- Android エミュレータからは `http://10.0.2.2:8000`、実機からは開発機の LAN 内 IP（同一 Wi-Fi 内）でアクセスしてください。
