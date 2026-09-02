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


def set_repository(repo: LineRepository) -> None:
    global _repository
    _repository = repo


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
