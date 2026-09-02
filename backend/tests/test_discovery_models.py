from __future__ import annotations

import pytest
from pydantic import ValidationError

from discovery.models import (
    ActionType,
    DiscoverySession,
    DomainType,
    Experiment,
    ExperimentResultCreate,
    ExperimentSelectRequest,
    InterestSignalCreate,
    InterestSignalSource,
)


class TestSessionValidation:
    def test_session_can_be_created_with_defaults(self) -> None:
        session = DiscoverySession(student_label="student-a")
        assert session.student_label == "student-a"
        assert session.status == "active"

    def test_session_rejects_empty_label(self) -> None:
        with pytest.raises(ValueError):
            DiscoverySession(student_label="")

    def test_session_rejects_whitespace_label(self) -> None:
        with pytest.raises(ValueError):
            DiscoverySession(student_label="   ")

    def test_session_rejects_too_long_label(self) -> None:
        with pytest.raises(ValueError):
            DiscoverySession(student_label="a" * 101)


class TestInterestSignalValidation:
    def test_valid_signal_passes(self) -> None:
        signal = InterestSignalCreate(
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="Python tutorial",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at="2026-09-01T10:00:00Z",
        )
        assert signal.action_type == ActionType.SEARCH
        assert signal.domain == DomainType.TECH

    def test_invalid_action_type_rejected(self) -> None:
        with pytest.raises(ValidationError):
            InterestSignalCreate(
                action_type="invalid_action",
                domain=DomainType.TECH,
                content_summary="summary",
                source=InterestSignalSource.SEARCH_HISTORY,
                occurred_at="2026-09-01T10:00:00Z",
            )

    def test_invalid_domain_rejected(self) -> None:
        with pytest.raises(ValidationError):
            InterestSignalCreate(
                action_type=ActionType.SEARCH,
                domain="invalid_domain",
                content_summary="summary",
                source=InterestSignalSource.SEARCH_HISTORY,
                occurred_at="2026-09-01T10:00:00Z",
            )

    def test_naive_occurred_at_rejected(self) -> None:
        with pytest.raises(ValidationError):
            InterestSignalCreate(
                action_type=ActionType.SEARCH,
                domain=DomainType.TECH,
                content_summary="summary",
                source=InterestSignalSource.SEARCH_HISTORY,
                occurred_at="2026-09-01T10:00:00",
            )

    def test_occurred_at_with_offset_normalized_to_utc(self) -> None:
        signal = InterestSignalCreate(
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="summary",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at="2026-09-01T10:00:00+09:00",
        )
        assert signal.occurred_at.endswith("+00:00") or signal.occurred_at.endswith("Z")

    def test_occurred_at_z_and_offset_are_same_utc(self) -> None:
        z = InterestSignalCreate(
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="summary",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at="2026-09-01T01:00:00Z",
        )
        offset = InterestSignalCreate(
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="summary",
            source=InterestSignalSource.SEARCH_HISTORY,
            occurred_at="2026-09-01T10:00:00+09:00",
        )
        assert z.occurred_at == offset.occurred_at


class TestExperimentValidation:
    def test_valid_experiment_passes(self) -> None:
        experiment = Experiment(
            session_id=1,
            title="Try coding for 10 minutes",
            description="Write a small Python script",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        assert experiment.planned_minutes == 10
        assert experiment.status == "generated"

    def test_planned_minutes_below_5_rejected(self) -> None:
        with pytest.raises(ValueError):
            Experiment(
                session_id=1,
                title="Too short",
                description="desc",
                domain=DomainType.TECH,
                planned_minutes=3,
            )

    def test_planned_minutes_above_15_rejected(self) -> None:
        with pytest.raises(ValueError):
            Experiment(
                session_id=1,
                title="Too long",
                description="desc",
                domain=DomainType.TECH,
                planned_minutes=20,
            )


class TestExperimentResultValidation:
    def test_valid_result_passes(self) -> None:
        result = ExperimentResultCreate(
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
            reflection="It was fun",
        )
        assert result.enjoyment == 4

    def test_enjoyment_below_1_rejected(self) -> None:
        with pytest.raises(ValidationError):
            ExperimentResultCreate(
                enjoyment=0,
                curiosity=3,
                retry_intent=3,
                confidence=0.5,
            )

    def test_enjoyment_above_5_rejected(self) -> None:
        with pytest.raises(ValidationError):
            ExperimentResultCreate(
                enjoyment=6,
                curiosity=3,
                retry_intent=3,
                confidence=0.5,
            )

    def test_retry_intent_out_of_range_rejected(self) -> None:
        with pytest.raises(ValidationError):
            ExperimentResultCreate(
                enjoyment=3,
                curiosity=3,
                retry_intent=0,
                confidence=0.5,
            )

    def test_confidence_above_1_rejected(self) -> None:
        with pytest.raises(ValidationError):
            ExperimentResultCreate(
                enjoyment=3,
                curiosity=3,
                retry_intent=3,
                confidence=1.1,
            )

    def test_confidence_below_0_rejected(self) -> None:
        with pytest.raises(ValidationError):
            ExperimentResultCreate(
                enjoyment=3,
                curiosity=3,
                retry_intent=3,
                confidence=-0.1,
            )


class TestExperimentSelectRequest:
    def test_select_request_requires_non_empty_note(self) -> None:
        with pytest.raises(ValidationError):
            ExperimentSelectRequest(selection_note="")

    def test_select_request_with_valid_note_passes(self) -> None:
        req = ExperimentSelectRequest(selection_note="I want to try this")
        assert req.selection_note == "I want to try this"
