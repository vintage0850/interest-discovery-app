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
            continue

        try:
            line_client.push_message(account.line_user_id, reminder.message)
            repo.mark_sent(reminder.id, current_time_fn())
        except LineApiException:
            repo.mark_failed(reminder.id)
        processed += 1

    return ProcessResult(processed=processed)
