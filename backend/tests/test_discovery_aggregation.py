from __future__ import annotations

import datetime

from discovery.aggregation import build_behavior_summary, build_behavior_summary_for_period
from discovery.models import (
    ActionType,
    DomainType,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    InterestSignal,
    InterestSignalSource,
)


class TestBehaviorSummary:
    def test_empty_summary(self) -> None:
        summary = build_behavior_summary([], [])
        assert summary.total_signals == 0
        assert summary.total_experiments == 0
        assert summary.completed_experiments == 0
        assert summary.avg_enjoyment is None

    def test_action_type_counts(self) -> None:
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=datetime.datetime.now(datetime.timezone.utc),
            ),
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.ART.value,
                content_summary="Painting",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=datetime.datetime.now(datetime.timezone.utc),
            ),
            InterestSignal(
                session_id=1,
                action_type=ActionType.VIEW.value,
                domain=DomainType.TECH.value,
                content_summary="Rust",
                source=InterestSignalSource.BROWSING_HISTORY.value,
                occurred_at=datetime.datetime.now(datetime.timezone.utc),
            ),
        ]
        summary = build_behavior_summary(signals, [])
        assert summary.total_signals == 3
        assert summary.action_type_counts == {"search": 2, "view": 1}
        assert summary.domain_counts == {"tech": 2, "art": 1}

    def test_completed_experiment_averages(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=12,
            ),
        ]
        results = [
            ExperimentResult(
                experiment_id=1,
                enjoyment=4,
                curiosity=5,
                retry_intent=3,
                confidence=0.8,
            ),
        ]
        summary = build_behavior_summary([], experiments, results)
        assert summary.total_experiments == 1
        assert summary.completed_experiments == 1
        assert summary.avg_enjoyment == 4.0
        assert summary.avg_curiosity == 5.0
        assert summary.avg_retry_intent == 3.0
        assert summary.avg_confidence == 0.8

    def test_skipped_experiments_counted(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.SKIPPED.value,
                skipped_at=datetime.datetime.now(datetime.timezone.utc),
            ),
        ]
        summary = build_behavior_summary([], experiments, [])
        assert summary.skipped_experiments == 1
        assert summary.completed_experiments == 0

    def test_duration_ratio_high(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=16,
            ),
        ]
        summary = build_behavior_summary([], experiments, [])
        assert 1 in summary.duration_ratio_high
        assert 1 not in summary.duration_ratio_very_high

    def test_duration_ratio_very_high(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=20,
            ),
        ]
        summary = build_behavior_summary([], experiments, [])
        assert 1 in summary.duration_ratio_high
        assert 1 in summary.duration_ratio_very_high

    def test_duration_ratio_not_high(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=11,
            ),
        ]
        summary = build_behavior_summary([], experiments, [])
        assert summary.duration_ratio_high == []

    def test_total_minutes_spent_sums_completed_experiments(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=12,
            ),
            Experiment(
                id=2,
                session_id=1,
                title="Try painting",
                description="desc",
                domain=DomainType.ART.value,
                planned_minutes=5,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=8,
            ),
            Experiment(
                id=3,
                session_id=1,
                title="Skipped one",
                description="desc",
                domain=DomainType.ART.value,
                planned_minutes=5,
                status=ExperimentStatus.SKIPPED.value,
            ),
        ]
        summary = build_behavior_summary([], experiments, [])
        assert summary.total_minutes_spent == 20

    def test_domain_experiment_and_completed_counts(self) -> None:
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
            ),
            Experiment(
                id=2,
                session_id=1,
                title="Try more coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.GENERATED.value,
            ),
            Experiment(
                id=3,
                session_id=1,
                title="Try painting",
                description="desc",
                domain=DomainType.ART.value,
                planned_minutes=5,
                status=ExperimentStatus.SKIPPED.value,
            ),
        ]
        summary = build_behavior_summary([], experiments, [])
        assert summary.domain_experiment_counts == {"tech": 2, "art": 1}
        assert summary.domain_completed_counts == {"tech": 1}

    def test_discrepancy_between_signals_and_results(self) -> None:
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=datetime.datetime.now(datetime.timezone.utc),
            ),
        ]
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.ART.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
            ),
        ]
        results = [
            ExperimentResult(
                experiment_id=1,
                enjoyment=5,
                curiosity=5,
                retry_intent=5,
                confidence=1.0,
            ),
        ]
        summary = build_behavior_summary(signals, experiments, results)
        assert len(summary.discrepancies) > 0
        discrepancy = summary.discrepancies[0]
        assert discrepancy["type"] == "high_result_no_signal"
        assert discrepancy["domain"] == DomainType.ART.value


