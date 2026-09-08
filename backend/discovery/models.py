import datetime
import enum
import json
from typing import Any, Optional

from pydantic import Field, field_validator
from sqlalchemy import DateTime, JSON, String, TypeDecorator, UniqueConstraint
from sqlalchemy.orm import validates
from sqlmodel import Field as SQLField, Relationship, SQLModel


class UTCDateTime(TypeDecorator):
    """SQLite上でもタイムゾーン付きdatetimeを保持する型。

    SQLAlchemyの ``DateTime(timezone=True)`` はSQLiteネイティブでは
    tzinfoを失うため、読み込み時にnaiveなdatetimeをUTCとして解釈する。
    書き込み値は常にUTC tz-awareであることを前提とする。
    """

    impl = DateTime(timezone=True)
    cache_ok = True

    def process_result_value(
        self, value: datetime.datetime | None, dialect: Any
    ) -> datetime.datetime | None:
        if value is not None and value.tzinfo is None:
            return value.replace(tzinfo=datetime.timezone.utc)
        return value


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


class PsychAxis(str, enum.Enum):
    INVESTIGATE = "INVESTIGATE"
    CREATE = "CREATE"
    EXECUTE = "EXECUTE"
    COMMUNICATE = "COMMUNICATE"


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
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )
    updated_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )
    nickname: Optional[str] = SQLField(default=None, sa_type=String(100))
    age_range: Optional[str] = SQLField(default=None, sa_type=String(32))
    school_stage: Optional[str] = SQLField(default=None, sa_type=String(32))
    optional_interests: Optional[str] = SQLField(default=None, sa_type=String(1000))
    initial_self_understanding_score: Optional[float] = SQLField(default=None)

    signals: list["InterestSignal"] = Relationship(back_populates="session")
    experiments: list["Experiment"] = Relationship(back_populates="session")
    hypotheses: list["InterestHypothesis"] = Relationship(back_populates="session")
    psych_axis_results: list["PsychAxisResult"] = Relationship(back_populates="session")
    reflections: list["UserReflection"] = Relationship(back_populates="session")
    evidences: list["Evidence"] = Relationship(back_populates="session")

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
    occurred_at: datetime.datetime = SQLField(sa_type=UTCDateTime())
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )

    session: "DiscoverySession" = Relationship(back_populates="signals")


class Evidence(SQLModel, table=True):
    """シグナルを集計・要約した観察証拠（エビデンス）。"""

    __tablename__ = "evidence"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    domain: str = SQLField(sa_type=String(32), index=True)
    signal_count: int
    summary_text: str = SQLField(sa_type=String(1000))
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )

    session: "DiscoverySession" = Relationship(back_populates="evidences")

    @validates("domain")
    def _validate_domain(self, key: str, value: str) -> str:
        if value not in {d.value for d in DomainType}:
            raise ValueError(f"invalid domain: {value}")
        return value

    @validates("signal_count")
    def _validate_signal_count(self, key: str, value: int) -> int:
        if value < 1:
            raise ValueError("signal_count must be at least 1")
        return value

    @validates("summary_text")
    def _validate_summary_text(self, key: str, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("summary_text must not be empty")
        if len(value) > 1000:
            raise ValueError("summary_text must be 1000 characters or less")
        return value


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
    selected_at: Optional[datetime.datetime] = SQLField(default=None, sa_type=UTCDateTime())
    started_at: Optional[datetime.datetime] = SQLField(default=None, sa_type=UTCDateTime())
    completed_at: Optional[datetime.datetime] = SQLField(default=None, sa_type=UTCDateTime())
    skipped_at: Optional[datetime.datetime] = SQLField(default=None, sa_type=UTCDateTime())
    selection_note: Optional[str] = None
    skip_reason: Optional[str] = None
    actual_minutes: Optional[int] = None
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
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
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
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
    supporting_evidence: list[int] = SQLField(
        default_factory=list, sa_type=JSON
    )
    suggested_next_domains: list[str] = SQLField(default_factory=list, sa_type=JSON)
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
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
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )


class PsychAxisResult(SQLModel, table=True):
    """心理軸アンケートの結果（軸ごとの平均スコア）。"""

    __tablename__ = "psych_axis_result"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    axis: str = SQLField(sa_type=String(32))
    score: float
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )
    updated_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )

    session: "DiscoverySession" = Relationship(back_populates="psych_axis_results")

    __table_args__ = (
        UniqueConstraint("session_id", "axis", name="uq_psych_axis_result_session_axis"),
    )

    @validates("axis")
    def _validate_axis(self, key: str, value: str) -> str:
        if value not in {axis.value for axis in PsychAxis}:
            raise ValueError(f"invalid psych axis: {value}")
        return value

    @validates("score")
    def _validate_score(self, key: str, value: float) -> float:
        if value < 1.0 or value > 5.0:
            raise ValueError("score must be between 1.0 and 5.0")
        return value


class UserReflection(SQLModel, table=True):
    """ユーザーが自由に書ける日記的振り返り。"""

    __tablename__ = "user_reflection"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    content: str = SQLField(sa_type=String(2000))
    mood: Optional[int] = SQLField(default=None)
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )

    session: "DiscoverySession" = Relationship(back_populates="reflections")

    @validates("content")
    def _validate_content(self, key: str, value: str) -> str:
        if not value or not value.strip():
            raise ValueError("content must not be empty")
        if len(value) > 2000:
            raise ValueError("content must be 2000 characters or less")
        return value

    @validates("mood")
    def _validate_mood(self, key: str, value: Optional[int]) -> Optional[int]:
        if value is not None and (value < 1 or value > 5):
            raise ValueError("mood must be between 1 and 5")
        return value


