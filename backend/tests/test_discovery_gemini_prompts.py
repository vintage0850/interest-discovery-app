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
        from discovery.models import Evidence

        response = MagicMock()
        response.text = """{
            "summary": "技術分野への興味が強い",
            "confidence": 0.8,
            "supporting_evidence": [1],
            "suggested_next_domains": ["art", "music"]
        }"""
        client._client.models.generate_content.return_value = response
        evidences = [
            Evidence(
                id=1,
                session_id=1,
                domain=DomainType.TECH.value,
                signal_count=2,
                summary_text="techに関するシグナル2件: Python tutorial",
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
        hypothesis = client.update_hypothesis(evidences, experiments, results)
        assert hypothesis["summary"] == "技術分野への興味が強い"
        assert hypothesis["confidence"] == 0.8
        assert hypothesis["supporting_evidence"] == [1]
        assert hypothesis["suggested_next_domains"] == ["art", "music"]

        call_args = client._client.models.generate_content.call_args
        prompt = call_args.kwargs["contents"]
        assert "Python tutorial" in prompt or "エビデンス" in prompt

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


class TestGenerateWeeklyNarrative:
    def test_returns_weekly_narrative(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = """{
            "weekly_insights": "技術分野への興味が高まっています",
            "change_from_past": "前週より実験完了数が増えました"
        }"""
        client._client.models.generate_content.return_value = response

        result = client.generate_weekly_narrative(
            recent_summary={"total_signals": 5, "completed_experiments": 2},
            previous_summary={"total_signals": 2, "completed_experiments": 0},
            top_domain="tech",
        )
        assert result["weekly_insights"] == "技術分野への興味が高まっています"
        assert result["change_from_past"] == "前週より実験完了数が増えました"

    def test_rejects_insights_over_200_chars(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = f"""{{
            "weekly_insights": "{'あ' * 201}",
            "change_from_past": "前週より増えました"
        }}"""
        client._client.models.generate_content.return_value = response

        with pytest.raises(ValueError):
            client.generate_weekly_narrative(
                recent_summary={"total_signals": 5, "completed_experiments": 2},
                previous_summary={"total_signals": 2, "completed_experiments": 0},
                top_domain="tech",
            )

    def test_rejects_change_from_past_over_200_chars(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = f"""{{
            "weekly_insights": "技術分野への興味が高まっています",
            "change_from_past": "{'あ' * 201}"
        }}"""
        client._client.models.generate_content.return_value = response

        with pytest.raises(ValueError):
            client.generate_weekly_narrative(
                recent_summary={"total_signals": 5, "completed_experiments": 2},
                previous_summary={"total_signals": 2, "completed_experiments": 0},
                top_domain="tech",
            )

    def test_rejects_malformed_json(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = "not valid json"
        client._client.models.generate_content.return_value = response

        with pytest.raises(ValueError):
            client.generate_weekly_narrative(
                recent_summary={},
                previous_summary={},
                top_domain="tech",
            )

    def test_sdk_exception_is_wrapped(self, client: DiscoveryGeminiClient) -> None:
        from google.genai.errors import APIError

        client._client.models.generate_content.side_effect = APIError(
            code=500, response_json={"error": "network error"}
        )
        with pytest.raises(RuntimeError) as exc_info:
            client.generate_weekly_narrative(
                recent_summary={},
                previous_summary={},
                top_domain="tech",
            )
        assert "network error" not in str(exc_info.value)
        assert "Gemini API" in str(exc_info.value)

    def test_request_includes_top_domain_and_summaries(self, client: DiscoveryGeminiClient) -> None:
        response = MagicMock()
        response.text = """{
            "weekly_insights": "技術分野への興味が高まっています",
            "change_from_past": "前週より実験完了数が増えました"
        }"""
        client._client.models.generate_content.return_value = response

        client.generate_weekly_narrative(
            recent_summary={"total_signals": 5, "completed_experiments": 2},
            previous_summary={"total_signals": 2, "completed_experiments": 0},
            top_domain="tech",
        )

        call_args = client._client.models.generate_content.call_args
        prompt = call_args.kwargs["contents"]
        assert "tech" in prompt
        assert "recent" in prompt.lower() or "直近" in prompt
        assert "previous" in prompt.lower() or "前週" in prompt
