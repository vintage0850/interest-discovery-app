from __future__ import annotations

import datetime
import enum
from typing import Any, Optional

from pydantic import field_validator
from sqlalchemy import String
from sqlalchemy.orm import validates
from sqlmodel import Field as SQLField, SQLModel


def _validate_utc_datetime(value: Any) -> datetime.datetime:
    if isinstance(value, str):
        try:
            dt = datetime.datetime.fromisoformat(value)
        except ValueError as e:
            raise ValueError(f"Invalid ISO datetime format: {value}") from e
    elif isinstance(value, datetime.datetime):
        dt = value
    else:
        raise ValueError(f"Invalid type for scheduled_at: {type(value).__name__}")

    if dt.tzinfo is None or dt.utcoffset() is None:
        raise ValueError("scheduled_at must include timezone information (UTC with 'Z' suffix)")
    if dt.utcoffset() != datetime.timedelta(0):
        raise ValueError(f"scheduled_at must be in UTC timezone (got offset {dt.utcoffset()})")
    return dt


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

    @validates("scheduled_at")
    def _validate_scheduled_at_sqla(self, key: str, value: Any) -> datetime.datetime:
        return _validate_utc_datetime(value)

    @field_validator("scheduled_at", mode="before")
    @classmethod
    def _validate_scheduled_at_pydantic(cls, value: Any) -> datetime.datetime:
        return _validate_utc_datetime(value)


class ReminderCreate(SQLModel):
    message: str = SQLField(min_length=1, max_length=500)
    scheduled_at: datetime.datetime

    @field_validator("scheduled_at", mode="before")
    @classmethod
    def _validate_scheduled_at(cls, value: Any) -> datetime.datetime:
        return _validate_utc_datetime(value)


class ReminderResponse(SQLModel):
    id: int
    message: str
    scheduled_at: datetime.datetime
    status: str
    sent_at: Optional[datetime.datetime]
    created_at: datetime.datetime