class WeeklyNarrativeCache(SQLModel, table=True):
    """週次ナラティブ（Gemini生成）のセッション×日付単位のキャッシュ。

    毎回のDiscovery/Report画面表示でGeminiを呼び出すと数秒〜十数秒かかるため、
    同じUTC日付内は再生成せずキャッシュを返す。
    """

    __tablename__ = "weekly_narrative_cache"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    cache_date: str = SQLField(sa_type=String(10), index=True)
    weekly_insights: str = SQLField(sa_type=String(200))
    change_from_past: str = SQLField(sa_type=String(200))
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )

    __table_args__ = (
        UniqueConstraint(
            "session_id", "cache_date", name="uq_weekly_narrative_cache_session_date"
        ),
    )


class MonthlyNarrativeCache(SQLModel, table=True):
    """月次ナラティブ（Gemini生成）のセッション×月単位のキャッシュ。

    同一セッション・同一UTC月(YYYY-MM)では初回に確定したナラティブを再利用する。
    """

    __tablename__ = "monthly_narrative_cache"

    id: Optional[int] = SQLField(default=None, primary_key=True)
    session_id: int = SQLField(foreign_key="discovery_session.id", index=True)
    cache_month: str = SQLField(sa_type=String(7), index=True)
    monthly_insights: str = SQLField(sa_type=String(200))
    progress_wave: str = SQLField(sa_type=String(200))
    continuity_insight: str = SQLField(sa_type=String(200))
    created_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )

    __table_args__ = (
        UniqueConstraint(
            "session_id",
            "cache_month",
            name="uq_monthly_narrative_cache_session_month",
        ),
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
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
    )
    updated_at: datetime.datetime = SQLField(
        default_factory=lambda: datetime.datetime.now(datetime.timezone.utc),
        sa_type=UTCDateTime(),
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


class EvidenceResponse(SQLModel):
    id: int
    session_id: int
    domain: str
    signal_count: int
    summary_text: str
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
    supporting_evidence: list[int]
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
    dive_candidate_domains: list[str] = Field(default_factory=list)


class SessionSummary(SQLModel):
    session: SessionResponse
    behavior_summary: BehaviorSummary
    latest_hypothesis: Optional[HypothesisResponse]
    criteria: list[CriterionResponse] = Field(default_factory=list)
    psych_axis_scores: dict[str, float] = Field(default_factory=dict)


class WeeklyNarrativeResponse(SQLModel):
    weekly_insights: str = Field(..., min_length=1, max_length=200)
    change_from_past: str = Field(..., min_length=1, max_length=200)


class MonthlyNarrativeResponse(SQLModel):
    period_start: str = Field(..., min_length=10, max_length=10)
    period_end_exclusive: str = Field(..., min_length=10, max_length=10)
    monthly_insights: str = Field(..., min_length=1, max_length=200)
    progress_wave: str = Field(..., min_length=1, max_length=200)
    continuity_insight: str = Field(..., min_length=1, max_length=200)

    @field_validator("monthly_insights", "progress_wave", "continuity_insight")
    @classmethod
    def _validate_trimmed_text(cls, value: str) -> str:
        trimmed = value.strip()
        if not trimmed:
            raise ValueError("field must not be empty or whitespace only")
        if len(trimmed) > 200:
            raise ValueError("field must be 200 characters or less after trimming")
        if "\n" in trimmed or "\r" in trimmed:
            raise ValueError("field must not contain newlines")
        return trimmed


class NotificationCandidateDomainStatus(str, enum.Enum):
    DIVE_CANDIDATE = "DIVE_CANDIDATE"
    TRIED = "TRIED"
    EXPLORED = "EXPLORED"
    UNEXPLORED = "UNEXPLORED"


class NotificationCandidateResponse(SQLModel):
    experiment: ExperimentResponse
    domain_status: str
    reason: str


class PsychAxisSurveySubmitRequest(SQLModel):
    scores: dict[str, float]

    @field_validator("scores")
    @classmethod
    def _validate_scores(cls, value: dict[str, float]) -> dict[str, float]:
        expected = {axis.value for axis in PsychAxis}
        if set(value.keys()) != expected:
            raise ValueError("scores must contain exactly the four psych axes")
        for axis, score in value.items():
            if not isinstance(score, (int, float)) or isinstance(score, bool):
                raise ValueError(f"score for {axis} must be numeric")
            if score < 1.0 or score > 5.0:
                raise ValueError(f"score for {axis} must be between 1.0 and 5.0")
        return value


class PsychAxisResultResponse(SQLModel):
    id: int
    session_id: int
    axis: str
    score: float
    created_at: datetime.datetime
    updated_at: datetime.datetime


class UserReflectionCreate(SQLModel):
    content: str = Field(..., min_length=1, max_length=2000)
    mood: Optional[int] = Field(default=None, ge=1, le=5)


class UserReflectionResponse(SQLModel):
    id: int
    session_id: int
    content: str
    mood: Optional[int]
    created_at: datetime.datetime
