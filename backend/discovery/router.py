from __future__ import annotations

import datetime
import os
from typing import Annotated, Any, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlmodel import SQLModel, create_engine

from discovery.aggregation import (
    build_behavior_summary,
    build_behavior_summary_for_period,
    build_monthly_metrics,
    build_notification_candidates,
)
from discovery.gemini_prompts import DiscoveryGeminiClient
from discovery.models import (
    CriterionResponse,
    DiscoverySession,
    DomainType,
    Evidence,
    EvidenceResponse,
    Experiment,
    ExperimentGenerateRequest,
    ExperimentResponse,
    ExperimentResultCreate,
    ExperimentResultResponse,
    ExperimentSelectRequest,
    ExperimentSkipRequest,
    HypothesisFeedbackCreate,
    HypothesisFeedbackResult,
    HypothesisResponse,
    HypothesisUpdateRequest,
    InterestHypothesis,
    InterestSignal,
    InterestSignalCreate,
    InterestSignalResponse,
    MilestoneNarrativeResponse,
    MonthlyNarrativeResponse,
    NotificationCandidateResponse,
    OnboardingUpdateRequest,
    PsychAxisResult,
    PsychAxisResultResponse,
    PsychAxisSurveySubmitRequest,
    SessionCreate,
    SessionResponse,
    SessionSummary,
    UserReflection,
    UserReflectionCreate,
    UserReflectionResponse,
    WeeklyNarrativeResponse,
)
from discovery.repository import DiscoveryRepository, StateTransitionError

# この確信度未満の仮説はまだ「気づき」として提示しない。
# でっち上げの洞察よりも「まだ十分な根拠がない」と伝える方が誠実（§34）。
MIN_HYPOTHESIS_CONFIDENCE = 0.3

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


