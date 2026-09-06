from __future__ import annotations

import datetime
from collections import defaultdict
from statistics import mean
from typing import Any

from discovery.models import BehaviorSummary, Experiment, ExperimentResult, ExperimentStatus, InterestSignal


_DURATION_RATIO_HIGH = 1.5
_DURATION_RATIO_VERY_HIGH = 2.0


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

    def _as_utc(dt: datetime.datetime) -> datetime.datetime:
        if dt.tzinfo is None:
            return dt.replace(tzinfo=datetime.timezone.utc)
        return dt.astimezone(datetime.timezone.utc)

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
    )


def _avg(values: list[float | int]) -> float | None:
    if not values:
        return None
    return mean(values)


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

