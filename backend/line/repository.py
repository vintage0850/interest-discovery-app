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
