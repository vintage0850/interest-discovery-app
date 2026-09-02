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
