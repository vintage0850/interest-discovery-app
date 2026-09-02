# LINEリマインダー連携 — 設計（MVPスコープ）

## 背景

ユーザーからLINE Messaging APIの認証情報（Channel ID/Secret/Access Token）が提供され、
「アプリ内で作成したリマインダーをLINEで受け取れるようにしたい」という依頼があった。
LINEは通知配信チャネルであり、自己理解/興味発見の推論パイプラインには組み込まない。

対象は`MyApplication`の`backend/`（FastAPI、`discovery`と同居）。

## スコープ縮小の方針（ユーザー承認済み）

このbackendには認証・`users`テーブルが存在しない（`discovery`も`student_label`文字列のみで
動く匿名設計）。ユーザー承認により、本番向け仕様（複数ユーザー・正式Account Link OAuth風フロー）
は今回作らず、以下のMVPに縮小する：

- ユーザー管理なし。LINEアカウントは常に1件のみ（`line_account`テーブルに1行、
  followイベントで得た`line_user_id`を保存するだけ）
- 正式Account Linkは使わない。follow時にそのまま連携完了とみなす
- スケジューラはRedis/BullMQ等を使わず、単一プロセス内のポーリング（APScheduler、60秒間隔）

複数ユーザー対応・正式Account Linkは別タスクとして将来起票する。

## モジュール構成

`backend/discovery/`と同じ構造で`backend/line/`を新規作成する。

```
backend/line/
  __init__.py
  models.py       # SQLModelテーブル + リクエスト/レスポンススキーマ
  signature.py    # x-line-signature検証（HMAC-SHA256）
  line_client.py  # LINE Messaging API呼び出し（httpx）
  scheduler.py    # 期限到来リマインダーの処理ロジック（純粋関数、APSchedulerから呼ばれる）
  router.py       # /api/webhooks/line, /reminders エンドポイント
```

DBは`discovery`と同様に自モジュール専用のSQLiteファイル（`line.db`）を使う
（`discovery.db`とは分離し、モジュール間の結合を避ける）。

## データモデル（`backend/line/models.py`）

```python
class ReminderStatus(str, enum.Enum):
    SCHEDULED = "SCHEDULED"
    PROCESSING = "PROCESSING"
    SENT = "SENT"
    FAILED = "FAILED"
    CANCELED = "CANCELED"


class LineAccount(SQLModel, table=True):
    __tablename__ = "line_account"
    id: Optional[int] = SQLField(default=None, primary_key=True)
    line_user_id: str = SQLField(index=True, unique=True)
    linked_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )


class Reminder(SQLModel, table=True):
    __tablename__ = "reminder"
    id: Optional[int] = SQLField(default=None, primary_key=True)
    message: str
    scheduled_at: datetime.datetime  # UTC（TIMEZONE.mdのルールに従い、timezone列は持たない）
    status: str = SQLField(default=ReminderStatus.SCHEDULED.value, sa_type=String(16))
    sent_at: Optional[datetime.datetime] = None
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )
```

リクエスト/レスポンススキーマ：

```python
class ReminderCreate(SQLModel):
    message: str = Field(..., min_length=1, max_length=500)
    scheduled_at: datetime.datetime  # ISO 8601、UTC（Zサフィックス必須）


class ReminderResponse(SQLModel):
    id: int
    message: str
    scheduled_at: datetime.datetime
    status: str
    sent_at: Optional[datetime.datetime]
    created_at: datetime.datetime
```

## 署名検証（`backend/line/signature.py`）

```python
def verify_line_signature(body: bytes, signature: str, channel_secret: str) -> bool:
    digest = hmac.new(channel_secret.encode("utf-8"), body, hashlib.sha256).digest()
    expected = base64.b64encode(digest).decode("utf-8")
    return hmac.compare_digest(expected, signature)
```

**重要:** 署名検証はJSONパース前の生バイト列に対して行う。FastAPIのエンドポイントは
Pydanticモデルではなく`Request`を直接受け取り、`await request.body()`で生バイトを
取得してから検証・パースする。

## LINE APIクライアント（`backend/line/line_client.py`）

```python
class LineApiException(Exception):
    pass


class LineClient:
    def __init__(self, channel_access_token: str, http_client: httpx.Client | None = None):
        self._token = channel_access_token
        self._client = http_client or httpx.Client(base_url="https://api.line.me")

    def push_message(self, line_user_id: str, message: str) -> None:
        response = self._client.post(
            "/v2/bot/message/push",
            headers={"Authorization": f"Bearer {self._token}"},
            json={"to": line_user_id, "messages": [{"type": "text", "text": message}]},
        )
        if response.status_code != 200:
            raise LineApiException(
                f"LINE push failed (HTTP {response.status_code}): {response.text}"
            )
```

テストでは`httpx.MockTransport`を使い、新規依存追加（respx等）は行わない。

## Webhook・リマインダーAPI（`backend/line/router.py`）

```python
router = APIRouter(tags=["line"])

@router.post("/api/webhooks/line")
async def line_webhook(request: Request, repo: ... = Depends(...)) -> dict:
    body = await request.body()
    signature = request.headers.get("x-line-signature", "")
    if not verify_line_signature(body, signature, channel_secret):
        raise HTTPException(status_code=401, detail="Invalid signature")

    payload = json.loads(body)
    for event in payload.get("events", []):
        if event.get("type") == "follow" and event.get("source", {}).get("type") == "user":
            line_user_id = event["source"]["userId"]
            repo.upsert_line_account(line_user_id)
    return {"status": "ok"}


@router.post("/reminders", response_model=ReminderResponse, status_code=201)
def create_reminder(request: ReminderCreate, repo: ... = Depends(...)) -> Reminder: ...

@router.get("/reminders", response_model=list[ReminderResponse])
def list_reminders(repo: ... = Depends(...)) -> list[Reminder]: ...

@router.delete("/reminders/{reminder_id}", response_model=ReminderResponse)
def cancel_reminder(reminder_id: int, repo: ... = Depends(...)) -> Reminder:
    """SCHEDULED状態のみCANCELEDにできる。それ以外は409。"""
```

