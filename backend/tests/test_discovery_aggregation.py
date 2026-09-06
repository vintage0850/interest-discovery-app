from __future__ import annotations

import datetime

import pytest

from discovery.aggregation import (
    _DIVE_CANDIDATE_MIN_CONFIDENCE,
    _DIVE_CANDIDATE_MIN_RATING,
    _DIVE_CANDIDATE_MIN_RESULT_COUNT,
    _DURATION_RATIO_HIGH,
    build_behavior_summary,
    build_behavior_summary_for_period,
)
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


class TestDiveCandidateDomains:
    """案件21: DIVE_CANDIDATE 判定ロジックのテスト。"""

    def _experiment(
        self,
        id: int,
        domain: str,
        status: str = ExperimentStatus.COMPLETED.value,
        planned_minutes: int = 10,
        actual_minutes: int | None = None,
    ) -> Experiment:
        return Experiment(
            id=id,
            session_id=1,
            title=f"exp-{id}",
            description="desc",
            domain=domain,
            planned_minutes=planned_minutes,
            status=status,
            actual_minutes=actual_minutes,
        )

    def _result(
        self,
        experiment_id: int,
        enjoyment: int = 4,
        curiosity: int = 4,
        retry_intent: int = 4,
        confidence: float = 0.70,
    ) -> ExperimentResult:
        return ExperimentResult(
            experiment_id=experiment_id,
            enjoyment=enjoyment,
            curiosity=curiosity,
            retry_intent=retry_intent,
            confidence=confidence,
        )

    def test_boundary_all_thresholds_exactly_becomes_candidate(self) -> None:
        """境界値: 2件の評価付き完了実験で全平均が閾値ちょうど、時間比率も1.5ちょうど。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
            self._experiment(2, domain, actual_minutes=15),
        ]
        results = [
            self._result(1),
            self._result(2),
        ]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == [domain]

    def test_single_completed_experiment_is_not_candidate(self) -> None:
        """否定条件: 完了実験が1件のみでは候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
        ]
        results = [self._result(1)]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_single_result_with_two_completions_is_not_candidate(self) -> None:
        """否定条件: 完了2件でも評価結果が1件のみでは候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
            self._experiment(2, domain, actual_minutes=15),
        ]
        results = [self._result(1)]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_low_enjoyment_is_not_candidate(self) -> None:
        """否定条件: enjoyment平均が4.0未満では候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
            self._experiment(2, domain, actual_minutes=15),
        ]
        results = [
            self._result(1, enjoyment=int(_DIVE_CANDIDATE_MIN_RATING)),
            self._result(2, enjoyment=int(_DIVE_CANDIDATE_MIN_RATING) - 1),
        ]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_low_curiosity_is_not_candidate(self) -> None:
        """否定条件: curiosity平均が4.0未満では候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
            self._experiment(2, domain, actual_minutes=15),
        ]
        results = [
            self._result(1, curiosity=int(_DIVE_CANDIDATE_MIN_RATING)),
            self._result(2, curiosity=int(_DIVE_CANDIDATE_MIN_RATING) - 1),
        ]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_low_retry_intent_is_not_candidate(self) -> None:
        """否定条件: retry_intent平均が4.0未満では候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
            self._experiment(2, domain, actual_minutes=15),
        ]
        results = [
            self._result(1, retry_intent=int(_DIVE_CANDIDATE_MIN_RATING)),
            self._result(2, retry_intent=int(_DIVE_CANDIDATE_MIN_RATING) - 1),
        ]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_low_confidence_is_not_candidate(self) -> None:
        """否定条件: confidence平均が0.70未満では候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=15),
            self._experiment(2, domain, actual_minutes=15),
        ]
        # 0.70未満の平均を作る: 0.69 と 0.70 -> 平均 0.695
        results = [
            self._result(1, confidence=_DIVE_CANDIDATE_MIN_CONFIDENCE),
            self._result(2, confidence=round(_DIVE_CANDIDATE_MIN_CONFIDENCE - 0.01, 2)),
        ]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_no_high_duration_ratio_is_not_candidate(self) -> None:
        """否定条件: 時間比率1.5以上の実験が1件もないと候補にならない。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=10),
            self._experiment(2, domain, actual_minutes=10),
        ]
        results = [self._result(1), self._result(2)]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == []

    def test_duration_ratio_very_high_counts(self) -> None:
        """時間比率2.0以上は1.5以上の条件を満たす。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, actual_minutes=20),
            self._experiment(2, domain, actual_minutes=10),
        ]
        results = [self._result(1), self._result(2)]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == [domain]
        assert 1 in summary.duration_ratio_high
        assert 1 in summary.duration_ratio_very_high

    def test_mixed_invalid_data_does_not_cause_false_positives_or_errors(self) -> None:
        """別ドメイン、未完了、スキップ、結果欠落、actual_minutes欠落が混在しても正しい。"""
        tech = DomainType.TECH.value
        art = DomainType.ART.value
        experiments = [
            # tech: 2件完了・評価付き、1件は時間比率不足
            self._experiment(1, tech, actual_minutes=15),
            self._experiment(2, tech, actual_minutes=10),
            # art: 評価付き完了1件、高評価・長時間だが件数不足
            self._experiment(3, art, actual_minutes=20),
            # 未完了・スキップ・結果欠落・actual_minutes欠落
            self._experiment(4, tech, status=ExperimentStatus.STARTED.value, actual_minutes=15),
            self._experiment(5, tech, status=ExperimentStatus.SKIPPED.value),
            self._experiment(6, tech, actual_minutes=None),
        ]
        results = [
            self._result(1),
            self._result(2),
            self._result(3),
        ]
        summary = build_behavior_summary([], experiments, results)
        # tech は 2件評価付き完了、平均閾値達成、時間比率1.5以上が1件ある
        assert summary.dive_candidate_domains == [tech]
        # duration_ratio_high は既存の意味を維持
        assert summary.duration_ratio_high == [1, 3]
        assert summary.duration_ratio_very_high == [3]

    def test_multiple_candidates_sorted_and_deduplicated(self) -> None:
        """複数候補は昇順・重複なし。"""
        art = DomainType.ART.value
        tech = DomainType.TECH.value
        experiments = [
            self._experiment(1, tech, actual_minutes=15),
            self._experiment(2, tech, actual_minutes=15),
            self._experiment(3, art, actual_minutes=15),
            self._experiment(4, art, actual_minutes=15),
        ]
        results = [self._result(i) for i in range(1, 5)]
        summary = build_behavior_summary([], experiments, results)
        assert summary.dive_candidate_domains == [art, tech]

    def test_existing_global_averages_unchanged(self) -> None:
        """グローバル平均は全評価付き完了実験から計算し、候補判定に依存しない。"""
        tech = DomainType.TECH.value
        art = DomainType.ART.value
        experiments = [
            self._experiment(1, tech, actual_minutes=15),
            self._experiment(2, tech, actual_minutes=15),
            self._experiment(3, art, actual_minutes=10),
        ]
        results = [
            self._result(1),
            self._result(2),
            self._result(3, enjoyment=3, curiosity=3, retry_intent=3, confidence=0.5),
        ]
        summary = build_behavior_summary([], experiments, results)
        # techのみ候補、artは低評価で非候補
        assert summary.dive_candidate_domains == [tech]
        # グローバル平均は全3件の平均
        assert summary.avg_enjoyment == (4 + 4 + 3) / 3
        assert summary.avg_curiosity == (4 + 4 + 3) / 3
        assert summary.avg_retry_intent == (4 + 4 + 3) / 3
        assert summary.avg_confidence == pytest.approx((0.70 + 0.70 + 0.5) / 3)

    def test_domain_counts_unaffected(self) -> None:
        """domain_experiment_counts / domain_completed_counts の意味・値は変わらない。"""
        tech = DomainType.TECH.value
        experiments = [
            self._experiment(1, tech, actual_minutes=15),
            self._experiment(2, tech, actual_minutes=15),
            self._experiment(3, tech, status=ExperimentStatus.GENERATED.value),
        ]
        results = [self._result(1), self._result(2)]
        summary = build_behavior_summary([], experiments, results)
        assert summary.domain_experiment_counts == {tech: 3}
        assert summary.domain_completed_counts == {tech: 2}
        assert summary.dive_candidate_domains == [tech]
