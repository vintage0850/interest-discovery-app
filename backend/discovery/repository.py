from __future__ import annotations

import json
import datetime
from typing import Any, Optional

from sqlalchemy import desc, update
from sqlalchemy.engine import Engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import selectinload
from sqlmodel import Session, select

from discovery.aggregation import summarize_domain_signals
from discovery.models import (
    ActionType,
    Criterion,
    DiscoverySession,
    DomainType,
    Evidence,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    HypothesisFeedback,
    HypothesisReaction,
    InterestHypothesis,
    InterestSignal,
    InterestSignalSource,
    MonthlyNarrativeCache,
    PsychAxis,
    PsychAxisResult,
    UserReflection,
    WeeklyNarrativeCache,
)

# 仮説へのユーザー反応が確信度に与える増減。同感で強まり、違うで弱まる。
# わからない は判断保留として確信度を変えない（§15）。
HYPOTHESIS_CONFIDENCE_DELTA: dict[str, float] = {
    HypothesisReaction.AGREE.value: 0.15,
    HypothesisReaction.UNSURE.value: 0.0,
    HypothesisReaction.DISAGREE.value: -0.15,
}
# この確信度を超えて「同感」された仮説は、個人の意思決定基準として昇格させる（§17）。
CRITERION_PROMOTION_THRESHOLD = 0.6


class StateTransitionError(ValueError):
    """実験の状態遷移が許可されていない場合に発生するエラー。"""


