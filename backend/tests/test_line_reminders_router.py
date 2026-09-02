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
