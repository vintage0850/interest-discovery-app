from __future__ import annotations

import datetime
from collections import defaultdict
from statistics import mean
from typing import Any

from discovery.models import (
    BehaviorSummary,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    InterestSignal,
    NotificationCandidateDomainStatus,
    NotificationCandidateResponse,
)


_DURATION_RATIO_HIGH = 1.5
_DURATION_RATIO_VERY_HIGH = 2.0

_DIVE_CANDIDATE_MIN_RESULT_COUNT = 2
_DIVE_CANDIDATE_MIN_RATING = 4.0
_DIVE_CANDIDATE_MIN_CONFIDENCE = 0.70


def _as_utc(dt: datetime.datetime) -> datetime.datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=datetime.timezone.utc)
    return dt.astimezone(datetime.timezone.utc)


def build_behavior_summary_for_period(
    signals: list[InterestSignal],
    experiments: list[Experiment],
    results: list[ExperimentResult] | None = None,
    start: datetime.datetime | None = None,
    end: datetime.datetime | None = None,
) -> BehaviorSummary:
    """指定した期間内のシグナルと完了済み実験から行動サマリーを計算する。

    期間は ``[start, end)`` で半開区間として扱う。
    シグナルは ``created_at``、実験は ``completed_at`` でフィルタリングする。
    """
    if start is None or end is None:
        raise ValueError("start and end must be provided")

    utc_start = _as_utc(start)
    utc_end = _as_utc(end)

    filtered_signals = []
    for s in signals:
        if s.created_at is None:
            continue
        created_at = _as_utc(s.created_at)
        if utc_start <= created_at < utc_end:
            filtered_signals.append(s)

    filtered_experiments = []
    for e in experiments:
        if (
            e.status == ExperimentStatus.COMPLETED.value
            and e.completed_at is not None
        ):
            completed_at = _as_utc(e.completed_at)
            if utc_start <= completed_at < utc_end:
                filtered_experiments.append(e)
    experiment_ids = {e.id for e in filtered_experiments}
    filtered_results = [
        r for r in (results or [])
        if r.experiment_id in experiment_ids
    ]

    return build_behavior_summary(filtered_signals, filtered_experiments, filtered_results)


