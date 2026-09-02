# LINEリマインダー連携 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `backend/`に`line`モジュールを新設し、LINE Messaging APIのWebhook受信・署名検証・リマインダーCRUD・期限到来時のPush送信までをMVPスコープで実装する。

**Architecture:** `discovery`モジュールと同じ構造（`models.py` + `repository.py` + `router.py`）を`backend/line/`に新設する。DBは`line.db`という専用SQLiteファイル。スケジューラはAPSchedulerによる60秒間隔ポーリングで、処理ロジック自体は`process_due_reminders()`という純粋関数としてテスト可能にする。

**Tech Stack:** FastAPI, SQLModel, httpx（LINE API呼び出し・テストは`httpx.MockTransport`）, APScheduler（新規依存）, pytest。

**Spec:** `docs/superpowers/specs/2026-09-03-line-reminder-integration-design.md`

## Global Constraints

- ユーザー管理・`users`テーブルは作らない。LINEアカウントは常に1件のみ管理する。
- 正式Account Link OAuthフローは作らない。followイベントで得た`line_user_id`をそのまま連携完了として保存する。
- スケジューラはRedis/BullMQ等を使わず、APSchedulerによる単一プロセス内ポーリング（60秒間隔）。
- 署名検証は`x-line-signature`ヘッダーと、JSONパース前の生バイト列に対するHMAC-SHA256で行う。
- `LINE_CHANNEL_ID` / `LINE_CHANNEL_SECRET` / `LINE_CHANNEL_ACCESS_TOKEN` / `LINE_WEBHOOK_PATH`は`backend/.env`から読む（設定済み）。コード・コミット・ドキュメントに値を書き込まない。
- `Reminder.scheduled_at`はUTCのみ保持し、timezone列は持たない（プロジェクト全体のTIMEZONE.mdルールに従う）。
- リマインダーの状態遷移: `SCHEDULED → PROCESSING → (SENT | FAILED)`、`SCHEDULED → CANCELED`。それ以外の遷移は許可しない。

---

## Task 1: データモデルとリポジトリ層

**Files:**
- Create: `backend/line/__init__.py`（空ファイル）
- Create: `backend/line/models.py`
- Create: `backend/line/repository.py`
- Test: `backend/tests/test_line_repository.py`

**Interfaces:**
- Produces: `ReminderStatus`enum、`LineAccount`/`Reminder`（SQLModelテーブル）、`ReminderCreate`/`ReminderResponse`（スキーマ）、`class LineRepository(engine: Engine)`とそのメソッド群（以降のTaskが使う）:
  - `upsert_line_account(line_user_id: str) -> LineAccount`
  - `get_active_account() -> LineAccount | None`
  - `create_reminder(message: str, scheduled_at: datetime.datetime) -> Reminder`
  - `list_reminders() -> list[Reminder]`
  - `get_reminder(reminder_id: int) -> Reminder | None`
  - `cancel_reminder(reminder_id: int) -> Reminder`（`StateTransitionError`はSCHEDULED以外への遷移時）
  - `find_due_reminders(now: datetime.datetime) -> list[Reminder]`
  - `try_claim_processing(reminder_id: int) -> bool`
  - `mark_sent(reminder_id: int, sent_at: datetime.datetime) -> None`
  - `mark_failed(reminder_id: int) -> None`

- [ ] **Step 1: 失敗するテストを書く**

`backend/tests/test_line_repository.py`を新規作成する:

