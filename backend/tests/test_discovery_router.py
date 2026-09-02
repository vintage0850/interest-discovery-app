from __future__ import annotations

from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.pool import StaticPool
from sqlmodel import SQLModel, create_engine

from discovery.gemini_prompts import DiscoveryGeminiClient
from discovery.models import DomainType
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


class TestSessionEndpoints:
    def test_create_session(self, test_client: TestClient) -> None:
        response = test_client.post("/sessions", json={"student_label": "student-a"})
        assert response.status_code == 201
        data = response.json()
        assert data["student_label"] == "student-a"
        assert data["status"] == "active"
        assert "id" in data

    def test_create_session_missing_label(self, test_client: TestClient) -> None:
        response = test_client.post("/sessions", json={})
        assert response.status_code == 422


class TestSignalEndpoints:
    def test_add_signal(self, test_client: TestClient) -> None:
        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        payload = {
            "action_type": "search",
            "domain": "tech",
            "content_summary": "Python tutorial",
            "source": "search_history",
            "occurred_at": "2026-09-01T10:00:00Z",
        }
        response = test_client.post(f"/sessions/{session['id']}/signals", json=payload)
        assert response.status_code == 201
        data = response.json()
        assert data["session_id"] == session["id"]
        assert data["domain"] == "tech"
        assert data["action_type"] == "search"

    def test_add_signal_invalid_domain(self, test_client: TestClient) -> None:
        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        payload = {
            "action_type": "search",
            "domain": "invalid_domain",
            "content_summary": "summary",
            "source": "search_history",
            "occurred_at": "2026-09-01T10:00:00Z",
        }
        response = test_client.post(f"/sessions/{session['id']}/signals", json=payload)
        assert response.status_code == 422

    def test_add_signal_session_not_found(self, test_client: TestClient) -> None:
        payload = {
            "action_type": "search",
            "domain": "tech",
            "content_summary": "summary",
            "source": "search_history",
            "occurred_at": "2026-09-01T10:00:00Z",
        }
        response = test_client.post("/sessions/999/signals", json=payload)
        assert response.status_code == 404


