from __future__ import annotations

import datetime
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.pool import StaticPool
from sqlmodel import SQLModel, create_engine

from discovery.aggregation import build_notification_candidates
from discovery.gemini_prompts import DiscoveryGeminiClient
from discovery.models import (
    DomainType,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    NotificationCandidateDomainStatus,
    NotificationCandidateResponse,
)
from discovery.repository import DiscoveryRepository
from discovery.router import get_gemini_client, get_repository
from main import app


@pytest.fixture
def test_client() -> TestClient:
    engine = create_engine(
        "sqlite:///:memory:?cache=shared",
        echo=False,
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    SQLModel.metadata.create_all(engine)
    repo = DiscoveryRepository(engine)

    app.dependency_overrides[get_repository] = lambda: repo
    app.dependency_overrides[get_gemini_client] = lambda: MagicMock(spec=DiscoveryGeminiClient)

    with TestClient(app) as client:
        yield client

    app.dependency_overrides.clear()


class TestBuildNotificationCandidates:
    def _experiment(
        self,
        id: int,
        domain: str,
        status: str = ExperimentStatus.SELECTED.value,
        planned_minutes: int = 10,
        actual_minutes: int | None = None,
        selected_at: datetime.datetime | None = None,
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
            selected_at=selected_at,
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

    def test_empty_experiments_returns_empty(self) -> None:
        candidates = build_notification_candidates([], [])
        assert candidates == []

    def test_generated_started_completed_skipped_are_excluded(self) -> None:
        experiments = [
            self._experiment(1, DomainType.TECH.value, status=ExperimentStatus.GENERATED.value),
            self._experiment(2, DomainType.TECH.value, status=ExperimentStatus.STARTED.value),
            self._experiment(3, DomainType.TECH.value, status=ExperimentStatus.COMPLETED.value),
            self._experiment(4, DomainType.TECH.value, status=ExperimentStatus.SKIPPED.value),
        ]
        candidates = build_notification_candidates(experiments, [])
        assert candidates == []

    def test_selected_only_included(self) -> None:
        experiments = [
            self._experiment(1, DomainType.TECH.value, status=ExperimentStatus.GENERATED.value),
            self._experiment(2, DomainType.TECH.value, status=ExperimentStatus.SELECTED.value),
        ]
        candidates = build_notification_candidates(experiments, [])
        assert len(candidates) == 1
        assert candidates[0].experiment.id == 2

    def test_selected_experiment_without_completed_is_explored(self) -> None:
        """選択済み実験だけのドメインは、未完了でもEXPLOREDとして候補になる。"""
        experiments = [
            self._experiment(1, DomainType.TECH.value),
        ]
        candidates = build_notification_candidates(experiments, [])
        assert len(candidates) == 1
        assert candidates[0].domain_status == NotificationCandidateDomainStatus.EXPLORED.value

    def test_explored_domain_status(self) -> None:
        experiments = [
            self._experiment(1, DomainType.TECH.value, status=ExperimentStatus.GENERATED.value),
            self._experiment(2, DomainType.TECH.value),
        ]
        candidates = build_notification_candidates(experiments, [])
        assert len(candidates) == 1
        assert candidates[0].domain_status == NotificationCandidateDomainStatus.EXPLORED.value

    def test_tried_domain_status(self) -> None:
        experiments = [
            self._experiment(1, DomainType.TECH.value, status=ExperimentStatus.COMPLETED.value),
            self._experiment(2, DomainType.TECH.value),
        ]
        candidates = build_notification_candidates(experiments, [])
        assert len(candidates) == 1
        assert candidates[0].domain_status == NotificationCandidateDomainStatus.TRIED.value

    def test_dive_candidate_domain_status(self) -> None:
        experiments = [
            self._experiment(1, DomainType.TECH.value, status=ExperimentStatus.COMPLETED.value, actual_minutes=15),
            self._experiment(2, DomainType.TECH.value, status=ExperimentStatus.COMPLETED.value, actual_minutes=15),
            self._experiment(3, DomainType.TECH.value),
        ]
        results = [self._result(1), self._result(2)]
        candidates = build_notification_candidates(experiments, results)
        assert len(candidates) == 1
        assert candidates[0].domain_status == NotificationCandidateDomainStatus.DIVE_CANDIDATE.value

    def test_dive_boundary_exact_thresholds(self) -> None:
        """2件の評価付き完了実験で全平均が閾値ちょうど、時間比率も1.5ちょうどならDIVE。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, status=ExperimentStatus.COMPLETED.value, actual_minutes=15),
            self._experiment(2, domain, status=ExperimentStatus.COMPLETED.value, actual_minutes=15),
            self._experiment(3, domain),
        ]
        results = [self._result(1), self._result(2)]
        candidates = build_notification_candidates(experiments, results)
        assert candidates[0].domain_status == NotificationCandidateDomainStatus.DIVE_CANDIDATE.value

    def test_low_rating_is_dive_not_tried(self) -> None:
        """DIVE閾値未満のドメインは、選択済み実験があれば通常候補（TRIED）にできる。"""
        domain = DomainType.TECH.value
        experiments = [
            self._experiment(1, domain, status=ExperimentStatus.COMPLETED.value, actual_minutes=15),
            self._experiment(2, domain, status=ExperimentStatus.COMPLETED.value, actual_minutes=15),
            self._experiment(3, domain),
        ]
        results = [
            self._result(1),
            self._result(2, enjoyment=3),
        ]
        candidates = build_notification_candidates(experiments, results)
        assert len(candidates) == 1
        assert candidates[0].domain_status == NotificationCandidateDomainStatus.TRIED.value

    def test_ordering_dive_before_tried_before_explored(self) -> None:
        base = datetime.datetime(2026, 9, 7, 10, 0, 0, tzinfo=datetime.timezone.utc)
        experiments = [
            # EXPLORED domain, selected earliest
            self._experiment(1, DomainType.ART.value, selected_at=base - datetime.timedelta(minutes=10)),
            # TRIED domain, selected in the middle
            self._experiment(
                2,
                DomainType.MUSIC.value,
                status=ExperimentStatus.COMPLETED.value,
                selected_at=base,
            ),
            self._experiment(
                3,
                DomainType.MUSIC.value,
                selected_at=base + datetime.timedelta(minutes=5),
            ),
            # DIVE domain, selected latest
            self._experiment(
                4,
                DomainType.TECH.value,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=15,
                selected_at=base + datetime.timedelta(minutes=10),
            ),
            self._experiment(
                5,
                DomainType.TECH.value,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=15,
                selected_at=base + datetime.timedelta(minutes=15),
            ),
            self._experiment(
                6,
                DomainType.TECH.value,
                selected_at=base + datetime.timedelta(minutes=20),
            ),
        ]
        results = [self._result(4), self._result(5)]
        candidates = build_notification_candidates(experiments, results)
        statuses = [c.domain_status for c in candidates]
        assert statuses == [
            NotificationCandidateDomainStatus.DIVE_CANDIDATE.value,
            NotificationCandidateDomainStatus.TRIED.value,
            NotificationCandidateDomainStatus.EXPLORED.value,
        ]
        assert candidates[0].experiment.id == 6  # DIVE selected latest still first
        assert candidates[1].experiment.id == 3  # TRIED selected later
        assert candidates[2].experiment.id == 1  # EXPLORED selected earliest

    def test_same_status_sorted_by_selected_at_then_id(self) -> None:
        base = datetime.datetime(2026, 9, 7, 10, 0, 0, tzinfo=datetime.timezone.utc)
        experiments = [
            self._experiment(2, DomainType.TECH.value, selected_at=base + datetime.timedelta(minutes=5)),
            self._experiment(1, DomainType.TECH.value, selected_at=base),
            self._experiment(3, DomainType.TECH.value, selected_at=base),
        ]
        # All EXPLORED because no completed experiments
        candidates = build_notification_candidates(experiments, [])
        assert [c.experiment.id for c in candidates] == [1, 3, 2]


class TestNotificationCandidatesEndpoint:
    def _repo(self) -> DiscoveryRepository:
        return app.dependency_overrides[get_repository]()

    def _create_session(self, test_client: TestClient) -> dict:
        return test_client.post("/sessions", json={"student_label": "student-a"}).json()

    def _seed_signal(self, test_client: TestClient, session_id: int) -> None:
        test_client.post(
            f"/sessions/{session_id}/signals",
            json={
                "action_type": "search",
                "domain": "tech",
                "content_summary": "Python tutorial",
                "source": "search_history",
                "occurred_at": "2026-09-01T10:00:00Z",
            },
        )

    def _generate_experiment(self, test_client: TestClient, session_id: int) -> dict:
        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.generate_experiments.return_value = [
            MagicMock(
                title="Hello Python",
                description="Write a one-line print script",
                domain="tech",
                planned_minutes=10,
            ),
        ]
        app.dependency_overrides[get_gemini_client] = lambda: mock_client
        generated = test_client.post(
            f"/sessions/{session_id}/experiments/generate",
            json={"n_candidates": 1},
        ).json()
        return generated[0]

    def test_endpoint_returns_selected_candidate(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )

        response = test_client.get(f"/sessions/{session_id}/notification-candidates")
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 1
        assert data[0]["experiment"]["id"] == experiment_id
        assert data[0]["domain_status"] == NotificationCandidateDomainStatus.EXPLORED.value
        assert "reason" in data[0]

    def test_endpoint_returns_empty_when_none_selected(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        self._generate_experiment(test_client, session_id)

        response = test_client.get(f"/sessions/{session_id}/notification-candidates")
        assert response.status_code == 200
        assert response.json() == []

    def test_endpoint_returns_404_for_missing_session(self, test_client: TestClient) -> None:
        response = test_client.get("/sessions/999/notification-candidates")
        assert response.status_code == 404

    def test_endpoint_orders_dive_first(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.generate_experiments.return_value = [
            MagicMock(
                title="Hello Python",
                description="desc",
                domain="tech",
                planned_minutes=10,
            ),
            MagicMock(
                title="Hello Art",
                description="desc",
                domain="art",
                planned_minutes=10,
            ),
        ]
        app.dependency_overrides[get_gemini_client] = lambda: mock_client
        generated = test_client.post(
            f"/sessions/{session_id}/experiments/generate",
            json={"n_candidates": 2},
        ).json()
        tech_id = generated[0]["id"]
        art_id = generated[1]["id"]

        # Make tech domain a DIVE_CANDIDATE by completing two highly-rated experiments.
        test_client.post(f"/experiments/{tech_id}/select", json={"selection_note": "note"})
        test_client.post(f"/experiments/{tech_id}/start")
        test_client.post(
            f"/experiments/{tech_id}/complete",
            json={"enjoyment": 5, "curiosity": 5, "retry_intent": 5, "confidence": 0.9},
        )
        # Generate another tech experiment to select.
        mock_client.generate_experiments.return_value = [
            MagicMock(
                title="More Python",
                description="desc",
                domain="tech",
                planned_minutes=10,
            ),
        ]
        second_tech = test_client.post(
            f"/sessions/{session_id}/experiments/generate",
            json={"n_candidates": 1},
        ).json()[0]
        test_client.post(f"/experiments/{second_tech['id']}/select", json={"selection_note": "note"})
        test_client.post(f"/experiments/{second_tech['id']}/start")
        # Long duration ratio to satisfy DIVE boundary.
        import datetime as dt
        repo = self._repo()
        exp_obj = repo.get_experiment(second_tech["id"])
        assert exp_obj is not None
        assert exp_obj.started_at is not None
        exp_obj.started_at = exp_obj.started_at - dt.timedelta(minutes=20)
        from sqlmodel import Session
        with Session(repo._engine) as db:
            db.add(exp_obj)
            db.commit()
        test_client.post(
            f"/experiments/{second_tech['id']}/complete",
            json={"enjoyment": 5, "curiosity": 5, "retry_intent": 5, "confidence": 0.9},
        )

        # Select a third tech experiment (should be DIVE_CANDIDATE).
        mock_client.generate_experiments.return_value = [
            MagicMock(
                title="Deep Python",
                description="desc",
                domain="tech",
                planned_minutes=10,
            ),
        ]
        third_tech = test_client.post(
            f"/sessions/{session_id}/experiments/generate",
            json={"n_candidates": 1},
        ).json()[0]
        test_client.post(f"/experiments/{third_tech['id']}/select", json={"selection_note": "note"})

        # Select art experiment (EXPLORED).
        test_client.post(f"/experiments/{art_id}/select", json={"selection_note": "note"})

        response = test_client.get(f"/sessions/{session_id}/notification-candidates")
        assert response.status_code == 200
        data = response.json()
        assert len(data) == 2
        assert data[0]["experiment"]["domain"] == "tech"
        assert data[0]["domain_status"] == NotificationCandidateDomainStatus.DIVE_CANDIDATE.value
        assert data[1]["experiment"]["domain"] == "art"
        assert data[1]["domain_status"] == NotificationCandidateDomainStatus.EXPLORED.value