```python
from __future__ import annotations

import datetime

import pytest
from sqlalchemy.pool import StaticPool
from sqlmodel import create_engine, SQLModel

from line.models import ReminderStatus
from line.repository import LineRepository, StateTransitionError


@pytest.fixture
def repo() -> LineRepository:
    engine = create_engine(
        "sqlite:///:memory:?cache=shared",
        echo=False,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    return LineRepository(engine)


class TestLineAccount:
    def test_upsert_creates_new_account(self, repo: LineRepository) -> None:
        account = repo.upsert_line_account("U123")
        assert account.line_user_id == "U123"
        assert repo.get_active_account().line_user_id == "U123"

    def test_upsert_is_idempotent_for_same_user(self, repo: LineRepository) -> None:
        repo.upsert_line_account("U123")
        repo.upsert_line_account("U123")
        assert repo.get_active_account().line_user_id == "U123"

    def test_get_active_account_returns_none_when_empty(self, repo: LineRepository) -> None:
        assert repo.get_active_account() is None


class TestReminderCrud:
    def test_create_and_list(self, repo: LineRepository) -> None:
        scheduled = datetime.datetime(2026, 9, 10, tzinfo=datetime.timezone.utc)
        created = repo.create_reminder("勉強する", scheduled)
        assert created.status == ReminderStatus.SCHEDULED.value

        listed = repo.list_reminders()
        assert len(listed) == 1
        assert listed[0].id == created.id

    def test_cancel_scheduled_reminder(self, repo: LineRepository) -> None:
        created = repo.create_reminder("勉強する", datetime.datetime.now(datetime.timezone.utc))
        canceled = repo.cancel_reminder(created.id)
        assert canceled.status == ReminderStatus.CANCELED.value

    def test_cancel_non_scheduled_reminder_raises(self, repo: LineRepository) -> None:
        created = repo.create_reminder("勉強する", datetime.datetime.now(datetime.timezone.utc))
        repo.try_claim_processing(created.id)
        with pytest.raises(StateTransitionError):
            repo.cancel_reminder(created.id)


class TestDueReminders:
    def test_find_due_reminders_only_returns_past_scheduled(self, repo: LineRepository) -> None:
        now = datetime.datetime.now(datetime.timezone.utc)
        past = repo.create_reminder("過去", now - datetime.timedelta(minutes=1))
        future = repo.create_reminder("未来", now + datetime.timedelta(minutes=10))

        due = repo.find_due_reminders(now)

        due_ids = {r.id for r in due}
        assert past.id in due_ids
        assert future.id not in due_ids

    def test_try_claim_processing_only_succeeds_once(self, repo: LineRepository) -> None:
        created = repo.create_reminder("勉強する", datetime.datetime.now(datetime.timezone.utc))

        first = repo.try_claim_processing(created.id)
        second = repo.try_claim_processing(created.id)

        assert first is True
        assert second is False

    def test_mark_sent_and_mark_failed(self, repo: LineRepository) -> None:
        sent_target = repo.create_reminder("A", datetime.datetime.now(datetime.timezone.utc))
        failed_target = repo.create_reminder("B", datetime.datetime.now(datetime.timezone.utc))
        repo.try_claim_processing(sent_target.id)
        repo.try_claim_processing(failed_target.id)

        sent_at = datetime.datetime.now(datetime.timezone.utc)
        repo.mark_sent(sent_target.id, sent_at)
        repo.mark_failed(failed_target.id)

        assert repo.get_reminder(sent_target.id).status == ReminderStatus.SENT.value
        assert repo.get_reminder(failed_target.id).status == ReminderStatus.FAILED.value
```

- [ ] **Step 2: テストを実行し失敗を確認する**

Run: `cd backend && python -m pytest tests/test_line_repository.py -v`
Expected: `ModuleNotFoundError: No module named 'line'`でFAIL

- [ ] **Step 3: `backend/line/__init__.py`を作成する（空ファイル）**

- [ ] **Step 4: `backend/line/models.py`を実装する**

```python
from __future__ import annotations

import datetime
import enum
from typing import Optional

from sqlmodel import Field as SQLField, SQLModel
from sqlalchemy import String


class ReminderStatus(str, enum.Enum):
    SCHEDULED = "SCHEDULED"
    PROCESSING = "PROCESSING"
    SENT = "SENT"
    FAILED = "FAILED"
    CANCELED = "CANCELED"


class LineAccount(SQLModel, table=True):
    """連携済みのLINEアカウント（MVP: 常に1件のみ）。"""

    __tablename__ = "line_account"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    line_user_id: str = SQLField(index=True, unique=True)
    linked_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )


class Reminder(SQLModel, table=True):
    """LINEで配信するリマインダー。"""

    __tablename__ = "reminder"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    message: str
    scheduled_at: datetime.datetime
    status: str = SQLField(default=ReminderStatus.SCHEDULED.value, sa_type=String(16))
    sent_at: Optional[datetime.datetime] = None
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )


class ReminderCreate(SQLModel):
    message: str = SQLField(min_length=1, max_length=500)
    scheduled_at: datetime.datetime


class ReminderResponse(SQLModel):
    id: int
    message: str
    scheduled_at: datetime.datetime
    status: str
    sent_at: Optional[datetime.datetime]
    created_at: datetime.datetime
```

- [ ] **Step 5: `backend/line/repository.py`を実装する**

