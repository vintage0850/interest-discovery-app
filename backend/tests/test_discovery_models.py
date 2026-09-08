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
    MonthlyNarrativeResponse,
    OnboardingUpdateRequest,
    PsychAxis,
    PsychAxisSurveySubmitRequest,
    UserReflectionCreate,
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


class TestOnboardingUpdateRequest:
    def test_all_fields_optional(self) -> None:
        req = OnboardingUpdateRequest()
        assert req.nickname is None
        assert req.age_range is None
        assert req.school_stage is None
        assert req.optional_interests is None
        assert req.initial_self_understanding_score is None

    def test_valid_full_request_passes(self) -> None:
        req = OnboardingUpdateRequest(
            nickname="Taro",
            age_range="teen",
            school_stage="middle_school",
            optional_interests=["tech", "art"],
            initial_self_understanding_score=3.5,
        )
        assert req.nickname == "Taro"
        assert req.optional_interests == ["tech", "art"]
        assert req.initial_self_understanding_score == pytest.approx(3.5)

    def test_score_below_range_rejected(self) -> None:
        with pytest.raises(ValidationError):
            OnboardingUpdateRequest(initial_self_understanding_score=-0.1)

    def test_score_above_range_rejected(self) -> None:
        with pytest.raises(ValidationError):
            OnboardingUpdateRequest(initial_self_understanding_score=5.1)

    def test_score_at_lower_boundary_passes(self) -> None:
        req = OnboardingUpdateRequest(initial_self_understanding_score=0.0)
        assert req.initial_self_understanding_score == pytest.approx(0.0)

    def test_score_at_upper_boundary_passes(self) -> None:
        req = OnboardingUpdateRequest(initial_self_understanding_score=5.0)
        assert req.initial_self_understanding_score == pytest.approx(5.0)


class TestPsychAxisSurveySubmitRequest:
    def _valid_scores(self) -> dict[str, float]:
        return {
            PsychAxis.INVESTIGATE.value: 3.0,
            PsychAxis.CREATE.value: 4.0,
            PsychAxis.EXECUTE.value: 2.0,
            PsychAxis.COMMUNICATE.value: 5.0,
        }

    def test_valid_survey_passes(self) -> None:
        req = PsychAxisSurveySubmitRequest(scores=self._valid_scores())
        assert req.scores == self._valid_scores()

    def test_missing_axis_rejected(self) -> None:
        scores = dict(self._valid_scores())
        del scores[PsychAxis.CREATE.value]
        with pytest.raises(ValidationError):
            PsychAxisSurveySubmitRequest(scores=scores)

    def test_unknown_axis_rejected(self) -> None:
        scores = dict(self._valid_scores())
        scores["UNKNOWN"] = 3.0
        with pytest.raises(ValidationError):
            PsychAxisSurveySubmitRequest(scores=scores)

    def test_score_below_1_rejected(self) -> None:
        scores = dict(self._valid_scores())
        scores[PsychAxis.INVESTIGATE.value] = 0.5
        with pytest.raises(ValidationError):
            PsychAxisSurveySubmitRequest(scores=scores)

    def test_score_above_5_rejected(self) -> None:
        scores = dict(self._valid_scores())
        scores[PsychAxis.INVESTIGATE.value] = 5.5
        with pytest.raises(ValidationError):
            PsychAxisSurveySubmitRequest(scores=scores)

    def test_non_numeric_score_rejected(self) -> None:
        scores = dict(self._valid_scores())
        scores[PsychAxis.INVESTIGATE.value] = "high"  # type: ignore[assignment]
        with pytest.raises(ValidationError):
            PsychAxisSurveySubmitRequest(scores=scores)


class TestUserReflectionCreate:
    def test_valid_reflection_passes(self) -> None:
        req = UserReflectionCreate(content="Today I felt curious.", mood=4)
        assert req.content == "Today I felt curious."
        assert req.mood == 4

    def test_reflection_without_mood_passes(self) -> None:
        req = UserReflectionCreate(content="Just a note.")
        assert req.content == "Just a note."
        assert req.mood is None

    def test_empty_content_rejected(self) -> None:
        with pytest.raises(ValidationError):
            UserReflectionCreate(content="")

    def test_content_too_long_rejected(self) -> None:
        with pytest.raises(ValidationError):
            UserReflectionCreate(content="x" * 2001)

    def test_mood_below_1_rejected(self) -> None:
        with pytest.raises(ValidationError):
            UserReflectionCreate(content="note", mood=0)

    def test_mood_above_5_rejected(self) -> None:
        with pytest.raises(ValidationError):
            UserReflectionCreate(content="note", mood=6)


