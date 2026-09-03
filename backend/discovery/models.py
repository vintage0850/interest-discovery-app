import datetime
import enum
import json
from typing import Any, Optional

from pydantic import Field, field_validator
from sqlalchemy import JSON, String
from sqlalchemy.orm import validates
from sqlmodel import Field as SQLField, Relationship, SQLModel


class ActionType(str, enum.Enum):
    SEARCH = "search"
    VIEW = "view"
    SAVE = "save"
    SHARE = "share"
    CREATE = "create"
    LIKE = "like"
    COMMENT = "comment"
    EXPERIMENT_SELECTED = "EXPERIMENT_SELECTED"
    EXPERIMENT_SKIPPED = "EXPERIMENT_SKIPPED"
    EXPERIMENT_STARTED = "EXPERIMENT_STARTED"
    EXPERIMENT_COMPLETED = "EXPERIMENT_COMPLETED"
    LONGER_THAN_PLANNED = "LONGER_THAN_PLANNED"
    EXPLICIT_FEEDBACK = "EXPLICIT_FEEDBACK"


class DomainType(str, enum.Enum):
    TECH = "tech"
    ART = "art"
    MUSIC = "music"
    SPORTS = "sports"
    SCIENCE = "science"
    SOCIAL = "social"
    MAKING = "making"
    NATURE = "nature"
    BUSINESS = "business"
    OTHER = "other"


class InterestSignalSource(str, enum.Enum):
    SEARCH_HISTORY = "search_history"
    BROWSING_HISTORY = "browsing_history"
    APP_USAGE = "app_usage"
    SCHOOL_WORK = "school_work"
    HOBBY = "hobby"
    CONVERSATION = "conversation"
    OTHER = "other"
    SYSTEM = "system"


class ExperimentStatus(str, enum.Enum):
    GENERATED = "generated"
    SELECTED = "selected"
    STARTED = "started"
    COMPLETED = "completed"
    SKIPPED = "skipped"


# ---------------------------------------------------------------------------
# SQLModel tables
# ---------------------------------------------------------------------------


class DiscoverySession(SQLModel, table=True):
    """生徒1人分の興味探索セッション。"""

    __tablename__ = "discovery_session"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    student_label: str = SQLField(index=True)
    status: str = SQLField(default="active", sa_type=String(32))
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )
    updated_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )
    nickname: Optional[str] = SQLField(default=None, sa_type=String(100))
    age_range: Optional[str] = SQLField(default=None, sa_type=String(32))
    school_stage: Optional[str] = SQLField(default=None, sa_type=String(32))
    optional_interests: Optional[str] = SQLField(default=None, sa_type=String(1000))
    initial_self_understanding_score: Optional[float] = SQLField(default=None)

    signals: list["InterestSignal"] = Relationship(back_populates="session")
    experiments: list["Experiment"] = Relationship(back_populates="session")
    hypotheses: list["InterestHypothesis"] = Relationship(back_populates="session")

    @field_validator("student_label")
    @classmethod
    def _validate_student_label(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("student_label must not be empty")
        if len(value) > 100:
            raise ValueError("student_label must be 100 characters or less")
        return value.strip()

    @validates("student_label")
    def _validate_student_label_sqla(self, key: str, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("student_label must not be empty")
        if len(value) > 100:
            raise ValueError("student_label must be 100 characters or less")
        return value.strip()


class InterestSignal(SQLModel, table=True):
    """生徒が示した弱い興味シグナル。"""

    __tablename__ = "interest_signal"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    action_type: str = SQLField(sa_type=String(32))
    domain: str = SQLField(sa_type=String(32), index=True)
    content_summary: str
    source: str = SQLField(sa_type=String(32))
    occurred_at: datetime.datetime
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )

    session: "DiscoverySession" = Relationship(back_populates="signals")


class Experiment(SQLModel, table=True):
    """生成された行動実験。"""

    __tablename__ = "experiment"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    title: str
    description: str
    domain: str = SQLField(sa_type=String(32), index=True)
    planned_minutes: int
    status: str = SQLField(default=ExperimentStatus.GENERATED.value, sa_type=String(32))
    selected_at: Optional[datetime.datetime] = None
    started_at: Optional[datetime.datetime] = None
    completed_at: Optional[datetime.datetime] = None
    skipped_at: Optional[datetime.datetime] = None
    selection_note: Optional[str] = None
    skip_reason: Optional[str] = None
    actual_minutes: Optional[int] = None
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )

    session: "DiscoverySession" = Relationship(back_populates="experiments")
    result: Optional["ExperimentResult"] = Relationship(
        back_populates="experiment",
        sa_relationship_kwargs={"uselist": False},
    )

    @validates("planned_minutes")
    def _validate_planned_minutes(self, key: str, value: int) -> int:
        if value < 5 or value > 15:
            raise ValueError("planned_minutes must be between 5 and 15")
        return value