```python
from __future__ import annotations

import datetime

from sqlalchemy import update
from sqlalchemy.engine import Engine
from sqlmodel import Session, select

from line.models import LineAccount, Reminder, ReminderStatus


class StateTransitionError(ValueError):
    """リマインダーの状態遷移が許可されていない場合に発生するエラー。"""


class LineRepository:
    """line ドメインの永続化層。"""

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def upsert_line_account(self, line_user_id: str) -> LineAccount:
        with Session(self._engine) as db:
            existing = db.exec(
                select(LineAccount).where(LineAccount.line_user_id == line_user_id)
            ).first()
            if existing is not None:
                return existing
            account = LineAccount(line_user_id=line_user_id)
            db.add(account)
            db.commit()
            db.refresh(account)
            return account

    def get_active_account(self) -> LineAccount | None:
        with Session(self._engine) as db:
            return db.exec(select(LineAccount)).first()

    def create_reminder(self, message: str, scheduled_at: datetime.datetime) -> Reminder:
        reminder = Reminder(message=message, scheduled_at=scheduled_at)
        with Session(self._engine) as db:
            db.add(reminder)
            db.commit()
            db.refresh(reminder)
            return reminder

    def list_reminders(self) -> list[Reminder]:
        with Session(self._engine) as db:
            return list(db.exec(select(Reminder)).all())

    def get_reminder(self, reminder_id: int) -> Reminder | None:
        with Session(self._engine) as db:
            return db.get(Reminder, reminder_id)

    def cancel_reminder(self, reminder_id: int) -> Reminder:
        with Session(self._engine) as db:
            reminder = db.get(Reminder, reminder_id)
            if reminder is None:
                raise ValueError(f"Reminder not found: {reminder_id}")
            if reminder.status != ReminderStatus.SCHEDULED.value:
                raise StateTransitionError(
                    f"Cannot cancel reminder in status {reminder.status}"
                )
            reminder.status = ReminderStatus.CANCELED.value
            db.add(reminder)
            db.commit()
            db.refresh(reminder)
            return reminder

    def find_due_reminders(self, now: datetime.datetime) -> list[Reminder]:
        with Session(self._engine) as db:
            return list(
                db.exec(
                    select(Reminder).where(
                        Reminder.status == ReminderStatus.SCHEDULED.value,
                        Reminder.scheduled_at <= now,
                    )
                ).all()
            )

    def try_claim_processing(self, reminder_id: int) -> bool:
        with Session(self._engine) as db:
            result = db.exec(
                update(Reminder)
                .where(
                    Reminder.id == reminder_id,
                    Reminder.status == ReminderStatus.SCHEDULED.value,
                )
                .values(status=ReminderStatus.PROCESSING.value)
            )
            db.commit()
            return result.rowcount > 0

    def mark_sent(self, reminder_id: int, sent_at: datetime.datetime) -> None:
        with Session(self._engine) as db:
            reminder = db.get(Reminder, reminder_id)
            reminder.status = ReminderStatus.SENT.value
            reminder.sent_at = sent_at
            db.add(reminder)
            db.commit()

    def mark_failed(self, reminder_id: int) -> None:
        with Session(self._engine) as db:
            reminder = db.get(Reminder, reminder_id)
            reminder.status = ReminderStatus.FAILED.value
            db.add(reminder)
            db.commit()
```

- [ ] **Step 6: テストを実行してグリーンになることを確認する**

Run: `cd backend && python -m pytest tests/test_line_repository.py -v`
Expected: 全テスト合格（9 tests）

- [ ] **Step 7: コミット**

```bash
git add backend/line/__init__.py backend/line/models.py backend/line/repository.py backend/tests/test_line_repository.py
git commit -m "feat(line): add LineAccount/Reminder models and repository layer"
```

---

## Task 2: Webhook署名検証

**Files:**
- Create: `backend/line/signature.py`
- Test: `backend/tests/test_line_signature.py`

**Interfaces:**
- Produces: `def verify_line_signature(body: bytes, signature: str, channel_secret: str) -> bool`

- [ ] **Step 1: 失敗するテストを書く**

`backend/tests/test_line_signature.py`:

```python
from __future__ import annotations

import base64
import hashlib
import hmac

from line.signature import verify_line_signature

SECRET = "test-channel-secret"
BODY = b'{"events":[]}'


def _sign(body: bytes, secret: str) -> str:
    digest = hmac.new(secret.encode("utf-8"), body, hashlib.sha256).digest()
    return base64.b64encode(digest).decode("utf-8")


def test_valid_signature_returns_true() -> None:
    signature = _sign(BODY, SECRET)
    assert verify_line_signature(BODY, signature, SECRET) is True


def test_invalid_signature_returns_false() -> None:
    assert verify_line_signature(BODY, "invalid-signature", SECRET) is False


def test_signature_for_different_body_returns_false() -> None:
    signature = _sign(BODY, SECRET)
    tampered_body = b'{"events":[{"type":"follow"}]}'
    assert verify_line_signature(tampered_body, signature, SECRET) is False


def test_empty_signature_returns_false() -> None:
    assert verify_line_signature(BODY, "", SECRET) is False
```