class DiscoveryRepository:
    """Discovery ドメインの永続化層。"""

    def __init__(self, engine: Engine) -> None:
        self._engine = engine

    def create_session(self, student_label: str) -> DiscoverySession:
        session = DiscoverySession(student_label=student_label)
        with Session(self._engine) as db:
            db.add(session)
            db.commit()
            db.refresh(session)
            return session

    def get_session(self, session_id: int) -> DiscoverySession | None:
        with Session(self._engine) as db:
            return db.get(DiscoverySession, session_id)

    def list_sessions(self, student_label: str) -> list[DiscoverySession]:
        """指定した生徒ラベルのセッションを updated_at 降順で取得する。"""
        with Session(self._engine) as db:
            statement = (
                select(DiscoverySession)
                .where(DiscoverySession.student_label == student_label)
                .order_by(desc(DiscoverySession.updated_at))
            )
            return list(db.exec(statement).all())

    def update_onboarding_info(
        self,
        session_id: int,
        nickname: Optional[str] = None,
        age_range: Optional[str] = None,
        school_stage: Optional[str] = None,
        optional_interests: Optional[list[str]] = None,
        initial_self_understanding_score: Optional[float] = None,
    ) -> DiscoverySession:
        """オンボーディング情報を部分更新する。None のフィールドは上書きしない。"""
        with Session(self._engine) as db:
            session = db.get(DiscoverySession, session_id)
            if session is None:
                raise ValueError(f"Session {session_id} not found")

            if nickname is not None:
                session.nickname = nickname
            if age_range is not None:
                session.age_range = age_range
            if school_stage is not None:
                session.school_stage = school_stage
            if optional_interests is not None:
                session.optional_interests = json.dumps(
                    optional_interests, ensure_ascii=False
                )
            if initial_self_understanding_score is not None:
                session.initial_self_understanding_score = initial_self_understanding_score

            session.updated_at = datetime.datetime.now(datetime.timezone.utc)
            db.add(session)
            db.commit()
            db.refresh(session)
            return session

    def add_signal(
        self,
        session_id: int,
        action_type: str,
        domain: DomainType,
        content_summary: str,
        source: str,
        occurred_at: datetime.datetime,
    ) -> InterestSignal:
        signal = InterestSignal(
            session_id=session_id,
            action_type=action_type,
            domain=domain.value,
            content_summary=content_summary,
            source=source,
            occurred_at=occurred_at,
        )
        with Session(self._engine) as db:
            db.add(signal)
            db.commit()
            db.refresh(signal)

        self._touch_session_by_id(session_id)
        return signal

    def _touch_session_by_id(self, session_id: int) -> None:
        """別トランザクションで完了した書き込み後に updated_at を更新する。"""
        with Session(self._engine) as db:
            session = db.get(DiscoverySession, session_id)
            if session is not None:
                session.updated_at = datetime.datetime.now(datetime.timezone.utc)
                db.add(session)
                db.commit()

    def list_signals(self, session_id: int) -> list[InterestSignal]:
        with Session(self._engine) as db:
            statement = select(InterestSignal).where(InterestSignal.session_id == session_id)
            return list(db.exec(statement).all())

    def create_experiment(
        self,
        session_id: int,
        title: str,
        description: str,
        domain: DomainType,
        planned_minutes: int,
    ) -> Experiment:
        experiment = Experiment(
            session_id=session_id,
            title=title,
            description=description,
            domain=domain.value,
            planned_minutes=planned_minutes,
        )
        with Session(self._engine) as db:
            db.add(experiment)
            db.commit()
            db.refresh(experiment)

        self._touch_session_by_id(session_id)
        return experiment

    def get_experiment(self, experiment_id: int) -> Experiment | None:
        with Session(self._engine) as db:
            return db.get(Experiment, experiment_id)

    def list_experiments(self, session_id: int) -> list[Experiment]:
        with Session(self._engine) as db:
            statement = select(Experiment).where(Experiment.session_id == session_id)
            return list(db.exec(statement).all())

    def _add_auto_signal(
        self,
        db: Session,
        session_id: int,
        action_type: ActionType,
        domain: str,
        content_summary: str,
    ) -> InterestSignal:
        signal = InterestSignal(
            session_id=session_id,
            action_type=action_type.value,
            domain=domain,
            content_summary=content_summary,
            source=InterestSignalSource.SYSTEM.value,
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        db.add(signal)
        return signal

    def _touch_session(self, db: Session, session: DiscoverySession) -> None:
        """セッションの updated_at を現在時刻に更新する。"""
        session.updated_at = datetime.datetime.now(datetime.timezone.utc)
        db.add(session)

    def _require_transition_success(
        self,
        db: Session,
        result: Any,
        experiment_id: int,
        target_status: ExperimentStatus,
    ) -> tuple[Experiment, bool]:
        """条件付きUPDATEの結果を検証し、(実験, 状態が実際に変化したか)を返す。

        rowcount > 0 なら状態が変化したとみなす。
        rowcount == 0 なら、対象が存在しない（404）、同じ状態への冪等再送（200）、
        または許可されていない遷移（409）のいずれかである。
        """
        if result.rowcount > 0:
            experiment = db.get(Experiment, experiment_id)
            if experiment is None:
                raise ValueError(f"Experiment {experiment_id} not found")
            return experiment, True

        experiment = db.get(Experiment, experiment_id)
        if experiment is None:
            raise ValueError(f"Experiment {experiment_id} not found")
        if experiment.status == target_status.value:
            return experiment, False
        raise StateTransitionError(
            f"Cannot transition experiment to {target_status.value} from status {experiment.status}"
        )

    def select_experiment(self, experiment_id: int, selection_note: str) -> Experiment:
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            statement = (
                update(Experiment)
                .where(
                    Experiment.id == experiment_id,
                    Experiment.status == ExperimentStatus.GENERATED.value,
                )
                .values(
                    status=ExperimentStatus.SELECTED.value,
                    selected_at=now,
                    selection_note=selection_note,
                )
            )
            result = db.exec(statement)
            experiment, changed = self._require_transition_success(
                db, result, experiment_id, ExperimentStatus.SELECTED
            )
            if changed:
                self._add_auto_signal(
                    db,
                    experiment.session_id,
                    ActionType.EXPERIMENT_SELECTED,
                    experiment.domain,
                    f"Experiment '{experiment.title}' selected: {selection_note}",
                )
            db.commit()
            db.refresh(experiment)
            self._touch_session_by_id(experiment.session_id)
            return experiment

    def skip_experiment(self, experiment_id: int, reason: str | None) -> Experiment:
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            statement = (
                update(Experiment)
                .where(
                    Experiment.id == experiment_id,
                    Experiment.status.in_(
                        (ExperimentStatus.GENERATED.value, ExperimentStatus.SELECTED.value)
                    ),
                )
                .values(
                    status=ExperimentStatus.SKIPPED.value,
                    skipped_at=now,
                    skip_reason=reason,
                )
            )
            result = db.exec(statement)
            experiment, changed = self._require_transition_success(
                db, result, experiment_id, ExperimentStatus.SKIPPED
            )
            if changed:
                self._add_auto_signal(
                    db,
                    experiment.session_id,
                    ActionType.EXPERIMENT_SKIPPED,
                    experiment.domain,
                    f"Experiment '{experiment.title}' skipped"
                    + (f": {reason}" if reason else ""),
                )
            db.commit()
            db.refresh(experiment)
            self._touch_session_by_id(experiment.session_id)
            return experiment

    def start_experiment(self, experiment_id: int) -> Experiment:
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            statement = (
                update(Experiment)
                .where(
                    Experiment.id == experiment_id,
                    Experiment.status == ExperimentStatus.SELECTED.value,
                )
                .values(
                    status=ExperimentStatus.STARTED.value,
                    started_at=now,
                )
            )
            result = db.exec(statement)
            experiment, changed = self._require_transition_success(
                db, result, experiment_id, ExperimentStatus.STARTED
            )
            if changed:
                self._add_auto_signal(
                    db,
                    experiment.session_id,
                    ActionType.EXPERIMENT_STARTED,
                    experiment.domain,
                    f"Experiment '{experiment.title}' started",
                )
            db.commit()
            db.refresh(experiment)
            self._touch_session_by_id(experiment.session_id)
            return experiment

    def _fetch_result_with_experiment(self, db: Session, result_id: int) -> ExperimentResult:
        statement = (
            select(ExperimentResult)
            .where(ExperimentResult.id == result_id)
            .options(
                selectinload(ExperimentResult.experiment).selectinload(Experiment.result)
            )
        )
        return db.exec(statement).one()

    def complete_experiment(
        self,
        experiment_id: int,
        enjoyment: int,
        curiosity: int,
        retry_intent: int,
        confidence: float,
        reflection: str | None = None,
    ) -> ExperimentResult:
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            statement = (
                update(Experiment)
                .where(
                    Experiment.id == experiment_id,
                    Experiment.status == ExperimentStatus.STARTED.value,
                    Experiment.started_at.is_not(None),
                )
                .values(
                    status=ExperimentStatus.COMPLETED.value,
                    completed_at=now,
                )
            )
            result = db.exec(statement)
            experiment, changed = self._require_transition_success(
                db, result, experiment_id, ExperimentStatus.COMPLETED
            )

            if not changed:
                statement = select(ExperimentResult).where(
                    ExperimentResult.experiment_id == experiment_id
                )
                existing = db.exec(statement).first()
                if existing is not None:
                    return self._fetch_result_with_experiment(db, existing.id)
                raise StateTransitionError("Completed experiment has no result")

            if experiment.started_at is None:
                raise ValueError("Experiment has not been started")

            experiment.actual_minutes = max(
                0,
                round(
                    (now - experiment.started_at).total_seconds() / 60
                ),
            )

            result = ExperimentResult(
                experiment_id=experiment_id,
                enjoyment=enjoyment,
                curiosity=curiosity,
                retry_intent=retry_intent,
                confidence=confidence,
                reflection=reflection,
            )
            db.add(result)
            try:
                db.flush()
            except IntegrityError:
                db.rollback()
                statement = select(ExperimentResult).where(
                    ExperimentResult.experiment_id == experiment_id
                )
                existing = db.exec(statement).first()
                if existing is not None:
                    return self._fetch_result_with_experiment(db, existing.id)
                raise

            self._add_auto_signal(
                db,
                experiment.session_id,
                ActionType.EXPERIMENT_COMPLETED,
                experiment.domain,
                f"Experiment '{experiment.title}' completed",
            )
            self._add_auto_signal(
                db,
                experiment.session_id,
                ActionType.EXPLICIT_FEEDBACK,
                experiment.domain,
                f"enjoyment={enjoyment}, curiosity={curiosity}, retry_intent={retry_intent}, confidence={confidence}"
                + (f"; reflection={reflection}" if reflection else ""),
            )
            duration_ratio = experiment.actual_minutes / experiment.planned_minutes
            if duration_ratio >= 1.5:
                self._add_auto_signal(
                    db,
                    experiment.session_id,
                    ActionType.LONGER_THAN_PLANNED,
                    experiment.domain,
                    f"actual {experiment.actual_minutes} min vs planned {experiment.planned_minutes} min (ratio {duration_ratio:.2f})",
                )
            db.commit()
            db.refresh(result)
            db.refresh(experiment)
            self._touch_session_by_id(experiment.session_id)
            return self._fetch_result_with_experiment(db, result.id)

    def create_hypothesis(
        self,
        session_id: int,
        summary: str,
        confidence: float,
        supporting_evidence: list[int],
        suggested_next_domains: list[str],
    ) -> InterestHypothesis:
        hypothesis = InterestHypothesis(
            session_id=session_id,
            summary=summary,
            confidence=confidence,
            supporting_evidence=supporting_evidence,
            suggested_next_domains=suggested_next_domains,
        )
        with Session(self._engine) as db:
            db.add(hypothesis)
            db.commit()
            db.refresh(hypothesis)

        self._touch_session_by_id(session_id)
        return hypothesis

    def get_latest_hypothesis(self, session_id: int) -> InterestHypothesis | None:
        with Session(self._engine) as db:
            statement = (
                select(InterestHypothesis)
                .where(InterestHypothesis.session_id == session_id)
                .order_by(desc(InterestHypothesis.created_at))
                .limit(1)
            )
            return db.exec(statement).first()

    def add_hypothesis_feedback(
        self, hypothesis_id: int, reaction: HypothesisReaction
    ) -> tuple[HypothesisFeedback, InterestHypothesis, Criterion | None]:
        """仮説への反応を記録し、確信度を更新する。閾値を超えて同感されたら基準に昇格する。"""
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            hypothesis = db.get(InterestHypothesis, hypothesis_id)
            if hypothesis is None:
                raise ValueError(f"Hypothesis {hypothesis_id} not found")

            feedback = HypothesisFeedback(hypothesis_id=hypothesis_id, reaction=reaction.value)
            db.add(feedback)

            delta = HYPOTHESIS_CONFIDENCE_DELTA[reaction.value]
            hypothesis.confidence = max(0.0, min(1.0, hypothesis.confidence + delta))
            db.add(hypothesis)

            criterion: Criterion | None = None
            if (
                reaction == HypothesisReaction.AGREE
                and hypothesis.confidence >= CRITERION_PROMOTION_THRESHOLD
            ):
                existing = db.exec(
                    select(Criterion).where(
                        Criterion.source_hypothesis_id == hypothesis_id
                    )
                ).first()
                if existing is not None:
                    existing.confidence = hypothesis.confidence
                    existing.updated_at = now
                    db.add(existing)
                    criterion = existing
                else:
                    criterion = Criterion(
                        session_id=hypothesis.session_id,
                        label=hypothesis.summary,
                        description=hypothesis.summary,
                        confidence=hypothesis.confidence,
                        source_hypothesis_id=hypothesis_id,
                        user_confirmed=True,
                    )
                    db.add(criterion)

            db.commit()
            db.refresh(feedback)
            db.refresh(hypothesis)
            if criterion is not None:
                db.refresh(criterion)
            self._touch_session_by_id(hypothesis.session_id)
            return feedback, hypothesis, criterion

    def list_criteria(self, session_id: int) -> list[Criterion]:
        with Session(self._engine) as db:
            statement = (
                select(Criterion)
                .where(Criterion.session_id == session_id)
                .order_by(desc(Criterion.confidence))
            )
            return list(db.exec(statement).all())

    def upsert_psych_axis_results(
        self, session_id: int, scores: dict[str, float]
    ) -> list[PsychAxisResult]:
        """心理軸アンケート結果を upsert する。同一軸があれば上書きする。"""
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            session = db.get(DiscoverySession, session_id)
            if session is None:
                raise ValueError(f"Session {session_id} not found")

            results: list[PsychAxisResult] = []
            for axis, score in scores.items():
                # SQLAlchemy validates で軸とスコアを検証
                candidate = PsychAxisResult(
                    session_id=session_id, axis=axis, score=score
                )
                existing = db.exec(
                    select(PsychAxisResult).where(
                        PsychAxisResult.session_id == session_id,
                        PsychAxisResult.axis == axis,
                    )
                ).first()
                if existing is not None:
                    existing.score = candidate.score
                    existing.updated_at = now
                    db.add(existing)
                    results.append(existing)
                else:
                    db.add(candidate)
                    results.append(candidate)

            session.updated_at = now
            db.add(session)
            db.commit()
            for result in results:
                db.refresh(result)
            return results

    def get_psych_axis_results(self, session_id: int) -> list[PsychAxisResult]:
        """指定セッションの心理軸アンケート結果を created_at 降順で取得する。"""
        with Session(self._engine) as db:
            statement = (
                select(PsychAxisResult)
                .where(PsychAxisResult.session_id == session_id)
                .order_by(desc(PsychAxisResult.created_at))
            )
            return list(db.exec(statement).all())

    def get_psych_axis_scores(self, session_id: int) -> dict[str, float]:
        """指定セッションの心理軸スコアを {axis: score} の dict で返す。"""
        results = self.get_psych_axis_results(session_id)
        return {result.axis: result.score for result in results}

    def create_user_reflection(
        self, session_id: int, content: str, mood: Optional[int] = None
    ) -> UserReflection:
        """ユーザー主導の振り返りを作成する。"""
        now = datetime.datetime.now(datetime.timezone.utc)
        with Session(self._engine) as db:
            session = db.get(DiscoverySession, session_id)
            if session is None:
                raise ValueError(f"Session {session_id} not found")

            reflection = UserReflection(
                session_id=session_id, content=content, mood=mood
            )
            db.add(reflection)
            session.updated_at = now
            db.add(session)
            db.commit()
            db.refresh(reflection)
            return reflection

    def list_user_reflections(self, session_id: int) -> list[UserReflection]:
        """指定セッションの振り返りを created_at 降順で取得する。"""
        with Session(self._engine) as db:
            statement = (
                select(UserReflection)
                .where(UserReflection.session_id == session_id)
                .order_by(desc(UserReflection.created_at))
            )
            return list(db.exec(statement).all())

    def get_weekly_narrative_cache(
        self, session_id: int, cache_date: str
    ) -> WeeklyNarrativeCache | None:
        """指定セッション・UTC日付のキャッシュ済み週次ナラティブを取得する。"""
        with Session(self._engine) as db:
            statement = select(WeeklyNarrativeCache).where(
                WeeklyNarrativeCache.session_id == session_id,
                WeeklyNarrativeCache.cache_date == cache_date,
            )
            return db.exec(statement).first()

    def save_weekly_narrative_cache(
        self,
        session_id: int,
        cache_date: str,
        weekly_insights: str,
        change_from_past: str,
    ) -> WeeklyNarrativeCache:
        """週次ナラティブをキャッシュとして保存する（同一日付なら上書き）。"""
        with Session(self._engine) as db:
            existing = db.exec(
                select(WeeklyNarrativeCache).where(
                    WeeklyNarrativeCache.session_id == session_id,
                    WeeklyNarrativeCache.cache_date == cache_date,
                )
            ).first()
            if existing is not None:
                existing.weekly_insights = weekly_insights
                existing.change_from_past = change_from_past
                db.add(existing)
                db.commit()
                db.refresh(existing)
                return existing

            cache = WeeklyNarrativeCache(
                session_id=session_id,
                cache_date=cache_date,
                weekly_insights=weekly_insights,
                change_from_past=change_from_past,
            )
            db.add(cache)
            try:
                db.commit()
            except IntegrityError:
                # 同時リクエストで先に他方がINSERTした場合はそちらを正とする。
                db.rollback()
                winner = db.exec(
                    select(WeeklyNarrativeCache).where(
                        WeeklyNarrativeCache.session_id == session_id,
                        WeeklyNarrativeCache.cache_date == cache_date,
                    )
                ).first()
                if winner is not None:
                    return winner
                raise
            db.refresh(cache)
            return cache

    def get_monthly_narrative_cache(
        self, session_id: int, cache_month: str
    ) -> MonthlyNarrativeCache | None:
        """指定セッション・UTC月のキャッシュ済み月次ナラティブを取得する。"""
        with Session(self._engine) as db:
            statement = select(MonthlyNarrativeCache).where(
                MonthlyNarrativeCache.session_id == session_id,
                MonthlyNarrativeCache.cache_month == cache_month,
            )
            return db.exec(statement).first()

    def save_monthly_narrative_cache(
        self,
        session_id: int,
        cache_month: str,
        monthly_insights: str,
        progress_wave: str,
        continuity_insight: str,
    ) -> MonthlyNarrativeCache:
        """月次ナラティブをキャッシュとして保存する（同一月なら上書き）。"""
        with Session(self._engine) as db:
            existing = db.exec(
                select(MonthlyNarrativeCache).where(
                    MonthlyNarrativeCache.session_id == session_id,
                    MonthlyNarrativeCache.cache_month == cache_month,
                )
            ).first()
            if existing is not None:
                existing.monthly_insights = monthly_insights
                existing.progress_wave = progress_wave
                existing.continuity_insight = continuity_insight
                db.add(existing)
                db.commit()
                db.refresh(existing)
                return existing

            cache = MonthlyNarrativeCache(
                session_id=session_id,
                cache_month=cache_month,
                monthly_insights=monthly_insights,
                progress_wave=progress_wave,
                continuity_insight=continuity_insight,
            )
            db.add(cache)
            try:
                db.commit()
            except IntegrityError:
                # 同時リクエストで先に他方がINSERTした場合はそちらを正とする。
                db.rollback()
                winner = db.exec(
                    select(MonthlyNarrativeCache).where(
                        MonthlyNarrativeCache.session_id == session_id,
                        MonthlyNarrativeCache.cache_month == cache_month,
                    )
                ).first()
                if winner is not None:
                    return winner
                raise
            db.refresh(cache)
            return cache

    def get_summary_data(self, session_id: int) -> dict[str, Any]:
        with Session(self._engine) as db:
            signals = list(
                db.exec(
                    select(InterestSignal).where(InterestSignal.session_id == session_id)
                ).all()
            )
            experiments = list(
                db.exec(
                    select(Experiment).where(Experiment.session_id == session_id)
                ).all()
            )
            results = list(
                db.exec(
                    select(ExperimentResult)
                    .join(Experiment)
                    .where(Experiment.session_id == session_id)
                ).all()
            )
            return {
                "signals": signals,
                "experiments": experiments,
                "results": results,
            }

    def build_evidence(self, session_id: int) -> list[Evidence]:
        """対象セッションの InterestSignal を domain 単位で集計し、Evidence レコードを作成・永続化する。"""
        with Session(self._engine) as db:
            session = db.get(DiscoverySession, session_id)
            if session is None:
                raise ValueError(f"Session {session_id} not found")

            statement = select(InterestSignal).where(InterestSignal.session_id == session_id)
            signals = list(db.exec(statement).all())
            if not signals:
                return []

            from collections import defaultdict
            signals_by_domain: dict[str, list[InterestSignal]] = defaultdict(list)
            for signal in signals:
                signals_by_domain[signal.domain].append(signal)

            evidences: list[Evidence] = []
            for domain in sorted(signals_by_domain.keys()):
                domain_signals = signals_by_domain[domain]
                summary = summarize_domain_signals(domain, domain_signals)
                evidence = Evidence(
                    session_id=session_id,
                    domain=domain,
                    signal_count=len(domain_signals),
                    summary_text=summary,
                )
                db.add(evidence)
                evidences.append(evidence)

            session.updated_at = datetime.datetime.now(datetime.timezone.utc)
            db.add(session)
            db.commit()
            for evidence in evidences:
                db.refresh(evidence)
            return evidences

    def list_evidence(self, session_id: int) -> list[Evidence]:
        """指定セッションのエビデンス一覧を作成日時降順で取得する。"""
        with Session(self._engine) as db:
            statement = (
                select(Evidence)
                .where(Evidence.session_id == session_id)
                .order_by(desc(Evidence.created_at), desc(Evidence.id))
            )
            return list(db.exec(statement).all())

    def update_evidence_behavior_categories(
        self,
        evidence_id: int,
        behavior_categories: dict[str, float] | None,
    ) -> Evidence:
        """指定エビデンスの behavior_categories を更新する。"""
        with Session(self._engine) as db:
            evidence = db.get(Evidence, evidence_id)
            if evidence is None:
                raise ValueError(f"Evidence {evidence_id} not found")
            evidence.behavior_categories = behavior_categories
            db.add(evidence)
            db.commit()
            db.refresh(evidence)
            return evidence

