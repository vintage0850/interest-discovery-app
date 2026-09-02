from __future__ import annotations

import os

from apscheduler.schedulers.background import BackgroundScheduler
from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlmodel import SQLModel, create_engine

from discovery.router import router as discovery_router
from gemini_client import GeminiClient
from line.line_client import LineClient
from line.repository import LineRepository
from line.router import router as line_router
from line.router import set_repository as set_line_repository
from line.scheduler import process_due_reminders
from models import AnalyzeRequest, AnalyzeResponse

load_dotenv()

app = FastAPI(title="Reverse FAQ Backend")
app.include_router(discovery_router)
app.include_router(line_router)

_line_engine = create_engine(
    "sqlite:///line.db",
    connect_args={"check_same_thread": False},
    echo=False,
)
SQLModel.metadata.create_all(_line_engine)
set_line_repository(LineRepository(_line_engine))

_scheduler = BackgroundScheduler()


@app.on_event("startup")
def _start_line_scheduler() -> None:
    line_client = LineClient(channel_access_token=os.environ.get("LINE_CHANNEL_ACCESS_TOKEN", ""))
    line_repo = LineRepository(_line_engine)
    _scheduler.add_job(
        lambda: process_due_reminders(line_repo, line_client),
        "interval",
        seconds=60,
        id="process_due_reminders",
    )
    _scheduler.start()


@app.on_event("shutdown")
def _stop_line_scheduler() -> None:
    _scheduler.shutdown(wait=False)

# Android エミュレータ / 実機からの開発アクセスを許可する最小限の CORS。
# 本番デプロイ時は origins を絞り込むこと。
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_client: GeminiClient | None = None


def get_client() -> GeminiClient:
    """遅延初期化で GeminiClient を取得する。"""
    global _client
    if _client is None:
        _client = GeminiClient(api_key=os.environ.get("GEMINI_API_KEY"))
    return _client


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/cases/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    client = get_client()
    return client.analyze(
        document_text=request.document_text,
        user_context=request.user_context,
    )