class TestEvidenceValidation:
    def test_valid_evidence_passes(self) -> None:
        from discovery.models import Evidence, EvidenceResponse

        evidence = Evidence(
            session_id=1,
            domain=DomainType.TECH.value,
            signal_count=3,
            summary_text="techに関するシグナル3件",
        )
        assert evidence.session_id == 1
        assert evidence.domain == DomainType.TECH.value
        assert evidence.signal_count == 3
        assert evidence.summary_text == "techに関するシグナル3件"

    def test_signal_count_below_1_rejected(self) -> None:
        from discovery.models import Evidence

        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=0,
                summary_text="tech summary",
            )

    def test_invalid_domain_rejected(self) -> None:
        from discovery.models import Evidence

        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain="unknown_domain",
                signal_count=1,
                summary_text="tech summary",
            )

    def test_empty_summary_text_rejected(self) -> None:
        from discovery.models import Evidence

        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=1,
                summary_text="",
            )

    def test_whitespace_summary_text_rejected(self) -> None:
        from discovery.models import Evidence

        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=1,
                summary_text="   ",
            )

    def test_too_long_summary_text_rejected(self) -> None:
        from discovery.models import Evidence

        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=1,
                summary_text="a" * 1001,
            )

    def test_evidence_response_schema(self) -> None:
        import datetime
        from discovery.models import EvidenceResponse

        now = datetime.datetime.now(datetime.timezone.utc)
        resp = EvidenceResponse(
            id=10,
            session_id=1,
            domain="tech",
            signal_count=2,
            summary_text="tech summary",
            created_at=now,
        )
        assert resp.id == 10
        assert resp.session_id == 1
        assert resp.domain == "tech"
        assert resp.signal_count == 2
        assert resp.summary_text == "tech summary"


class TestHypothesisResponseValidation:
    def test_supporting_evidence_accepts_int_list(self) -> None:
        import datetime
        from discovery.models import HypothesisResponse

        now = datetime.datetime.now(datetime.timezone.utc)
        resp = HypothesisResponse(
            id=1,
            session_id=1,
            summary="Likes tech",
            confidence=0.8,
            supporting_evidence=[1, 2, 3],
            suggested_next_domains=["art"],
            created_at=now,
        )
        assert resp.supporting_evidence == [1, 2, 3]

    def test_supporting_evidence_rejects_dict_list(self) -> None:
        import datetime
        from discovery.models import HypothesisResponse

        now = datetime.datetime.now(datetime.timezone.utc)
        with pytest.raises(ValidationError):
            HypothesisResponse(
                id=1,
                session_id=1,
                summary="Likes tech",
                confidence=0.8,
                supporting_evidence=[{"domain": "tech"}],  # type: ignore[arg-type]
                suggested_next_domains=["art"],
                created_at=now,
            )


class TestMonthlyNarrativeResponseValidation:
    def test_valid_monthly_narrative_response_passes(self) -> None:
        response = MonthlyNarrativeResponse(
            period_start="2026-08-02",
            period_end_exclusive="2026-09-01",
            monthly_insights="直近30日間の気づきです",
            progress_wave="進み方の波の分析です",
            continuity_insight="継続率に関する分析です",
        )
        assert response.period_start == "2026-08-02"
        assert response.period_end_exclusive == "2026-09-01"
        assert response.monthly_insights == "直近30日間の気づきです"

    def test_monthly_narrative_response_rejects_empty_insights(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="",
                progress_wave="進み方の波",
                continuity_insight="継続率",
            )

    def test_monthly_narrative_response_rejects_whitespace_only_insights(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="   ",
                progress_wave="進み方の波",
                continuity_insight="継続率",
            )

    def test_monthly_narrative_response_rejects_too_long_insights(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="あ" * 201,
                progress_wave="進み方の波",
                continuity_insight="継続率",
            )

    def test_monthly_narrative_response_rejects_too_long_progress_wave(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="直近30日間の気づき",
                progress_wave="あ" * 201,
                continuity_insight="継続率",
            )

    def test_monthly_narrative_response_rejects_too_long_continuity_insight(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="直近30日間の気づき",
                progress_wave="進み方の波",
                continuity_insight="あ" * 201,
            )

    def test_monthly_narrative_response_rejects_newlines(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="1行目\n2行目",
                progress_wave="進み方の波",
                continuity_insight="継続率",
            )

    def test_monthly_narrative_response_rejects_carriage_returns(self) -> None:
        with pytest.raises(ValidationError):
            MonthlyNarrativeResponse(
                period_start="2026-08-02",
                period_end_exclusive="2026-09-01",
                monthly_insights="1行目\r2行目",
                progress_wave="進み方の波",
                continuity_insight="継続率",
            )