- [ ] **Step 2: テストを実行し失敗を確認する**

Run: `cd backend && python -m pytest tests/test_line_signature.py -v`
Expected: `ModuleNotFoundError: No module named 'line.signature'`でFAIL

- [ ] **Step 3: `backend/line/signature.py`を実装する**

```python
from __future__ import annotations

import base64
import hashlib
import hmac


def verify_line_signature(body: bytes, signature: str, channel_secret: str) -> bool:
    """x-line-signature ヘッダーを検証する。生バイト列に対してのみ計算すること。"""
    if not signature:
        return False
    digest = hmac.new(channel_secret.encode("utf-8"), body, hashlib.sha256).digest()
    expected = base64.b64encode(digest).decode("utf-8")
    return hmac.compare_digest(expected, signature)
```

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `cd backend && python -m pytest tests/test_line_signature.py -v`
Expected: 全テスト合格（4 tests）

- [ ] **Step 5: コミット**

```bash
git add backend/line/signature.py backend/tests/test_line_signature.py
git commit -m "feat(line): add webhook signature verification"
```

---

## Task 3: LINE Messaging APIクライアント

**Files:**
- Create: `backend/line/line_client.py`
- Test: `backend/tests/test_line_client.py`

**Interfaces:**
- Produces: `class LineApiException(Exception)`、`class LineClient(channel_access_token: str, http_client: httpx.Client | None = None)`とその`push_message(line_user_id: str, message: str) -> None`

- [ ] **Step 1: 失敗するテストを書く**

`backend/tests/test_line_client.py`:

```python
from __future__ import annotations

import json

import httpx
import pytest

from line.line_client import LineApiException, LineClient


def test_push_message_sends_correct_request() -> None:
    captured: dict = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("authorization")
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={})

    client = LineClient(
        channel_access_token="test-token",
        http_client=httpx.Client(
            base_url="https://api.line.me", transport=httpx.MockTransport(handler)
        ),
    )

    client.push_message("U123", "リマインダー：勉強する")

    assert captured["url"] == "https://api.line.me/v2/bot/message/push"
    assert captured["auth"] == "Bearer test-token"
    assert captured["body"] == {
        "to": "U123",
        "messages": [{"type": "text", "text": "リマインダー：勉強する"}],
    }


def test_push_message_raises_on_non_200() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(400, json={"message": "invalid"})

    client = LineClient(
        channel_access_token="test-token",
        http_client=httpx.Client(
            base_url="https://api.line.me", transport=httpx.MockTransport(handler)
        ),
    )

    with pytest.raises(LineApiException):
        client.push_message("U123", "テスト")
```

- [ ] **Step 2: テストを実行し失敗を確認する**

Run: `cd backend && python -m pytest tests/test_line_client.py -v`
Expected: `ModuleNotFoundError: No module named 'line.line_client'`でFAIL

- [ ] **Step 3: `backend/line/line_client.py`を実装する**

```python
from __future__ import annotations

import httpx


class LineApiException(Exception):
    """LINE Messaging API呼び出し時のエラー。"""


class LineClient:
    """LINE Messaging APIをhttpxで呼び出すクライアント。"""

    def __init__(self, channel_access_token: str, http_client: httpx.Client | None = None) -> None:
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

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `cd backend && python -m pytest tests/test_line_client.py -v`
Expected: 全テスト合格（2 tests）

- [ ] **Step 5: コミット**

```bash
git add backend/line/line_client.py backend/tests/test_line_client.py
git commit -m "feat(line): add LINE Messaging API push client"
```

---

## Task 4: スケジューラ処理ロジック

**Files:**
- Create: `backend/line/scheduler.py`
- Test: `backend/tests/test_line_scheduler.py`

**Interfaces:**
- Consumes: Task 1の`LineRepository`、Task 3の`LineClient`/`LineApiException`。
- Produces: `@dataclass class ProcessResult: processed: int`、`def process_due_reminders(repo: LineRepository, line_client: LineClient, now: Callable[[], datetime.datetime] | None = None) -> ProcessResult`

- [ ] **Step 1: 失敗するテストを書く**

`backend/tests/test_line_scheduler.py`:

```python
from __future__ import annotations