- `events`が空配列（LINEの検証リクエスト）でも200を返す（ループが0回まわるだけで自然に満たす）
- 署名不正時は401
- `follow`イベント以外（`unfollow`等）は今回は無視する（ログのみ、非目標）

## スケジューラ処理ロジック（`backend/line/scheduler.py`）

APSchedulerのジョブから60秒ごとに呼ばれる**純粋な処理関数**として実装する
（タイマー自体はテストせず、この関数だけを直接呼んでテストする）。

```python
def process_due_reminders(
    engine: Engine,
    line_client: LineClient,
    now: Callable[[], datetime.datetime] = lambda: datetime.datetime.now(datetime.timezone.utc),
) -> ProcessResult:
    """期限到来のSCHEDULEDリマインダーを1件ずつ処理する。"""
    with Session(engine) as session:
        due = session.exec(
            select(Reminder).where(
                Reminder.status == ReminderStatus.SCHEDULED.value,
                Reminder.scheduled_at <= now(),
            )
        ).all()

        processed = 0
        for reminder in due:
            # 二重送信防止: WHERE status='SCHEDULED' 条件付きUPDATEで1件だけ確保する
            result = session.exec(
                update(Reminder)
                .where(Reminder.id == reminder.id, Reminder.status == ReminderStatus.SCHEDULED.value)
                .values(status=ReminderStatus.PROCESSING.value)
            )
            session.commit()
            if result.rowcount == 0:
                continue  # 他プロセス/tickが既に処理済み

            account = session.exec(select(LineAccount)).first()
            if account is None:
                reminder.status = ReminderStatus.FAILED.value
                session.add(reminder)
                session.commit()
                continue

            try:
                line_client.push_message(account.line_user_id, reminder.message)
                reminder.status = ReminderStatus.SENT.value
                reminder.sent_at = now()
            except LineApiException:
                reminder.status = ReminderStatus.FAILED.value
            session.add(reminder)
            session.commit()
            processed += 1

        return ProcessResult(processed=processed)
```

`ProcessResult`は`processed: int`を持つ単純なdataclass（テストで検証しやすくするため）。

## `main.py`への統合

```python
from line.router import router as line_router
from line.scheduler import process_due_reminders
from line.line_client import LineClient
from apscheduler.schedulers.background import BackgroundScheduler

app.include_router(line_router)

_scheduler = BackgroundScheduler()

@app.on_event("startup")
def _start_scheduler() -> None:
    line_client = LineClient(channel_access_token=os.environ.get("LINE_CHANNEL_ACCESS_TOKEN", ""))
    _scheduler.add_job(
        lambda: process_due_reminders(line_engine, line_client),
        "interval", seconds=60, id="process_due_reminders",
    )
    _scheduler.start()

@app.on_event("shutdown")
def _stop_scheduler() -> None:
    _scheduler.shutdown(wait=False)
```

## 依存関係の追加

`backend/requirements.txt`に`apscheduler>=3.10.0`を追加する。

## テスト方針（TDD）

- `backend/tests/test_line_signature.py`: 既知のsecret/bodyに対する期待署名値でHMAC検証をテスト（正しい署名→True、改ざんした署名→False）
- `backend/tests/test_line_webhook.py`: FastAPI `TestClient`で`/api/webhooks/line`をテスト
  - 有効な署名＋followイベント → 200、`line_account`テーブルに1行作成される
  - 無効な署名 → 401
  - `events: []`（検証リクエスト） → 200
- `backend/tests/test_line_reminders_router.py`: `/reminders`のPOST/GET/DELETE
  - 作成→一覧に含まれる
  - SCHEDULED状態のリマインダーをキャンセル→CANCELEDになる
  - SENT状態のリマインダーをキャンセルしようとすると409
- `backend/tests/test_line_scheduler.py`: `process_due_reminders`を直接呼ぶ
  - 期限到来＆LineAccountありのSCHEDULEDリマインダー → `LineClient`（モック）が呼ばれ、SENTになる
  - LineAccountが無い場合 → FAILEDになる
  - `LineClient.push_message`が例外を投げた場合 → FAILEDになる
  - 期限未到来のリマインダーは処理されない
  - 既にPROCESSING/SENT状態のリマインダーは再処理されない
- `backend/tests/test_line_client.py`: `httpx.MockTransport`で`push_message`が正しいURL・ヘッダー・ボディを送ることを検証

## 非目標（今回のスコープ外）

- 複数ユーザー対応・`users`テーブル・正式Account Link OAuth風フロー
- Androidアプリ側のリマインダー作成UI・「LINE連携」設定画面の実装（バックエンドAPIのみ今回作る）
- retry policy（送信失敗時の再試行）— 今回はFAILEDのまま放置し、手動確認とする
- unfollow等、follow以外のWebhookイベント種別への対応

## デプロイ・Webhook検証

self-understanding-mvpと同様にローカル開発のみで、公開URLが無い。`ngrok`で
`backend`（ポート8000）を一時的に公開し、LINE Developers ConsoleのWebhook URLに
登録して検証する。この手順は実装完了後、Claudeが実施する。
