from __future__ import annotations

import datetime

import pytest
from sqlalchemy.pool import StaticPool
from sqlmodel import Session, SQLModel, create_engine

from discovery.models import (
    ActionType,
    DiscoverySession,
    DomainType,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    HypothesisReaction,
    InterestSignal,
    InterestSignalSource,
    PsychAxis,
    PsychAxisResult,
    UserReflection,
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
            supporting_evidence=[1, 2],
            suggested_next_domains=["art", "music"],
        )
        assert hypothesis.id is not None
        assert hypothesis.summary == "Likes tech"
        assert hypothesis.supporting_evidence == [1, 2]

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


class TestOnboardingRepository:
    def test_update_onboarding_info_sets_all_fields(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        updated = repository.update_onboarding_info(
            session.id,
            nickname="Taro",
            age_range="teen",
            school_stage="middle_school",
            optional_interests=["tech", "art"],
            initial_self_understanding_score=3.5,
        )
        assert updated.nickname == "Taro"
        assert updated.age_range == "teen"
        assert updated.school_stage == "middle_school"
        assert updated.optional_interests == '["tech", "art"]'
        assert updated.initial_self_understanding_score == pytest.approx(3.5)

    def test_optional_interests_serialized_to_json(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.update_onboarding_info(
            session.id,
            optional_interests=["music", "sports"],
        )
        fetched = repository.get_session(session.id)
        assert fetched is not None
        assert fetched.optional_interests == '["music", "sports"]'

    def test_partial_update_preserves_existing_values(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.update_onboarding_info(
            session.id,
            nickname="Taro",
            optional_interests=["tech"],
        )
        repository.update_onboarding_info(
            session.id,
            age_range="teen",
        )
        fetched = repository.get_session(session.id)
        assert fetched is not None
        assert fetched.nickname == "Taro"
        assert fetched.age_range == "teen"
        assert fetched.optional_interests == '["tech"]'

    def test_none_fields_are_not_updated(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.update_onboarding_info(
            session.id,
            nickname="Taro",
            initial_self_understanding_score=4.0,
        )
        repository.update_onboarding_info(
            session.id,
            age_range="teen",
        )
        fetched = repository.get_session(session.id)
        assert fetched is not None
        assert fetched.nickname == "Taro"
        assert fetched.age_range == "teen"
        assert fetched.initial_self_understanding_score == pytest.approx(4.0)

    def test_update_onboarding_info_not_found(self, repository: DiscoveryRepository) -> None:
        with pytest.raises(ValueError):
            repository.update_onboarding_info(
                999,
                nickname="Taro",
            )


class TestSessionListRepository:
    def test_list_sessions_orders_by_updated_at_desc(self, repository: DiscoveryRepository) -> None:
        older = repository.create_session("student-a")
        newer = repository.create_session("student-a")
        # updated_at を変えるため、新しい方だけオンボーディングを更新する
        repository.update_onboarding_info(newer.id, nickname="Newer")
        sessions = repository.list_sessions("student-a")
        assert len(sessions) == 2
        assert sessions[0].id == newer.id
        assert sessions[1].id == older.id

    def test_list_sessions_filters_by_student_label(self, repository: DiscoveryRepository) -> None:
        repository.create_session("student-a")
        repository.create_session("student-b")
        sessions = repository.list_sessions("student-a")
        assert len(sessions) == 1
        assert sessions[0].student_label == "student-a"


class TestSessionUpdatedAt:
    def test_add_signal_updates_session_updated_at(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        before = session.updated_at
        repository.add_signal(
            session.id,
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="Python",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        after = repository.get_session(session.id)
        assert after is not None
        assert after.updated_at > before

    def test_create_experiment_updates_session_updated_at(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        before = session.updated_at
        repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        after = repository.get_session(session.id)
        assert after is not None
        assert after.updated_at > before

    def test_complete_experiment_updates_session_updated_at(self, repository: DiscoveryRepository) -> None:
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
        before = repository.get_session(session.id).updated_at
        repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
        )
        after = repository.get_session(session.id)
        assert after is not None
        assert after.updated_at > before

    def test_add_hypothesis_feedback_updates_session_updated_at(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        hypothesis = repository.create_hypothesis(
            session.id,
            summary="Likes tech",
            confidence=0.5,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        before = repository.get_session(session.id).updated_at
        repository.add_hypothesis_feedback(hypothesis.id, HypothesisReaction.AGREE)
        after = repository.get_session(session.id)
        assert after is not None
        assert after.updated_at > before

    def test_upsert_psych_axis_results_updates_session_updated_at(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        before = session.updated_at
        repository.upsert_psych_axis_results(
            session.id,
            {
                PsychAxis.INVESTIGATE.value: 3.0,
                PsychAxis.CREATE.value: 4.0,
                PsychAxis.EXECUTE.value: 2.0,
                PsychAxis.COMMUNICATE.value: 5.0,
            },
        )
        after = repository.get_session(session.id)
        assert after is not None
        assert after.updated_at > before

    def test_create_reflection_updates_session_updated_at(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        before = session.updated_at
        repository.create_user_reflection(session.id, "Today I felt curious.", mood=4)
        after = repository.get_session(session.id)
        assert after is not None
        assert after.updated_at > before


class TestPsychAxisRepository:
    def test_upsert_psych_axis_results_creates_records(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        results = repository.upsert_psych_axis_results(
            session.id,
            {
                PsychAxis.INVESTIGATE.value: 3.0,
                PsychAxis.CREATE.value: 4.0,
                PsychAxis.EXECUTE.value: 2.0,
                PsychAxis.COMMUNICATE.value: 5.0,
            },
        )
        assert len(results) == 4
        assert all(r.session_id == session.id for r in results)
        assert {r.axis for r in results} == {axis.value for axis in PsychAxis}

    def test_upsert_overwrites_existing_axis(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.upsert_psych_axis_results(
            session.id,
            {PsychAxis.INVESTIGATE.value: 3.0},
        )
        results = repository.upsert_psych_axis_results(
            session.id,
            {PsychAxis.INVESTIGATE.value: 4.5},
        )
        assert len(results) == 1
        assert results[0].score == pytest.approx(4.5)

    def test_get_psych_axis_results(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        repository.upsert_psych_axis_results(
            session.id,
            {
                PsychAxis.INVESTIGATE.value: 3.0,
                PsychAxis.CREATE.value: 4.0,
            },
        )
        fetched = repository.get_psych_axis_results(session.id)
        assert len(fetched) == 2
        assert fetched[0].axis in {PsychAxis.INVESTIGATE.value, PsychAxis.CREATE.value}

    def test_get_psych_axis_results_empty(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        assert repository.get_psych_axis_results(session.id) == []

    def test_invalid_axis_rejected(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        with pytest.raises(ValueError):
            repository.upsert_psych_axis_results(session.id, {"UNKNOWN": 3.0})

    def test_invalid_score_rejected(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        with pytest.raises(ValueError):
            repository.upsert_psych_axis_results(
                session.id,
                {PsychAxis.INVESTIGATE.value: 6.0},
            )


class TestUserReflectionRepository:
    def test_create_reflection(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        reflection = repository.create_user_reflection(
            session.id, "Today I felt curious.", mood=4
        )
        assert reflection.id is not None
        assert reflection.session_id == session.id
        assert reflection.content == "Today I felt curious."
        assert reflection.mood == 4

    def test_create_reflection_without_mood(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        reflection = repository.create_user_reflection(session.id, "Just a note.")
        assert reflection.mood is None

    def test_list_reflections_orders_by_created_at_desc(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        first = repository.create_user_reflection(session.id, "First")
        second = repository.create_user_reflection(session.id, "Second")
        reflections = repository.list_user_reflections(session.id)
        assert len(reflections) == 2
        assert reflections[0].id == second.id
        assert reflections[1].id == first.id

    def test_create_reflection_rejects_empty_content(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        with pytest.raises(ValueError):
            repository.create_user_reflection(session.id, "")

    def test_create_reflection_rejects_too_long_content(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        with pytest.raises(ValueError):
            repository.create_user_reflection(session.id, "x" * 2001)


class TestSessionCascadeDeletionRepository:
    def test_delete_session_cascade_removes_all_related_rows(
        self, repository: DiscoveryRepository
    ) -> None:
        import datetime
        from discovery.models import ActionType, DomainType, InterestSignalSource

        session = repository.create_session("student-a")
        session_id = session.id

        # InterestSignal
        repository.add_signal(
            session_id,
            action_type=ActionType.SEARCH.value,
            domain=DomainType.TECH,
            content_summary="Python tutorial",
            source=InterestSignalSource.SEARCH_HISTORY.value,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )

        # Evidence
        repository.build_evidence(session_id)

        # Experiment + ExperimentResult
        experiment = repository.create_experiment(
            session_id,
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

        # InterestHypothesis + HypothesisFeedback + Criterion
        hypothesis = repository.create_hypothesis(
            session_id,
            summary="Likes tech",
            confidence=0.7,
            supporting_evidence=[],
            suggested_next_domains=[],
        )
        repository.add_hypothesis_feedback(hypothesis.id, HypothesisReaction.AGREE)

        # PsychAxisResult
        repository.upsert_psych_axis_results(
            session_id,
            {
                PsychAxis.INVESTIGATE.value: 3.0,
                PsychAxis.CREATE.value: 4.0,
                PsychAxis.EXECUTE.value: 2.0,
                PsychAxis.COMMUNICATE.value: 5.0,
            },
        )

        # UserReflection
        repository.create_user_reflection(session_id, "Today I felt curious.", mood=4)

        # WeeklyNarrativeCache
        repository.save_weekly_narrative_cache(
            session_id,
            cache_date="2026-09-08",
            weekly_insights="Insights",
            change_from_past="Changes",
        )

        assert repository.delete_session_cascade(session_id) is True

        # All related rows should be gone
        from sqlmodel import Session as DBSession

        with DBSession(repository._engine) as db:
            assert db.get(DiscoverySession, session_id) is None
            assert repository.list_signals(session_id) == []
            assert repository.list_evidence(session_id) == []
            assert repository.list_experiments(session_id) == []
            assert repository.get_summary_data(session_id)["results"] == []
            assert repository.get_latest_hypothesis(session_id) is None
            assert repository.list_criteria(session_id) == []
            assert repository.get_psych_axis_results(session_id) == []
            assert repository.list_user_reflections(session_id) == []
            assert repository.get_weekly_narrative_cache(session_id, "2026-09-08") is None

    def test_delete_session_cascade_returns_false_for_missing_session(
        self, repository: DiscoveryRepository
    ) -> None:
        assert repository.delete_session_cascade(99999) is False

    def test_delete_session_cascade_does_not_affect_other_sessions(
        self, repository: DiscoveryRepository
    ) -> None:
        import datetime
        from discovery.models import ActionType, DomainType, InterestSignalSource

        session_a = repository.create_session("student-a")
        session_b = repository.create_session("student-b")

        repository.add_signal(
            session_a.id,
            action_type=ActionType.SEARCH.value,
            domain=DomainType.TECH,
            content_summary="Python tutorial",
            source=InterestSignalSource.SEARCH_HISTORY.value,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        repository.add_signal(
            session_b.id,
            action_type=ActionType.SEARCH.value,
            domain=DomainType.ART,
            content_summary="Art tutorial",
            source=InterestSignalSource.SEARCH_HISTORY.value,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )

        assert repository.delete_session_cascade(session_a.id) is True
        assert repository.get_session(session_a.id) is None
        assert repository.get_session(session_b.id) is not None
        assert len(repository.list_signals(session_b.id)) == 1


class TestEvidenceRepository:
    def test_build_evidence_creates_records_by_domain(
        self, repository: DiscoveryRepository
    ) -> None:
        import datetime
        from discovery.models import ActionType, DomainType, InterestSignalSource

        session = repository.create_session("student-a")
        now = datetime.datetime.now(datetime.timezone.utc)
        repository.add_signal(
            session_id=session.id,
            action_type=ActionType.SEARCH.value,
            domain=DomainType.TECH,
            content_summary="Python入門",
            source=InterestSignalSource.SEARCH_HISTORY.value,
            occurred_at=now,
        )
        repository.add_signal(
            session_id=session.id,
            action_type=ActionType.VIEW.value,
            domain=DomainType.TECH,
            content_summary="FastAPIガイド",
            source=InterestSignalSource.BROWSING_HISTORY.value,
            occurred_at=now,
        )
        repository.add_signal(
            session_id=session.id,
            action_type=ActionType.SEARCH.value,
            domain=DomainType.ART,
            content_summary="デッサン入門",
            source=InterestSignalSource.SEARCH_HISTORY.value,
            occurred_at=now,
        )

        evidences = repository.build_evidence(session.id)
        assert len(evidences) == 2

        evidence_by_domain = {e.domain: e for e in evidences}
        assert "tech" in evidence_by_domain
        assert "art" in evidence_by_domain

        tech_ev = evidence_by_domain["tech"]
        assert tech_ev.session_id == session.id
        assert tech_ev.signal_count == 2
        assert "Python入門" in tech_ev.summary_text or "tech" in tech_ev.summary_text
        assert tech_ev.id is not None
        assert tech_ev.created_at is not None

        art_ev = evidence_by_domain["art"]
        assert art_ev.session_id == session.id
        assert art_ev.signal_count == 1
        assert art_ev.id is not None

    def test_build_evidence_no_signals_returns_empty(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        evidences = repository.build_evidence(session.id)
        assert evidences == []

    def test_build_evidence_nonexistent_session_raises(
        self, repository: DiscoveryRepository
    ) -> None:
        with pytest.raises(ValueError):
            repository.build_evidence(99999)

    def test_list_evidence_orders_by_created_at_desc(
        self, repository: DiscoveryRepository
    ) -> None:
        import datetime
        from discovery.models import ActionType, DomainType, InterestSignalSource

        session = repository.create_session("student-a")
        now = datetime.datetime.now(datetime.timezone.utc)
        repository.add_signal(
            session_id=session.id,
            action_type=ActionType.SEARCH.value,
            domain=DomainType.TECH,
            content_summary="Python入門",
            source=InterestSignalSource.SEARCH_HISTORY.value,
            occurred_at=now,
        )
        repository.build_evidence(session.id)

        evidence_list = repository.list_evidence(session.id)
        assert len(evidence_list) >= 1
        assert evidence_list[0].session_id == session.id

    def test_list_evidence_returns_empty_when_none_exist(
        self, repository: DiscoveryRepository
    ) -> None:
        session = repository.create_session("student-a")
        assert repository.list_evidence(session.id) == []