import datetime
from unittest.mock import MagicMock

import pytest
from sqlalchemy.pool import StaticPool
from sqlmodel import create_engine, SQLModel

from line.line_client import LineApiException, LineClient
from line.models import ReminderStatus
from line.repository import LineRepository
from line.scheduler import process_due_reminders


@pytest.fixture
def repo() -> LineRepository:
    engine = create_engine(
        "sqlite:///:memory:?cache=shared",
        echo=False,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    return LineRepository(engine)


def test_due_reminder_with_account_is_sent(repo: LineRepository) -> None:
    repo.upsert_line_account("U123")
    now = datetime.datetime.now(datetime.timezone.utc)
    reminder = repo.create_reminder("勉強する", now - datetime.timedelta(minutes=1))

    line_client = MagicMock(spec=LineClient)
    result = process_due_reminders(repo, line_client, now=lambda: now)

    assert result.processed == 1
    line_client.push_message.assert_called_once_with("U123", "勉強する")
    assert repo.get_reminder(reminder.id).status == ReminderStatus.SENT.value


def test_due_reminder_without_account_is_failed(repo: LineRepository) -> None:
    now = datetime.datetime.now(datetime.timezone.utc)
    reminder = repo.create_reminder("勉強する", now - datetime.timedelta(minutes=1))

    line_client = MagicMock(spec=LineClient)
    result = process_due_reminders(repo, line_client, now=lambda: now)

    assert result.processed == 0
    line_client.push_message.assert_not_called()
    assert repo.get_reminder(reminder.id).status == ReminderStatus.FAILED.value


def test_push_failure_marks_reminder_failed(repo: LineRepository) -> None:
    repo.upsert_line_account("U123")
    now = datetime.datetime.now(datetime.timezone.utc)
    reminder = repo.create_reminder("勉強する", now - datetime.timedelta(minutes=1))

    line_client = MagicMock(spec=LineClient)
    line_client.push_message.side_effect = LineApiException("boom")

    result = process_due_reminders(repo, line_client, now=lambda: now)

    assert result.processed == 1
    assert repo.get_reminder(reminder.id).status == ReminderStatus.FAILED.value


def test_future_reminder_is_not_processed(repo: LineRepository) -> None:
    repo.upsert_line_account("U123")
    now = datetime.datetime.now(datetime.timezone.utc)
    reminder = repo.create_reminder("勉強する", now + datetime.timedelta(minutes=10))

    line_client = MagicMock(spec=LineClient)
    result = process_due_reminders(repo, line_client, now=lambda: now)

    assert result.processed == 0
    line_client.push_message.assert_not_called()
    assert repo.get_reminder(reminder.id).status == ReminderStatus.SCHEDULED.value


def test_already_processing_reminder_is_not_reprocessed(repo: LineRepository) -> None:
    repo.upsert_line_account("U123")
    now = datetime.datetime.now(datetime.timezone.utc)
    reminder = repo.create_reminder("勉強する", now - datetime.timedelta(minutes=1))
    repo.try_claim_processing(reminder.id)

    line_client = MagicMock(spec=LineClient)
    result = process_due_reminders(repo, line_client, now=lambda: now)

    assert result.processed == 0
    line_client.push_message.assert_not_called()
```

- [ ] **Step 2: テストを実行し失敗を確認する**

Run: `cd backend && python -m pytest tests/test_line_scheduler.py -v`
Expected: `ModuleNotFoundError: No module named 'line.scheduler'`でFAIL

- [ ] **Step 3: `backend/line/scheduler.py`を実装する**

```python
from __future__ import annotations

import datetime
from dataclasses import dataclass
from typing import Callable

from line.line_client import LineApiException, LineClient
from line.repository import LineRepository


@dataclass
class ProcessResult:
    processed: int


def process_due_reminders(
    repo: LineRepository,
    line_client: LineClient,
    now: Callable[[], datetime.datetime] | None = None,
) -> ProcessResult:
    """期限到来のSCHEDULEDリマインダーを1件ずつ処理する。"""
    current_time_fn = now or (lambda: datetime.datetime.now(datetime.timezone.utc))
    current_time = current_time_fn()

    due = repo.find_due_reminders(current_time)
    processed = 0

    for reminder in due:
        if not repo.try_claim_processing(reminder.id):
            continue

        account = repo.get_active_account()
        if account is None:
            repo.mark_failed(reminder.id)
            processed += 1
            continue

        try:
            line_client.push_message(account.line_user_id, reminder.message)
            repo.mark_sent(reminder.id, current_time_fn())
        except LineApiException:
            repo.mark_failed(reminder.id)
        processed += 1

    return ProcessResult(processed=processed)
```

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `cd backend && python -m pytest tests/test_line_scheduler.py -v`
Expected: 全テスト合格（5 tests）

- [ ] **Step 5: コミット**

```bash
git add backend/line/scheduler.py backend/tests/test_line_scheduler.py
git commit -m "feat(line): add due-reminder processing scheduler logic"
```

---

## Task 5: Webhook・リマインダーAPIルーター

**Files:**
- Create: `backend/line/router.py`
- Test: `backend/tests/test_line_webhook.py`
- Test: `backend/tests/test_line_reminders_router.py`

**Interfaces:**
- Consumes: Task 1〜4の`LineRepository`/`verify_line_signature`/`ReminderCreate`/`ReminderResponse`。
- Produces: `router = APIRouter(tags=["line"])`（`POST /api/webhooks/line`, `POST /reminders`, `GET /reminders`, `DELETE /reminders/{reminder_id}`）、`get_line_repository()`（FastAPI Depends、Task 6でmain.pyから上書きされる）、`get_channel_secret()`（Depends）。

- [ ] **Step 1: 失敗するテストを書く**

`backend/tests/test_line_webhook.py`:

```python
from __future__ import annotations

import base64
import hashlib
import hmac
import json

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.pool import StaticPool
from sqlmodel import create_engine, SQLModel

from line.repository import LineRepository
from line.router import get_channel_secret, get_line_repository
from main import app

SECRET = "test-channel-secret"


def _sign(body: bytes) -> str:
    digest = hmac.new(SECRET.encode("utf-8"), body, hashlib.sha256).digest()
    return base64.b64encode(digest).decode("utf-8")


@pytest.fixture
def test_client() -> TestClient:
    engine = create_engine(
        "sqlite:///:memory:?cache=shared",
        echo=False,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    repo = LineRepository(engine)

    app.dependency_overrides[get_line_repository] = lambda: repo
    app.dependency_overrides[get_channel_secret] = lambda: SECRET

    with TestClient(app) as client:
        yield client

    app.dependency_overrides.clear()


def test_follow_event_persists_line_user_id(test_client: TestClient) -> None:
    body = json.dumps(
        {
            "events": [
                {"type": "follow", "source": {"type": "user", "userId": "U123"}}
            ]
        }
    ).encode("utf-8")
    signature = _sign(body)

    response = test_client.post(
        "/api/webhooks/line",
        content=body,
        headers={"x-line-signature": signature, "Content-Type": "application/json"},
    )

    assert response.status_code == 200


def test_invalid_signature_is_rejected(test_client: TestClient) -> None:
    body = json.dumps({"events": []}).encode("utf-8")

    response = test_client.post(
        "/api/webhooks/line",
        content=body,
        headers={"x-line-signature": "invalid", "Content-Type": "application/json"},
    )

    assert response.status_code == 401


def test_empty_events_verification_request_returns_200(test_client: TestClient) -> None:
    body = json.dumps({"events": []}).encode("utf-8")
    signature = _sign(body)

    response = test_client.post(
        "/api/webhooks/line",
        content=body,
        headers={"x-line-signature": signature, "Content-Type": "application/json"},
    )

    assert response.status_code == 200
```

`backend/tests/test_line_reminders_router.py`:

```python
from __future__ import annotations

import datetime

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.pool import StaticPool
from sqlmodel import create_engine, SQLModel

from line.repository import LineRepository
from line.router import get_channel_secret, get_line_repository
from main import app


@pytest.fixture
def test_client() -> TestClient:
    engine = create_engine(
        "sqlite:///:memory:?cache=shared",
        echo=False,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    repo = LineRepository(engine)

    app.dependency_overrides[get_line_repository] = lambda: repo
    app.dependency_overrides[get_channel_secret] = lambda: "test-secret"

    with TestClient(app) as client:
        yield client

    app.dependency_overrides.clear()


def test_create_and_list_reminder(test_client: TestClient) -> None:
    scheduled_at = (
        datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)
    ).isoformat()

    create_response = test_client.post(
        "/reminders", json={"message": "勉強する", "scheduled_at": scheduled_at}
    )
    assert create_response.status_code == 201
    reminder_id = create_response.json()["id"]

    list_response = test_client.get("/reminders")
    assert list_response.status_code == 200
    assert any(r["id"] == reminder_id for r in list_response.json())


def test_cancel_scheduled_reminder(test_client: TestClient) -> None:
    scheduled_at = (
        datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)
    ).isoformat()
    created = test_client.post(
        "/reminders", json={"message": "勉強する", "scheduled_at": scheduled_at}
    ).json()

    response = test_client.delete(f"/reminders/{created['id']}")

    assert response.status_code == 200
    assert response.json()["status"] == "CANCELED"


def test_cancel_already_canceled_reminder_returns_409(test_client: TestClient) -> None:
    scheduled_at = (
        datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)
    ).isoformat()
    created = test_client.post(
        "/reminders", json={"message": "勉強する", "scheduled_at": scheduled_at}
    ).json()
    test_client.delete(f"/reminders/{created['id']}")

    response = test_client.delete(f"/reminders/{created['id']}")

    assert response.status_code == 409
