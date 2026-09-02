from __future__ import annotations

import datetime
import os
from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException, status
from sqlmodel import SQLModel, create_engine

from discovery.aggregation import build_behavior_summary
from discovery.gemini_prompts import DiscoveryGeminiClient
from discovery.models import (
    DiscoverySession,
    DomainType,
    Experiment,
    ExperimentGenerateRequest,
    ExperimentResponse,
    ExperimentResultCreate,
    ExperimentResultResponse,
    ExperimentSelectRequest,
    ExperimentSkipRequest,
    HypothesisResponse,
    HypothesisUpdateRequest,
    InterestHypothesis,
    InterestSignal,
    InterestSignalCreate,
    InterestSignalResponse,
    SessionCreate,
    SessionResponse,
    SessionSummary,
)
from discovery.repository import DiscoveryRepository, StateTransitionError

_ENGINE = create_engine(
    "sqlite:///discovery.db",
    connect_args={"check_same_thread": False},
    echo=False,
)
SQLModel.metadata.create_all(_ENGINE)

_repository = DiscoveryRepository(_ENGINE)


def get_repository() -> DiscoveryRepository:
    """FastAPI 依存関係: DiscoveryRepository を返す。"""
    return _repository


def get_gemini_client() -> DiscoveryGeminiClient:
    """FastAPI 依存関係: Gemini クライアントを返す。"""
    return DiscoveryGeminiClient(api_key=os.environ.get("GEMINI_API_KEY"))


router = APIRouter(tags=["discovery"])


def _parse_iso_datetime(value: str) -> datetime.datetime:
    """ISO 8601 文字列（Z も可）を UTC datetime に変換する。"""
    return datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))


def _require_session(repo: DiscoveryRepository, session_id: int) -> DiscoverySession:
    session = repo.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Session not found")
    return session


@router.post("/sessions", response_model=SessionResponse, status_code=status.HTTP_201_CREATED)
def create_session(
    request: SessionCreate,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> DiscoverySession:
    """新しい興味探索セッションを作成する。"""
    return repo.create_session(request.student_label)


@router.post(
    "/sessions/{session_id}/signals",
    response_model=InterestSignalResponse,
    status_code=status.HTTP_201_CREATED,
)
def add_signal(
    session_id: int,
    request: InterestSignalCreate,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> InterestSignal:
    """セッションに弱い興味シグナルを追加する。"""
    _require_session(repo, session_id)
    return repo.add_signal(
        session_id=session_id,
        action_type=request.action_type.value,
        domain=request.domain,
        content_summary=request.content_summary,
        source=request.source.value,
        occurred_at=_parse_iso_datetime(request.occurred_at),
    )


@router.post(
    "/sessions/{session_id}/experiments/generate",
    response_model=list[ExperimentResponse],
    status_code=status.HTTP_201_CREATED,
)
def generate_experiments(
    session_id: int,
    request: ExperimentGenerateRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
    client: Annotated[DiscoveryGeminiClient, Depends(get_gemini_client)],
) -> list[Experiment]:
    """興味シグナルから Gemini で行動実験候補を生成し保存する。"""
    _require_session(repo, session_id)
    signals = repo.list_signals(session_id)
    try:
        candidates = client.generate_experiments(signals, n_candidates=request.n_candidates)
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Experiment generation is currently unavailable",
        ) from exc

    saved: list[Experiment] = []
    for candidate in candidates:
        try:
            domain = DomainType(candidate.domain)
        except ValueError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Invalid domain returned by Gemini: {candidate.domain}",
            ) from exc
        saved.append(
            repo.create_experiment(
                session_id=session_id,
                title=candidate.title,
                description=candidate.description,
                domain=domain,
                planned_minutes=candidate.planned_minutes,
            )
        )
    return saved


@router.post(
    "/experiments/{experiment_id}/select",
    response_model=ExperimentResponse,
)
def select_experiment(
    experiment_id: int,
    request: ExperimentSelectRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> Experiment:
    """指定した実験候補を選択済みにする。"""
    experiment = repo.get_experiment(experiment_id)
    if experiment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Experiment not found")
    try:
        return repo.select_experiment(experiment_id, request.selection_note)
    except StateTransitionError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.post("/experiments/{experiment_id}/skip", response_model=ExperimentResponse)
def skip_experiment(
    experiment_id: int,
    request: ExperimentSkipRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> Experiment:
    """実験をスキップ済みにする。"""
    experiment = repo.get_experiment(experiment_id)
    if experiment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Experiment not found")
    try:
        return repo.skip_experiment(experiment_id, request.reason)
    except StateTransitionError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.post("/experiments/{experiment_id}/start", response_model=ExperimentResponse)
def start_experiment(
    experiment_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> Experiment:
    """実験を開始済みにする。"""
    experiment = repo.get_experiment(experiment_id)
    if experiment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Experiment not found")
    try:
        return repo.start_experiment(experiment_id)
    except StateTransitionError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.post(
    "/experiments/{experiment_id}/complete",
    response_model=ExperimentResultResponse,
    status_code=status.HTTP_201_CREATED,
)
def complete_experiment(
    experiment_id: int,
    request: ExperimentResultCreate,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> Any:
    """実験を完了し、主観評価を保存する。"""
    experiment = repo.get_experiment(experiment_id)
    if experiment is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Experiment not found")
    try:
        result = repo.complete_experiment(
            experiment_id=experiment_id,
            enjoyment=request.enjoyment,
            curiosity=request.curiosity,
            retry_intent=request.retry_intent,
            confidence=request.confidence,
            reflection=request.reflection,
        )
    except StateTransitionError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=str(exc),
        ) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    return result


@router.post(
    "/sessions/{session_id}/hypothesis/update",
    response_model=HypothesisResponse,
    status_code=status.HTTP_201_CREATED,
)
def update_hypothesis(
    session_id: int,
    request: HypothesisUpdateRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
    client: Annotated[DiscoveryGeminiClient, Depends(get_gemini_client)],
) -> InterestHypothesis:
    """シグナルと実験結果から Gemini で興味仮説を生成し保存する。"""
    _require_session(repo, session_id)
    data = repo.get_summary_data(session_id)
    try:
        hypothesis_data = client.update_hypothesis(
            data["signals"], data["experiments"], data["results"]
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Hypothesis update is currently unavailable",
        ) from exc

    try:
        return repo.create_hypothesis(
            session_id=session_id,
            summary=hypothesis_data["summary"],
            confidence=hypothesis_data["confidence"],
            supporting_evidence=hypothesis_data["supporting_evidence"],
            suggested_next_domains=hypothesis_data["suggested_next_domains"],
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Invalid hypothesis data: {exc}",
        ) from exc


@router.get("/sessions/{session_id}/summary", response_model=SessionSummary)
def get_summary(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> dict[str, Any]:
    """セッションの行動サマリーを取得する。"""
    session = _require_session(repo, session_id)
    data = repo.get_summary_data(session_id)
    behavior_summary = build_behavior_summary(
        data["signals"], data["experiments"], data["results"]
    )
    latest_hypothesis = repo.get_latest_hypothesis(session_id)
    return {
        "session": session,
        "behavior_summary": behavior_summary,
        "latest_hypothesis": latest_hypothesis,
    }
