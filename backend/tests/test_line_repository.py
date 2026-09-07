from __future__ import annotations

import datetime
from zoneinfo import ZoneInfo

import pytest
from pydantic import ValidationError
from sqlalchemy.pool import StaticPool
from sqlmodel import Session, SQLModel, create_engine, select

from line.models import LineAccount, Reminder, ReminderCreate, ReminderStatus
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

    def test_upsert_different_user_replaces_account_and_maintains_single_row(
        self, repo: LineRepository
    ) -> None:
        repo.upsert_line_account("U123")
        second = repo.upsert_line_account("U456")

        assert second.line_user_id == "U456"
        active = repo.get_active_account()
        assert active is not None
        assert active.line_user_id == "U456"

        with Session(repo._engine) as db:
            accounts = list(db.exec(select(LineAccount)).all())
            assert len(accounts) == 1
            assert accounts[0].line_user_id == "U456"

    def test_upsert_cleans_up_multiple_accounts_if_present(
        self, repo: LineRepository
    ) -> None:
        with Session(repo._engine) as db:
            db.add(LineAccount(line_user_id="U_OLD_1"))
            db.add(LineAccount(line_user_id="U_OLD_2"))
            db.commit()

        updated = repo.upsert_line_account("U_NEW")
        assert updated.line_user_id == "U_NEW"

        with Session(repo._engine) as db:
            accounts = list(db.exec(select(LineAccount)).all())
            assert len(accounts) == 1
            assert accounts[0].line_user_id == "U_NEW"


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

    def test_cancel_sent_reminder_raises_state_transition_error(
        self, repo: LineRepository
    ) -> None:
        created = repo.create_reminder(
            "勉強する", datetime.datetime.now(datetime.timezone.utc)
        )
        repo.try_claim_processing(created.id)
        repo.mark_sent(created.id, datetime.datetime.now(datetime.timezone.utc))

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

    def test_sent_reminder_cannot_be_reclaimed(self, repo: LineRepository) -> None:
        created = repo.create_reminder(
            "勉強する", datetime.datetime.now(datetime.timezone.utc)
        )
        repo.try_claim_processing(created.id)
        repo.mark_sent(created.id, datetime.datetime.now(datetime.timezone.utc))

        assert repo.try_claim_processing(created.id) is False

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

    def test_mark_sent_requires_processing_status(self, repo: LineRepository) -> None:
        created = repo.create_reminder(
            "A", datetime.datetime.now(datetime.timezone.utc)
        )
        now = datetime.datetime.now(datetime.timezone.utc)

        # SCHEDULED状態からは直接SENTにできない
        with pytest.raises(StateTransitionError):
            repo.mark_sent(created.id, now)

        # PROCESSINGに移行後、一度SENTにしたら再度のmark_sentは不可
        repo.try_claim_processing(created.id)
        repo.mark_sent(created.id, now)
        with pytest.raises(StateTransitionError):
            repo.mark_sent(created.id, now)

    def test_mark_failed_requires_processing_status(self, repo: LineRepository) -> None:
        created = repo.create_reminder(
            "B", datetime.datetime.now(datetime.timezone.utc)
        )

        # SCHEDULED状態からは直接FAILEDにできない
        with pytest.raises(StateTransitionError):
            repo.mark_failed(created.id)

        # PROCESSINGに移行後、一度FAILEDにしたら再度のmark_failedは不可
        repo.try_claim_processing(created.id)
        repo.mark_failed(created.id)
        with pytest.raises(StateTransitionError):
            repo.mark_failed(created.id)

    def test_mark_sent_and_mark_failed_nonexistent_raises_value_error(
        self, repo: LineRepository
    ) -> None:
        now = datetime.datetime.now(datetime.timezone.utc)
        with pytest.raises(ValueError):
            repo.mark_sent(99999, now)

        with pytest.raises(ValueError):
            repo.mark_failed(99999)


class TestReminderValidation:
    def test_reminder_scheduled_at_rejects_naive_datetime(
        self, repo: LineRepository
    ) -> None:
        naive = datetime.datetime(2026, 9, 10, 10, 0)
        with pytest.raises(ValueError, match="UTC"):
            repo.create_reminder("勉強する", naive)

    def test_reminder_scheduled_at_rejects_non_utc_offset(
        self, repo: LineRepository
    ) -> None:
        jst = datetime.datetime(2026, 9, 10, 10, 0, tzinfo=ZoneInfo("Asia/Tokyo"))
        with pytest.raises(ValueError, match="UTC"):
            repo.create_reminder("勉強する", jst)

    def test_reminder_create_schema_rejects_naive_string(self) -> None:
        with pytest.raises(ValidationError):
            ReminderCreate(message="勉強する", scheduled_at="2026-09-10T10:00:00")

    def test_reminder_create_schema_rejects_non_utc_offset_string(self) -> None:
        with pytest.raises(ValidationError):
            ReminderCreate(
                message="勉強する", scheduled_at="2026-09-10T10:00:00+09:00"
            )

    def test_reminder_create_schema_accepts_utc_z_suffix(self) -> None:
        create = ReminderCreate(
            message="勉強する", scheduled_at="2026-09-10T10:00:00Z"
        )
        assert create.scheduled_at.tzinfo is not None
        assert create.scheduled_at.utcoffset() == datetime.timedelta(0)

    def test_reminder_create_schema_accepts_utc_zero_offset(self) -> None:
        create = ReminderCreate(
            message="勉強する", scheduled_at="2026-09-10T10:00:00+00:00"
        )
        assert create.scheduled_at.tzinfo is not None
        assert create.scheduled_at.utcoffset() == datetime.timedelta(0)