```

- [ ] **Step 2: テストを実行し失敗を確認する**

Run: `cd backend && python -m pytest tests/test_line_webhook.py tests/test_line_reminders_router.py -v`
Expected: `ModuleNotFoundError: No module named 'line.router'`でFAIL

- [ ] **Step 3: `backend/line/router.py`を実装する**

```python
from __future__ import annotations

import json
import os
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status

from line.models import ReminderCreate, ReminderResponse
from line.repository import LineRepository, StateTransitionError
from line.signature import verify_line_signature

_ENGINE = None  # main.py が起動時に create_line_engine() の結果を差し込む
_repository: LineRepository | None = None


def get_line_repository() -> LineRepository:
    if _repository is None:
        raise RuntimeError("LineRepository is not initialized")
    return _repository


def get_channel_secret() -> str:
    return os.environ.get("LINE_CHANNEL_SECRET", "")


router = APIRouter(tags=["line"])


@router.post("/api/webhooks/line")
async def line_webhook(
    request: Request,
    repo: Annotated[LineRepository, Depends(get_line_repository)],
    channel_secret: Annotated[str, Depends(get_channel_secret)],
) -> dict:
    body = await request.body()
    signature = request.headers.get("x-line-signature", "")

    if not verify_line_signature(body, signature, channel_secret):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid signature")

    payload = json.loads(body)
    for event in payload.get("events", []):
        if event.get("type") == "follow" and event.get("source", {}).get("type") == "user":
            line_user_id = event["source"]["userId"]
            repo.upsert_line_account(line_user_id)

    return {"status": "ok"}


