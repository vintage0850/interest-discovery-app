from __future__ import annotations

from collections import defaultdict
from statistics import mean
from typing import Any

from discovery.models import BehaviorSummary, Experiment, ExperimentResult, ExperimentStatus, InterestSignal


_DURATION_RATIO_HIGH = 1.5
_DURATION_RATIO_VERY_HIGH = 2.0


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