class TestExperimentEndpoints:
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

    def test_generate_experiments(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        self._seed_signal(test_client, session["id"])

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

        response = test_client.post(
            f"/sessions/{session['id']}/experiments/generate",
            json={"n_candidates": 1},
        )
        assert response.status_code == 201
        data = response.json()
        assert len(data) == 1
        assert data[0]["title"] == "Hello Python"
        assert data[0]["session_id"] == session["id"]
        assert data[0]["planned_minutes"] == 10

    def test_generate_experiments_gemini_error(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        self._seed_signal(test_client, session["id"])

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.generate_experiments.side_effect = ValueError("malformed json")
        app.dependency_overrides[get_gemini_client] = lambda: mock_client

        response = test_client.post(
            f"/sessions/{session['id']}/experiments/generate",
            json={"n_candidates": 1},
        )
        assert response.status_code == 503

    def test_select_experiment(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        response = test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "selected"
        assert data["selection_note"] == "I want to try this"

        # EXPERIMENT_SELECTED シグナルが自動生成されること
        signals = self._repo().list_signals(session_id)
        selected_signals = [s for s in signals if s.action_type == "EXPERIMENT_SELECTED"]
        assert len(selected_signals) == 1
        assert selected_signals[0].domain == "tech"

    def test_select_experiment_not_found(self, test_client: TestClient) -> None:
        response = test_client.post(
            "/experiments/999/select",
            json={"selection_note": "note"},
        )
        assert response.status_code == 404

    def test_skip_experiment(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        response = test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "Not now"},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "skipped"
        assert data["skip_reason"] == "Not now"

        # EXPERIMENT_SKIPPED シグナルが自動生成されること
        signals = self._repo().list_signals(session_id)
        skipped_signals = [s for s in signals if s.action_type == "EXPERIMENT_SKIPPED"]
        assert len(skipped_signals) == 1
        assert skipped_signals[0].domain == "tech"

    def test_start_experiment(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        # SELECTED 状態を経由して STARTED に遷移すること
        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        response = test_client.post(f"/experiments/{experiment_id}/start")
        assert response.status_code == 200
        assert response.json()["status"] == "started"

        # EXPERIMENT_STARTED シグナルが自動生成されること
        signals = self._repo().list_signals(session_id)
        started_signals = [s for s in signals if s.action_type == "EXPERIMENT_STARTED"]
        assert len(started_signals) == 1
        assert started_signals[0].domain == "tech"

    def test_complete_experiment(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")

        response = test_client.post(
            f"/experiments/{experiment_id}/complete",
            json={
                "enjoyment": 4,
                "curiosity": 5,
                "retry_intent": 3,
                "confidence": 0.8,
                "reflection": "It was fun",
            },
        )
        assert response.status_code == 201
        data = response.json()
        assert data["enjoyment"] == 4
        assert data["confidence"] == 0.8

        # actual_minutes はサーバー側で計算されること
        experiment_obj = self._repo().get_experiment(experiment_id)
        assert experiment_obj is not None
        assert experiment_obj.actual_minutes is not None
        assert experiment_obj.actual_minutes >= 0

        # EXPERIMENT_COMPLETED / EXPLICIT_FEEDBACK シグナルが自動生成されること
        signals = self._repo().list_signals(session_id)
        completed_signals = [s for s in signals if s.action_type == "EXPERIMENT_COMPLETED"]
        feedback_signals = [s for s in signals if s.action_type == "EXPLICIT_FEEDBACK"]
        assert len(completed_signals) == 1
        assert len(feedback_signals) == 1

    def test_complete_experiment_longer_than_planned(self, test_client: TestClient) -> None:
        """duration_ratio >= 1.5 の場合 LONGER_THAN_PLANNED シグナルが生成される。"""
        import datetime

        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")

        # 実験所要時間を長く見せるため、started_at を過去に更新する
        repo = self._repo()
        experiment_obj = repo.get_experiment(experiment_id)
        assert experiment_obj is not None
        assert experiment_obj.started_at is not None
        experiment_obj.started_at = experiment_obj.started_at - datetime.timedelta(minutes=20)
        from sqlmodel import Session
        with Session(repo._engine) as db:
            db.add(experiment_obj)
            db.commit()

        response = test_client.post(
            f"/experiments/{experiment_id}/complete",
            json={
                "enjoyment": 4,
                "curiosity": 5,
                "retry_intent": 3,
                "confidence": 0.8,
                "reflection": "It took longer",
            },
        )
        assert response.status_code == 201

        experiment_obj = self._repo().get_experiment(experiment_id)
        assert experiment_obj is not None
        planned = experiment_obj.planned_minutes
        actual = experiment_obj.actual_minutes
        assert actual is not None
        assert actual / planned >= 1.5

        signals = self._repo().list_signals(session_id)
        longer_signals = [s for s in signals if s.action_type == "LONGER_THAN_PLANNED"]
        assert len(longer_signals) == 1

    def test_complete_experiment_invalid_score(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(f"/experiments/{experiment_id}/start")

        response = test_client.post(
            f"/experiments/{experiment_id}/complete",
            json={
                "enjoyment": 6,
                "curiosity": 5,
                "retry_intent": 3,
                "confidence": 0.8,
            },
        )
        assert response.status_code == 422

    def test_experiment_not_found(self, test_client: TestClient) -> None:
        response = test_client.post("/experiments/999/start")
        assert response.status_code == 404

    def test_double_select_is_idempotent(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        response1 = test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        response2 = test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        assert response1.status_code == 200
        assert response2.status_code == 200
        signals = self._repo().list_signals(session_id)
        assert len([s for s in signals if s.action_type == "EXPERIMENT_SELECTED"]) == 1

    def test_double_start_is_idempotent(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        response1 = test_client.post(f"/experiments/{experiment_id}/start")
        response2 = test_client.post(f"/experiments/{experiment_id}/start")
        assert response1.status_code == 200
        assert response2.status_code == 200
        signals = self._repo().list_signals(session_id)
        assert len([s for s in signals if s.action_type == "EXPERIMENT_STARTED"]) == 1

    def test_double_complete_is_idempotent(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "I want to try this"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")
        payload = {
            "enjoyment": 4,
            "curiosity": 5,
            "retry_intent": 3,
            "confidence": 0.8,
        }
        response1 = test_client.post(
            f"/experiments/{experiment_id}/complete",
            json=payload,
        )
        response2 = test_client.post(
            f"/experiments/{experiment_id}/complete",
            json=payload,
        )
        assert response1.status_code == 201
        assert response2.status_code == 201
        assert response1.json()["id"] == response2.json()["id"]
        signals = self._repo().list_signals(session_id)
        assert len([s for s in signals if s.action_type == "EXPERIMENT_COMPLETED"]) == 1
        assert len([s for s in signals if s.action_type == "EXPLICIT_FEEDBACK"]) == 1

    def test_skip_after_complete_is_rejected(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "note"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")
        test_client.post(
            f"/experiments/{experiment_id}/complete",
            json={"enjoyment": 4, "curiosity": 5, "retry_intent": 3, "confidence": 0.8},
        )
        response = test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "too late"},
        )
        assert response.status_code == 409

    def test_start_after_skip_is_rejected(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "Not now"},
        )
        response = test_client.post(f"/experiments/{experiment_id}/start")
        assert response.status_code == 409

    def test_start_from_generated_is_rejected(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        response = test_client.post(f"/experiments/{experiment_id}/start")
        assert response.status_code == 409

    def test_skip_after_start_is_rejected(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "note"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")
        response = test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "changed mind"},
        )
        assert response.status_code == 409

    def test_select_after_skip_is_rejected(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "Not now"},
        )
        response = test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "note"},
        )
        assert response.status_code == 409

    def test_double_skip_is_idempotent(self, test_client: TestClient) -> None:
        session = self._create_session(test_client)
        session_id = session["id"]
        self._seed_signal(test_client, session_id)
        experiment = self._generate_experiment(test_client, session_id)
        experiment_id = experiment["id"]

        response1 = test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "Not now"},
        )
        response2 = test_client.post(
            f"/experiments/{experiment_id}/skip",
            json={"reason": "Not now"},
        )
        assert response1.status_code == 200
        assert response2.status_code == 200
        signals = self._repo().list_signals(session_id)
        assert len([s for s in signals if s.action_type == "EXPERIMENT_SKIPPED"]) == 1
    def _create_full_session(self, test_client: TestClient) -> int:
        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        session_id = session["id"]
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
        experiment_id = generated[0]["id"]
        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "note"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")
        test_client.post(
            f"/experiments/{experiment_id}/complete",
            json={
                "enjoyment": 4,
                "curiosity": 5,
                "retry_intent": 3,
                "confidence": 0.8,
            },
        )
        return session_id

    def test_update_hypothesis(self, test_client: TestClient) -> None:
        session_id = self._create_full_session(test_client)

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.update_hypothesis.return_value = {
            "summary": "技術分野への興味が強い",
            "confidence": 0.8,
            "supporting_evidence": [{"domain": "tech", "description": "検索シグナルが多い"}],
            "suggested_next_domains": ["art", "music"],
        }
        app.dependency_overrides[get_gemini_client] = lambda: mock_client

        response = test_client.post(
            f"/sessions/{session_id}/hypothesis/update",
            json={},
        )
        assert response.status_code == 201
        data = response.json()
        assert data["summary"] == "技術分野への興味が強い"
        assert data["confidence"] == 0.8
        assert data["suggested_next_domains"] == ["art", "music"]

    def test_update_hypothesis_gemini_error(self, test_client: TestClient) -> None:
        session_id = self._create_full_session(test_client)

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.update_hypothesis.side_effect = ValueError("malformed json")
        app.dependency_overrides[get_gemini_client] = lambda: mock_client

        response = test_client.post(
            f"/sessions/{session_id}/hypothesis/update",
            json={},
        )
        assert response.status_code == 503

    def test_get_summary(self, test_client: TestClient) -> None:
        session_id = self._create_full_session(test_client)

        response = test_client.get(f"/sessions/{session_id}/summary")
        assert response.status_code == 200
        data = response.json()
        assert data["session"]["id"] == session_id
        # 1件の手動シグナル + select/start/complete で生成される4件の自動シグナル
        assert data["behavior_summary"]["total_signals"] == 5
        assert data["behavior_summary"]["total_experiments"] == 1
        assert data["behavior_summary"]["completed_experiments"] == 1


class TestExistingEndpoints:
    def test_health_endpoint(self, test_client: TestClient) -> None:
        response = test_client.get("/health")
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}

    def test_cases_analyze_regression(self, test_client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
        """既存 /cases/analyze がモック化された GeminiClient で動作すること。"""
        from main import GeminiClient

        mock_client = MagicMock(spec=GeminiClient)
        mock_client.analyze.return_value = MagicMock(
            questions=[
                {
                    "title": "Sample question",
                    "reason": "Because",
                    "risk_level": "MEDIUM",
                    "source_text": "contract text",
                    "source_page": 1,
                }
            ]
        )
        monkeypatch.setattr("main.get_client", lambda: mock_client)

        response = test_client.post(
            "/cases/analyze",
            json={
                "case_id": 1,
                "document_text": "sample contract",
                "user_context": {"age": 20},
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert len(data["questions"]) == 1
        assert data["questions"][0]["title"] == "Sample question"
        # APIキー等の秘密情報がレスポンスに含まれないこと
        assert "GEMINI_API_KEY" not in response.text


class TestGeminiErrorHandling:
    def _create_full_session(self, test_client: TestClient) -> int:
        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        session_id = session["id"]
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
        experiment_id = generated[0]["id"]
        test_client.post(
            f"/experiments/{experiment_id}/select",
            json={"selection_note": "note"},
        )
        test_client.post(f"/experiments/{experiment_id}/start")
        test_client.post(
            f"/experiments/{experiment_id}/complete",
            json={
                "enjoyment": 4,
                "curiosity": 5,
                "retry_intent": 3,
                "confidence": 0.8,
            },
        )
        return session_id

    def test_generate_experiments_returns_503_when_api_key_missing(
        self,
        test_client: TestClient,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.delenv("GEMINI_API_KEY", raising=False)
        app.dependency_overrides[get_gemini_client] = lambda: DiscoveryGeminiClient(api_key=None)

        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        response = test_client.post(
            f"/sessions/{session['id']}/experiments/generate",
            json={"n_candidates": 1},
        )
        assert response.status_code == 503
        assert "GEMINI_API_KEY" not in response.text

    def test_update_hypothesis_returns_503_when_api_key_missing(
        self,
        test_client: TestClient,
        monkeypatch: pytest.MonkeyPatch,
    ) -> None:
        monkeypatch.delenv("GEMINI_API_KEY", raising=False)
        session_id = self._create_full_session(test_client)
        app.dependency_overrides[get_gemini_client] = lambda: DiscoveryGeminiClient(api_key=None)

        response = test_client.post(
            f"/sessions/{session_id}/hypothesis/update",
            json={},
        )
        assert response.status_code == 503
        assert "GEMINI_API_KEY" not in response.text

    def test_generate_experiments_returns_503_on_sdk_error(self, test_client: TestClient) -> None:
        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        test_client.post(
            f"/sessions/{session['id']}/signals",
            json={
                "action_type": "search",
                "domain": "tech",
                "content_summary": "Python tutorial",
                "source": "search_history",
                "occurred_at": "2026-09-01T10:00:00Z",
            },
        )

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.generate_experiments.side_effect = RuntimeError("Gemini API request failed")
        app.dependency_overrides[get_gemini_client] = lambda: mock_client

        response = test_client.post(
            f"/sessions/{session['id']}/experiments/generate",
            json={"n_candidates": 1},
        )
        assert response.status_code == 503
        assert "Gemini API request failed" not in response.text


class TestValidationEndpoints:
    def test_create_session_rejects_empty_label(self, test_client: TestClient) -> None:
        response = test_client.post("/sessions", json={"student_label": ""})
        assert response.status_code == 422

    def test_create_session_rejects_whitespace_label(self, test_client: TestClient) -> None:
        response = test_client.post("/sessions", json={"student_label": "   "})
        assert response.status_code == 422

    def test_create_session_rejects_too_long_label(self, test_client: TestClient) -> None:
        response = test_client.post("/sessions", json={"student_label": "a" * 101})
        assert response.status_code == 422

    def test_add_signal_rejects_naive_occurred_at(self, test_client: TestClient) -> None:
        session = test_client.post("/sessions", json={"student_label": "student-a"}).json()
        payload = {
            "action_type": "search",
            "domain": "tech",
            "content_summary": "Python tutorial",
            "source": "search_history",
            "occurred_at": "2026-09-01T10:00:00",
        }
        response = test_client.post(f"/sessions/{session['id']}/signals", json=payload)
        assert response.status_code == 422
