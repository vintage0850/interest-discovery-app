from __future__ import annotations

import datetime

import pytest
from sqlalchemy.pool import StaticPool
from sqlmodel import Session, SQLModel, create_engine

from discovery.models import (
    ActionType,
    DomainType,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    HypothesisReaction,
    InterestSignal,
    InterestSignalSource,
)
from discovery.repository import DiscoveryRepository, StateTransitionError


@pytest.fixture
def repository() -> DiscoveryRepository:
    engine = create_engine(
        "sqlite:///:memory:?cache=shared",
        echo=False,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    return DiscoveryRepository(engine)


class TestSessionRepository:
    def test_create_session(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        assert session.id is not None
        assert session.student_label == "student-a"
        assert session.status == "active"

    def test_get_session(self, repository: DiscoveryRepository) -> None:
        created = repository.create_session("student-a")
        fetched = repository.get_session(created.id)
        assert fetched is not None
        assert fetched.id == created.id

    def test_get_session_not_found(self, repository: DiscoveryRepository) -> None:
        assert repository.get_session(999) is None


class TestSignalRepository:
    def test_add_signal(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        signal = repository.add_signal(
            session.id,
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="Python tutorial",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        assert signal.id is not None
        assert signal.session_id == session.id
        assert signal.domain == DomainType.TECH.value

    def test_list_signals(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.add_signal(
            session.id,
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="Python tutorial",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        signals = repository.list_signals(session.id)
        assert len(signals) == 1
        assert signals[0].domain == DomainType.TECH.value


class TestExperimentRepository:
    def test_create_experiment(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        assert experiment.id is not None
        assert experiment.status == ExperimentStatus.GENERATED.value

    def test_get_experiment(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        created = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        fetched = repository.get_experiment(created.id)
        assert fetched is not None
        assert fetched.id == created.id

    def test_select_experiment(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        updated = repository.select_experiment(experiment.id, "I want to try this")
        assert updated.status == ExperimentStatus.SELECTED.value
        assert updated.selected_at is not None

    def test_skip_experiment(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        updated = repository.skip_experiment(experiment.id, "Not now")
        assert updated.status == ExperimentStatus.SKIPPED.value
        assert updated.skipped_at is not None

    def test_start_experiment(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "I want to try this")
        updated = repository.start_experiment(experiment.id)
        assert updated.status == ExperimentStatus.STARTED.value
        assert updated.started_at is not None

    def test_complete_experiment(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.start_experiment(experiment.id)
        result = repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
            reflection="Fun",
        )
        assert result.experiment.status == ExperimentStatus.COMPLETED.value
        assert result.enjoyment == 4
        assert result.experiment.result is not None
        assert result.experiment.actual_minutes is not None

    def test_complete_nonexistent_experiment(self, repository: DiscoveryRepository) -> None:
        with pytest.raises(ValueError):
            repository.complete_experiment(
                999,
                enjoyment=4,
                curiosity=5,
                retry_intent=3,
                confidence=0.8,
            )

    def test_double_select_is_idempotent(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.select_experiment(experiment.id, "note")
        signals = repository.list_signals(session.id)
        assert len([s for s in signals if s.action_type == ActionType.EXPERIMENT_SELECTED.value]) == 1

    def test_double_start_is_idempotent(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.start_experiment(experiment.id)
        repository.start_experiment(experiment.id)
        signals = repository.list_signals(session.id)
        assert len([s for s in signals if s.action_type == ActionType.EXPERIMENT_STARTED.value]) == 1

    def test_double_complete_is_idempotent(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.start_experiment(experiment.id)
        result1 = repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
        )
        result2 = repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
        )
        assert result1.id == result2.id
        signals = repository.list_signals(session.id)
        assert len([s for s in signals if s.action_type == ActionType.EXPERIMENT_COMPLETED.value]) == 1
        assert len([s for s in signals if s.action_type == ActionType.EXPLICIT_FEEDBACK.value]) == 1

    def test_double_skip_is_idempotent(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.skip_experiment(experiment.id, "Not now")
        repository.skip_experiment(experiment.id, "Not now")
        signals = repository.list_signals(session.id)
        assert len([s for s in signals if s.action_type == ActionType.EXPERIMENT_SKIPPED.value]) == 1

    def test_select_after_skip_is_rejected(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.skip_experiment(experiment.id, "Not now")
        with pytest.raises(StateTransitionError):
            repository.select_experiment(experiment.id, "note")
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.skip_experiment(experiment.id, "Not now")
        with pytest.raises(StateTransitionError):
            repository.start_experiment(experiment.id)

    def test_skip_after_complete_is_rejected(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.start_experiment(experiment.id)
        repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
        )
        with pytest.raises(StateTransitionError):
            repository.skip_experiment(experiment.id, "too late")

    def test_start_from_generated_is_rejected(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        with pytest.raises(StateTransitionError):
            repository.start_experiment(experiment.id)

    def test_skip_after_start_is_rejected(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.start_experiment(experiment.id)
        with pytest.raises(StateTransitionError):
            repository.skip_experiment(experiment.id, "changed mind")


class TestHypothesisRepository:
    def test_create_hypothesis(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes tech",
            confidence=0.7,
            supporting_evidence=[{"domain": "tech", "count": 3}],
            suggested_next_domains=["art", "music"],
        )
        assert hypothesis.id is not None
        assert hypothesis.summary == "Likes tech"

    def test_get_latest_hypothesis(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.create_hypothesis(
            session.id,
            summary="Old",
            confidence=0.5,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        latest = repository.create_hypothesis(
            session.id,
            summary="New",
            confidence=0.7,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        fetched = repository.get_latest_hypothesis(session.id)
        assert fetched is not None
        assert fetched.id == latest.id

    def test_get_latest_hypothesis_none(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        assert repository.get_latest_hypothesis(session.id) is None


class TestHypothesisFeedbackRepository:
    def test_agree_increases_confidence(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.4,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.AGREE
        )
        assert updated.confidence == pytest.approx(0.55)
        assert criterion is None  # まだ昇格閾値(0.6)未満

    def test_disagree_decreases_confidence(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.4,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.DISAGREE
        )
        assert updated.confidence == pytest.approx(0.25)
        assert criterion is None

    def test_unsure_keeps_confidence_unchanged(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.4,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.UNSURE
        )
        assert updated.confidence == pytest.approx(0.4)
        assert criterion is None

    def test_confidence_is_clamped_to_one(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.95,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, _ = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.AGREE
        )
        assert updated.confidence == pytest.approx(1.0)

    def test_confidence_is_clamped_to_zero(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.05,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, _ = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.DISAGREE
        )
        assert updated.confidence == pytest.approx(0.0)

    def test_agree_past_threshold_promotes_criterion(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.5,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.AGREE
        )
        assert updated.confidence == pytest.approx(0.65)
        assert criterion is not None
        assert criterion.session_id == session.id
        assert criterion.source_hypothesis_id == hypothesis.id
        assert criterion.user_confirmed is True
        assert criterion.confidence == pytest.approx(0.65)

    def test_agree_at_exact_threshold_promotes_criterion(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.45,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.AGREE
        )
        assert updated.confidence == pytest.approx(0.6)
        assert criterion is not None
        assert criterion.confidence == pytest.approx(0.6)

    def test_agree_just_below_threshold_does_not_promote_criterion(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.44,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        _, updated, criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.AGREE
        )
        assert updated.confidence == pytest.approx(0.59)
        assert criterion is None

    def test_repeated_agree_updates_existing_criterion_not_duplicate(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes comparing options",
            confidence=0.55,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        repository.add_hypothesis_feedback(hypothesis.id, HypothesisReaction.AGREE)
        _, _, second_criterion = repository.add_hypothesis_feedback(
            hypothesis.id, HypothesisReaction.AGREE
        )
        criteria = repository.list_criteria(session.id)
        assert len(criteria) == 1
        assert second_criterion is not None
        assert second_criterion.id == criteria[0].id

    def test_feedback_for_nonexistent_hypothesis_raises(
        self, repository: DiscoveryRepository
    ) -> None:
        with pytest.raises(ValueError):
            repository.add_hypothesis_feedback(999, HypothesisReaction.AGREE)


class TestCriterionRepository:
    def test_list_criteria_empty(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        assert repository.list_criteria(session.id) == []

    def test_list_criteria_ordered_by_confidence_desc(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        low = repository.create_hypothesis(
            session.id, summary="Low", confidence=0.5,
            supporting_evidence=[], suggested_next_domains=[],
        )
        high = repository.create_hypothesis(
            session.id, summary="High", confidence=0.7,
            supporting_evidence=[], suggested_next_domains=[],
        )
        repository.add_hypothesis_feedback(low.id, HypothesisReaction.AGREE)
        repository.add_hypothesis_feedback(high.id, HypothesisReaction.AGREE)
        criteria = repository.list_criteria(session.id)
        assert len(criteria) == 2
        assert criteria[0].label == "High"
        assert criteria[1].label == "Low"


class TestSummaryRepository:
    def test_get_summary_data(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.add_signal(
            session.id,
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="Python",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "note")
        repository.start_experiment(experiment.id)
        repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
        )
        data = repository.get_summary_data(session.id)
        # 手動シグナル1件 + select/start/complete の自動シグナル4件
        assert len(data["signals"]) == 5
        assert len(data["experiments"]) == 1
        assert len(data["results"]) == 1
