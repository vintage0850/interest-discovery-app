from __future__ import annotations

import datetime
from typing import Any

from sqlalchemy import desc, update
from sqlalchemy.engine import Engine
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import selectinload
from sqlmodel import Session, select

from discovery.models import (
    ActionType,
    DiscoverySession,
    DomainType,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    InterestHypothesis,
    InterestSignal,
    InterestSignalSource,
)


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
            return signal

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
        now = datetime.datetime.now(datetime.timezone.utc).replace(tzinfo=None)
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
                    (now - experiment.started_at.replace(tzinfo=None)).total_seconds() / 60
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
            return self._fetch_result_with_experiment(db, result.id)

    def create_hypothesis(
        self,
        session_id: int,
        summary: str,
        confidence: float,
        supporting_evidence: list[dict[str, Any]],
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
