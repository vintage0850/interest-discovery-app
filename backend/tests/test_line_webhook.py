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