def build_monthly_metrics(
    signals: list[InterestSignal],
    experiments: list[Experiment],
    start: datetime.datetime,
    end: datetime.datetime,
) -> dict[str, Any]:
    """指定した期間の月次レポート用メトリクスを決定論的に計算する。

    期間は ``[start, end)`` で半開区間として扱う。
    月次専用メトリクス（完了率・アクティブ日数・進捗の波）を返す。

    Note:
        この関数は ``end - start == 30日`` を前提としている。
        ``active_day_rate`` の分母および ``progress_segments`` の区間幅は
        30日固定で計算される。
    """

    utc_start = _as_utc(start)
    utc_end = _as_utc(end)

    started_experiments: list[Experiment] = []
    for experiment in experiments:
        if experiment.started_at is None:
            continue
        started_at = _as_utc(experiment.started_at)
        if utc_start <= started_at < utc_end:
            started_experiments.append(experiment)

    started_experiment_count = len(started_experiments)
    completed_started_experiment_count = 0
    for experiment in started_experiments:
        if experiment.completed_at is not None:
            completed_at = _as_utc(experiment.completed_at)
            if utc_start <= completed_at < utc_end:
                completed_started_experiment_count += 1

    completion_rate: float | None = None
    if started_experiment_count > 0:
        completion_rate = (
            completed_started_experiment_count / started_experiment_count
        )

    completed_experiments_in_period: list[Experiment] = []
    for experiment in experiments:
        if (
            experiment.status == ExperimentStatus.COMPLETED.value
            and experiment.completed_at is not None
        ):
            completed_at = _as_utc(experiment.completed_at)
            if utc_start <= completed_at < utc_end:
                completed_experiments_in_period.append(experiment)

    active_dates: set[datetime.date] = set()
    for signal in signals:
        if signal.created_at is not None:
            created_at = _as_utc(signal.created_at)
            if utc_start <= created_at < utc_end:
                active_dates.add(created_at.date())
    for experiment in started_experiments:
        started_at = _as_utc(experiment.started_at)
        active_dates.add(started_at.date())
    for experiment in completed_experiments_in_period:
        completed_at = _as_utc(experiment.completed_at)
        active_dates.add(completed_at.date())

    active_days = len(active_dates)
    active_day_rate = active_days / 30.0

    segment_starts = [
        utc_start,
        utc_start + datetime.timedelta(days=10),
        utc_start + datetime.timedelta(days=20),
    ]
    progress_segments: list[dict[str, int]] = []
    for i, seg_start in enumerate(segment_starts):
        seg_end = utc_end if i == len(segment_starts) - 1 else segment_starts[i + 1]
        seg_active_dates: set[datetime.date] = set()
        seg_completed_count = 0
        for experiment in completed_experiments_in_period:
            completed_at = _as_utc(experiment.completed_at)
            if seg_start <= completed_at < seg_end:
                seg_completed_count += 1
                seg_active_dates.add(completed_at.date())
        for signal in signals:
            if signal.created_at is not None:
                created_at = _as_utc(signal.created_at)
                if utc_start <= created_at < utc_end and seg_start <= created_at < seg_end:
                    seg_active_dates.add(created_at.date())
        for experiment in started_experiments:
            started_at = _as_utc(experiment.started_at)
            if seg_start <= started_at < seg_end:
                seg_active_dates.add(started_at.date())
        progress_segments.append(
            {
                "completed_experiment_count": seg_completed_count,
                "active_days": len(seg_active_dates),
            }
        )

    return {
        "started_experiment_count": started_experiment_count,
        "completed_started_experiment_count": completed_started_experiment_count,
        "completion_rate": completion_rate,
        "active_days": active_days,
        "active_day_rate": active_day_rate,
        "progress_segments": progress_segments,
    }


def build_behavior_summary(
    signals: list[InterestSignal],
    experiments: list[Experiment],
    results: list[ExperimentResult] | None = None,
) -> BehaviorSummary:
    """シグナルと実験結果から決定論的な行動サマリーを計算する。"""
    results = results or []

    action_type_counts: dict[str, int] = defaultdict(int)
    domain_counts: dict[str, int] = defaultdict(int)

    for signal in signals:
        action_type_counts[signal.action_type] += 1
        domain_counts[signal.domain] += 1

    total_experiments = len(experiments)
    completed_experiments = 0
    skipped_experiments = 0
    duration_ratio_high: list[int] = []
    duration_ratio_very_high: list[int] = []
    total_minutes_spent = 0
    domain_experiment_counts: dict[str, int] = defaultdict(int)
    domain_completed_counts: dict[str, int] = defaultdict(int)

    completed_results: list[ExperimentResult] = []
    result_by_experiment_id: dict[int, ExperimentResult] = {r.experiment_id: r for r in results}

    for experiment in experiments:
        domain_experiment_counts[experiment.domain] += 1

        if experiment.status == ExperimentStatus.COMPLETED.value:
            completed_experiments += 1
            domain_completed_counts[experiment.domain] += 1
            if experiment.id in result_by_experiment_id:
                completed_results.append(result_by_experiment_id[experiment.id])
        elif experiment.status == ExperimentStatus.SKIPPED.value:
            skipped_experiments += 1

        if experiment.status == ExperimentStatus.COMPLETED.value and experiment.actual_minutes is not None:
            total_minutes_spent += experiment.actual_minutes
            ratio = experiment.actual_minutes / experiment.planned_minutes
            if ratio >= _DURATION_RATIO_VERY_HIGH:
                duration_ratio_very_high.append(experiment.id)
                duration_ratio_high.append(experiment.id)
            elif ratio >= _DURATION_RATIO_HIGH:
                duration_ratio_high.append(experiment.id)

    avg_enjoyment = _avg([r.enjoyment for r in completed_results])
    avg_curiosity = _avg([r.curiosity for r in completed_results])
    avg_retry_intent = _avg([r.retry_intent for r in completed_results])
    avg_confidence = _avg([r.confidence for r in completed_results])

    discrepancies = _detect_discrepancies(
        signals=signals,
        experiments=experiments,
        results=results,
    )

    dive_candidate_domains = _compute_dive_candidate_domains(experiments, results)

    return BehaviorSummary(
        total_signals=len(signals),
        action_type_counts=dict(action_type_counts),
        domain_counts=dict(domain_counts),
        total_experiments=total_experiments,
        completed_experiments=completed_experiments,
        skipped_experiments=skipped_experiments,
        avg_enjoyment=avg_enjoyment,
        avg_curiosity=avg_curiosity,
        avg_retry_intent=avg_retry_intent,
        avg_confidence=avg_confidence,
        duration_ratio_high=duration_ratio_high,
        duration_ratio_very_high=duration_ratio_very_high,
        discrepancies=discrepancies,
        total_minutes_spent=total_minutes_spent,
        domain_experiment_counts=dict(domain_experiment_counts),
        domain_completed_counts=dict(domain_completed_counts),
        dive_candidate_domains=dive_candidate_domains,
    )


