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