@router.post("/reminders", response_model=ReminderResponse, status_code=status.HTTP_201_CREATED)
def create_reminder(
    request: ReminderCreate,
    repo: Annotated[LineRepository, Depends(get_line_repository)],
) -> ReminderResponse:
    return repo.create_reminder(request.message, request.scheduled_at)


@router.get("/reminders", response_model=list[ReminderResponse])
def list_reminders(
    repo: Annotated[LineRepository, Depends(get_line_repository)],
) -> list[ReminderResponse]:
    return repo.list_reminders()


@router.delete("/reminders/{reminder_id}", response_model=ReminderResponse)
def cancel_reminder(
    reminder_id: int,
    repo: Annotated[LineRepository, Depends(get_line_repository)],
) -> ReminderResponse:
    try:
        return repo.cancel_reminder(reminder_id)
    except StateTransitionError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
```

**注:** モジュールレベルの`_repository`は、Task 6で`main.py`が起動時に`line.router.set_repository(repo)`のような形で初期化する（Task 6で追記する）。このTaskの時点ではテストが`app.dependency_overrides`で`get_line_repository`を上書きするため、`_repository`が未初期化でもテストは通る。

- [ ] **Step 4: テストを実行してグリーンになることを確認する**

Run: `cd backend && python -m pytest tests/test_line_webhook.py tests/test_line_reminders_router.py -v`
Expected: 全テスト合格（6 tests）

- [ ] **Step 5: コミット**

```bash
git add backend/line/router.py backend/tests/test_line_webhook.py backend/tests/test_line_reminders_router.py
git commit -m "feat(line): add webhook and reminder CRUD endpoints"
```

---

## Task 6: main.pyへの統合とスケジューラ起動

**Files:**
- Modify: `backend/line/router.py`
- Modify: `backend/main.py`
- Modify: `backend/requirements.txt`

**Interfaces:**
- Consumes: Task 1〜5の全モジュール。
- Produces: `def set_repository(repo: LineRepository) -> None`（`line/router.py`に追加）、`main.py`起動時に`line_router`がマウントされ、APSchedulerが60秒間隔で`process_due_reminders`を呼ぶ。

- [ ] **Step 1: `backend/requirements.txt`に依存を追加する**

末尾に1行追加する:

```
apscheduler>=3.10.0
```

- [ ] **Step 2: `backend/line/router.py`に初期化用の関数を追加する**

ファイル冒頭の`_repository: LineRepository | None = None`の直後に追加する:

```python
def set_repository(repo: LineRepository) -> None:
    global _repository
    _repository = repo