class TestBehaviorSummaryForPeriod:
    def _utc(self, day: int, hour: int = 0) -> datetime.datetime:
        return datetime.datetime(2026, 9, day, hour, 0, 0, tzinfo=datetime.timezone.utc)

    def test_empty_period_returns_zero_summary(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        summary = build_behavior_summary_for_period([], [], [], start, end)
        assert summary.total_signals == 0
        assert summary.total_experiments == 0
        assert summary.completed_experiments == 0

    def test_filters_signals_by_created_at(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Recent",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=self._utc(5),
                created_at=self._utc(5),
            ),
            InterestSignal(
                session_id=1,
                action_type=ActionType.VIEW.value,
                domain=DomainType.ART.value,
                content_summary="Old",
                source=InterestSignalSource.BROWSING_HISTORY.value,
                occurred_at=self._utc(10),
                created_at=self._utc(10),
            ),
        ]
        summary = build_behavior_summary_for_period(signals, [], [], start, end)
        assert summary.total_signals == 1
        assert summary.domain_counts == {"tech": 1}

    def test_filters_experiments_by_completed_at(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Recent experiment",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
                created_at=self._utc(5),
                completed_at=self._utc(5),
            ),
            Experiment(
                id=2,
                session_id=1,
                title="Old experiment",
                description="desc",
                domain=DomainType.ART.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
                created_at=self._utc(10),
                completed_at=self._utc(10),
            ),
        ]
        summary = build_behavior_summary_for_period([], experiments, [], start, end)
        assert summary.total_experiments == 1
        assert summary.completed_experiments == 1
        assert summary.domain_experiment_counts == {"tech": 1}

    def test_excludes_experiments_not_completed_in_period(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Not completed",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.GENERATED.value,
                created_at=self._utc(5),
            ),
            Experiment(
                id=2,
                session_id=1,
                title="Completed later",
                description="desc",
                domain=DomainType.ART.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
                created_at=self._utc(5),
                completed_at=self._utc(12),
            ),
        ]
        summary = build_behavior_summary_for_period([], experiments, [], start, end)
        assert summary.total_experiments == 0
        assert summary.completed_experiments == 0

    def test_includes_results_for_experiments_completed_in_period(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Completed in period",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
                created_at=self._utc(5),
                completed_at=self._utc(5),
            ),
        ]
        results = [
            ExperimentResult(
                experiment_id=1,
                enjoyment=5,
                curiosity=5,
                retry_intent=5,
                confidence=1.0,
            ),
        ]
        summary = build_behavior_summary_for_period([], experiments, results, start, end)
        assert summary.avg_enjoyment == 5.0
        assert summary.avg_confidence == 1.0

    def test_boundary_is_half_open(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Exactly at start",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=start,
                created_at=start,
            ),
            InterestSignal(
                session_id=1,
                action_type=ActionType.VIEW.value,
                domain=DomainType.ART.value,
                content_summary="Exactly at end",
                source=InterestSignalSource.BROWSING_HISTORY.value,
                occurred_at=end,
                created_at=end,
            ),
        ]
        summary = build_behavior_summary_for_period(signals, [], [], start, end)
        assert summary.total_signals == 1
        assert summary.domain_counts == {"tech": 1}

    def test_computes_domain_counts_for_filtered_signals(self) -> None:
        start = self._utc(1)
        end = self._utc(8)
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Recent tech",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=self._utc(5),
                created_at=self._utc(5),
            ),
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Recent tech 2",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at=self._utc(6),
                created_at=self._utc(6),
            ),
            InterestSignal(
                session_id=1,
                action_type=ActionType.VIEW.value,
                domain=DomainType.ART.value,
                content_summary="Old art",
                source=InterestSignalSource.BROWSING_HISTORY.value,
                occurred_at=self._utc(15),
                created_at=self._utc(15),
            ),
        ]
        summary = build_behavior_summary_for_period(signals, [], [], start, end)
        assert summary.domain_counts == {"tech": 2}
