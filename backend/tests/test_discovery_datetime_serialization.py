from __future__ import annotations

import datetime
import json

import pytest
from sqlalchemy.pool import StaticPool
from sqlmodel import SQLModel, create_engine

from discovery.models import (
    ActionType,
    DomainType,
    ExperimentResponse,
    InterestSignalResponse,
    SessionResponse,
)
from discovery.repository import DiscoveryRepository


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


class TestDatetimeSerialization:
    """DBから読み直したdatetimeレスポンスがoffset付き文字列でシリアライズされることを検証する。"""

    def _assert_has_offset(self, value: str) -> None:
        assert isinstance(value, str)
        assert value.endswith("+00:00") or value.endswith("Z"), (
            f"expected UTC offset string, got {value!r}"
        )

    def _serialize_response(self, response: SQLModel) -> dict:
        return json.loads(response.model_dump_json())

    def test_session_response_created_at_has_offset(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        fetched = repository.get_session(session.id)
        assert fetched is not None
        response = SessionResponse.model_validate(fetched)
        serialized = self._serialize_response(response)
        self._assert_has_offset(serialized["created_at"])
        self._assert_has_offset(serialized["updated_at"])

    def test_signal_response_created_at_has_offset(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        signal = repository.add_signal(
            session.id,
            action_type=ActionType.SEARCH,
            domain=DomainType.TECH,
            content_summary="Python tutorial",
            source="search_history",
            occurred_at=datetime.datetime.now(datetime.timezone.utc),
        )
        signals = repository.list_signals(session.id)
        assert len(signals) == 1
        response = InterestSignalResponse.model_validate(signals[0])
        serialized = self._serialize_response(response)
        self._assert_has_offset(serialized["created_at"])
        self._assert_has_offset(serialized["occurred_at"])

    def test_experiment_response_completed_at_has_offset(self, repository: DiscoveryRepository) -> None:
        session = repository.create_session("student-a")
        experiment = repository.create_experiment(
            session.id,
            title="Try coding",
            description="Write Python",
            domain=DomainType.TECH,
            planned_minutes=10,
        )
        repository.select_experiment(experiment.id, "I want to try this")
        repository.start_experiment(experiment.id)
        result = repository.complete_experiment(
            experiment.id,
            enjoyment=4,
            curiosity=5,
            retry_intent=3,
            confidence=0.8,
        )
        fetched_experiment = repository.get_experiment(experiment.id)
        assert fetched_experiment is not None
        response = ExperimentResponse.model_validate(fetched_experiment)
        serialized = self._serialize_response(response)
        assert serialized["completed_at"] is not None
        self._assert_has_offset(serialized["completed_at"])
        assert serialized["started_at"] is not None
        self._assert_has_offset(serialized["started_at"])

        # complete_experiment の actual_minutes 計算で tz-aware 同士の減算が行われること
        assert fetched_experiment.actual_minutes is not None
        assert fetched_experiment.actual_minutes >= 0
