from __future__ import annotations

import os

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from discovery.router import router as discovery_router
from gemini_client import GeminiClient
from line.router import router as line_router
from models import AnalyzeRequest, AnalyzeResponse

load_dotenv()

app = FastAPI(title="Reverse FAQ Backend")
app.include_router(discovery_router)
app.include_router(line_router)

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