@router.get("/sessions", response_model=list[SessionResponse])
def list_sessions(
    student_label: Annotated[str, Query(min_length=1)],
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> list[DiscoverySession]:
    """指定した生徒ラベルのセッション一覧を updated_at 降順で取得する。"""
    return repo.list_sessions(student_label)


@router.delete("/sessions/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_session(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> None:
    """指定したセッションとそれに紐づく全データを削除する。"""
    deleted = repo.delete_session_cascade(session_id)
    if not deleted:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND, detail="Session not found"
        )


@router.post(
    "/sessions/{session_id}/psych-axis-survey",
    response_model=list[PsychAxisResultResponse],
)
def submit_psych_axis_survey(
    session_id: int,
    request: PsychAxisSurveySubmitRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> list[PsychAxisResult]:
    """心理軸アンケート結果を登録・更新する。"""
    _require_session(repo, session_id)
    return repo.upsert_psych_axis_results(session_id, request.scores)


@router.post(
    "/sessions/{session_id}/reflections",
    response_model=UserReflectionResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_reflection(
    session_id: int,
    request: UserReflectionCreate,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> UserReflection:
    """ユーザー主導の振り返りを作成する。"""
    _require_session(repo, session_id)
    try:
        return repo.create_user_reflection(
            session_id, content=request.content, mood=request.mood
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)
        ) from exc


@router.get(
    "/sessions/{session_id}/reflections",
    response_model=list[UserReflectionResponse],
)
def list_reflections(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> list[UserReflection]:
    """指定セッションの振り返り一覧を created_at 降順で取得する。"""
    _require_session(repo, session_id)
    return repo.list_user_reflections(session_id)


@router.patch(
    "/sessions/{session_id}/onboarding",
    response_model=SessionResponse,
)
def update_onboarding_info(
    session_id: int,
    request: OnboardingUpdateRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> DiscoverySession:
    """セッションにオンボーディング情報を部分更新する。"""
    _require_session(repo, session_id)
    return repo.update_onboarding_info(
        session_id, **request.model_dump(exclude_unset=True)
    )


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
    response_model=Optional[HypothesisResponse],
    status_code=status.HTTP_201_CREATED,
)
def update_hypothesis(
    session_id: int,
    request: HypothesisUpdateRequest,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
    client: Annotated[DiscoveryGeminiClient, Depends(get_gemini_client)],
) -> InterestHypothesis | None:
    """シグナルと実験結果から Gemini で興味仮説を生成し保存する。

    確信度が MIN_HYPOTHESIS_CONFIDENCE 未満の場合は保存も提示もしない
    （でっち上げの気づきより「まだ根拠が足りない」の方が誠実、§34）。
    """
    _require_session(repo, session_id)
    evidences = repo.build_evidence(session_id)
    data = repo.get_summary_data(session_id)

    try:
        category_map = client.classify_behavior_categories(
            evidences, data["experiments"], data["results"]
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Behavior category classification is currently unavailable",
        ) from exc

    if isinstance(category_map, dict):
        for evidence in evidences:
            categories = category_map.get(evidence.id)
            if isinstance(categories, dict):
                repo.update_evidence_behavior_categories(evidence.id, categories)

    try:
        hypothesis_data = client.update_hypothesis(
            evidences, data["experiments"], data["results"]
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Hypothesis update is currently unavailable",
        ) from exc

    if hypothesis_data["confidence"] < MIN_HYPOTHESIS_CONFIDENCE:
        return None

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


@router.post(
    "/hypotheses/{hypothesis_id}/feedback",
    response_model=HypothesisFeedbackResult,
    status_code=status.HTTP_201_CREATED,
)
def add_hypothesis_feedback(
    hypothesis_id: int,
    request: HypothesisFeedbackCreate,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> dict[str, Any]:
    """仮説への生徒の反応（同感/わからない/違う）を記録し、確信度を更新する。

    閾値を超えて同感された仮説は個人の意思決定基準（Criterion）に昇格する（§17）。
    """
    try:
        feedback, hypothesis, criterion = repo.add_hypothesis_feedback(
            hypothesis_id, request.reaction
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    return {
        "feedback": feedback,
        "updated_hypothesis": hypothesis,
        "new_criterion": criterion,
    }


@router.get("/sessions/{session_id}/evidence", response_model=list[EvidenceResponse])
def list_evidence(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> list[Evidence]:
    """セッションのエビデンス一覧を作成日時降順で取得する。"""
    _require_session(repo, session_id)
    return repo.list_evidence(session_id)


@router.get("/sessions/{session_id}/criteria", response_model=list[CriterionResponse])
def list_criteria(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> Any:
    """生徒に同感された、確信度の高い個人の意思決定基準の一覧を取得する。"""
    _require_session(repo, session_id)
    return repo.list_criteria(session_id)


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
    criteria = repo.list_criteria(session_id)
    psych_axis_scores = repo.get_psych_axis_scores(session_id)
    return {
        "session": session,
        "behavior_summary": behavior_summary,
        "latest_hypothesis": latest_hypothesis,
        "criteria": criteria,
        "psych_axis_scores": psych_axis_scores,
    }


@router.get(
    "/sessions/{session_id}/notification-candidates",
    response_model=list[NotificationCandidateResponse],
)
def get_notification_candidates(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
) -> list[NotificationCandidateResponse]:
    """通知候補となる選択済み実験を、ドメイン状態と共に取得する。"""
    _require_session(repo, session_id)
    data = repo.get_summary_data(session_id)
    return build_notification_candidates(
        data["experiments"], data["results"]
    )


def _compute_top_domain(summary: dict[str, Any]) -> str:
    """ドメインカウントから最も活動の多かったドメインを返す。"""
    domain_counts: dict[str, int] = summary.get("domain_counts") or {}
    if not domain_counts:
        return DomainType.OTHER.value
    return max(domain_counts.items(), key=lambda item: item[1])[0]


def _prepare_metrics_for_prompt(metrics: dict[str, Any]) -> dict[str, Any]:
    """月次メトリクスをGeminiプロンプト用に百分率に丸める。"""
    prepared = dict(metrics)
    for key in ("completion_rate", "active_day_rate"):
        value = prepared.get(key)
        if value is not None:
            prepared[key] = round(value * 100, 1)
    return prepared


@router.get(
    "/sessions/{session_id}/report/weekly-narrative",
    response_model=WeeklyNarrativeResponse,
)
def get_weekly_narrative(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
    client: Annotated[DiscoveryGeminiClient, Depends(get_gemini_client)],
) -> dict[str, str]:
    """直近7日とその前7日を比較した週次AIナラティブを取得する。

    Gemini呼び出しは数秒〜十数秒かかるため、同一UTC日付内はセッション単位で
    キャッシュを再利用する（Discovery/Report両画面から呼ばれるたびの再生成を防ぐ）。
    """
    _require_session(repo, session_id)
    now = datetime.datetime.now(datetime.timezone.utc)
    cache_date = now.strftime("%Y-%m-%d")

    cached = repo.get_weekly_narrative_cache(session_id, cache_date)
    if cached is not None:
        return {
            "weekly_insights": cached.weekly_insights,
            "change_from_past": cached.change_from_past,
        }

    recent_start = now - datetime.timedelta(days=7)
    previous_start = now - datetime.timedelta(days=14)

    data = repo.get_summary_data(session_id)
    recent_summary = build_behavior_summary_for_period(
        data["signals"], data["experiments"], data["results"], recent_start, now
    ).model_dump()
    previous_summary = build_behavior_summary_for_period(
        data["signals"], data["experiments"], data["results"], previous_start, recent_start
    ).model_dump()

    top_domain = _compute_top_domain(recent_summary)

    try:
        narrative_data = client.generate_weekly_narrative(
            recent_summary, previous_summary, top_domain
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Weekly narrative generation is currently unavailable",
        ) from exc

    saved = repo.save_weekly_narrative_cache(
        session_id,
        cache_date,
        narrative_data["weekly_insights"],
        narrative_data["change_from_past"],
    )

    return {
        "weekly_insights": saved.weekly_insights,
        "change_from_past": saved.change_from_past,
    }


@router.get(
    "/sessions/{session_id}/report/monthly-narrative",
    response_model=MonthlyNarrativeResponse,
)
def get_monthly_narrative(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
    client: Annotated[DiscoveryGeminiClient, Depends(get_gemini_client)],
) -> dict[str, str]:
    """直近30日とその前30日を比較した月次AIナラティブを取得する。

    Gemini呼び出しは数秒〜十数秒かかるため、同一UTC月内はセッション単位で
    キャッシュを再利用する。
    """
    _require_session(repo, session_id)
    now = datetime.datetime.now(datetime.timezone.utc)
    period_end = datetime.datetime(
        now.year, now.month, 1, 0, 0, 0, tzinfo=datetime.timezone.utc
    )
    cache_month = period_end.strftime("%Y-%m")

    cached = repo.get_monthly_narrative_cache(session_id, cache_month)
    if cached is not None:
        return {
            "period_start": (
                period_end - datetime.timedelta(days=30)
            ).strftime("%Y-%m-%d"),
            "period_end_exclusive": period_end.strftime("%Y-%m-%d"),
            "monthly_insights": cached.monthly_insights,
            "progress_wave": cached.progress_wave,
            "continuity_insight": cached.continuity_insight,
        }

    recent_start = period_end - datetime.timedelta(days=30)
    previous_start = period_end - datetime.timedelta(days=60)

    data = repo.get_summary_data(session_id)
    recent_metrics = build_monthly_metrics(
        data["signals"], data["experiments"], recent_start, period_end
    )
    previous_metrics = build_monthly_metrics(
        data["signals"], data["experiments"], previous_start, recent_start
    )

    recent_behavior = build_behavior_summary_for_period(
        data["signals"], data["experiments"], data["results"], recent_start, period_end
    ).model_dump()
    previous_behavior = build_behavior_summary_for_period(
        data["signals"],
        data["experiments"],
        data["results"],
        previous_start,
        recent_start,
    ).model_dump()

    recent_metrics["behavior_summary"] = recent_behavior
    previous_metrics["behavior_summary"] = previous_behavior

    try:
        narrative_data = client.generate_monthly_narrative(
            _prepare_metrics_for_prompt(recent_metrics),
            _prepare_metrics_for_prompt(previous_metrics),
        )
    except (ValueError, RuntimeError) as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Monthly narrative generation is currently unavailable",
        ) from exc

    saved = repo.save_monthly_narrative_cache(
        session_id,
        cache_month,
        narrative_data["monthly_insights"],
        narrative_data["progress_wave"],
        narrative_data["continuity_insight"],
    )

    return {
        "period_start": recent_start.strftime("%Y-%m-%d"),
        "period_end_exclusive": period_end.strftime("%Y-%m-%d"),
        "monthly_insights": saved.monthly_insights,
        "progress_wave": saved.progress_wave,
        "continuity_insight": saved.continuity_insight,
    }


@router.get(
    "/sessions/{session_id}/report/milestone-narrative",
    response_model=MilestoneNarrativeResponse,
)
def get_milestone_narrative(
    session_id: int,
    repo: Annotated[DiscoveryRepository, Depends(get_repository)],
    client: Annotated[DiscoveryGeminiClient, Depends(get_gemini_client)],
    milestone: Annotated[int | None, Query(ge=1)] = None,
) -> dict[str, Any]:
    """シグナル蓄積件数に応じたマイルストーンAIナラティブを取得する。

    シグナル総件数が10件未満（milestone=0）の場合はレポート対象外。
    同一マイルストーン内ではセッション単位でキャッシュを再利用する。

    milestone クエリパラメータを省略すると、現在のシグナル総件数から
    最新マイルストーンを自動算出する。Android Worker は未通知の過去
    マイルストーンを指定して取得するためにこのパラメータを使う。
    """
    _require_session(repo, session_id)
    signal_count = repo.count_signals(session_id)
    current_milestone = signal_count // 10
    if milestone is None:
        milestone = current_milestone
    if milestone == 0 or milestone > current_milestone:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Not enough signals to generate a milestone narrative",
        )

    cached, is_owner = repo.get_or_reserve_milestone_narrative_cache(
        session_id, milestone
    )
    if cached.insight_text:
        return {
            "milestone": milestone,
            "insight_text": cached.insight_text,
        }

    if not is_owner:
        # 他リクエストが生成中。勝者のキャッシュが確定するまで待つ。
        winner_cache = repo.wait_for_milestone_narrative_cache(session_id, milestone)
        if winner_cache is None or not winner_cache.insight_text:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Milestone narrative generation is currently unavailable",
            )
        return {
            "milestone": milestone,
            "insight_text": winner_cache.insight_text,
        }

    data = repo.get_summary_data(session_id)
    behavior_summary = build_behavior_summary(
        data["signals"], data["experiments"], data["results"]
    ).model_dump()
    top_domain = _compute_top_domain(behavior_summary)

    try:
        narrative_data = client.generate_milestone_narrative(
            signal_count=signal_count,
            milestone=milestone,
            top_domain=top_domain,
        )
    except (ValueError, RuntimeError) as exc:
        # 生成失敗時は placeholder 予約を解除し、次回リクエストが再生成できるようにする。
        repo.release_milestone_reservation(session_id, milestone)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Milestone narrative generation is currently unavailable",
        ) from exc

    saved = repo.save_milestone_narrative_cache(
        session_id,
        milestone,
        narrative_data["insight_text"],
    )

    return {
        "milestone": milestone,
        "insight_text": saved.insight_text,
    }
