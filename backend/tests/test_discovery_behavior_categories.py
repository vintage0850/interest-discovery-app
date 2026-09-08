from __future__ import annotations

import json
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.pool import StaticPool
from sqlmodel import SQLModel, create_engine

from discovery.gemini_prompts import DiscoveryGeminiClient
from discovery.models import BehaviorCategory, DomainType, Evidence
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
    app.dependency_overrides[get_gemini_client] = lambda: MagicMock(
        spec=DiscoveryGeminiClient
    )

    with TestClient(app) as client:
        yield client

    app.dependency_overrides.clear()


@pytest.fixture
def client() -> DiscoveryGeminiClient:
    client = DiscoveryGeminiClient(api_key="dummy-key")
    client._client = MagicMock()
    client._client.models.generate_content = MagicMock()
    return client


def _create_full_session(test_client: TestClient) -> int:
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


class TestBehaviorCategoryEnum:
    def test_all_expected_categories_exist(self) -> None:
        values = {c.value for c in BehaviorCategory}
        expected = {
            "EXPLORE",
            "COMPARE",
            "ANALYZE",
            "CREATE",
            "IMPROVE",
            "ORGANIZE",
            "PRACTICE",
            "COMMUNICATE",
            "DECIDE",
            "REFLECT",
        }
        assert values == expected


class TestEvidenceBehaviorCategoriesValidation:
    def test_valid_categories_accepted(self) -> None:
        evidence = Evidence(
            session_id=1,
            domain=DomainType.TECH.value,
            signal_count=2,
            summary_text="summary",
            behavior_categories={
                BehaviorCategory.COMPARE.value: 0.7,
                BehaviorCategory.DECIDE.value: 0.3,
            },
        )
        assert evidence.behavior_categories == {
            "COMPARE": 0.7,
            "DECIDE": 0.3,
        }

    def test_invalid_category_name_rejected(self) -> None:
        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=2,
                summary_text="summary",
                behavior_categories={"INVALID": 0.5},
            )

    def test_score_above_one_rejected(self) -> None:
        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=2,
                summary_text="summary",
                behavior_categories={BehaviorCategory.CREATE.value: 1.1},
            )

    def test_score_below_zero_rejected(self) -> None:
        with pytest.raises(ValueError):
            Evidence(
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=2,
                summary_text="summary",
                behavior_categories={BehaviorCategory.CREATE.value: -0.1},
            )

    def test_none_categories_accepted(self) -> None:
        evidence = Evidence(
            session_id=1,
            domain=DomainType.TECH.value,
            signal_count=2,
            summary_text="summary",
            behavior_categories=None,
        )
        assert evidence.behavior_categories is None


class TestClassifyBehaviorCategories:
    def test_returns_category_map(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = """[
            {"evidence_id": 1, "categories": {"COMPARE": 0.7, "DECIDE": 0.3}},
            {"evidence_id": 2, "categories": {"EXPLORE": 0.9}}
        ]"""
        client._client.models.generate_content.return_value = response

        evidences = [
            Evidence(
                id=1, session_id=1, domain="tech", signal_count=1, summary_text="s1"
            ),
            Evidence(
                id=2, session_id=1, domain="art", signal_count=1, summary_text="s2"
            ),
        ]
        result = client.classify_behavior_categories(evidences, [], [])
        assert result == {
            1: {"COMPARE": 0.7, "DECIDE": 0.3},
            2: {"EXPLORE": 0.9},
        }

    def test_rejects_invalid_category_from_gemini(
        self, client: DiscoveryGeminiClient
    ) -> None:
        response = MagicMock()
        response.text = """[
            {"evidence_id": 1, "categories": {"INVALID": 0.5}}
        ]"""
        client._client.models.generate_content.return_value = response
        with pytest.raises(ValueError):
            client.classify_behavior_categories([], [], [])

    def test_rejects_score_out_of_range(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = """[
            {"evidence_id": 1, "categories": {"CREATE": 1.5}}
        ]"""
        client._client.models.generate_content.return_value = response
        with pytest.raises(ValueError):
            client.classify_behavior_categories([], [], [])

    def test_rejects_malformed_json(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = "not valid json"
        client._client.models.generate_content.return_value = response
        with pytest.raises(ValueError):
            client.classify_behavior_categories([], [], [])

    def test_sdk_exception_is_wrapped(self, client: DiscoveryGeminiClient) -> None:
        from google.genai.errors import APIError

        client._client.models.generate_content.side_effect = APIError(
            code=500, response_json={"error": "network error"}
        )
        with pytest.raises(RuntimeError) as exc_info:
            client.classify_behavior_categories([], [], [])
        assert "network error" not in str(exc_info.value)
        assert "Gemini API" in str(exc_info.value)

    def test_prompt_includes_evidence_summaries(
        self, client: DiscoveryGeminiClient
    ) -> None:
        response = MagicMock()
        response.text = """[]"""
        client._client.models.generate_content.return_value = response

        evidences = [
            Evidence(
                id=10,
                session_id=1,
                domain="tech",
                signal_count=2,
                summary_text="Python tutorial",
            ),
        ]
        client.classify_behavior_categories(evidences, [], [])
        call_args = client._client.models.generate_content.call_args
        prompt = call_args.kwargs["contents"]
        assert "Python tutorial" in prompt or "エビデンス" in prompt


class TestBehaviorCategoryRouterIntegration:
    def test_update_hypothesis_classifies_and_saves_behavior_categories(
        self, test_client: TestClient
    ) -> None:
        session_id = _create_full_session(test_client)

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.update_hypothesis.return_value = {
            "summary": "技術分野への興味が強い",
            "confidence": 0.8,
            "supporting_evidence": [1],
            "suggested_next_domains": ["art"],
        }
        mock_client.classify_behavior_categories.return_value = {
            1: {"COMPARE": 0.7, "DECIDE": 0.3},
        }
        app.dependency_overrides[get_gemini_client] = lambda: mock_client

        response = test_client.post(
            f"/sessions/{session_id}/hypothesis/update",
            json={},
        )
        assert response.status_code == 201

        repo = app.dependency_overrides[get_repository]()
        evidence = repo.list_evidence(session_id)[0]
        assert evidence.behavior_categories == {"COMPARE": 0.7, "DECIDE": 0.3}

        call_args = mock_client.classify_behavior_categories.call_args
        first_arg = call_args.args[0]
        assert isinstance(first_arg, list)
        assert len(first_arg) == 1
        assert isinstance(first_arg[0], Evidence)

    def test_update_hypothesis_returns_503_when_classification_fails(
        self, test_client: TestClient
    ) -> None:
        session_id = _create_full_session(test_client)

        mock_client = MagicMock(spec=DiscoveryGeminiClient)
        mock_client.classify_behavior_categories.side_effect = ValueError(
            "malformed json"
        )
        app.dependency_overrides[get_gemini_client] = lambda: mock_client

        response = test_client.post(
            f"/sessions/{session_id}/hypothesis/update",
            json={},
        )
        assert response.status_code == 503

        repo = app.dependency_overrides[get_repository]()
        assert repo.list_evidence(session_id)[0].behavior_categories is None