```

- [ ] **Step 3: `backend/main.py`を変更する**

変更前:

```python
from __future__ import annotations

import os

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from discovery.router import router as discovery_router
from gemini_client import GeminiClient
from models import AnalyzeRequest, AnalyzeResponse

load_dotenv()

app = FastAPI(title="Reverse FAQ Backend")
app.include_router(discovery_router)
```

変更後:

```python
from __future__ import annotations

import os

from apscheduler.schedulers.background import BackgroundScheduler
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlmodel import SQLModel, create_engine

from discovery.router import router as discovery_router
from gemini_client import GeminiClient
from line.line_client import LineClient
from line.repository import LineRepository
from line.router import router as line_router
from line.router import set_repository as set_line_repository
from line.scheduler import process_due_reminders
from models import AnalyzeRequest, AnalyzeResponse

load_dotenv()

app = FastAPI(title="Reverse FAQ Backend")
app.include_router(discovery_router)
app.include_router(line_router)

_line_engine = create_engine(
    "sqlite:///line.db",
    connect_args={"check_same_thread": False},
    echo=False,
)
SQLModel.metadata.create_all(_line_engine)
set_line_repository(LineRepository(_line_engine))

_scheduler = BackgroundScheduler()


@app.on_event("startup")
def _start_line_scheduler() -> None:
    line_client = LineClient(channel_access_token=os.environ.get("LINE_CHANNEL_ACCESS_TOKEN", ""))
    line_repo = LineRepository(_line_engine)
    _scheduler.add_job(
        lambda: process_due_reminders(line_repo, line_client),
        "interval",
        seconds=60,
        id="process_due_reminders",
    )
    _scheduler.start()


@app.on_event("shutdown")
def _stop_line_scheduler() -> None:
    _scheduler.shutdown(wait=False)
```

`app.add_middleware(CORSMiddleware, ...)`以降の既存コードは変更しない。

- [ ] **Step 4: 依存関係をインストールする**

Run: `cd backend && pip install -r requirements.txt`
Expected: `apscheduler`が正常にインストールされる

- [ ] **Step 5: 全テストを実行してグリーンになることを確認する**

Run: `cd backend && python -m pytest -v`
Expected: BUILD SUCCESSFUL相当（既存のdiscovery/reversefaqテストも含め全件合格。新規追加分は 9+4+2+5+6 = 26 tests）

- [ ] **Step 6: サーバーを起動して手動疎通確認する**

Run: `cd backend && uvicorn main:app --host 0.0.0.0 --port 8000`（別ターミナル）

別ターミナルで:

```bash
curl -X POST http://localhost:8000/reminders \
  -H "Content-Type: application/json" \
  -d '{"message": "動作確認", "scheduled_at": "2030-01-01T00:00:00Z"}'
curl http://localhost:8000/reminders
```

Expected: 1件目は201でリマインダーが返り、2件目のGETで一覧にそのリマインダーが含まれる。

- [ ] **Step 7: コミット**

```bash
git add backend/requirements.txt backend/line/router.py backend/main.py
git commit -m "feat(line): wire line module into main.py and start the reminder scheduler"
```

---

## Self-Review Notes

- **spec coverage:** 設計書の署名検証・データモデル・LINEクライアント・スケジューラ・Webhook/CRUDエンドポイント・main.py統合は全てTask 1〜6でカバー。非目標（users テーブル・正式Account Link・Androidアプリ側UI・retry policy・follow以外のイベント種別）は計画に含めていない。
- **Webhook公開・LINE Developers Console検証:** ngrokでのトンネル作成とLINE Developers ConsoleへのWebhook URL登録・followイベントの実地確認・Push通知の実地確認は、コード変更を伴わないためこの計画のTaskには含めない。Task 6完了後にClaudeが手動で実施する。
- **型整合性:** `LineRepository`のメソッド名・戻り値型は、Task 1で定義したものをTask 4・5がそのまま使用しており一致している。