def _avg(values: list[float | int]) -> float | None:
    if not values:
        return None
    return mean(values)


def _compute_dive_candidate_domains(
    experiments: list[Experiment],
    results: list[ExperimentResult],
) -> list[str]:
    """DIVE_CANDIDATE 判定基準を満たすドメインID一覧を返す。

    各ドメインについて、評価付き完了実験が2件以上あり、
    enjoyment/curiosity/retry_intent の平均が4.0以上、
    confidence 平均が0.70以上、かつ planned_minutes の1.5倍以上で
    実施した実験が1件以上あれば候補とする。
    """
    result_by_experiment_id = {r.experiment_id: r for r in results}

    domain_completed: dict[str, list[Experiment]] = defaultdict(list)
    for experiment in experiments:
        if experiment.status == ExperimentStatus.COMPLETED.value:
            domain_completed[experiment.domain].append(experiment)

    candidates: list[str] = []
    for domain, completed in domain_completed.items():
        if len(completed) < _DIVE_CANDIDATE_MIN_RESULT_COUNT:
            continue

        rated = [
            e
            for e in completed
            if e.id in result_by_experiment_id and e.actual_minutes is not None
        ]
        if len(rated) < _DIVE_CANDIDATE_MIN_RESULT_COUNT:
            continue

        rated_results = [result_by_experiment_id[e.id] for e in rated]
        if (
            _avg([r.enjoyment for r in rated_results]) < _DIVE_CANDIDATE_MIN_RATING
            or _avg([r.curiosity for r in rated_results]) < _DIVE_CANDIDATE_MIN_RATING
            or _avg([r.retry_intent for r in rated_results])
            < _DIVE_CANDIDATE_MIN_RATING
            or _avg([r.confidence for r in rated_results])
            < _DIVE_CANDIDATE_MIN_CONFIDENCE
        ):
            continue

        if not any(
            e.actual_minutes / e.planned_minutes >= _DURATION_RATIO_HIGH
            for e in rated
        ):
            continue

        candidates.append(domain)

    return sorted(candidates)