class ExperimentResult(SQLModel, table=True):
    """実験完了後の生徒の主観評価。"""

    __tablename__ = "experiment_result"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    experiment_id: int = SQLField(foreign_key="experiment.id", unique=True)
    enjoyment: int
    curiosity: int
    retry_intent: int
    confidence: float
    reflection: Optional[str] = None
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )

    experiment: "Experiment" = Relationship(back_populates="result")

    @validates("enjoyment", "curiosity", "retry_intent")
    def _validate_one_to_five(self, key: str, value: int) -> int:
        if value < 1 or value > 5:
            raise ValueError(f"{key} must be between 1 and 5")
        return value

    @validates("confidence")
    def _validate_confidence(self, key: str, value: float) -> float:
        if value < 0.0 or value > 1.0:
            raise ValueError("confidence must be between 0.0 and 1.0")
        return value


class InterestHypothesis(SQLModel, table=True):
    """Gemini が生成した仮の興味仮説。"""

    __tablename__ = "interest_hypothesis"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    summary: str
    confidence: float
    supporting_evidence: list[dict[str, Any]] = SQLField(
        default_factory=list, sa_type=JSON
    )
    suggested_next_domains: list[str] = SQLField(default_factory=list, sa_type=JSON)
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )

    session: "DiscoverySession" = Relationship(back_populates="hypotheses")

    @validates("confidence")
    def _validate_confidence(self, key: str, value: float) -> float:
        if value < 0.0 or value > 1.0:
            raise ValueError("confidence must be between 0.0 and 1.0")
        return value


class HypothesisReaction(str, enum.Enum):
    AGREE = "agree"
    UNSURE = "unsure"
    DISAGREE = "disagree"


class HypothesisFeedback(SQLModel, table=True):
    """仮説に対する生徒の反応（同感/わからない/違う）。"""

    __tablename__ = "hypothesis_feedback"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    hypothesis_id: int = SQLField(foreign_key="interest_hypothesis.id", index=True)
    reaction: str = SQLField(sa_type=String(16))
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )


class Criterion(SQLModel, table=True):
    """生徒に同感された仮説から昇格した、意思決定の個人的な基準。"""

    __tablename__ = "criterion"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    label: str
    description: str
    confidence: float
    source_hypothesis_id: int = SQLField(foreign_key="interest_hypothesis.id", unique=True)
    user_confirmed: bool = SQLField(default=True)
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )
    updated_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc)
    )

    @validates("confidence")
    def _validate_confidence(self, key: str, value: float) -> float:
        if value < 0.0 or value > 1.0:
            raise ValueError("confidence must be between 0.0 and 1.0")
        return value


# ---------------------------------------------------------------------------
# Request / response schemas
# ---------------------------------------------------------------------------


class SessionCreate(SQLModel):
    student_label: str

    @field_validator("student_label")
    @classmethod
    def _validate_student_label(cls, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("student_label must not be empty")
        if len(value) > 100:
            raise ValueError("student_label must be 100 characters or less")
        return value.strip()


class SessionResponse(SQLModel):
    id: int
    student_label: str
    status: str
    created_at: datetime.datetime
    updated_at: datetime.datetime
    nickname: Optional[str] = None
    age_range: Optional[str] = None
    school_stage: Optional[str] = None
    optional_interests: Optional[list[str]] = None
    initial_self_understanding_score: Optional[float] = None

    @field_validator("optional_interests", mode="before")
    @classmethod
    def _parse_optional_interests(cls, value: Any) -> Any:
        if isinstance(value, str):
            return json.loads(value)
        return value


class OnboardingUpdateRequest(SQLModel):
    nickname: Optional[str] = None
    age_range: Optional[str] = None
    school_stage: Optional[str] = None
    optional_interests: Optional[list[str]] = None
    initial_self_understanding_score: Optional[float] = Field(
        default=None, ge=0.0, le=5.0
    )


class InterestSignalCreate(SQLModel):
    action_type: ActionType
    domain: DomainType
    content_summary: str = Field(..., min_length=1, max_length=500)
    source: InterestSignalSource
    occurred_at: str

    @field_validator("occurred_at")
    @classmethod
    def _validate_occurred_at(cls, value: str) -> str:
        dt = datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))
        if dt.tzinfo is None:
            raise ValueError("occurred_at must include timezone information")
        utc_dt = dt.astimezone(datetime.timezone.utc)
        return utc_dt.strftime("%Y-%m-%dT%H:%M:%S+00:00")


