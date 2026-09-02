from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from discovery.gemini_prompts import DiscoveryGeminiClient
from discovery.models import (
    ActionType,
    DomainType,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    InterestSignal,
    InterestSignalSource,
)


@pytest.fixture
def client() -> DiscoveryGeminiClient:
    client = DiscoveryGeminiClient(api_key="dummy-key")
    client._client = MagicMock()
    client._client.models.generate_content = MagicMock()
    return client


@pytest.fixture
def mock_response() -> MagicMock:
    response = MagicMock()
    response.text = """[
      {
        "title": "10分でPythonのHello Worldを書く",
        "description": "ターミナルを開き、print文だけで動くプログラムを作る",
        "domain": "tech",
        "planned_minutes": 10
      },
      {
        "title": "5分で水彩画の色見本を作る",
        "description": "持っている絵の具で3色だけ色見本を塗る",
        "domain": "art",
        "planned_minutes": 5
      }
    ]"""
    return response


class TestGenerateExperiments:
    def test_returns_experiment_candidates(self, client: DiscoveryGeminiClient, mock_response: MagicMock) -> None:
        client._client.models.generate_content.return_value = mock_response
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python tutorial",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at="2026-09-01T10:00:00Z",
            ),
        ]
        experiments = client.generate_experiments(signals, n_candidates=2)
        assert len(experiments) == 2
        assert experiments[0].domain == DomainType.TECH.value
        assert 5 <= experiments[0].planned_minutes <= 15
        assert experiments[0].title != ""

    def test_rejects_malformed_json(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = "not valid json"
        client._client.models.generate_content.return_value = response
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python tutorial",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at="2026-09-01T10:00:00Z",
            ),
        ]
        with pytest.raises(ValueError):
            client.generate_experiments(signals, n_candidates=2)

    def test_rejects_out_of_range_planned_minutes(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = """[{
            "title": "Too long",
            "description": "desc",
            "domain": "tech",
            "planned_minutes": 20
        }]"""
        client._client.models.generate_content.return_value = response
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python tutorial",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at="2026-09-01T10:00:00Z",
            ),
        ]
        with pytest.raises(ValueError):
            client.generate_experiments(signals, n_candidates=1)


class TestUpdateHypothesis:
    def test_returns_hypothesis_data(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = """{
            "summary": "技術分野への興味が強い",
            "confidence": 0.8,
            "supporting_evidence": [
                {"domain": "tech", "description": "検索シグナルが多い"}
            ],
            "suggested_next_domains": ["art", "music"]
        }"""
        client._client.models.generate_content.return_value = response
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python tutorial",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at="2026-09-01T10:00:00Z",
            ),
        ]
        experiments = [
            Experiment(
                id=1,
                session_id=1,
                title="Try coding",
                description="desc",
                domain=DomainType.TECH.value,
                planned_minutes=10,
                status=ExperimentStatus.COMPLETED.value,
                actual_minutes=10,
            ),
        ]
        results = [
            ExperimentResult(
                experiment_id=1,
                enjoyment=4,
                curiosity=5,
                retry_intent=4,
                confidence=0.8,
            ),
        ]
        hypothesis = client.update_hypothesis(signals, experiments, results)
        assert hypothesis["summary"] == "技術分野への興味が強い"
        assert hypothesis["confidence"] == 0.8
        assert hypothesis["suggested_next_domains"] == ["art", "music"]

    def test_rejects_malformed_hypothesis_json(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = "not valid json"
        client._client.models.generate_content.return_value = response
        with pytest.raises(ValueError):
            client.update_hypothesis([], [], [])


class TestClientErrorHandling:
    def test_missing_api_key_raises_runtime_error(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("GEMINI_API_KEY", raising=False)
        client = DiscoveryGeminiClient(api_key=None)
        with pytest.raises(RuntimeError) as exc_info:
            client.generate_experiments([])
        assert "Gemini API" in str(exc_info.value)

    def test_sdk_exception_is_wrapped_as_runtime_error(self, client: DiscoveryGeminiClient) -> None:
        from google.genai.errors import APIError

        client._client.models.generate_content.side_effect = APIError(
            code=500, response_json={"error": "network error"}
        )
        signals = [
            InterestSignal(
                session_id=1,
                action_type=ActionType.SEARCH.value,
                domain=DomainType.TECH.value,
                content_summary="Python tutorial",
                source=InterestSignalSource.SEARCH_HISTORY.value,
                occurred_at="2026-09-01T10:00:00Z",
            ),
        ]
        with pytest.raises(RuntimeError) as exc_info:
            client.generate_experiments(signals, n_candidates=1)
        assert "network error" not in str(exc_info.value)
        assert "Gemini API" in str(exc_info.value)

    def test_update_hypothesis_sdk_exception_is_wrapped(self, client: DiscoveryGeminiClient) -> None:
        from google.genai.errors import APIError

        client._client.models.generate_content.side_effect = APIError(
            code=500, response_json={"error": "network error"}
        )
        with pytest.raises(RuntimeError) as exc_info:
            client.update_hypothesis([], [], [])
        assert "network error" not in str(exc_info.value)
        assert "Gemini API" in str(exc_info.value)
