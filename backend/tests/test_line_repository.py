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