class InterestSignalResponse(SQLModel):
    id: int
    session_id: int
    action_type: str
    domain: str
    content_summary: str
    source: str
    occurred_at: datetime.datetime
    created_at: datetime.datetime


class ExperimentGenerateRequest(SQLModel):
    n_candidates: int = Field(default=3, ge=1, le=5)


class ExperimentResponse(SQLModel):
    id: int
    session_id: int
    title: str
    description: str
    domain: str
    planned_minutes: int
    status: str
    selected_at: Optional[datetime.datetime]
    started_at: Optional[datetime.datetime]
    completed_at: Optional[datetime.datetime]
    skipped_at: Optional[datetime.datetime]
    selection_note: Optional[str]
    skip_reason: Optional[str]
    actual_minutes: Optional[int]
    created_at: datetime.datetime


class ExperimentSelectRequest(SQLModel):
    selection_note: str = Field(..., min_length=1, max_length=500)


class ExperimentSkipRequest(SQLModel):
    reason: Optional[str] = Field(default=None, max_length=500)


class ExperimentResultCreate(SQLModel):
    enjoyment: int = Field(..., ge=1, le=5)
    curiosity: int = Field(..., ge=1, le=5)
    retry_intent: int = Field(..., ge=1, le=5)
    confidence: float = Field(..., ge=0.0, le=1.0)
    reflection: Optional[str] = Field(default=None, max_length=2000)


class ExperimentResultResponse(SQLModel):
    id: int
    experiment_id: int
    enjoyment: int
    curiosity: int
    retry_intent: int
    confidence: float
    reflection: Optional[str]
    created_at: datetime.datetime


class HypothesisUpdateRequest(SQLModel):
    pass


class HypothesisResponse(SQLModel):
    id: int
    session_id: int
    summary: str
    confidence: float
    supporting_evidence: list[dict[str, Any]]
    suggested_next_domains: list[str]
    created_at: datetime.datetime


class HypothesisFeedbackCreate(SQLModel):
    reaction: HypothesisReaction


class HypothesisFeedbackResponse(SQLModel):
    id: int
    hypothesis_id: int
    reaction: str
    created_at: datetime.datetime


class CriterionResponse(SQLModel):
    id: int
    session_id: int
    label: str
    description: str
    confidence: float
    source_hypothesis_id: int
    user_confirmed: bool
    created_at: datetime.datetime
    updated_at: datetime.datetime


class HypothesisFeedbackResult(SQLModel):
    feedback: HypothesisFeedbackResponse
    updated_hypothesis: HypothesisResponse
    new_criterion: Optional[CriterionResponse] = None


class BehaviorSummary(SQLModel):
    total_signals: int
    action_type_counts: dict[str, int]
    domain_counts: dict[str, int]
    total_experiments: int
    completed_experiments: int
    skipped_experiments: int
    avg_enjoyment: Optional[float]
    avg_curiosity: Optional[float]
    avg_retry_intent: Optional[float]
    avg_confidence: Optional[float]
    duration_ratio_high: list[int]
    duration_ratio_very_high: list[int]
    discrepancies: list[dict[str, Any]]
    total_minutes_spent: int
    domain_experiment_counts: dict[str, int]
    domain_completed_counts: dict[str, int]


class SessionSummary(SQLModel):
    session: SessionResponse
    behavior_summary: BehaviorSummary
    latest_hypothesis: Optional[HypothesisResponse]
    criteria: list[CriterionResponse] = Field(default_factory=list)