def _detect_discrepancies(
    signals: list[InterestSignal],
    experiments: list[Experiment],
    results: list[ExperimentResult],
) -> list[dict[str, Any]]:
    """興味シグナルと実験結果の間の乖離を検出する。"""
    discrepancies: list[dict[str, Any]] = []

    signal_domains = {s.domain for s in signals}
    result_by_experiment_id = {r.experiment_id: r for r in results}

    for experiment in experiments:
        if experiment.status != ExperimentStatus.COMPLETED.value:
            continue
        result = result_by_experiment_id.get(experiment.id)
        if result is None:
            continue

        avg_score = (result.enjoyment + result.curiosity + result.retry_intent) / 3
        if avg_score >= 4.0 and experiment.domain not in signal_domains:
            discrepancies.append(
                {
                    "type": "high_result_no_signal",
                    "domain": experiment.domain,
                    "experiment_id": experiment.id,
                    "message": (
                        f"'{experiment.domain}' で高い評価が得られたが、"
                        "興味シグナルは検出されていません"
                    ),
                }
            )

    return discrepancies


def summarize_domain_signals(domain: str, signals: list[InterestSignal]) -> str:
    """同一ドメインのシグナル群から要約テキストを生成する。"""
    count = len(signals)
    summaries = [s.content_summary for s in signals if s.content_summary]
    if summaries:
        details = "、".join(summaries[:5])
        text = f"{domain}に関するシグナル{count}件: {details}"
    else:
        text = f"{domain}に関するシグナル{count}件"
    if len(text) > 1000:
        text = text[:997] + "..."
    return text


def build_notification_candidates(
    experiments: list[Experiment],
    results: list[ExperimentResult] | None = None,
) -> list[NotificationCandidateResponse]:
    """通知候補となる選択済み実験を抽出し、ドメイン状態付きで安定ソートして返す。

    各ドメインの状態は ``build_behavior_summary`` によるセッション全期間集計を正本とする。
    優先順位は ``DIVE_CANDIDATE > TRIED > EXPLORED`` であり、``UNEXPLORED`` は候補外とする。
    同順位では ``selected_at`` 昇順、さらに実験ID昇順とする。
    """
    results = results or []
    summary = build_behavior_summary([], experiments, results)
    dive_domains = set(summary.dive_candidate_domains)

    def _domain_status(domain: str) -> NotificationCandidateDomainStatus:
        if domain in dive_domains:
            return NotificationCandidateDomainStatus.DIVE_CANDIDATE
        if summary.domain_completed_counts.get(domain, 0) > 0:
            return NotificationCandidateDomainStatus.TRIED
        if summary.domain_experiment_counts.get(domain, 0) > 0:
            return NotificationCandidateDomainStatus.EXPLORED
        return NotificationCandidateDomainStatus.UNEXPLORED

    def _reason(status: NotificationCandidateDomainStatus) -> str:
        if status == NotificationCandidateDomainStatus.DIVE_CANDIDATE:
            return "過去の実験結果から深掘りに値する分野です"
        if status == NotificationCandidateDomainStatus.TRIED:
            return "すでに試したことのある分野です"
        if status == NotificationCandidateDomainStatus.EXPLORED:
            return "興味の傾向が見られる分野です"
        return ""

    priority = {
        NotificationCandidateDomainStatus.DIVE_CANDIDATE.value: 0,
        NotificationCandidateDomainStatus.TRIED.value: 1,
        NotificationCandidateDomainStatus.EXPLORED.value: 2,
        NotificationCandidateDomainStatus.UNEXPLORED.value: 3,
    }
    min_selected_at = datetime.datetime.min.replace(tzinfo=datetime.timezone.utc)

    candidates: list[NotificationCandidateResponse] = []
    for experiment in experiments:
        if experiment.status != ExperimentStatus.SELECTED.value:
            continue
        status = _domain_status(experiment.domain)
        if status == NotificationCandidateDomainStatus.UNEXPLORED:
            continue
        candidates.append(
            NotificationCandidateResponse(
                experiment=experiment,
                domain_status=status.value,
                reason=_reason(status),
            )
        )

    candidates.sort(
        key=lambda c: (
            priority[c.domain_status],
            (c.experiment.selected_at or min_selected_at),
            c.experiment.id,
        )
    )
    return candidates

